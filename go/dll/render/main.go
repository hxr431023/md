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

//export render_ui
func render_ui(t unsafe.Pointer, content, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderUI(cstr(content)))
}

//export render_line
func render_line(t unsafe.Pointer, line, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderLine(cstr(line)))
}

//export render_line_count
func render_line_count(t unsafe.Pointer, content *C.char) C.int {
	return C.int(engine.RenderLineCount(cstr(content)))
}

//export render_get_line
func render_get_line(t unsafe.Pointer, content *C.char, n C.int, buf *C.char, size C.int) C.int {
	s, ok := engine.RenderGetLine(cstr(content), int(n))
	if !ok {
		return -1
	}
	return writeBuf(buf, size, s)
}

//export render_find
func render_find(t unsafe.Pointer, content, query, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderFind(cstr(content), cstr(query)))
}

//export render_replace
func render_replace(t unsafe.Pointer, content, find, repl, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderReplace(cstr(content), cstr(find), cstr(repl)))
}

//export render_insert
func render_insert(t unsafe.Pointer, content, left, right, ins, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderInsert(cstr(content), cstr(left), cstr(right), cstr(ins)))
}

//export render_paged
func render_paged(t unsafe.Pointer, content *C.char, w, h C.int, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderPaged(cstr(content), int(w), int(h)))
}

//export render_write_idp
func render_write_idp(t unsafe.Pointer, filePath, typ, data *C.char) C.int {
	return C.int(idpclient.Push(cstr(typ), "render", cstr(data)))
}

//export render_to_idp
func render_to_idp(t unsafe.Pointer, typ, data, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.IDPCreatePacket(cstr(typ), "render", cstr(data)))
}

//export render_read_idp
func render_read_idp(t unsafe.Pointer, filePath, buf *C.char, size C.int) C.int {
	s, ok := idpclient.PopJSON()
	if !ok {
		return 0
	}
	return writeBuf(buf, size, s)
}

func main() {}
