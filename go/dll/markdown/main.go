package main

// 本文件由 tools/gen_dlls.py 生成，请勿手改；改 engine/api.go 后重新生成。
// MDRenderer Go DLL：薄 cgo 适配层。业务实现统一在 internal/engine。

/*
#include <stdlib.h>
#include <stdint.h>
*/
import "C"

import (
	"unsafe"

	"github.com/hxr431023/md/go/internal/engine"
	"github.com/hxr431023/md/go/internal/idpclient"
)

func cstr(p *C.char) string {
	if p == nil {
		return ""
	}
	return C.GoString(p)
}

// writeBuf 写入 UTF-8 字节并返回实际字节数（不是字符数），缓冲区不足时按 rune 边界截断。
func writeBuf(buf *C.char, size C.int, s string) C.int {
	if buf == nil || size <= 0 {
		return 0
	}
	max := int(size) - 1
	b := []byte(s)
	if len(b) > max {
		cut := max
		for cut > 0 && b[cut]&0xC0 == 0x80 {
			cut--
		}
		b = b[:cut]
	}
	if len(b) > 0 {
		dest := unsafe.Slice((*byte)(unsafe.Pointer(buf)), int(size))
		copy(dest, b)
		dest[len(b)] = 0
	} else {
		*(*byte)(unsafe.Pointer(buf)) = 0
	}
	return C.int(len(b))
}

//export render_html
func render_html(t unsafe.Pointer, md, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.MarkdownHTML(cstr(md)))
}

//export classify_line
func classify_line(t unsafe.Pointer, line *C.char) C.int {
	return C.int(engine.ClassifyLine(cstr(line)))
}

//export count_inline_styles
func count_inline_styles(t unsafe.Pointer, line *C.char) C.int {
	return C.int(engine.CountInlineStyles(cstr(line)))
}

//export count_lines
func count_lines(t unsafe.Pointer, text *C.char) C.int { return C.int(engine.CountLines(cstr(text))) }

//export count_words
func count_words(t unsafe.Pointer, text *C.char) C.int { return C.int(engine.CountWords(cstr(text))) }

//export count_chars
func count_chars(t unsafe.Pointer, text *C.char) C.int { return C.int(engine.CountChars(cstr(text))) }

//export markdown_write_idp
func markdown_write_idp(t unsafe.Pointer, filePath, typ, data *C.char) C.int {
	return C.int(idpclient.Push(cstr(typ), "markdown", cstr(data)))
}

//export markdown_to_idp
func markdown_to_idp(t unsafe.Pointer, typ, data, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.IDPCreatePacket(cstr(typ), "markdown", cstr(data)))
}

//export markdown_read_idp
func markdown_read_idp(t unsafe.Pointer, filePath, buf *C.char, size C.int) C.int {
	s, ok := idpclient.PopJSON()
	if !ok {
		return 0
	}
	return writeBuf(buf, size, s)
}

func main() {}
