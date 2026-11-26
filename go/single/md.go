// MDRenderer —— Go 单文件版
//
// 一个文件包含全部功能：文档模型、Markdown 解析、渲染指令、分页、日志（FLF + zlib 自压缩）、
// 配置（TMMF）、TMMF/LERF 解析、文件转换、快速排序、线程统计、可视化、单实例锁、CMD、
// IDP 内存栈、控制层 dispatch，以及命令行入口。
//
// 构建（单文件可执行）：
//
//	go build -trimpath -ldflags "-s -w" -o md.exe .\md.go
//
// 用法：
//
//	md.exe version
//	md.exe render   <file.md>                 # Markdown → HTML（stdout）
//	md.exe ui       <file.md>                 # 渲染指令 JSON
//	md.exe paged    <file.md> [w h]           # 分页 JSON（默认 A4 794x1123）
//	md.exe find     <file.md> <query>
//	md.exe replace  <file.md> <find> <replace> [--write]
//	md.exe insert   <file.md> <left> <right> <insert> [--write]
//	md.exe stats    <file.md>
//	md.exe log      <baseDir> [params op result]
//	md.exe logcache <baseDir> <op> <params>
//	md.exe selftest                          # 内置自检
//	md.exe dispatch <type> <json>            # 走控制层 dispatch（调试 IDP 契约）
package main

import (
	"bufio"
	"bytes"
	"compress/zlib"
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"math"

	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"runtime/metrics"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode"
	"unicode/utf8"
)

const (
	AppName    = "MDRenderer"
	Version    = "1.0.10"
	Edition    = "go"
	MaxLogSize = 10 * 1024 * 1024
	MaxUndo    = 500
)

// ============================================================================
// 基础工具
// ============================================================================

func runeLen(s string) int { return utf8.RuneCountInString(s) }

// esc 按 JSON 字符串规则转义（不含外层引号）。
func esc(s string) string {
	var b strings.Builder
	for _, r := range s {
		switch r {
		case '\\':
			b.WriteString(`\\`)
		case '"':
			b.WriteString(`\"`)
		case '\n':
			b.WriteString(`\n`)
		case '\r':
			b.WriteString(`\r`)
		case '\t':
			b.WriteString(`\t`)
		case '\b':
			b.WriteString(`\b`)
		case '\f':
			b.WriteString(`\f`)
		default:
			if r < 0x20 {
				b.WriteString(fmt.Sprintf(`\u%04x`, r))
			} else {
				b.WriteRune(r)
			}
		}
	}
	return b.String()
}

func jq(s string) string { return `"` + esc(s) + `"` }

func jb(v bool) string {
	if v {
		return "true"
	}
	return "false"
}

// runeOffToByte 把「第 n 个字符」转换成字节下标；越界时返回字符串长度。
func runeOffToByte(s string, off int) int {
	if off <= 0 {
		return 0
	}
	i, n := 0, 0
	for i < len(s) && n < off {
		_, size := utf8.DecodeRuneInString(s[i:])
		i += size
		n++
	}
	return i
}

// byteOffToRune 把字节下标转换成字符下标。
func byteOffToRune(s string, byteOff int) int {
	if byteOff <= 0 {
		return 0
	}
	if byteOff > len(s) {
		byteOff = len(s)
	}
	return utf8.RuneCountInString(s[:byteOff])
}

func clamp(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

func readTextFile(path string) (string, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func writeTextFile(path, content string) error {
	if dir := filepath.Dir(path); dir != "" {
		_ = os.MkdirAll(dir, 0o755)
	}
	return os.WriteFile(path, []byte(content), 0o644)
}

// ============================================================================
// 文档模型（doc）
// ============================================================================

type snapshot struct {
	content string
	cursor  int
}

type Document struct {
	mu       sync.Mutex
	content  string
	filePath string
	dirty    bool
	cursor   int
	undo     []snapshot
	redo     []snapshot
}

var docState = &Document{undo: []snapshot{{content: "", cursor: 0}}}

func (d *Document) pushUndo() {
	d.undo = append(d.undo, snapshot{content: d.content, cursor: d.cursor})
	if len(d.undo) > MaxUndo {
		d.undo = d.undo[len(d.undo)-MaxUndo:]
	}
}

func (d *Document) New() {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.content = ""
	d.filePath = ""
	d.dirty = false
	d.cursor = 0
	d.undo = []snapshot{{content: "", cursor: 0}}
	d.redo = nil
}

func (d *Document) Open(path string) int {
	d.mu.Lock()
	defer d.mu.Unlock()
	content, err := readTextFile(path)
	if err != nil {
		return -1
	}
	d.content = content
	d.filePath = path
	d.dirty = false
	d.cursor = runeLen(content)
	d.undo = []snapshot{{content: content, cursor: d.cursor}}
	d.redo = nil
	return runeLen(content)
}

func (d *Document) Save() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.filePath == "" {
		return -2
	}
	if err := writeTextFile(d.filePath, d.content); err != nil {
		return -1
	}
	d.dirty = false
	return 0
}

func (d *Document) SaveAs(path string) int {
	d.mu.Lock()
	defer d.mu.Unlock()
	if err := writeTextFile(path, d.content); err != nil {
		return -1
	}
	d.filePath = path
	d.dirty = false
	return 0
}

func (d *Document) SetContent(content string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.pushUndo()
	d.content = content
	d.cursor = clamp(d.cursor, 0, runeLen(content))
	d.dirty = true
	d.redo = nil
}

func (d *Document) InsertText(pos int, text string) int {
	d.mu.Lock()
	defer d.mu.Unlock()
	n := runeLen(d.content)
	pos = clamp(pos, 0, n)
	bpos := runeOffToByte(d.content, pos)
	d.pushUndo()
	d.content = d.content[:bpos] + text + d.content[bpos:]
	d.cursor = pos + runeLen(text)
	d.dirty = true
	d.redo = nil
	return 0
}

func (d *Document) DeleteText(start, end int) int {
	d.mu.Lock()
	defer d.mu.Unlock()
	n := runeLen(d.content)
	start = clamp(start, 0, n)
	end = clamp(end, start, n)
	if start == end {
		return 0
	}
	bs := runeOffToByte(d.content, start)
	be := runeOffToByte(d.content, end)
	d.pushUndo()
	d.content = d.content[:bs] + d.content[be:]
	d.cursor = start
	d.dirty = true
	d.redo = nil
	return 0
}

func (d *Document) GetText(start, end int) string {
	d.mu.Lock()
	defer d.mu.Unlock()
	n := runeLen(d.content)
	start = clamp(start, 0, n)
	end = clamp(end, start, n)
	return d.content[runeOffToByte(d.content, start):runeOffToByte(d.content, end)]
}

func (d *Document) Undo() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	if len(d.undo) <= 1 {
		return -1
	}
	d.redo = append(d.redo, snapshot{content: d.content, cursor: d.cursor})
	last := d.undo[len(d.undo)-1]
	d.undo = d.undo[:len(d.undo)-1]
	d.content = last.content
	d.cursor = clamp(last.cursor, 0, runeLen(d.content))
	d.dirty = true
	return 0
}

func (d *Document) Redo() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	if len(d.redo) == 0 {
		return -1
	}
	d.undo = append(d.undo, snapshot{content: d.content, cursor: d.cursor})
	last := d.redo[len(d.redo)-1]
	d.redo = d.redo[:len(d.redo)-1]
	d.content = last.content
	d.cursor = clamp(last.cursor, 0, runeLen(d.content))
	d.dirty = true
	return 0
}

func (d *Document) CanUndo() bool { return len(d.undo) > 1 }
func (d *Document) CanRedo() bool { return len(d.redo) > 0 }

func (d *Document) CursorLine() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	cut := runeOffToByte(d.content, d.cursor)
	return strings.Count(d.content[:cut], "\n")
}

func (d *Document) CursorCol() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	cut := runeOffToByte(d.content, d.cursor)
	lineStart := strings.LastIndex(d.content[:cut], "\n") + 1
	return runeLen(d.content[lineStart:cut])
}

func (d *Document) CharCount() int { d.mu.Lock(); defer d.mu.Unlock(); return runeLen(d.content) }

func (d *Document) WordCount() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	return countWords(d.content)
}

func (d *Document) LineCount() int {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.content == "" {
		return 0
	}
	return strings.Count(d.content, "\n") + 1
}

func (d *Document) Snapshot() (content, path string, dirty bool, cursor int) {
	d.mu.Lock()
	defer d.mu.Unlock()
	return d.content, d.filePath, d.dirty, d.cursor
}

// ============================================================================
// Markdown：行分类 / 内联样式 / HTML
// ============================================================================

type InlineStyle struct {
	Start int
	End   int
	Style string
}

func countWords(s string) int {
	if strings.TrimSpace(s) == "" {
		return 0
	}
	return len(strings.Fields(s))
}

// classifyLine 返回 0=empty,1=h1,2=h2,3=h3,4=quote,5=list,6=ordered,7=code,8=paragraph
func classifyLine(line string) int {
	if line == "" {
		return 0
	}
	if strings.HasPrefix(line, "### ") {
		return 3
	}
	if strings.HasPrefix(line, "## ") {
		return 2
	}
	if strings.HasPrefix(line, "# ") {
		return 1
	}
	if strings.HasPrefix(line, "> ") {
		return 4
	}
	if strings.HasPrefix(line, "- ") {
		return 5
	}
	if isOrderedStart(line) {
		return 6
	}
	if strings.HasPrefix(line, "    ") || strings.HasPrefix(line, "\t") {
		return 7
	}
	return 8
}

func isOrderedStart(s string) bool {
	i := 0
	for i < len(s) && s[i] >= '0' && s[i] <= '9' {
		i++
	}
	return i > 0 && i+1 < len(s) && s[i] == '.' && s[i+1] == ' '
}

func stripListMarker(t string) string {
	if strings.HasPrefix(t, "- ") || strings.HasPrefix(t, "* ") {
		return strings.TrimSpace(t[2:])
	}
	if isOrderedStart(t) {
		if i := strings.Index(t, ". "); i >= 0 {
			return strings.TrimSpace(t[i+2:])
		}
	}
	return strings.TrimSpace(t)
}

func isListStart(t string) bool {
	return strings.HasPrefix(t, "- ") || strings.HasPrefix(t, "* ") || isOrderedStart(t)
}

func isWideRune(r rune) bool {
	if r >= 0x1100 && r <= 0x115F {
		return true
	}
	if r >= 0x2E80 && r <= 0xA4CF {
		return true
	}
	if r >= 0xAC00 && r <= 0xD7A3 {
		return true
	}
	if r >= 0xF900 && r <= 0xFAFF {
		return true
	}
	if r >= 0xFE10 && r <= 0xFE6F {
		return true
	}
	if r >= 0xFF00 && r <= 0xFF60 {
		return true
	}
	if r >= 0xFFE0 && r <= 0xFFE6 {
		return true
	}
	if r >= 0x1F300 && r <= 0x1FAFF {
		return true
	}
	return false
}

func runeCols(r rune) int {
	switch r {
	case '\t':
		return 4
	case '\n', '\r':
		return 0
	}
	if r < 0x20 {
		return 0
	}
	if isWideRune(r) {
		return 2
	}
	return 1
}

func displayWidth(s string) int {
	w := 0
	for _, r := range s {
		w += runeCols(r)
	}
	return w
}

// wrapText 按半角列宽折行（CJK 记 2 列），优先在空格处断开。
func wrapText(text string, cols int) []string {
	if cols < 2 {
		cols = 2
	}
	var out []string
	for _, para := range strings.Split(text, "\n") {
		if para == "" {
			out = append(out, "")
			continue
		}
		var line strings.Builder
		width := 0
		lastSpace := -1 // 行内字节位置不可直接切，记录 rune 序号
		var runes []rune
		for _, r := range para {
			runes = append(runes, r)
		}
		start := 0
		for i := 0; i < len(runes); i++ {
			r := runes[i]
			rw := runeCols(r)
			if width+rw > cols && i > start {
				cut := i
				if lastSpace > start {
					cut = lastSpace
				}
				out = append(out, strings.TrimRight(string(runes[start:cut]), " "))
				start = cut
				for start < i && runes[start] == ' ' {
					start++
				}
				width = 0
				for k := start; k < i; k++ {
					width += runeCols(runes[k])
				}
				lastSpace = -1
				for k := start; k < i; k++ {
					if runes[k] == ' ' {
						lastSpace = k
					}
				}
			}
			width += rw
			if r == ' ' {
				lastSpace = i
			}
			_ = line
		}
		if start < len(runes) {
			out = append(out, strings.TrimRight(string(runes[start:]), " "))
		} else if start == len(runes) {
			out = append(out, "")
		}
	}
	if len(out) == 0 {
		out = append(out, "")
	}
	return out
}

// findDelimited 找到成对分隔符（不含转义），回调返回替换文本。
func replacePairs(s, delim, openTag, closeTag string, allowNested bool) string {
	if delim == "" {
		return s
	}
	var b strings.Builder
	i := 0
	for i < len(s) {
		start := strings.Index(s[i:], delim)
		if start < 0 {
			b.WriteString(s[i:])
			break
		}
		start += i
		contentStart := start + len(delim)
		end := strings.Index(s[contentStart:], delim)
		if end < 0 || (!allowNested && end == 0) {
			b.WriteString(s[i:])
			break
		}
		end += contentStart
		b.WriteString(s[i:start])
		b.WriteString(openTag)
		b.WriteString(s[contentStart:end])
		b.WriteString(closeTag)
		i = end + len(delim)
	}
	return b.String()
}

func replaceItalic(s string) string {
	// 只处理单星号；跳过多星号序列。
	var b strings.Builder
	i := 0
	for i < len(s) {
		if s[i] != '*' {
			b.WriteByte(s[i])
			i++
			continue
		}
		run := 0
		for i+run < len(s) && s[i+run] == '*' {
			run++
		}
		if run >= 2 {
			b.WriteString(s[i : i+run])
			i += run
			continue
		}
		end := strings.IndexByte(s[i+1:], '*')
		if end < 0 {
			b.WriteByte('*')
			i++
			continue
		}
		end += i + 1
		// 结束星号后不能再接星号
		if end+1 < len(s) && s[end+1] == '*' {
			b.WriteByte('*')
			i++
			continue
		}
		b.WriteString("<em>")
		b.WriteString(s[i+1 : end])
		b.WriteString("</em>")
		i = end + 1
	}
	return b.String()
}

func inlineMD(s string) string {
	s = replacePairs(s, "***", "<strong><em>", "</em></strong>", false)
	s = replacePairs(s, "**", "<strong>", "</strong>", false)
	s = replaceItalic(s)
	s = replacePairs(s, "``", "<code>", "</code>", false)
	s = replacePairs(s, "~~", "<del>", "</del>", false)
	// [文字](链接)
	var b strings.Builder
	i := 0
	for i < len(s) {
		lb := strings.IndexByte(s[i:], '[')
		if lb < 0 {
			b.WriteString(s[i:])
			break
		}
		lb += i
		rb := strings.IndexByte(s[lb+1:], ']')
		if rb < 0 {
			b.WriteString(s[i:])
			break
		}
		rb += lb + 1
		if rb+1 >= len(s) || s[rb+1] != '(' {
			b.WriteString(s[i : lb+1])
			i = lb + 1
			continue
		}
		rp := strings.IndexByte(s[rb+2:], ')')
		if rp < 0 {
			b.WriteString(s[i:])
			break
		}
		rp += rb + 2
		b.WriteString(s[i:lb])
		b.WriteString(`<a href="`)
		b.WriteString(escHTML(s[rb+2 : rp]))
		b.WriteString(`">`)
		b.WriteString(s[lb+1 : rb])
		b.WriteString("</a>")
		i = rp + 1
	}
	return b.String()
}

func escHTML(s string) string {
	s = strings.ReplaceAll(s, "&", "&amp;")
	s = strings.ReplaceAll(s, "<", "&lt;")
	s = strings.ReplaceAll(s, ">", "&gt;")
	return s
}

func markdownToHTML(md string) string {
	var b strings.Builder
	inCode := false
	inUL, inOL := false, false
	closeLists := func() {
		if inUL {
			b.WriteString("</ul>\n")
			inUL = false
		}
		if inOL {
			b.WriteString("</ol>\n")
			inOL = false
		}
	}
	lines := strings.Split(md, "\n")
	for _, line := range lines {
		if strings.HasPrefix(line, "```") {
			closeLists()
			if inCode {
				b.WriteString("</code></pre>\n")
				inCode = false
			} else {
				b.WriteString("<pre><code>")
				inCode = true
			}
			continue
		}
		if inCode {
			b.WriteString(escHTML(line))
			b.WriteByte('\n')
			continue
		}
		switch {
		case strings.HasPrefix(line, "### "):
			closeLists()
			b.WriteString("<h3>" + inlineMD(line[4:]) + "</h3>\n")
		case strings.HasPrefix(line, "## "):
			closeLists()
			b.WriteString("<h2>" + inlineMD(line[3:]) + "</h2>\n")
		case strings.HasPrefix(line, "# "):
			closeLists()
			b.WriteString("<h1>" + inlineMD(line[2:]) + "</h1>\n")
		case strings.HasPrefix(line, "> "):
			closeLists()
			b.WriteString("<blockquote>" + inlineMD(line[2:]) + "</blockquote>\n")
		case strings.HasPrefix(line, "- ") || strings.HasPrefix(line, "* "):
			if inOL {
				b.WriteString("</ol>\n")
				inOL = false
			}
			if !inUL {
				b.WriteString("<ul>\n")
				inUL = true
			}
			b.WriteString("<li>" + inlineMD(strings.TrimSpace(line[2:])) + "</li>\n")
		case isOrderedStart(line):
			if inUL {
				b.WriteString("</ul>\n")
				inUL = false
			}
			if !inOL {
				b.WriteString("<ol>\n")
				inOL = true
			}
			if i := strings.Index(line, ". "); i >= 0 {
				b.WriteString("<li>" + inlineMD(strings.TrimSpace(line[i+2:])) + "</li>\n")
			}
		case strings.TrimSpace(line) == "":
			closeLists()
			b.WriteByte('\n')
		case strings.TrimSpace(line) == "---" || strings.TrimSpace(line) == "***":
			closeLists()
			b.WriteString("<hr>\n")
		default:
			closeLists()
			b.WriteString("<p>" + inlineMD(line) + "</p>\n")
		}
	}
	closeLists()
	if inCode {
		b.WriteString("</code></pre>\n")
	}
	return b.String()
}

// analyzeInlineStyles 返回内联样式区间（按 Java 版顺序：粗斜、粗、斜、代码、删除线）。
func analyzeInlineStyles(line string) []InlineStyle {
	var list []InlineStyle
	appendPairs := func(delim, style string) {
		i := 0
		for i < len(line) {
			s := strings.Index(line[i:], delim)
			if s < 0 {
				return
			}
			s += i
			e := strings.Index(line[s+len(delim):], delim)
			if e < 0 {
				return
			}
			e += s + len(delim)
			list = append(list, InlineStyle{Start: byteOffToRune(line, s), End: byteOffToRune(line, e+len(delim)), Style: style})
			i = e + len(delim)
		}
	}
	appendPairs("***", "bolditalic")
	appendPairs("**", "bold")
	// 单星号斜体
	i := 0
	for i < len(line) {
		if line[i] != '*' {
			i++
			continue
		}
		run := 0
		for i+run < len(line) && line[i+run] == '*' {
			run++
		}
		if run >= 2 {
			i += run
			continue
		}
		e := strings.IndexByte(line[i+1:], '*')
		if e < 0 {
			break
		}
		e += i + 1
		if e+1 < len(line) && line[e+1] == '*' {
			i++
			continue
		}
		list = append(list, InlineStyle{Start: byteOffToRune(line, i), End: byteOffToRune(line, e+1), Style: "italic"})
		i = e + 1
	}
	appendPairs("``", "code")
	appendPairs("~~", "strike")
	return list
}

// ============================================================================
// 渲染：渲染指令 / 分页
// ============================================================================

type element struct {
	Kind        string
	Level       int
	Ordered     bool
	Items       []string
	Text        string
	Src         string
	Action      string
	Placeholder string
	StyleIndex  string
	Width       int
	Height      int
	Headers     []string
	Rows        [][]string
	Lines       []string // 代码块正文
	Easter      bool
	RawJSON     string // 已构造好的控件 JSON
}

func parseControl(trimmed string) *element {
	if !strings.HasPrefix(trimmed, "{") || !strings.HasSuffix(trimmed, "}") {
		return nil
	}
	inner := strings.TrimSpace(trimmed[1 : len(trimmed)-1])
	colon := strings.Index(inner, ":")
	if colon <= 0 {
		return nil
	}
	tag := strings.TrimSpace(inner[:colon])
	payload := strings.TrimSpace(inner[colon+1:])
	switch tag {
	case "button":
		text, action := payload, ""
		if bar := strings.Index(payload, "|"); bar >= 0 {
			text, action = payload[:bar], payload[bar+1:]
		}
		raw := `{"kind":"button","text":` + jq(text) + `,"action":` + jq(action) + `}`
		return &element{Kind: "button", RawJSON: raw}
	case "input":
		return &element{Kind: "input", Placeholder: payload, RawJSON: `{"kind":"input","placeholder":` + jq(payload) + `}`}
	case "menu":
		raw := `{"kind":"menu","items":[`
		menus := strings.Split(payload, ";")
		for m, menu := range menus {
			if m > 0 {
				raw += ","
			}
			parts := strings.SplitN(menu, ">", 2)
			raw += `{"label":` + jq(strings.TrimSpace(parts[0])) + `,"sub":[`
			if len(parts) > 1 {
				subs := strings.Split(parts[1], ",")
				for s, sub := range subs {
					if s > 0 {
						raw += ","
					}
					raw += jq(strings.TrimSpace(sub))
				}
			}
			raw += "]}"
		}
		raw += "]}"
		return &element{Kind: "menu", RawJSON: raw}
	case "image":
		p := strings.Split(payload, "|")
		name, src := "", ""
		w, h := 0, 0
		if len(p) > 0 {
			name = strings.TrimSpace(p[0])
		}
		if len(p) > 1 {
			src = strings.TrimSpace(p[1])
		}
		if len(p) > 2 {
			w, _ = strconv.Atoi(strings.TrimSpace(p[2]))
		}
		if len(p) > 3 {
			h, _ = strconv.Atoi(strings.TrimSpace(p[3]))
		}
		raw := `{"kind":"image","text":` + jq(name) + `,"src":` + jq(src) +
			`,"width":` + strconv.Itoa(w) + `,"height":` + strconv.Itoa(h) + `}`
		return &element{Kind: "image", RawJSON: raw, Text: name, Width: w, Height: h}
	case "table":
		headStr, rowsStr := payload, ""
		if bar := strings.Index(payload, "|"); bar >= 0 {
			headStr, rowsStr = payload[:bar], payload[bar+1:]
		}
		var heads []string
		for _, x := range strings.Split(headStr, ",") {
			heads = append(heads, strings.TrimSpace(x))
		}
		var rows [][]string
		if rowsStr != "" {
			for _, r := range strings.Split(rowsStr, ";") {
				var cells []string
				for _, c := range strings.Split(r, ",") {
					cells = append(cells, strings.TrimSpace(c))
				}
				rows = append(rows, cells)
			}
		}
		raw := `{"kind":"table","headers":[`
		for i, h := range heads {
			if i > 0 {
				raw += ","
			}
			raw += jq(h)
		}
		raw += `],"rows":[`
		for ri, row := range rows {
			if ri > 0 {
				raw += ","
			}
			raw += "["
			for ci, cell := range row {
				if ci > 0 {
					raw += ","
				}
				raw += jq(cell)
			}
			raw += "]"
		}
		raw += "]}"
		return &element{Kind: "table", RawJSON: raw, Headers: heads, Rows: rows}
	}
	return nil
}

// parseBlocks 将 Markdown 解析为元素序列（UI 渲染与分页共用）。
func parseBlocks(content string) []element {
	var els []element
	lines := strings.Split(content, "\n")
	i := 0
	for i < len(lines) {
		line := lines[i]
		trimmed := strings.TrimSpace(line)
		if strings.HasPrefix(trimmed, "```") {
			i++
			var buf []string
			for i < len(lines) && !strings.HasPrefix(strings.TrimSpace(lines[i]), "```") {
				buf = append(buf, lines[i])
				i++
			}
			i++
			els = append(els, element{Kind: "code", Lines: buf, Text: strings.Join(buf, "\n")})
			continue
		}
		if ctrl := parseControl(trimmed); ctrl != nil {
			els = append(els, *ctrl)
			i++
			continue
		}
		if isListStart(trimmed) {
			ordered := isOrderedStart(trimmed)
			var items []string
			for i < len(lines) && isListStart(strings.TrimSpace(lines[i])) {
				items = append(items, stripListMarker(strings.TrimSpace(lines[i])))
				i++
			}
			els = append(els, element{Kind: "list", Ordered: ordered, Items: items})
			continue
		}
		if strings.HasPrefix(trimmed, "#") {
			level := 0
			for level < len(trimmed) && trimmed[level] == '#' {
				level++
			}
			if level >= 1 && level <= 6 && level < len(trimmed) && trimmed[level] == ' ' {
				els = append(els, element{Kind: "heading", Level: level, Text: strings.TrimSpace(trimmed[level:])})
				i++
				continue
			}
		}
		if strings.HasPrefix(trimmed, ">") {
			els = append(els, element{Kind: "quote", Text: strings.TrimSpace(trimmed[1:])})
			i++
			continue
		}
		if trimmed == "---" || trimmed == "***" {
			els = append(els, element{Kind: "separator"})
			i++
			continue
		}
		if len(trimmed) >= 2 && strings.HasPrefix(trimmed, "[") && strings.HasSuffix(trimmed, "]") {
			els = append(els, element{Kind: "image", Text: trimmed[1 : len(trimmed)-1]})
			i++
			continue
		}
		if strings.HasPrefix(trimmed, ":\"") {
			if q := strings.LastIndex(trimmed, "\""); q > 1 {
				idx := strings.TrimSpace(trimmed[q+1:])
				if _, err := strconv.Atoi(idx); err == nil {
					els = append(els, element{Kind: "dialog", Text: trimmed[2:q], StyleIndex: idx})
					i++
					continue
				}
			}
		}
		if strings.HasPrefix(trimmed, "≈") {
			if q := strings.LastIndex(trimmed, "≈"); q > 0 {
				idx := strings.TrimSpace(trimmed[q+1:])
				if _, err := strconv.Atoi(idx); err == nil {
					els = append(els, element{Kind: "link", Text: trimmed[1:q], StyleIndex: idx})
					i++
					continue
				}
			}
		}
		if strings.HasPrefix(trimmed, "\\\\>") {
			els = append(els, element{Kind: "paragraph", Text: trimmed, Easter: true})
			i++
			continue
		}
		if trimmed == "" {
			els = append(els, element{Kind: "blank"})
			i++
			continue
		}
		els = append(els, element{Kind: "paragraph", Text: line})
		i++
	}
	return els
}

func uiInstructions(content string) string {
	var b strings.Builder
	b.WriteString(`{"view":"ui","layout":"flow","elements":[`)
	first := true
	sep := func() {
		if !first {
			b.WriteByte(',')
		}
		first = false
	}
	for _, e := range parseBlocks(content) {
		sep()
		switch e.Kind {
		case "code":
			b.WriteString(`{"kind":"code","text":` + jq(e.Text) + `}`)
		case "heading":
			b.WriteString(`{"kind":"heading","level":` + strconv.Itoa(e.Level) + `,"text":` + jq(e.Text) + `}`)
		case "quote":
			b.WriteString(`{"kind":"quote","text":` + jq(e.Text) + `}`)
		case "separator":
			b.WriteString(`{"kind":"separator"}`)
		case "list":
			b.WriteString(`{"kind":"list","ordered":` + jb(e.Ordered) + `,"items":[`)
			for i, it := range e.Items {
				if i > 0 {
					b.WriteByte(',')
				}
				b.WriteString(jq(it))
			}
			b.WriteString(`]}`)
		case "blank":
			b.WriteString(`{"kind":"paragraph","text":"","inline":[]}`)
		case "image":
			b.WriteString(`{"kind":"image","text":` + jq(e.Text) + `}`)
		case "dialog":
			b.WriteString(`{"kind":"dialog","text":` + jq(e.Text) + `,"styleIndex":` + e.StyleIndex + `}`)
		case "link":
			b.WriteString(`{"kind":"link","path":` + jq(e.Text) + `,"styleIndex":` + e.StyleIndex + `}`)
		case "button", "input", "menu", "table":
			b.WriteString(e.RawJSON)
		default:
			styles := analyzeInlineStyles(e.Text)
			b.WriteString(`{"kind":"paragraph","text":` + jq(e.Text) + `,"inline":[`)
			for i, st := range styles {
				if i > 0 {
					b.WriteByte(',')
				}
				b.WriteString(`{"s":` + strconv.Itoa(st.Start) + `,"e":` + strconv.Itoa(st.End) + `,"st":` + jq(st.Style) + `}`)
			}
			b.WriteString(`]}`)
		}
	}
	b.WriteString(`]}`)
	return b.String()
}

// ---- 分页：超宽换行、超长换页、每页带页码 ----

type piece struct {
	json  string
	lines int
}

func elementLines(e element, cols int, maxLines int) []piece {
	var out []piece
	switch e.Kind {
	case "blank":
		return nil
	case "separator":
		return []piece{{`{"kind":"separator"}`, 1}}
	case "heading":
		wrapped := wrapText(e.Text, cols)
		extra := 1
		if e.Level == 1 {
			extra = 2
		}
		if len(wrapped) == 0 {
			wrapped = []string{""}
		}
		for _, chunk := range chunkLines(wrapped, imax(1, maxLines-extra)) {
			if len(chunk) == 1 {
				out = append(out, piece{`{"kind":"heading","level":` + strconv.Itoa(e.Level) + `,"text":` + jq(chunk[0]) + `}`, 1 + extra})
			} else {
				out = append(out, piece{`{"kind":"heading","level":` + strconv.Itoa(e.Level) + `,"text":` + jq(strings.Join(chunk, "\n")) + `}`, len(chunk) + extra})
			}
		}
	case "paragraph":
		wrapped := wrapText(e.Text, cols)
		for _, chunk := range chunkLines(wrapped, maxLines) {
			raw := `{"kind":"paragraph","text":` + jq(strings.Join(chunk, "\n"))
			if e.Easter {
				raw += `,"easter":true`
			}
			raw += `}`
			out = append(out, piece{raw, len(chunk)})
		}
	case "quote":
		wrapped := wrapText(e.Text, cols)
		for _, chunk := range chunkLines(wrapped, imax(1, maxLines-1)) {
			out = append(out, piece{`{"kind":"quote","text":` + jq(strings.Join(chunk, "\n")) + `}`, len(chunk) + 1})
		}
	case "code":
		body := e.Lines
		if len(body) == 0 {
			body = []string{}
		}
		cap := imax(1, maxLines-2)
		for start := 0; start < len(body) || start == 0; start += cap {
			end := imin(start+cap, len(body))
			chunk := body[start:end]
			out = append(out, piece{`{"kind":"code","text":` + jq(strings.Join(chunk, "\n")) + `}`, len(chunk) + 2})
			if len(body) == 0 {
				break
			}
			if end >= len(body) {
				break
			}
		}
	case "list":
		ordered := e.Ordered
		nextStart := 1
		var group []string
		groupLines := 0
		flush := func() {
			if len(group) == 0 {
				return
			}
			raw := `{"kind":"list","ordered":` + jb(ordered) + `,"items":[`
			for i, it := range group {
				if i > 0 {
					raw += ","
				}
				raw += jq(it)
			}
			raw += `]`
			if ordered && nextStart != 1 {
				raw += `,"start":` + strconv.Itoa(nextStart)
			}
			raw += `}`
			out = append(out, piece{raw, imax(1, groupLines)})
			if ordered {
				nextStart += len(group)
			}
			group, groupLines = nil, 0
		}
		for _, item := range e.Items {
			wrapped := wrapText(item, cols)
			if groupLines+len(wrapped) > maxLines && len(group) > 0 {
				flush()
			}
			if len(wrapped) > maxLines {
				// 超长单项：拆成多个列表元素，保持语义
				for _, chunk := range chunkLines(wrapped, maxLines) {
					group = append(group, strings.Join(chunk, "\n"))
					groupLines += len(chunk)
					flush()
				}
				continue
			}
			group = append(group, item)
			groupLines += len(wrapped)
		}
		flush()
	case "image":
		if e.RawJSON != "" {
			out = append(out, piece{e.RawJSON, 2})
		} else {
			out = append(out, piece{`{"kind":"image","text":` + jq(e.Text) + `}`, 2})
		}
	case "dialog":
		out = append(out, piece{`{"kind":"dialog","text":` + jq(e.Text) + `,"styleIndex":` + e.StyleIndex + `}`, 1})
	case "link":
		out = append(out, piece{`{"kind":"link","path":` + jq(e.Text) + `,"styleIndex":` + e.StyleIndex + `}`, 1})
	case "button", "input", "menu", "table":
		out = append(out, piece{e.RawJSON, 1})
	default:
		out = append(out, piece{`{"kind":"paragraph","text":` + jq(e.Text) + `}`, 1})
	}
	return out
}

func chunkLines(lines []string, n int) [][]string {
	if n < 1 {
		n = 1
	}
	if len(lines) == 0 {
		return [][]string{{""}}
	}
	var out [][]string
	for i := 0; i < len(lines); i += n {
		end := imin(i+n, len(lines))
		out = append(out, lines[i:end])
	}
	return out
}

func pagedJSON(content string, pageWidth, pageHeight int) string {
	if pageWidth <= 0 {
		pageWidth = 794
	}
	if pageHeight <= 0 {
		pageHeight = 1123
	}
	lineHeight := 22.0
	pageBody := math.Max(float64(pageHeight-90), 100)
	linesPerPage := imax(int(pageBody/lineHeight), 1)
	cols := imax(pageWidth/8, 16) // 8px = 半角列；CJK 记 2 列

	var pages []string
	var cur []string
	curLines := 0
	flush := func() {
		pages = append(pages, strings.Join(cur, ","))
		cur = nil
		curLines = 0
	}

	for _, e := range parseBlocks(content) {
		for _, p := range elementLines(e, cols, linesPerPage) {
			if p.lines <= 0 {
				continue
			}
			if curLines+p.lines > linesPerPage && curLines > 0 {
				flush()
			}
			cur = append(cur, p.json)
			curLines += p.lines
		}
	}
	if len(cur) > 0 {
		flush()
	}
	if len(pages) == 0 {
		pages = []string{""}
	}

	var b strings.Builder
	b.WriteString(`{"view":"paged","pageWidth":` + strconv.Itoa(pageWidth) +
		`,"pageHeight":` + strconv.Itoa(pageHeight) +
		`,"linesPerPage":` + strconv.Itoa(linesPerPage) +
		`,"pageCount":` + strconv.Itoa(len(pages)) + `,"pages":[`)
	for i, body := range pages {
		if i > 0 {
			b.WriteByte(',')
		}
		b.WriteString(`{"page":` + strconv.Itoa(i+1) + `,"elements":[` + body + `],"footer":{"text":"— ` + strconv.Itoa(i+1) + ` —"}}`)
	}
	b.WriteString(`]}`)
	return b.String()
}

// ---- 文本查找 / 替换 / 插入（字符偏移）----

func findJSON(content, query string) string {
	if query == "" {
		return `{"count":0,"positions":[]}`
	}
	var pos []int
	byteFrom := 0
	for byteFrom <= len(content) {
		p := strings.Index(content[byteFrom:], query)
		if p < 0 {
			break
		}
		p += byteFrom
		pos = append(pos, byteOffToRune(content, p))
		byteFrom = p + len(query)
	}
	var b strings.Builder
	b.WriteString(`{"count":` + strconv.Itoa(len(pos)) + `,"positions":[`)
	for i, p := range pos {
		if i > 0 {
			b.WriteByte(',')
		}
		b.WriteString(strconv.Itoa(p))
	}
	b.WriteString(`]}`)
	return b.String()
}

func replaceJSON(content, find, repl string) string {
	if find == "" {
		return `{"count":0,"text":` + jq(content) + `}`
	}
	count := strings.Count(content, find)
	return `{"count":` + strconv.Itoa(count) + `,"text":` + jq(strings.ReplaceAll(content, find, repl)) + `}`
}

func insertJSON(content, left, right, insert string) string {
	if left == "" || right == "" {
		return `{"count":0,"text":` + jq(content) + `}`
	}
	var b strings.Builder
	count := 0
	rest := content
	for {
		li := strings.Index(rest, left)
		if li < 0 {
			b.WriteString(rest)
			break
		}
		afterLeft := li + len(left)
		ri := strings.Index(rest[afterLeft:], right)
		if ri < 0 {
			b.WriteString(rest)
			break
		}
		ri += afterLeft
		b.WriteString(rest[:afterLeft])
		b.WriteString(insert)
		b.WriteString(rest[afterLeft : ri+len(right)])
		count++
		rest = rest[ri+len(right):]
	}
	return `{"count":` + strconv.Itoa(count) + `,"text":` + jq(b.String()) + `}`
}

func imax(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func imin(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// ============================================================================
// 日志（FLF，纯日志 + 自压缩）
// ============================================================================

type LogState struct {
	mu         sync.Mutex
	baseDir    string
	logDir     string
	cacheDir   string
	logFile    string
	compFile   string
	lastError  string
	startup    bool
	openFile   bool
	saveFile   bool
	newFile    bool
	errorLog   bool
	singleInst bool
	sdf        string
}

var logState = &LogState{
	baseDir:    ".",
	startup:    true,
	openFile:   true,
	saveFile:   true,
	newFile:    true,
	errorLog:   true,
	singleInst: true,
	sdf:        "2006-01-02 15:04:05",
}

func (l *LogState) init(base string) int {
	l.mu.Lock()
	defer l.mu.Unlock()
	l.baseDir = base
	l.logDir = filepath.Join(base, "log")
	l.cacheDir = filepath.Join(base, "cache", "log")
	if err := os.MkdirAll(l.logDir, 0o755); err != nil {
		l.lastError = err.Error()
		return -1
	}
	if err := os.MkdirAll(l.cacheDir, 0o755); err != nil {
		l.lastError = err.Error()
		return -1
	}
	date := time.Now().Format("2006-01-02")
	l.logFile = filepath.Join(l.cacheDir, date+".log")
	l.compFile = filepath.Join(l.logDir, date+".log.zst")

	if fileExists(l.compFile) && !fileExists(l.logFile) {
		if err := decompressFile(l.compFile, l.logFile); err != nil {
			l.lastError = "解压日志失败: " + err.Error()
		}
	}
	if info, err := os.Stat(l.logFile); err != nil || info.Size() == 0 {
		if err := l.writeHeaderLocked(); err != nil {
			l.lastError = err.Error()
			return -1
		}
	}
	return 0
}

func (l *LogState) writeHeaderLocked() error {
	header := "FLF.;utf8\nicon_mdrenderer\nMD 编辑器 V" + Version + "\n00000000000000000000\n…\n"
	return writeTextFile(l.logFile, header)
}

func (l *LogState) enabledFor(op string) bool {
	switch op {
	case "启动", "退出", "版本", "Java版本", "操作系统", "后端初始化", "后端关闭", "应用启动", "应用关闭":
		return l.startup
	case "打开文件", "读取文件":
		return l.openFile
	case "保存文件", "写入文件", "另存为":
		return l.saveFile
	case "新建文件":
		return l.newFile
	case "单实例检测":
		return l.singleInst
	default:
		return l.errorLog
	}
}

func (l *LogState) setEnabled(cat string, on bool) {
	l.mu.Lock()
	defer l.mu.Unlock()
	switch cat {
	case "startup":
		l.startup = on
	case "openfile":
		l.openFile = on
	case "savefile":
		l.saveFile = on
	case "newfile":
		l.newFile = on
	case "error":
		l.errorLog = on
	case "singleinstance":
		l.singleInst = on
	}
}

func (l *LogState) isEnabled(cat string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	switch cat {
	case "startup":
		return l.startup
	case "openfile":
		return l.openFile
	case "savefile":
		return l.saveFile
	case "newfile":
		return l.newFile
	case "error":
		return l.errorLog
	case "singleinstance":
		return l.singleInst
	}
	return false
}

func (l *LogState) write(params, op, result string, endGroup bool) {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.logFile == "" || !l.enabledFor(op) {
		return
	}
	now := time.Now().Format(l.sdf)
	entry := "<\\" + now + ">" + params + "{" + op + "}" + result + "。<" + now + "/>"
	if endGroup {
		entry += ";"
	}
	_ = l.appendEntryLocked(entry)
}

func (l *LogState) writeEnd(params, op, result string) { l.write(params, op, result, true) }

func (l *LogState) appendEntryLocked(entry string) error {
	if l.logFile == "" {
		return fmt.Errorf("log not initialized")
	}
	if info, err := os.Stat(l.logFile); err == nil && info.Size() > MaxLogSize {
		_ = os.Remove(l.logFile)
		if err := l.writeHeaderLocked(); err != nil {
			return err
		}
	}
	content := ""
	if b, err := os.ReadFile(l.logFile); err == nil {
		content = string(b)
	} else {
		if err := l.writeHeaderLocked(); err != nil {
			return err
		}
		b, _ := os.ReadFile(l.logFile)
		content = string(b)
	}
	content = strings.TrimSuffix(content, "…\n")
	content = strings.TrimSuffix(content, "…")
	content += entry + "\n…\n"

	lines := strings.Split(content, "\n")
	if len(lines) >= 4 {
		lines[3] = fmt.Sprintf("%020d", len([]byte(content)))
		content = strings.Join(lines, "\n")
	}
	return writeTextFile(l.logFile, content)
}

func (l *LogState) shutdown() {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.logFile != "" && fileExists(l.logFile) {
		if err := compressFile(l.logFile, l.compFile); err != nil {
			l.lastError = "压缩日志失败: " + err.Error()
		} else {
			_ = os.Remove(l.logFile)
		}
	}
}

func (l *LogState) clearAll() int {
	l.mu.Lock()
	defer l.mu.Unlock()
	count := 0
	if l.logDir != "" {
		entries, _ := os.ReadDir(l.logDir)
		for _, e := range entries {
			if !e.IsDir() && (strings.HasSuffix(e.Name(), ".zst") || strings.HasSuffix(e.Name(), ".log")) {
				if os.Remove(filepath.Join(l.logDir, e.Name())) == nil {
					count++
				}
			}
		}
	}
	if l.cacheDir != "" {
		entries, _ := os.ReadDir(l.cacheDir)
		for _, e := range entries {
			if !e.IsDir() && strings.HasSuffix(e.Name(), ".log") {
				if os.Remove(filepath.Join(l.cacheDir, e.Name())) == nil {
					count++
				}
			}
		}
	}
	if l.logFile != "" {
		_ = l.writeHeaderLocked()
	}
	return count
}

func (l *LogState) size() int64 {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.logFile == "" {
		return 0
	}
	if info, err := os.Stat(l.logFile); err == nil {
		return info.Size()
	}
	return 0
}

// cacheLookup 返回相同 op+params 的最近一条结果；未命中返回 ("", false)。
func (l *LogState) cacheLookup(op, params string) (string, bool) {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.logFile == "" || !fileExists(l.logFile) {
		return "", false
	}
	b, err := os.ReadFile(l.logFile)
	if err != nil {
		return "", false
	}
	lines := strings.Split(string(b), "\n")
	want := b64(params)
	for i := len(lines) - 1; i >= 0; i-- {
		idx := strings.Index(lines[i], "CACHE:")
		if idx < 0 {
			continue
		}
		payload := lines[i][idx+len("CACHE:"):]
		if end := strings.Index(payload, "。<"); end >= 0 {
			payload = payload[:end]
		}
		c1 := strings.Index(payload, ":")
		if c1 < 0 {
			continue
		}
		c2 := strings.Index(payload[c1+1:], ":")
		if c2 < 0 {
			continue
		}
		c2 += c1 + 1
		op2 := payload[:c1]
		p2 := payload[c1+1 : c2]
		r2 := payload[c2+1:]
		if op2 == op && p2 == want {
			if data, err := base64.StdEncoding.DecodeString(r2); err == nil {
				return string(data), true
			}
		}
	}
	return "", false
}

func (l *LogState) cachePut(op, params, result string) int {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.logFile == "" {
		l.mu.Unlock()
		l.init(l.baseDir)
		l.mu.Lock()
	}
	if l.logFile == "" {
		return -1
	}
	now := time.Now().Format(l.sdf)
	entry := "<\\" + now + ">CACHE:" + op + ":" + b64(params) + ":" + b64(result) + "。<" + now + "/>"
	if err := l.appendEntryLocked(entry); err != nil {
		return -1
	}
	return 0
}

func b64(s string) string { return base64.StdEncoding.EncodeToString([]byte(s)) }

// ============================================================================
// 压缩（zlib/DEFLATE；扩展名沿用 .zst）
// ============================================================================

func compressFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	if dir := filepath.Dir(dst); dir != "" {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return err
		}
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()
	zw, err := zlib.NewWriterLevel(out, zlib.BestSpeed)
	if err != nil {
		return err
	}
	if _, err := io.Copy(zw, in); err != nil {
		zw.Close()
		return err
	}
	if err := zw.Close(); err != nil {
		return err
	}
	return out.Sync()
}

func decompressFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	zr, err := zlib.NewReader(in)
	if err != nil {
		return err
	}
	defer zr.Close()
	if dir := filepath.Dir(dst); dir != "" {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return err
		}
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()
	if _, err := io.Copy(out, zr); err != nil {
		return err
	}
	return out.Sync()
}

func compressRatio(src string) float64 {
	info, err := os.Stat(src)
	if err != nil || info.Size() == 0 {
		return 0
	}
	tmp := filepath.Join(os.TempDir(), "md_ratio_tmp.zst")
	defer os.Remove(tmp)
	if err := compressFile(src, tmp); err != nil {
		return 0
	}
	cinfo, err := os.Stat(tmp)
	if err != nil {
		return 0
	}
	return float64(cinfo.Size()) / float64(info.Size())
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}

// ============================================================================
// 配置（Properties 风格 TMMF 文件）
// ============================================================================

type Config struct {
	mu      sync.Mutex
	baseDir string
	values  map[string]string
	loaded  bool
}

var cfg = &Config{values: map[string]string{}}

func (c *Config) path() string {
	return filepath.Join(c.baseDir, "resources", "cmd", "settings.tmmf")
}

func (c *Config) init(base string) int {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.baseDir = base
	c.values = map[string]string{}
	return 0
}

func (c *Config) loadLocked() int {
	c.loaded = true
	b, err := os.ReadFile(c.path())
	if err != nil {
		return -1
	}
	sc := bufio.NewScanner(bytes.NewReader(b))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, "!") {
			continue
		}
		eq := strings.Index(line, "=")
		if eq <= 0 {
			continue
		}
		c.values[strings.TrimSpace(line[:eq])] = strings.TrimSpace(line[eq+1:])
	}
	return 0
}

func (c *Config) loadSettings() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.loadLocked()
}

func (c *Config) saveSettings() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	var b strings.Builder
	b.WriteString("#MDRenderer Settings\n")
	b.WriteString("#" + time.Now().Format("Mon Jan 02 15:04:05 MST 2006") + "\n")
	keys := make([]string, 0, len(c.values))
	for k := range c.values {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, k := range keys {
		b.WriteString(k + "=" + c.values[k] + "\n")
	}
	if err := writeTextFile(c.path(), b.String()); err != nil {
		return -1
	}
	return 0
}

func (c *Config) get(key string) string { c.mu.Lock(); defer c.mu.Unlock(); return c.values[key] }

func (c *Config) set(key, val string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.values[key] = val
}

func (c *Config) has(key string) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	_, ok := c.values[key]
	return ok
}

func (c *Config) remove(key string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	delete(c.values, key)
}

func (c *Config) threadMode() string {
	v := c.get("thread_mode")
	if v == "" {
		return "folder"
	}
	return v
}

func (c *Config) setThreadMode(m string) int {
	if m != "folder" && m != "single" && m != "per_dll" {
		return -1
	}
	c.set("thread_mode", m)
	return 0
}

// ============================================================================
// TMMF 标签文件 / LERF 键值文件
// ============================================================================

const tmmfMagic = "TMMF."
const lerfMagic = "LERF."

func tmmfHeader() string {
	return "TMMF.;utf8\nicon_mdrenderer\n \n0\n…\n"
}

func lerfHeader() string {
	return "LERF.;utf8\nicon_mdrenderer\n \n0\n…\n"
}

func isSkippableLine(line string, idx int) bool {
	if idx < 4 {
		return true
	}
	t := strings.TrimSpace(line)
	return t == "" || strings.HasPrefix(t, "#") || t == "…" || t == "…  …"
}

func tmmfReadTag(path, tag string) (string, bool) {
	b, err := os.ReadFile(path)
	if err != nil {
		return "", false
	}
	open := "<\\" + tag + ">"
	closeTag := "<" + tag + "/>"
	lines := strings.Split(string(b), "\n")
	for i, line := range lines {
		if isSkippableLine(line, i) {
			continue
		}
		if strings.HasPrefix(line, open) {
			inner := line[len(open):]
			if end := strings.Index(inner, closeTag); end >= 0 {
				return inner[:end], true
			}
		}
	}
	return "", false
}

func tmmfListTags(path string) []string {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var tags []string
	lines := strings.Split(string(b), "\n")
	for i, line := range lines {
		if isSkippableLine(line, i) {
			continue
		}
		if strings.HasPrefix(line, "<\\") {
			if end := strings.Index(line, ">"); end > 2 {
				tags = append(tags, line[2:end])
			}
		}
	}
	return tags
}

func tmmfWriteTag(path, tag, content string) int {
	existing := ""
	if b, err := os.ReadFile(path); err == nil {
		existing = string(b)
	}
	if existing == "" || !strings.HasPrefix(strings.TrimSpace(existing), tmmfMagic) {
		existing = tmmfHeader()
	}
	open := "<\\" + tag + ">"
	closeTag := "<" + tag + "/>"
	newLine := open + content + closeTag
	lines := strings.Split(existing, "\n")
	replaced := false
	for i, line := range lines {
		if isSkippableLine(line, i) {
			continue
		}
		if strings.HasPrefix(line, open) {
			lines[i] = newLine
			replaced = true
			break
		}
	}
	if !replaced {
		content := strings.TrimSuffix(existing, "…\n")
		content = strings.TrimSuffix(content, "…")
		content += newLine + "\n…\n"
		existing = content
	} else {
		existing = strings.Join(lines, "\n")
	}
	if err := writeTextFile(path, existing); err != nil {
		return -1
	}
	return 0
}

func tmmfSearch(path, keywords string) string {
	b, err := os.ReadFile(path)
	if err != nil {
		return "[]"
	}
	terms := strings.Fields(keywords)
	var hits []string
	lines := strings.Split(string(b), "\n")
	for i, line := range lines {
		if isSkippableLine(line, i) {
			continue
		}
		match := len(terms) == 0
		for _, t := range terms {
			if strings.Contains(line, t) {
				match = true
				break
			}
		}
		if match {
			hits = append(hits, line)
		}
	}
	var out strings.Builder
	out.WriteString("[")
	for i, h := range hits {
		if i > 0 {
			out.WriteByte(',')
		}
		out.WriteString(jq(h))
	}
	out.WriteString("]")
	return out.String()
}

func lerfParse(path string) (map[string]string, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	lines := strings.Split(string(b), "\n")
	if len(lines) < 4 || !strings.HasPrefix(strings.TrimSpace(lines[0]), lerfMagic) {
		return nil, fmt.Errorf("无效的 LERF 文件")
	}
	m := map[string]string{}
	for i, line := range lines {
		if isSkippableLine(line, i) {
			continue
		}
		if !strings.HasPrefix(line, "<\\") {
			continue
		}
		gt := strings.Index(line, ">")
		if gt < 2 {
			continue
		}
		id := line[2:gt]
		rest := line[gt+1:]
		closeTag := "<" + id + "/>"
		if end := strings.Index(rest, closeTag); end >= 0 {
			m[id] = rest[:end]
		}
	}
	return m, nil
}

func lerfValidate(path string) (bool, []string) {
	b, err := os.ReadFile(path)
	if err != nil {
		return false, []string{err.Error()}
	}
	lines := strings.Split(string(b), "\n")
	var errs []string
	if len(lines) < 4 || !strings.HasPrefix(strings.TrimSpace(lines[0]), lerfMagic) {
		errs = append(errs, "第1行魔数错误，应为 LERF.;编码")
		return false, errs
	}
	status, err := strconv.Atoi(strings.TrimSpace(lines[3]))
	if err != nil {
		errs = append(errs, "第4行文件状态不是有效数字（0使用/1不使用/-1无效）")
	} else if status == -1 {
		errs = append(errs, "第4行文件状态为-1（无效文件）")
	}
	for i, line := range lines {
		if isSkippableLine(line, i) {
			continue
		}
		if !strings.HasPrefix(line, "<\\") {
			errs = append(errs, fmt.Sprintf("第%d行不是 <\\ID>text<ID/> 格式", i+1))
			continue
		}
		gt := strings.Index(line, ">")
		if gt < 2 {
			errs = append(errs, fmt.Sprintf("第%d行标签格式错误", i+1))
		}
	}
	return len(errs) == 0, errs
}

// ============================================================================
// 转换（MD→HTML / MD→纯文本 / TXT→MD）
// ============================================================================

func convertToFullHTML(md string) string {
	var b strings.Builder
	b.WriteString("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
	b.WriteString("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
	b.WriteString("<title>Converted Document</title>\n<style>\n")
	b.WriteString("body{font-family:'Microsoft YaHei',sans-serif;max-width:800px;margin:40px auto;padding:0 20px;line-height:1.6;color:#333;}\n")
	b.WriteString("h1{border-bottom:2px solid #eee;padding-bottom:10px;}\nh2{border-bottom:1px solid #eee;padding-bottom:5px;}\n")
	b.WriteString("code{background:#f4f4f4;padding:2px 6px;border-radius:3px;font-family:Consolas,monospace;}\n")
	b.WriteString("pre{background:#f4f4f4;padding:15px;border-radius:5px;overflow-x:auto;}\n")
	b.WriteString("blockquote{border-left:4px solid #ddd;margin:0;padding-left:15px;color:#666;}\n")
	b.WriteString("table{border-collapse:collapse;width:100%;}\nth,td{border:1px solid #ddd;padding:8px;text-align:left;}\nth{background:#f4f4f4;}\n")
	b.WriteString("</style>\n</head>\n<body>\n")
	b.WriteString(markdownToHTML(md))
	b.WriteString("\n</body>\n</html>")
	return b.String()
}

func convertToPlainText(md string) string {
	var b strings.Builder
	inCode := false
	for _, line := range strings.Split(md, "\n") {
		if strings.HasPrefix(strings.TrimSpace(line), "```") {
			inCode = !inCode
			continue
		}
		if inCode {
			b.WriteString(line + "\n")
			continue
		}
		cleaned := strings.TrimLeft(line, "#")
		cleaned = strings.TrimPrefix(strings.TrimSpace(cleaned), ">")
		cleaned = strings.TrimSpace(cleaned)
		if strings.HasPrefix(cleaned, "- ") || strings.HasPrefix(cleaned, "* ") {
			cleaned = strings.TrimSpace(cleaned[2:])
		} else if isOrderedStart(cleaned) {
			if i := strings.Index(cleaned, ". "); i >= 0 {
				cleaned = strings.TrimSpace(cleaned[i+2:])
			}
		}
		cleaned = replacePairs(cleaned, "**", "", "", false)
		cleaned = replaceItalic(cleaned)
		cleaned = replacePairs(cleaned, "`", "", "", false)
		cleaned = replacePairs(cleaned, "~~", "", "", false)
		b.WriteString(cleaned + "\n")
	}
	return b.String()
}

func convertTxtToMD(text string) string {
	var b strings.Builder
	for _, line := range strings.Split(text, "\n") {
		trimmed := strings.TrimSpace(line)
		if trimmed == "" {
			b.WriteString("\n")
			continue
		}
		if strings.HasPrefix(trimmed, "#") || strings.HasPrefix(trimmed, ">") ||
			strings.HasPrefix(trimmed, "- ") || strings.HasPrefix(trimmed, "* ") || isOrderedStart(trimmed) {
			b.WriteString(trimmed + "\n")
			continue
		}
		if runeLen(trimmed) < 50 && runeLen(trimmed) > 2 {
			upperish := true
			for _, r := range trimmed {
				if !(unicode.IsUpper(r) || unicode.IsDigit(r) || unicode.IsSpace(r) || r == '-' || r == ':') {
					upperish = false
					break
				}
			}
			if upperish {
				b.WriteString("## " + trimmed + "\n")
				continue
			}
		}
		if strings.HasPrefix(trimmed, "第") {
			b.WriteString("# " + trimmed + "\n")
			continue
		}
		b.WriteString(trimmed + "\n")
	}
	return strings.TrimSpace(b.String()) + "\n"
}

// ============================================================================
// 快速排序
// ============================================================================

func sortIntsCSV(numsStr, ascStr string) string {
	asc := strings.EqualFold(strings.TrimSpace(ascStr), "true")
	var nums []int
	for _, p := range strings.Split(numsStr, ",") {
		if v, err := strconv.Atoi(strings.TrimSpace(p)); err == nil {
			nums = append(nums, v)
		}
	}
	sort.Slice(nums, func(i, j int) bool {
		if asc {
			return nums[i] < nums[j]
		}
		return nums[i] > nums[j]
	})
	parts := make([]string, len(nums))
	for i, n := range nums {
		parts[i] = strconv.Itoa(n)
	}
	return strings.Join(parts, ",")
}

func sortStringsCSV(strsStr, ascStr string) string {
	asc := strings.EqualFold(strings.TrimSpace(ascStr), "true")
	arr := strings.Split(strsStr, ",")
	for i := range arr {
		arr[i] = strings.TrimSpace(arr[i])
	}
	sort.Slice(arr, func(i, j int) bool {
		if asc {
			return arr[i] < arr[j]
		}
		return arr[i] > arr[j]
	})
	return strings.Join(arr, ",")
}

func sortJSONByField(jsonText, field, ascStr string) string {
	asc := strings.EqualFold(strings.TrimSpace(ascStr), "true")
	var arr []map[string]any
	if err := json.Unmarshal([]byte(jsonText), &arr); err == nil {
		sort.SliceStable(arr, func(i, j int) bool {
			a := toFloat(arr[i][field])
			b := toFloat(arr[j][field])
			if asc {
				return a < b
			}
			return a > b
		})
		out, err := json.Marshal(arr)
		if err == nil {
			return string(out)
		}
	}
	return jsonText
}

func toFloat(v any) float64 {
	switch n := v.(type) {
	case float64:
		return n
	case int:
		return float64(n)
	case string:
		f, _ := strconv.ParseFloat(n, 64)
		return f
	}
	return 0
}

func quickSortInfo() string {
	return `{"algorithm":"quick_sort","avg_complexity":"O(n log n)","worst_complexity":"O(n^2)","space":"O(log n)","stable":false}`
}

// ============================================================================
// 文件 I/O 辅助
// ============================================================================

func fileRead(path string) (string, int) {
	b, err := os.ReadFile(path)
	if err != nil {
		return "", -1
	}
	return string(b), runeLen(string(b))
}

func fileWrite(path, content string) int {
	if err := writeTextFile(path, content); err != nil {
		return -1
	}
	return 0
}

func fileAppend(path, content string) int {
	if dir := filepath.Dir(path); dir != "" {
		_ = os.MkdirAll(dir, 0o755)
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return -1
	}
	defer f.Close()
	if _, err := f.WriteString(content); err != nil {
		return -1
	}
	return 0
}

func fileSize(path string) int64 {
	if info, err := os.Stat(path); err == nil {
		return info.Size()
	}
	return -1
}

// ============================================================================
// 线程统计（Go 版：goroutine 注册表 + 进程 CPU/内存 + 磁盘计数）
// ============================================================================

type threadEntry struct {
	Name       string
	Category   string
	CPUPercent float64
	CPUTimeMS  int64
	Alive      bool
}

type ThreadStats struct {
	mu         sync.Mutex
	mode       string
	start      time.Time
	threads    map[string]*threadEntry
	lastCPU    float64
	lastSample time.Time
	diskRead   int64
	diskWrite  int64
}

var threadStats = &ThreadStats{mode: "folder", start: time.Now(), threads: map[string]*threadEntry{}, lastSample: time.Now()}

func (t *ThreadStats) init(mode string) int {
	t.mu.Lock()
	defer t.mu.Unlock()
	if mode == "folder" || mode == "single" || mode == "per_dll" {
		t.mode = mode
	}
	t.start = time.Now()
	t.lastSample = time.Now()
	t.threads["main"] = &threadEntry{Name: "main", Category: "app", Alive: true}
	return 0
}

func (t *ThreadStats) register(name, category string) int {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.threads[name] = &threadEntry{Name: name, Category: category, Alive: true}
	return 0
}

func (t *ThreadStats) unregister(name string) {
	t.mu.Lock()
	defer t.mu.Unlock()
	if e, ok := t.threads[name]; ok {
		e.Alive = false
	}
}

func cpuSeconds() float64 {
	samples := []metrics.Sample{{Name: "/cpu/classes/total:cpu-seconds"}}
	metrics.Read(samples)
	if samples[0].Value.Kind() == metrics.KindFloat64 {
		return samples[0].Value.Float64()
	}
	return 0
}

func (t *ThreadStats) sample() int {
	t.mu.Lock()
	defer t.mu.Unlock()
	now := time.Now()
	cpu := cpuSeconds()
	elapsed := now.Sub(t.lastSample).Seconds()
	if elapsed > 0 && cpu > 0 {
		pct := (cpu - t.lastCPU) / elapsed * 100
		if pct > 100 {
			pct = 100
		}
		if main, ok := t.threads["main"]; ok {
			main.CPUPercent = math.Round(pct*10) / 10
			main.CPUTimeMS = int64(cpu * 1000)
		}
	}
	t.lastCPU = cpu
	t.lastSample = now
	return len(t.threads)
}

func (t *ThreadStats) statsJSON() string {
	t.mu.Lock()
	defer t.mu.Unlock()
	var ms runtime.MemStats
	runtime.ReadMemStats(&ms)
	used := ms.Alloc
	maxMem := ms.Sys
	usedPct := 0.0
	if maxMem > 0 {
		usedPct = float64(used) * 100 / float64(maxMem)
	}
	var b strings.Builder
	b.WriteString(`{"mode":` + jq(t.mode))
	b.WriteString(`,"uptime_ms":` + strconv.FormatInt(time.Since(t.start).Milliseconds(), 10))
	b.WriteString(`,"thread_count":` + strconv.Itoa(len(t.threads)))
	b.WriteString(`,"goroutines":` + strconv.Itoa(runtime.NumGoroutine()))
	b.WriteString(`,"threads":[`)
	i := 0
	for _, e := range t.threads {
		if i > 0 {
			b.WriteByte(',')
		}
		i++
		b.WriteString(`{"name":` + jq(e.Name) + `,"category":` + jq(e.Category))
		b.WriteString(`,"cpu_percent":` + strconv.FormatFloat(e.CPUPercent, 'f', 1, 64))
		b.WriteString(`,"cpu_time_ms":` + strconv.FormatInt(e.CPUTimeMS, 10))
		b.WriteString(`,"alive":` + jb(e.Alive) + `}`)
	}
	b.WriteString(`],"memory":{"used":` + strconv.FormatUint(used, 10))
	b.WriteString(`,"max":` + strconv.FormatUint(maxMem, 10))
	b.WriteString(`,"used_percent":` + strconv.FormatFloat(usedPct, 'f', 1, 64) + `}`)
	b.WriteString(`,"disk":{"read_bytes":` + strconv.FormatInt(t.diskRead, 10))
	b.WriteString(`,"write_bytes":` + strconv.FormatInt(t.diskWrite, 10) + `}}`)
	return b.String()
}

func (t *ThreadStats) memoryJSON() string {
	var ms runtime.MemStats
	runtime.ReadMemStats(&ms)
	used := ms.Alloc
	maxMem := ms.Sys
	pct := 0.0
	if maxMem > 0 {
		pct = float64(used) * 100 / float64(maxMem)
	}
	return `{"max":` + strconv.FormatUint(maxMem, 10) + `,"total":` + strconv.FormatUint(ms.TotalAlloc, 10) +
		`,"used":` + strconv.FormatUint(used, 10) + `,"free":` + strconv.FormatUint(maxMem-used, 10) +
		`,"used_percent":` + strconv.FormatFloat(pct, 'f', 1, 64) + `}`
}

func (t *ThreadStats) diskJSON() string {
	t.mu.Lock()
	defer t.mu.Unlock()
	return `{"read_bytes":` + strconv.FormatInt(t.diskRead, 10) + `,"write_bytes":` + strconv.FormatInt(t.diskWrite, 10) +
		`,"total_bytes":` + strconv.FormatInt(t.diskRead+t.diskWrite, 10) + `}`
}

// ============================================================================
// 可视化（统计 JSON → 自包含 HTML）
// ============================================================================

func visualizeStatsHTML(statsJSON string) string {
	var stats struct {
		Mode        string `json:"mode"`
		UptimeMS    int64  `json:"uptime_ms"`
		ThreadCount int    `json:"thread_count"`
		Goroutines  int    `json:"goroutines"`
		Threads     []struct {
			Name       string  `json:"name"`
			Category   string  `json:"category"`
			CPUPercent float64 `json:"cpu_percent"`
			CPUTimeMS  int64   `json:"cpu_time_ms"`
			Alive      bool    `json:"alive"`
		} `json:"threads"`
		Memory struct {
			Used        uint64  `json:"used"`
			Max         uint64  `json:"max"`
			UsedPercent float64 `json:"used_percent"`
		} `json:"memory"`
		Disk struct {
			ReadBytes  int64 `json:"read_bytes"`
			WriteBytes int64 `json:"write_bytes"`
		} `json:"disk"`
	}
	_ = json.Unmarshal([]byte(statsJSON), &stats)
	uptime := stats.UptimeMS / 1000
	var b strings.Builder
	b.WriteString("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n<title>MDRenderer 线程统计</title>\n<style>\n")
	b.WriteString("body{font-family:'Microsoft YaHei',Consolas,monospace;background:#1e1e1e;color:#d4d4d4;margin:0;padding:20px;}\n")
	b.WriteString("h1{color:#569cd6;border-bottom:1px solid #3c3c3c;padding-bottom:10px;}\nh2{color:#4ec9b0;margin-top:30px;}\n")
	b.WriteString(".card{background:#252526;border:1px solid #3c3c3c;border-radius:6px;padding:15px;margin:10px 0;}\n")
	b.WriteString(".stat-row{display:flex;justify-content:space-between;padding:5px 0;border-bottom:1px solid #3c3c3c;}\n")
	b.WriteString(".stat-label{color:#9cdcfe;}.stat-value{color:#ce9178;font-weight:bold;}\n")
	b.WriteString(".bar-container{background:#3c3c3c;border-radius:4px;height:24px;margin:5px 0;overflow:hidden;}\n")
	b.WriteString(".bar-fill{height:100%;border-radius:4px;}.bar-cpu{background:linear-gradient(90deg,#4ec9b0,#569cd6);}\n")
	b.WriteString(".bar-mem{background:linear-gradient(90deg,#ce9178,#dcdcaa);}\n")
	b.WriteString(".thread-item{padding:8px;border-left:3px solid #569cd6;margin:5px 0;background:#2d2d30;}\n")
	b.WriteString(".thread-name{color:#dcdcaa;font-weight:bold;}.thread-cat{color:#808080;font-size:12px;margin-left:10px;}\n")
	b.WriteString(".alive{color:#4ec9b0;}.dead{color:#808080;}\n")
	b.WriteString("table{width:100%;border-collapse:collapse;margin:10px 0;}\nth,td{border:1px solid #3c3c3c;padding:8px;text-align:left;}\n")
	b.WriteString("th{background:#2d2d30;color:#569cd6;}\n</style>\n</head>\n<body>\n<h1>MDRenderer 线程统计面板</h1>\n")
	b.WriteString("<div class=\"card\"><h2>概览</h2>\n")
	b.WriteString("<div class=\"stat-row\"><span class=\"stat-label\">线程模式</span><span class=\"stat-value\">" + escHTML(stats.Mode) + "</span></div>\n")
	b.WriteString("<div class=\"stat-row\"><span class=\"stat-label\">运行时间</span><span class=\"stat-value\">" +
		fmt.Sprintf("%d分%d秒", uptime/60, uptime%60) + "</span></div>\n")
	b.WriteString("<div class=\"stat-row\"><span class=\"stat-label\">线程总数</span><span class=\"stat-value\">" + strconv.Itoa(stats.ThreadCount) + "</span></div>\n")
	b.WriteString("<div class=\"stat-row\"><span class=\"stat-label\">Goroutine</span><span class=\"stat-value\">" + strconv.Itoa(stats.Goroutines) + "</span></div>\n</div>\n")
	b.WriteString("<div class=\"card\"><h2>CPU 占用</h2>\n")
	for _, th := range stats.Threads {
		cls := "dead"
		state := "已结束"
		if th.Alive {
			cls = "alive"
			state = "运行中"
		}
		b.WriteString("<div class=\"thread-item\"><span class=\"thread-name\">" + escHTML(th.Name) + "</span>")
		b.WriteString("<span class=\"thread-cat\">[" + escHTML(th.Category) + "]</span>")
		b.WriteString("<span class=\"" + cls + "\" style=\"float:right;\">" + state + "</span>\n")
		b.WriteString("<div class=\"bar-container\"><div class=\"bar-fill bar-cpu\" style=\"width:" +
			strconv.FormatFloat(th.CPUPercent, 'f', 0, 64) + "%\"></div></div>\n")
		b.WriteString("<span style=\"font-size:12px;color:#808080;\">CPU: " +
			strconv.FormatFloat(th.CPUPercent, 'f', 1, 64) + "%</span>\n</div>\n")
	}
	b.WriteString("</div>\n<div class=\"card\"><h2>内存占用</h2>\n")
	b.WriteString("<div class=\"bar-container\"><div class=\"bar-fill bar-mem\" style=\"width:" +
		strconv.FormatFloat(stats.Memory.UsedPercent, 'f', 0, 64) + "%\"></div></div>\n")
	b.WriteString("<div class=\"stat-row\"><span class=\"stat-label\">已用</span><span class=\"stat-value\">" + formatBytes(int64(stats.Memory.Used)) + "</span></div>\n")
	b.WriteString("<div class=\"stat-row\"><span class=\"stat-label\">最大</span><span class=\"stat-value\">" + formatBytes(int64(stats.Memory.Max)) + "</span></div>\n")
	b.WriteString("<div class=\"stat-row\"><span class=\"stat-label\">占用率</span><span class=\"stat-value\">" +
		strconv.FormatFloat(stats.Memory.UsedPercent, 'f', 1, 64) + "%</span></div>\n</div>\n")
	b.WriteString("<div class=\"card\"><h2>磁盘 I/O</h2>\n<table>\n<tr><th>方向</th><th>字节数</th><th>可读格式</th></tr>\n")
	b.WriteString("<tr><td>读取</td><td>" + strconv.FormatInt(stats.Disk.ReadBytes, 10) + "</td><td>" + formatBytes(stats.Disk.ReadBytes) + "</td></tr>\n")
	b.WriteString("<tr><td>写入</td><td>" + strconv.FormatInt(stats.Disk.WriteBytes, 10) + "</td><td>" + formatBytes(stats.Disk.WriteBytes) + "</td></tr>\n")
	b.WriteString("<tr><td>总计</td><td>" + strconv.FormatInt(stats.Disk.ReadBytes+stats.Disk.WriteBytes, 10) + "</td><td>" +
		formatBytes(stats.Disk.ReadBytes+stats.Disk.WriteBytes) + "</td></tr>\n</table>\n</div>\n")
	b.WriteString("<div class=\"card\"><h2>原始 JSON 数据</h2>\n<pre style=\"background:#1e1e1e;padding:10px;border-radius:4px;overflow-x:auto;font-size:12px;\">" +
		escHTML(statsJSON) + "</pre>\n</div>\n</body>\n</html>")
	return b.String()
}

func visualizeThreadModesHTML() string {
	return `<div class="card"><h2>线程模式说明</h2><table>
<tr><th>模式</th><th>说明</th><th>适用场景</th></tr>
<tr><td><b>folder（默认）</b></td><td>每个 DLL 分类文件夹一个线程：core/doc/tools/app 各一个线程</td><td>平衡性能和资源占用，推荐</td></tr>
<tr><td><b>single</b></td><td>全部单线程，所有 DLL 在一个线程中顺序执行</td><td>调试、低资源设备、兼容性优先</td></tr>
<tr><td><b>per_dll</b></td><td>每个独立 DLL 一个线程，最大并行度</td><td>高性能服务器、多核CPU、批量处理</td></tr>
</table></div>`
}

func formatBytes(bytes int64) string {
	if bytes < 1024 {
		return strconv.FormatInt(bytes, 10) + " B"
	}
	if bytes < 1024*1024 {
		return fmt.Sprintf("%.1f KB", float64(bytes)/1024)
	}
	if bytes < 1024*1024*1024 {
		return fmt.Sprintf("%.1f MB", float64(bytes)/(1024*1024))
	}
	return fmt.Sprintf("%.2f GB", float64(bytes)/(1024*1024*1024))
}

// ============================================================================
// 单实例锁（环回 TCP 端口占位；进程崩溃由系统自动释放）
// ============================================================================

type SingleLock struct {
	mu       sync.Mutex
	name     string
	listener net.Listener
	path     string
	locked   bool
}

var singleLock = &SingleLock{}

func (s *SingleLock) lockPath(name string) string {
	return filepath.Join(os.TempDir(), "mdrenderer_"+name+".lock")
}

func (s *SingleLock) tryLock(name string) int {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.locked {
		return 1
	}
	h := sha1.Sum([]byte("mdrenderer:" + name))
	port := 47000 + int(h[0])%2000
	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		s.locked = false
		return 0
	}
	s.listener = ln
	s.name = name
	s.path = s.lockPath(name)
	s.locked = true
	_ = writeTextFile(s.path, fmt.Sprintf("%d|%s|%d\n", os.Getpid(), mustGetwd(), time.Now().Unix()))
	return 1
}

func (s *SingleLock) release() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.listener != nil {
		_ = s.listener.Close()
		s.listener = nil
	}
	s.locked = false
}

func (s *SingleLock) isLocked() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.locked
}

func mustGetwd() string {
	wd, _ := os.Getwd()
	return wd
}

// ============================================================================
// CMD（内置命令 + 系统命令）
// ============================================================================

func cmdHelp() string {
	return `MDRenderer 命令：
  help                 显示帮助
  version              显示版本
  convert <in> <out> [html|text|md]   文件转换
  stats <file>         文档统计
  render <file>        Markdown → HTML
  settings             显示当前设置
  log                  显示日志状态
  log clear            清除日志
  exit                 退出
其他输入按系统命令执行（cmd /c，10 秒超时）。`
}

func cmdExec(cmd string) string {
	trimmed := strings.TrimSpace(cmd)
	lower := strings.ToLower(trimmed)
	switch {
	case lower == "help" || lower == "?":
		return cmdHelp()
	case lower == "version":
		return AppName + " v" + Version + " (" + Edition + ")"
	case lower == "settings":
		keys := make([]string, 0)
		cfg.mu.Lock()
		for k, v := range cfg.values {
			keys = append(keys, k+"="+v)
		}
		cfg.mu.Unlock()
		sort.Strings(keys)
		return strings.Join(keys, "\n")
	case lower == "log":
		return fmt.Sprintf("日志: %s\n大小: %d 字节\n压缩态: %s", logState.logFile, logState.size(), logState.compFile)
	case lower == "log clear":
		n := logState.clearAll()
		return fmt.Sprintf("已清除 %d 个日志文件", n)
	case lower == "exit" || lower == "quit":
		return "exit"
	}
	if strings.HasPrefix(lower, "stats ") {
		path := strings.TrimSpace(trimmed[6:])
		content, err := readTextFile(path)
		if err != nil {
			return "读取失败: " + err.Error()
		}
		return fmt.Sprintf("字符数: %d\n词数: %d\n行数: %d", runeLen(content), countWords(content), strings.Count(content, "\n")+1)
	}
	if strings.HasPrefix(lower, "render ") {
		path := strings.TrimSpace(trimmed[7:])
		content, err := readTextFile(path)
		if err != nil {
			return "读取失败: " + err.Error()
		}
		return markdownToHTML(content)
	}
	if strings.HasPrefix(lower, "convert ") {
		fields := strings.Fields(trimmed)
		if len(fields) < 3 {
			return "用法: convert <in> <out> [html|text|md]"
		}
		in, out := fields[1], fields[2]
		kind := "html"
		if len(fields) > 3 {
			kind = strings.ToLower(fields[3])
		}
		content, err := readTextFile(in)
		if err != nil {
			return "读取失败: " + err.Error()
		}
		var converted string
		switch kind {
		case "html":
			converted = convertToFullHTML(content)
		case "text":
			converted = convertToPlainText(content)
		case "md":
			converted = convertTxtToMD(content)
		default:
			return "未知格式: " + kind
		}
		if err := writeTextFile(out, converted); err != nil {
			return "写入失败: " + err.Error()
		}
		return "已转换: " + out
	}

	// 系统命令：cmd /c，UTF-8 输出
	done := make(chan struct {
		out string
		err error
	}, 1)
	go func() {
		c := exec.Command("cmd", "/c", "chcp 65001>nul & "+cmd)
		outBytes, err := c.CombinedOutput()
		done <- struct {
			out string
			err error
		}{string(outBytes), err}
	}()
	select {
	case r := <-done:
		if r.err != nil && r.out == "" {
			return "命令失败: " + r.err.Error()
		}
		return strings.TrimRight(r.out, "\r\n")
	case <-time.After(10 * time.Second):
		return "命令超时（10 秒）"
	}
}

// ============================================================================
// IDP（内存双向队列）
// ============================================================================

type Packet struct {
	Type      string
	Source    string
	JSONData  string
	Timestamp string
	Size      int
}

var (
	idpMu    sync.Mutex
	idpStack []Packet
)

func idpPush(p Packet) {
	idpMu.Lock()
	defer idpMu.Unlock()
	if p.Timestamp == "" {
		p.Timestamp = time.Now().Format("2006-01-02T15:04:05")
	}
	p.Size = len(p.JSONData)
	idpStack = append(idpStack, p)
}

func idpPopBack() (Packet, bool) {
	idpMu.Lock()
	defer idpMu.Unlock()
	if len(idpStack) == 0 {
		return Packet{}, false
	}
	p := idpStack[len(idpStack)-1]
	idpStack = idpStack[:len(idpStack)-1]
	return p, true
}

func idpPopFront() (Packet, bool) {
	idpMu.Lock()
	defer idpMu.Unlock()
	if len(idpStack) == 0 {
		return Packet{}, false
	}
	p := idpStack[0]
	idpStack = idpStack[1:]
	return p, true
}

func idpPushFront(p Packet) {
	idpMu.Lock()
	defer idpMu.Unlock()
	idpStack = append([]Packet{p}, idpStack...)
}

func idpPeek() (Packet, bool) {
	idpMu.Lock()
	defer idpMu.Unlock()
	if len(idpStack) == 0 {
		return Packet{}, false
	}
	return idpStack[len(idpStack)-1], true
}

func idpSize() int { idpMu.Lock(); defer idpMu.Unlock(); return len(idpStack) }

func idpClear() { idpMu.Lock(); defer idpMu.Unlock(); idpStack = nil }

func idpCreatePacket(typ, source, jsonData string) string {
	p := Packet{Type: typ, Source: source, JSONData: jsonData, Timestamp: time.Now().Format("2006-01-02T15:04:05"), Size: len(jsonData)}
	return `<?xml version="1.0" encoding="UTF-8"?>` + "\n" +
		`<idp version="1.0" type="` + esc(typ) + `" timestamp="` + p.Timestamp + `" source="` + esc(source) +
		`" size="` + strconv.Itoa(p.Size) + `"/>` + "\n===IDP-DATA===\n" + jsonData
}

// ============================================================================
// 控制层 dispatch（与 Rust 前端既有契约一致）
// ============================================================================

func isCacheable(typ string) bool {
	switch typ {
	case "app_render_html", "render_find", "render_replace", "render_insert", "render_paged", "visualize_stats_to_html":
		return true
	}
	return false
}

func withCachedFlag(s string) string {
	t := strings.TrimSpace(s)
	if !strings.HasPrefix(t, "{") || !strings.HasSuffix(t, "}") {
		return s
	}
	body := strings.TrimSpace(t[1 : len(t)-1])
	if body == "" {
		return `{"cached":true}`
	}
	return "{" + body + `,"cached":true}`
}

func parseArgs(jsonData string) map[string]any {
	m := map[string]any{}
	_ = json.Unmarshal([]byte(jsonData), &m)
	return m
}

func argStr(m map[string]any, key string) string {
	if v, ok := m[key]; ok {
		switch x := v.(type) {
		case string:
			return x
		case float64:
			return strconv.FormatFloat(x, 'f', -1, 64)
		case bool:
			return strconv.FormatBool(x)
		}
	}
	return ""
}

func argInt(m map[string]any, key string, def int) int {
	if v, ok := m[key]; ok {
		switch x := v.(type) {
		case float64:
			return int(x)
		case string:
			if n, err := strconv.Atoi(x); err == nil {
				return n
			}
		}
	}
	return def
}

func dispatch(typ, jsonData string) string {
	if isCacheable(typ) {
		if cached, ok := logState.cacheLookup(typ, jsonData); ok {
			return withCachedFlag(cached)
		}
		fresh := dispatchNoCache(typ, jsonData)
		if fresh != "" && !strings.HasPrefix(fresh, `{"error"`) {
			_ = logState.cachePut(typ, jsonData, fresh)
		}
		return fresh
	}
	return dispatchNoCache(typ, jsonData)
}

func dispatchNoCache(typ, jsonData string) string {
	args := parseArgs(jsonData)
	switch typ {
	case "app_version":
		return `{"version":"` + Version + `"}`
	case "app_new_file":
		docState.New()
		return `{"ok":0}`
	case "app_open_file":
		return `{"ok":` + strconv.Itoa(docState.Open(argStr(args, "path"))) + `}`
	case "app_save_file":
		return `{"ok":` + strconv.Itoa(docState.Save()) + `}`
	case "app_save_file_as":
		return `{"ok":` + strconv.Itoa(docState.SaveAs(argStr(args, "path"))) + `}`
	case "app_get_content":
		content, _, _, _ := docState.Snapshot()
		return `{"content":` + jq(content) + `}`
	case "app_get_file_path":
		_, path, _, _ := docState.Snapshot()
		return `{"path":` + jq(path) + `}`
	case "app_is_dirty":
		_, _, dirty, _ := docState.Snapshot()
		return `{"dirty":` + jb(dirty) + `}`
	case "app_insert_text":
		return `{"ok":` + strconv.Itoa(docState.InsertText(argInt(args, "pos", 0), argStr(args, "text"))) + `}`
	case "app_delete_text":
		return `{"ok":` + strconv.Itoa(docState.DeleteText(argInt(args, "start", 0), argInt(args, "end", 0))) + `}`
	case "app_undo":
		return `{"ok":` + strconv.Itoa(docState.Undo()) + `}`
	case "app_redo":
		return `{"ok":` + strconv.Itoa(docState.Redo()) + `}`
	case "app_can_undo":
		return `{"can":` + jb(docState.CanUndo()) + `}`
	case "app_can_redo":
		return `{"can":` + jb(docState.CanRedo()) + `}`
	case "app_set_cursor":
		docState.mu.Lock()
		docState.cursor = clamp(argInt(args, "pos", 0), 0, runeLen(docState.content))
		docState.mu.Unlock()
		return `{"ok":0}`
	case "app_get_cursor":
		_, _, _, cur := docState.Snapshot()
		return `{"pos":` + strconv.Itoa(cur) + `}`
	case "app_get_cursor_line":
		return `{"line":` + strconv.Itoa(docState.CursorLine()) + `}`
	case "app_get_cursor_col":
		return `{"col":` + strconv.Itoa(docState.CursorCol()) + `}`
	case "app_render_ui":
		content, _, _, _ := docState.Snapshot()
		return uiInstructions(content)
	case "app_render_html":
		return `{"html":` + jq(markdownToHTML(argStr(args, "md"))) + `}`
	case "app_get_status":
		content, path, dirty, cur := docState.Snapshot()
		name := "未命名"
		if path != "" {
			name = filepath.Base(path)
		}
		var b strings.Builder
		b.WriteString(`{"file_name":` + jq(name))
		b.WriteString(`,"file_path":` + jq(path))
		b.WriteString(`,"dirty":` + jb(dirty))
		b.WriteString(`,"char_count":` + strconv.Itoa(runeLen(content)))
		b.WriteString(`,"word_count":` + strconv.Itoa(countWords(content)))
		lines := 0
		if content != "" {
			lines = strings.Count(content, "\n") + 1
		}
		b.WriteString(`,"line_count":` + strconv.Itoa(lines))
		b.WriteString(`,"cursor_pos":` + strconv.Itoa(cur))
		b.WriteString(`,"cursor_line":` + strconv.Itoa(docState.CursorLine()))
		b.WriteString(`,"cursor_col":` + strconv.Itoa(docState.CursorCol()))
		b.WriteString(`,"can_undo":` + jb(docState.CanUndo()))
		b.WriteString(`,"can_redo":` + jb(docState.CanRedo()) + `}`)
		return b.String()
	case "app_get_setting":
		key := argStr(args, "key")
		return `{"key":` + jq(key) + `,"value":` + jq(cfg.get(key)) + `}`
	case "app_set_setting":
		cfg.set(argStr(args, "key"), argStr(args, "value"))
		return `{"ok":0}`
	case "app_save_settings":
		return `{"ok":` + strconv.Itoa(cfg.saveSettings()) + `}`
	case "app_log":
		if argStr(args, "end") == "true" || args["end"] == true {
			logState.writeEnd(argStr(args, "params"), argStr(args, "op"), argStr(args, "result"))
		} else {
			logState.write(argStr(args, "params"), argStr(args, "op"), argStr(args, "result"), false)
		}
		return `{"ok":0}`
	case "app_easter_detect":
		return `{"is_easter":` + jb(strings.HasPrefix(argStr(args, "line"), "\\\\>")) + `}`
	case "app_easter_exec":
		return `{"result":` + jq(cmdExec(argStr(args, "command"))) + `}`
	case "config_get_thread_mode":
		return `{"mode":` + jq(cfg.threadMode()) + `}`
	case "config_set_thread_mode":
		return `{"ok":` + strconv.Itoa(cfg.setThreadMode(argStr(args, "mode"))) + `}`
	case "doc_char_count":
		content, _, _, _ := docState.Snapshot()
		return `{"count":` + strconv.Itoa(runeLen(content)) + `}`
	case "threadstat_sample":
		threadStats.sample()
		return `{"ok":0}`
	case "threadstat_get_json":
		return `{"stats":` + threadStats.statsJSON() + `}`
	case "visualize_stats_to_html":
		return `{"html":` + jq(visualizeStatsHTML(argStr(args, "stats"))) + `}`
	case "log_clear_all":
		return `{"ok":` + strconv.Itoa(logState.clearAll()) + `}`
	case "single_try_lock":
		return `{"locked":` + jb(singleLock.tryLock(argStr(args, "name")) == 1) + `}`
	case "render_find":
		return `{"result":` + findJSON(argStr(args, "content"), argStr(args, "query")) + `}`
	case "render_replace":
		return `{"result":` + replaceJSON(argStr(args, "content"), argStr(args, "find"), argStr(args, "replace")) + `}`
	case "render_insert":
		return `{"result":` + insertJSON(argStr(args, "content"), argStr(args, "left"), argStr(args, "right"), argStr(args, "insert")) + `}`
	case "render_paged":
		return `{"result":` + pagedJSON(argStr(args, "content"), argInt(args, "pageWidth", 794), argInt(args, "pageHeight", 1123)) + `}`
	}
	return `{"error":` + jq("unknown type: "+typ) + `}`
}

// appIDPProcess 取最新请求 → dispatch → 结果压到队首（与 Java 版一致）。
func appIDPProcess() int {
	req, ok := idpPopBack()
	if !ok {
		return -1
	}
	result := dispatch(req.Type, req.JSONData)
	idpPushFront(Packet{Type: "result:" + req.Type, Source: "mdcontroller", JSONData: result,
		Timestamp: time.Now().Format("2006-01-02T15:04:05"), Size: len(result)})
	return 0
}

// ============================================================================
// 初始化 / 关闭
// ============================================================================

func appInit(baseDir string, mode string) {
	if mode == "" {
		mode = "folder"
	}
	_ = cfg.init(baseDir)
	_ = cfg.loadSettings()
	_ = logState.init(baseDir)
	_ = threadStats.init(mode)
	logState.write("", "启动", AppName+" v"+Version+" ("+Edition+")", false)
}

func appShutdown() {
	logState.write("", "退出", "正常退出", true)
	logState.shutdown()
	singleLock.release()
}

// ============================================================================
// 自检
// ============================================================================

type checkState struct {
	pass int
	fail int
}

func (c *checkState) check(name string, ok bool, detail string) {
	if ok {
		c.pass++
		fmt.Printf("  [PASS] %s\n", name)
		return
	}
	c.fail++
	fmt.Printf("  [FAIL] %s  %s\n", name, detail)
}

func runSelftest() int {
	c := &checkState{}
	tmp, err := os.MkdirTemp("", "md_go_selftest_*")
	if err != nil {
		fmt.Println("无法创建临时目录:", err)
		return 1
	}
	defer os.RemoveAll(tmp)
	fmt.Println("MDRenderer Go 单文件版自检")

	// 1. 文档模型
	docState.New()
	docState.InsertText(0, "你好，世界")
	c.check("插入中文后字符数=5", docState.CharCount() == 5, fmt.Sprintf("got %d", docState.CharCount()))
	c.check("光标随插入前移", func() bool { _, _, _, cur := docState.Snapshot(); return cur == 5 }(), "")
	c.check("可撤销", docState.CanUndo(), "")
	docState.Undo()
	c.check("撤销回到空文档", docState.CharCount() == 0, fmt.Sprintf("got %d", docState.CharCount()))
	docState.Redo()
	c.check("重做恢复内容", docState.CharCount() == 5, fmt.Sprintf("got %d", docState.CharCount()))
	docState.DeleteText(0, 3)
	c.check("删除前 3 字", docState.GetText(0, 3) == "世界", docState.GetText(0, 3))
	c.check("光标行=0", docState.CursorLine() == 0, "")
	c.check("光标列=0", docState.CursorCol() == 0, "")

	// 2. Markdown
	md := "# 标题\n\n**粗体** 与 *斜体* 和 `代码`\n\n- 甲\n- 乙\n\n```go\nfmt.Println(1)\n```\n"
	html := markdownToHTML(md)
	c.check("H1 渲染", strings.Contains(html, "<h1>标题</h1>"), html)
	c.check("粗体渲染", strings.Contains(html, "<strong>粗体</strong>"), html)
	c.check("斜体渲染", strings.Contains(html, "<em>斜体</em>"), html)
	c.check("代码块渲染", strings.Contains(html, "<pre><code>"), html)
	c.check("有序/无序列表", strings.Contains(html, "<ul>") && strings.Contains(html, "<li>甲</li>"), html)

	ui := uiInstructions(md)
	c.check("UI 指令含 heading", strings.Contains(ui, `"kind":"heading"`), ui)
	c.check("UI 指令含 list", strings.Contains(ui, `"kind":"list"`), ui)
	c.check("UI 指令是合法 JSON", json.Valid([]byte(ui)), "")

	// 3. 分页：超宽换行 + 超长换页 + 页码
	long := strings.Repeat("这是一段很长的中文段落，用来验证超宽换行与超长换页是否正确工作。", 120)
	pg := pagedJSON("# 长文\n\n"+long, 794, 1123)
	c.check("分页 JSON 合法", json.Valid([]byte(pg)), "")
	var paged struct {
		PageCount int `json:"pageCount"`
		Pages     []struct {
			Page     int   `json:"page"`
			Elements []any `json:"elements"`
		} `json:"pages"`
	}
	_ = json.Unmarshal([]byte(pg), &paged)
	c.check("超长文本分成多页", paged.PageCount > 1, fmt.Sprintf("pageCount=%d", paged.PageCount))
	c.check("页码从 1 连续", len(paged.Pages) > 0 && paged.Pages[0].Page == 1, "")

	small := pagedJSON("短文本", 200, 200)
	_ = json.Unmarshal([]byte(small), &paged)
	c.check("窄页仍至少一页", paged.PageCount >= 1, "")

	// 4. 查找/替换/插入（字符偏移）
	text := "甲中文乙中文丙"
	fj := findJSON(text, "中文")
	var fr struct {
		Count     int   `json:"count"`
		Positions []int `json:"positions"`
	}
	_ = json.Unmarshal([]byte(fj), &fr)
	c.check("查找命中 2 次", fr.Count == 2, fj)
	c.check("查找位置按字符计", len(fr.Positions) == 2 && fr.Positions[0] == 1, fj)
	rj := replaceJSON(text, "中文", "汉字")
	var rr struct {
		Count int    `json:"count"`
		Text  string `json:"text"`
	}
	_ = json.Unmarshal([]byte(rj), &rr)
	c.check("替换 2 次", rr.Count == 2 && rr.Text == "甲汉字乙汉字丙", rj)
	ij := insertJSON("A[x]B[y]", "[", "]", "-")
	var ir struct {
		Count int    `json:"count"`
		Text  string `json:"text"`
	}
	_ = json.Unmarshal([]byte(ij), &ir)
	c.check("区间插入 2 次", ir.Count == 2 && ir.Text == "A[-x]B[-y]", ij)

	// 5. 日志 + 缓存 + 压缩
	if rc := logState.init(tmp); rc != 0 {
		c.check("日志初始化", false, fmt.Sprintf("rc=%d err=%s", rc, logState.lastError))
	} else {
		c.check("日志初始化", true, "")
	}
	logState.write("demo.md", "打开文件", "成功", false)
	logState.write("demo.md", "打开文件", "成功", true)
	raw, _ := readTextFile(logState.logFile)
	lines := strings.Split(raw, "\n")
	c.check("FLF 首行", len(lines) > 0 && lines[0] == "FLF.;utf8", "")
	c.check("FLF 第4行20位", len(lines) > 3 && len(lines[3]) == 20, "")
	c.check("FLF 条目含操作", strings.Contains(raw, "{打开文件}"), raw)
	if rc := logState.cachePut("render_find", `{"q":"x"}`, `{"count":0}`); rc == 0 {
		c.check("缓存写入", true, "")
	} else {
		c.check("缓存写入", false, fmt.Sprintf("rc=%d", rc))
	}
	got, ok := logState.cacheLookup("render_find", `{"q":"x"}`)
	c.check("缓存命中", ok && got == `{"count":0}`, got)
	_, ok = logState.cacheLookup("render_find", `{"q":"y"}`)
	c.check("不同参数不命中", !ok, "")

	logState.shutdown()
	c.check("关闭后生成压缩态", fileExists(logState.compFile), logState.compFile)
	c.check("关闭后删除工作态", !fileExists(logState.logFile), logState.logFile)
	dec := filepath.Join(tmp, "roundtrip.log")
	if err := decompressFile(logState.compFile, dec); err == nil {
		back, _ := readTextFile(dec)
		c.check("zlib 解压还原", strings.Contains(back, "{打开文件}"), "")
	} else {
		c.check("zlib 解压还原", false, err.Error())
	}

	// 6. 配置
	cfg.baseDir = tmp
	cfg.values = map[string]string{}
	c.check("默认线程模式 folder", cfg.threadMode() == "folder", cfg.threadMode())
	cfg.set("thread_mode", "per_dll")
	c.check("保存设置", cfg.saveSettings() == 0, "")
	cfg.values = map[string]string{}
	_ = cfg.loadSettings()
	c.check("重新加载设置", cfg.threadMode() == "per_dll", cfg.threadMode())

	// 7. TMMF / LERF
	tmmfPath := filepath.Join(tmp, "settings.tmmf")
	_ = tmmfWriteTag(tmmfPath, "主题", "暗色")
	_ = tmmfWriteTag(tmmfPath, "字体", "微软雅黑")
	v, ok := tmmfReadTag(tmmfPath, "字体")
	c.check("TMMF 写入/读取", ok && v == "微软雅黑", v)
	c.check("TMMF 标签列表", len(tmmfListTags(tmmfPath)) == 2, fmt.Sprint(tmmfListTags(tmmfPath)))
	lerfPath := filepath.Join(tmp, "lang.lerf")
	_ = writeTextFile(lerfPath, "LERF.;utf8\nicon\n \n0\n<\\1>打开文件<1/>\n<\\2>保存文件<2/>\n…\n")
	m, err := lerfParse(lerfPath)
	c.check("LERF 解析", err == nil && m["1"] == "打开文件" && m["2"] == "保存文件", fmt.Sprint(m))
	valid, errs := lerfValidate(lerfPath)
	c.check("LERF 校验通过", valid, fmt.Sprint(errs))

	// 8. 排序
	c.check("整数升序", sortIntsCSV("5,2,8,1,9", "true") == "1,2,5,8,9", sortIntsCSV("5,2,8,1,9", "true"))
	c.check("整数降序", sortIntsCSV("5,2,8,1,9", "false") == "9,8,5,2,1", sortIntsCSV("5,2,8,1,9", "false"))
	c.check("字符串排序", sortStringsCSV("b,c,a", "true") == "a,b,c", sortStringsCSV("b,c,a", "true"))
	sj := sortJSONByField(`[{"n":"a","cpu":10},{"n":"b","cpu":5}]`, "cpu", "true")
	c.check("JSON 字段排序", strings.Index(sj, `"b"`) < strings.Index(sj, `"a"`), sj)

	// 9. 转换
	ch := convertToFullHTML("# 标题\n\n正文")
	c.check("MD→完整 HTML", strings.Contains(ch, "<!DOCTYPE html>") && strings.Contains(ch, "<h1>标题</h1>"), "")
	c.check("MD→纯文本", strings.Contains(convertToPlainText("# 标题\n- 项"), "标题"), "")
	c.check("TXT→MD", strings.Contains(convertTxtToMD("第一章 开始"), "# 第一章 开始"), convertTxtToMD("第一章 开始"))

	// 10. IDP + dispatch + 缓存标记
	idpClear()
	idpPush(Packet{Type: "app_version", Source: "rust", JSONData: `{}`})
	c.check("IDP 栈大小", idpSize() == 1, "")
	if appIDPProcess() == 0 {
		p, ok := idpPopFront()
		c.check("IDP 处理结果", ok && p.Type == "result:app_version" && strings.Contains(p.JSONData, Version), p.Type+" "+p.JSONData)
	} else {
		c.check("IDP 处理结果", false, "process failed")
	}

	first := dispatch("app_render_html", `{"md":"# Hi"}`)
	second := dispatch("app_render_html", `{"md":"# Hi"}`)
	c.check("dispatch render_html", strings.Contains(first, `"html"`), first)
	c.check("第二次命中日志缓存", strings.Contains(second, `"cached":true`), second)

	// 11. 单实例
	if singleLock.tryLock("selftest") != 1 {
		c.check("单实例锁获取", false, "listen failed")
	} else {
		c.check("单实例锁获取", true, "")
		c.check("重复获取返回已锁", singleLock.tryLock("selftest") == 1, "")
		singleLock.release()
		c.check("释放后未锁", !singleLock.isLocked(), "")
	}

	fmt.Printf("\n结果：%d 通过，%d 失败\n", c.pass, c.fail)
	if c.fail > 0 {
		return 1
	}
	return 0
}

// ============================================================================
// CLI
// ==========================================================================

func usage() {
	fmt.Print(`MDRenderer Go 单文件版 v` + Version + `

用法：
  md.exe version
  md.exe render   <file.md>
  md.exe ui       <file.md>
  md.exe paged    <file.md> [pageWidth pageHeight]
  md.exe find     <file.md> <query>
  md.exe replace  <file.md> <find> <replace> [--write]
  md.exe insert   <file.md> <left> <right> <insert> [--write]
  md.exe stats    <file.md>
  md.exe log      <baseDir> [params op result]
  md.exe logcache <baseDir> <op> <params>
  md.exe dispatch <type> <json>
  md.exe selftest
`)
}

func main() {
	if len(os.Args) < 2 {
		usage()
		return
	}
	cmd := strings.ToLower(os.Args[1])

	switch cmd {
	case "version", "-v", "--version":
		fmt.Printf("%s v%s (%s)\n", AppName, Version, Edition)
	case "help", "-h", "--help":
		usage()
	case "render":
		if len(os.Args) < 3 {
			fmt.Println("缺少文件路径")
			os.Exit(2)
		}
		content, err := readTextFile(os.Args[2])
		if err != nil {
			fmt.Println("读取失败:", err)
			os.Exit(1)
		}
		fmt.Print(markdownToHTML(content))
	case "ui":
		if len(os.Args) < 3 {
			fmt.Println("缺少文件路径")
			os.Exit(2)
		}
		content, err := readTextFile(os.Args[2])
		if err != nil {
			fmt.Println("读取失败:", err)
			os.Exit(1)
		}
		fmt.Print(uiInstructions(content))
	case "paged":
		if len(os.Args) < 3 {
			fmt.Println("缺少文件路径")
			os.Exit(2)
		}
		content, err := readTextFile(os.Args[2])
		if err != nil {
			fmt.Println("读取失败:", err)
			os.Exit(1)
		}
		w, h := 794, 1123
		if len(os.Args) > 4 {
			w, _ = strconv.Atoi(os.Args[3])
			h, _ = strconv.Atoi(os.Args[4])
		}
		fmt.Print(pagedJSON(content, w, h))
	case "find":
		if len(os.Args) < 4 {
			fmt.Println("用法: find <file> <query>")
			os.Exit(2)
		}
		content, err := readTextFile(os.Args[2])
		if err != nil {
			fmt.Println("读取失败:", err)
			os.Exit(1)
		}
		fmt.Print(findJSON(content, os.Args[3]))
	case "replace":
		if len(os.Args) < 5 {
			fmt.Println("用法: replace <file> <find> <replace> [--write]")
			os.Exit(2)
		}
		content, err := readTextFile(os.Args[2])
		if err != nil {
			fmt.Println("读取失败:", err)
			os.Exit(1)
		}
		out := replaceJSON(content, os.Args[3], os.Args[4])
		fmt.Println(out)
		if len(os.Args) > 5 && os.Args[5] == "--write" {
			var r struct {
				Text string `json:"text"`
			}
			_ = json.Unmarshal([]byte(out), &r)
			if err := writeTextFile(os.Args[2], r.Text); err != nil {
				fmt.Println("写入失败:", err)
				os.Exit(1)
			}
		}
	case "insert":
		if len(os.Args) < 6 {
			fmt.Println("用法: insert <file> <left> <right> <insert> [--write]")
			os.Exit(2)
		}
		content, err := readTextFile(os.Args[2])
		if err != nil {
			fmt.Println("读取失败:", err)
			os.Exit(1)
		}
		out := insertJSON(content, os.Args[3], os.Args[4], os.Args[5])
		fmt.Println(out)
		if len(os.Args) > 6 && os.Args[6] == "--write" {
			var r struct {
				Text string `json:"text"`
			}
			_ = json.Unmarshal([]byte(out), &r)
			if err := writeTextFile(os.Args[2], r.Text); err != nil {
				fmt.Println("写入失败:", err)
				os.Exit(1)
			}
		}
	case "stats":
		if len(os.Args) < 3 {
			fmt.Println("缺少文件路径")
			os.Exit(2)
		}
		content, err := readTextFile(os.Args[2])
		if err != nil {
			fmt.Println("读取失败:", err)
			os.Exit(1)
		}
		fmt.Printf("字符数: %d\n词数: %d\n行数: %d\n", runeLen(content), countWords(content), strings.Count(content, "\n")+1)
	case "log":
		if len(os.Args) < 3 {
			fmt.Println("用法: log <baseDir> [params op result]")
			os.Exit(2)
		}
		if rc := logState.init(os.Args[2]); rc != 0 {
			fmt.Println("初始化失败:", logState.lastError)
			os.Exit(1)
		}
		if len(os.Args) >= 6 {
			logState.write(os.Args[3], os.Args[4], os.Args[5], true)
		}
		sz := logState.size()
		fmt.Printf("log=%s\ncompressed=%s\nsize=%d\n", logState.logFile, logState.compFile, sz)
		logState.shutdown()
	case "logcache":
		if len(os.Args) < 5 {
			fmt.Println("用法: logcache <baseDir> <op> <params>")
			os.Exit(2)
		}
		if rc := logState.init(os.Args[2]); rc != 0 {
			fmt.Println("初始化失败:", logState.lastError)
			os.Exit(1)
		}
		if v, ok := logState.cacheLookup(os.Args[3], os.Args[4]); ok {
			fmt.Println(v)
		} else {
			fmt.Println("未命中")
			os.Exit(1)
		}
		logState.shutdown()
	case "dispatch":
		if len(os.Args) < 4 {
			fmt.Println("用法: dispatch <type> <json>")
			os.Exit(2)
		}
		base, _ := os.Getwd()
		appInit(base, "folder")
		fmt.Println(dispatch(os.Args[2], os.Args[3]))
		appShutdown()
	case "selftest":
		os.Exit(runSelftest())
	default:
		usage()
		os.Exit(2)
	}
}
