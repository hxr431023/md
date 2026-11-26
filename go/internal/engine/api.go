package engine

// api.go —— 供各 DLL 薄适配层调用的导出 API。
// 内部实现全部在 engine.go（单文件版的同一份代码），这里只做命名导出与类型收敛。

import (
	"bytes"
	"compress/zlib"
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
)

// ---------------- 生命周期 ----------------

func AppInit(baseDir, mode string) { appInit(baseDir, mode) }
func AppShutdown()                 { appShutdown() }
func AppVersion() string           { return Version }
func AppEdition() string           { return Edition }

// ---------------- 文档 ----------------

func DocNew() int                        { docState.New(); return 0 }
func DocOpen(path string) int            { return docState.Open(path) }
func DocSave() int                       { return docState.Save() }
func DocSaveAs(path string) int          { return docState.SaveAs(path) }
func DocContent() string                 { c, _, _, _ := docState.Snapshot(); return c }
func DocFilePath() string                { _, p, _, _ := docState.Snapshot(); return p }
func DocIsDirty() int                    { _, _, d, _ := docState.Snapshot(); return btoi(d) }
func DocSetContent(s string) int         { docState.SetContent(s); return 0 }
func DocInsert(pos int, text string) int { return docState.InsertText(pos, text) }
func DocDelete(start, end int) int       { return docState.DeleteText(start, end) }
func DocUndo() int                       { return docState.Undo() }
func DocRedo() int                       { return docState.Redo() }
func DocCanUndo() int                    { return btoi(docState.CanUndo()) }
func DocCanRedo() int                    { return btoi(docState.CanRedo()) }
func DocSetCursor(pos int) {
	docState.mu.Lock()
	docState.cursor = clamp(pos, 0, runeLen(docState.content))
	docState.mu.Unlock()
}
func DocCursor() int          { _, _, _, c := docState.Snapshot(); return c }
func DocCursorLine() int      { return docState.CursorLine() }
func DocCursorCol() int       { return docState.CursorCol() }
func DocText(s, e int) string { return docState.GetText(s, e) }
func DocCharCount() int       { return docState.CharCount() }
func DocWordCount() int       { return docState.WordCount() }
func DocLineCount() int       { return docState.LineCount() }
func DocUndoDepth() int       { return len(docState.undo) }
func DocRedoDepth() int       { return len(docState.redo) }

// ---------------- 配置 ----------------

func ConfigInit(base string) int       { return cfg.init(base) }
func ConfigLoad() int                  { return cfg.loadSettings() }
func ConfigSave() int                  { return cfg.saveSettings() }
func ConfigGet(k string) string        { return cfg.get(k) }
func ConfigSet(k, v string) int        { cfg.set(k, v); return 0 }
func ConfigHas(k string) int           { return btoi(cfg.has(k)) }
func ConfigRemove(k string) int        { cfg.remove(k); return 0 }
func ConfigThreadMode() string         { return cfg.threadMode() }
func ConfigSetThreadMode(m string) int { return cfg.setThreadMode(m) }

// ---------------- Markdown ----------------

func MarkdownHTML(md string) string     { return markdownToHTML(md) }
func ClassifyLine(line string) int      { return classifyLine(line) }
func CountInlineStyles(line string) int { return len(analyzeInlineStyles(line)) }
func CountLines(text string) int {
	if text == "" {
		return 0
	}
	return strings.Count(text, "\n") + 1
}
func CountWords(text string) int { return countWords(text) }
func CountChars(text string) int { return runeLen(text) }

// ---------------- 渲染 ----------------

func RenderUI(content string) string                  { return uiInstructions(content) }
func RenderHTMLFromMD(md string) string               { return markdownToHTML(md) }
func RenderFind(content, query string) string         { return findJSON(content, query) }
func RenderReplace(content, find, repl string) string { return replaceJSON(content, find, repl) }
func RenderInsert(content, left, right, insert string) string {
	return insertJSON(content, left, right, insert)
}
func RenderPaged(content string, w, h int) string { return pagedJSON(content, w, h) }

// RenderLine 单行 → 单个元素 JSON（供增量渲染）。
func RenderLine(line string) string {
	els := parseBlocks(line)
	if len(els) == 0 {
		return `{"kind":"paragraph","text":` + jq(line) + `}`
	}
	full := uiInstructions(line)
	var doc struct {
		Elements []json.RawMessage `json:"elements"`
	}
	if err := json.Unmarshal([]byte(full), &doc); err == nil && len(doc.Elements) > 0 {
		return string(doc.Elements[0])
	}
	return `{"kind":"paragraph","text":` + jq(line) + `}`
}

func RenderLineCount(content string) int {
	if content == "" {
		return 0
	}
	return strings.Count(content, "\n") + 1
}

func RenderGetLine(content string, n int) (string, bool) {
	lines := strings.Split(content, "\n")
	if n < 0 || n >= len(lines) {
		return "", false
	}
	return lines[n], true
}

// ---------------- 日志 / 压缩 ----------------

func LogInit(base string) int                    { return logState.init(base) }
func LogShutdown()                               { logState.shutdown() }
func LogWrite(p, op, r string)                   { logState.write(p, op, r, false) }
func LogWriteEnd(p, op, r string)                { logState.writeEnd(p, op, r) }
func LogSetEnabled(cat string, on int)           { logState.setEnabled(cat, on != 0) }
func LogIsEnabled(cat string) int                { return btoi(logState.isEnabled(cat)) }
func LogClearAll() int                           { return logState.clearAll() }
func LogSize() int64                             { return logState.size() }
func LogCacheLookup(op, p string) (string, bool) { return logState.cacheLookup(op, p) }
func LogCachePut(op, p, r string) int            { return logState.cachePut(op, p, r) }
func LogLastError() string {
	logState.mu.Lock()
	defer logState.mu.Unlock()
	return logState.lastError
}
func LogWorkPath() string {
	logState.mu.Lock()
	defer logState.mu.Unlock()
	return logState.logFile
}
func LogCompressedPath() string {
	logState.mu.Lock()
	defer logState.mu.Unlock()
	return logState.compFile
}

func CompressFile(src, dst string) int {
	if err := compressFile(src, dst); err != nil {
		return -1
	}
	return 0
}
func DecompressFile(src, dst string) int {
	if err := decompressFile(src, dst); err != nil {
		return -1
	}
	return 0
}
func StringToFile(path, content string) int {
	if err := writeTextFile(path, content); err != nil {
		return -1
	}
	return 0
}
func FileToString(path string) (string, int) {
	c, err := readTextFile(path)
	if err != nil {
		return "", -1
	}
	return c, 0
}
func CompressRatio(src string) float64 { return compressRatio(src) }

// CompressedFileToString 读取压缩文件并解压为 UTF-8 文本。
func CompressedFileToString(path string) (string, int) {
	in, err := os.Open(path)
	if err != nil {
		return "", -1
	}
	defer in.Close()
	zr, err := zlib.NewReader(in)
	if err != nil {
		return "", -1
	}
	defer zr.Close()
	data, err := io.ReadAll(zr)
	if err != nil {
		return "", -1
	}
	return string(data), 0
}

// CompressPercent 返回压缩后占原始大小的百分比（0-100），失败返回 -1。
func CompressPercent(path string) int {
	data, err := os.ReadFile(path)
	if err != nil {
		return -1
	}
	if len(data) == 0 {
		return 0
	}
	var buf bytes.Buffer
	zw, err := zlib.NewWriterLevel(&buf, zlib.BestSpeed)
	if err != nil {
		return -1
	}
	if _, err := zw.Write(data); err != nil {
		return -1
	}
	if err := zw.Close(); err != nil {
		return -1
	}
	return int(int64(buf.Len()) * 100 / int64(len(data)))
}

// ---------------- 文件 ----------------

func FileRead(path string) (string, int)  { return fileRead(path) }
func FileWrite(path, content string) int  { return fileWrite(path, content) }
func FileAppend(path, content string) int { return fileAppend(path, content) }
func FileExists(path string) int          { return btoi(fileExists(path)) }
func FileSize(path string) int64          { return fileSize(path) }
func FileDelete(path string) int {
	if os.Remove(path) == nil {
		return 0
	}
	return -1
}
func FileName(path string) string      { return filepath.Base(path) }
func FileExtension(path string) string { return strings.TrimPrefix(filepath.Ext(path), ".") }

// ---------------- 单实例 ----------------

func SingleTryLock(name string) int     { return singleLock.tryLock(name) }
func SingleRelease()                    { singleLock.release() }
func SingleIsLocked() int               { return btoi(singleLock.isLocked()) }
func SingleLockPath(name string) string { return singleLock.lockPath(name) }

// ---------------- TMMF / LERF ----------------

func TmmfReadAll(path string) (string, int) {
	c, err := readTextFile(path)
	if err != nil {
		return "", -1
	}
	return c, 0
}
func TmmfReadTag(path, tag string) (string, bool) { return tmmfReadTag(path, tag) }
func TmmfSearch(path, kw string) string           { return tmmfSearch(path, kw) }
func TmmfListTags(path string) string {
	tags := tmmfListTags(path)
	var b strings.Builder
	b.WriteString("[")
	for i, t := range tags {
		if i > 0 {
			b.WriteByte(',')
		}
		b.WriteString(jq(t))
	}
	b.WriteString("]")
	return b.String()
}
func TmmfLineCount(path string) int {
	c, err := readTextFile(path)
	if err != nil {
		return -1
	}
	return strings.Count(c, "\n") + 1
}
func TmmfWriteTag(path, tag, content string) int { return tmmfWriteTag(path, tag, content) }

func LerfLoad(path string) int {
	if _, err := lerfParse(path); err != nil {
		return -1
	}
	loadedLerf.mu.Lock()
	loadedLerf.path = path
	loadedLerf.mu.Unlock()
	return 0
}

// loadedLerf 保存最近一次 lerf_load 的文件，供 lerf_get(id) 使用。
var loadedLerf struct {
	mu   sync.Mutex
	path string
}

func LerfGetLoaded(id string) (string, bool) {
	loadedLerf.mu.Lock()
	p := loadedLerf.path
	loadedLerf.mu.Unlock()
	if p == "" {
		return "", false
	}
	return LerfGet(p, id)
}
func LerfGet(path, id string) (string, bool) {
	m, err := lerfParse(path)
	if err != nil {
		return "", false
	}
	v, ok := m[id]
	return v, ok
}
func LerfGetFromFile(path, id string) (string, bool) { return LerfGet(path, id) }

// LerfLoadedPath 返回当前已加载的 LERF 文件路径（空表示未加载）。
func LerfLoadedPath() string {
	loadedLerf.mu.Lock()
	defer loadedLerf.mu.Unlock()
	return loadedLerf.path
}
func LerfGetMulti(path, ids string) string {
	m, err := lerfParse(path)
	if err != nil {
		return "[]"
	}
	var b strings.Builder
	b.WriteString("[")
	for i, id := range strings.Split(ids, ",") {
		if i > 0 {
			b.WriteByte(',')
		}
		b.WriteString(jq(m[strings.TrimSpace(id)]))
	}
	b.WriteString("]")
	return b.String()
}
func LerfListAll(path string) string {
	m, err := lerfParse(path)
	if err != nil {
		return "[]"
	}
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Slice(keys, func(i, j int) bool {
		ai, _ := atoiSafe(keys[i])
		aj, _ := atoiSafe(keys[j])
		if ai != aj {
			return ai < aj
		}
		return keys[i] < keys[j]
	})
	var b strings.Builder
	b.WriteString("[")
	for i, k := range keys {
		if i > 0 {
			b.WriteByte(',')
		}
		b.WriteString(`{"id":` + jq(k) + `,"text":` + jq(m[k]) + `}`)
	}
	b.WriteString("]")
	return b.String()
}
func LerfValidate(path string) (bool, []string) { return lerfValidate(path) }
func LerfKeyCount(path string) int {
	m, err := lerfParse(path)
	if err != nil {
		return -1
	}
	return len(m)
}

// ---------------- 转换 ----------------

func ConvertHTML(md string) string { return convertToFullHTML(md) }
func ConvertHTMLFile(in, out string) int {
	md, err := readTextFile(in)
	if err != nil {
		return -1
	}
	return StringToFile(out, convertToFullHTML(md))
}
func ConvertText(md string) string { return convertToPlainText(md) }
func ConvertTextFile(in, out string) int {
	md, err := readTextFile(in)
	if err != nil {
		return -1
	}
	return StringToFile(out, convertToPlainText(md))
}
func ConvertTxt2Md(text string) string { return convertTxtToMD(text) }
func ConvertTxt2MdFile(in, out string) int {
	text, err := readTextFile(in)
	if err != nil {
		return -1
	}
	return StringToFile(out, convertTxtToMD(text))
}

// ---------------- 排序 ----------------

func QuickSortInts(nums, asc string) string     { return sortIntsCSV(nums, asc) }
func QuickSortStrings(strs, asc string) string  { return sortStringsCSV(strs, asc) }
func QuickSortJSON(j, field, asc string) string { return sortJSONByField(j, field, asc) }
func QuickSortInfo() string                     { return quickSortInfo() }

// ---------------- CMD ----------------

func CmdExec(cmd string) string { return cmdExec(cmd) }
func CmdHelp() string           { return cmdHelp() }

// ---------------- 线程统计 / 可视化 ----------------

func ThreadStatInit(mode string) int { return threadStats.init(mode) }
func ThreadStatMode() string {
	threadStats.mu.Lock()
	defer threadStats.mu.Unlock()
	return threadStats.mode
}
func ThreadStatSetMode(m string) int {
	if m != "folder" && m != "single" && m != "per_dll" {
		return -1
	}
	threadStats.mu.Lock()
	threadStats.mode = m
	threadStats.mu.Unlock()
	return 0
}
func ThreadStatRegister(name, category string) int { return threadStats.register(name, category) }
func ThreadStatUnregister(name string)             { threadStats.unregister(name) }
func ThreadStatSample() int                        { return threadStats.sample() }
func ThreadStatJSON() string                       { return threadStats.statsJSON() }
func ThreadStatMemory() string                     { return threadStats.memoryJSON() }
func ThreadStatDiskRead(n int64) {
	threadStats.mu.Lock()
	threadStats.diskRead += n
	threadStats.mu.Unlock()
}
func ThreadStatDiskWrite(n int64) {
	threadStats.mu.Lock()
	threadStats.diskWrite += n
	threadStats.mu.Unlock()
}
func ThreadStatDisk() string { return threadStats.diskJSON() }

func VisualizeStatsToHTML(stats string) string { return visualizeStatsHTML(stats) }
func VisualizeStatsToFile(stats, path string) int {
	return StringToFile(path, visualizeStatsHTML(stats))
}
func VisualizeSortedHTML(stats, field, asc string) string {
	var m map[string]any
	if err := json.Unmarshal([]byte(stats), &m); err != nil {
		return visualizeStatsHTML(stats)
	}
	if arr, ok := m["threads"].([]any); ok && field != "" {
		up := strings.EqualFold(asc, "true")
		sort.SliceStable(arr, func(i, j int) bool {
			a := toFloat(arr[i].(map[string]any)[field])
			b := toFloat(arr[j].(map[string]any)[field])
			if up {
				return a < b
			}
			return a > b
		})
		if out, err := json.Marshal(m); err == nil {
			return visualizeStatsHTML(string(out))
		}
	}
	return visualizeStatsHTML(stats)
}
func VisualizeThreadModes() string { return visualizeThreadModesHTML() }

// ---------------- IDP ----------------

func IDPPush(typ, source, jsonData string) int {
	idpPush(Packet{Type: typ, Source: source, JSONData: jsonData})
	return 0
}
func IDPPopJSON() (string, bool) {
	p, ok := idpPopFront()
	if !ok {
		return "", false
	}
	return p.JSONData, true
}
func IDPPopIDP() (string, bool) {
	p, ok := idpPopFront()
	if !ok {
		return "", false
	}
	return idpCreatePacket(p.Type, p.Source, p.JSONData), true
}
func IDPPeekJSON() string {
	p, ok := idpPeek()
	if !ok {
		return ""
	}
	return p.JSONData
}
func IDPStackSize() int { return idpSize() }
func IDPStackClear()    { idpClear() }
func IDPCreatePacket(typ, source, jsonData string) string {
	return idpCreatePacket(typ, source, jsonData)
}
func IDPEnqueue(typ, source, jsonData string) int { IDPPush(typ, source, jsonData); return 0 }
func IDPAppProcess() int                          { return appIDPProcess() }

func IDPDispatch(typ, jsonData string) string { return dispatch(typ, jsonData) }

// ---------------- 工具 ----------------

// JSONQuote 返回 JSON 字符串字面量（含引号）。
func JSONQuote(s string) string {
	b, _ := json.Marshal(s)
	return string(b)
}

// Int64String 十进制字符串。
func Int64String(n int64) string { return strconv.FormatInt(n, 10) }

func btoi(b bool) int {
	if b {
		return 1
	}
	return 0
}

func atoiSafe(s string) (int, error) {
	n := 0
	if s == "" {
		return 0, nil
	}
	for _, r := range s {
		if r < '0' || r > '9' {
			return 0, nil
		}
		n = n*10 + int(r-'0')
	}
	return n, nil
}
