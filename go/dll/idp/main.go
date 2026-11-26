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

//export idp_push
func idp_push(t unsafe.Pointer, typ, source, data *C.char) C.int {
	return C.int(engine.IDPPush(cstr(typ), cstr(source), cstr(data)))
}

//export idp_pop_json
func idp_pop_json(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	s, ok := engine.IDPPopJSON()
	if !ok {
		writeBuf(buf, size, "")
		return 0
	}
	return writeBuf(buf, size, s)
}

//export idp_pop_idp
func idp_pop_idp(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	s, ok := engine.IDPPopIDP()
	if !ok {
		writeBuf(buf, size, "")
		return 0
	}
	return writeBuf(buf, size, s)
}

//export idp_peek_json
func idp_peek_json(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.IDPPeekJSON())
}

//export idp_stack_size
func idp_stack_size(t unsafe.Pointer) C.int { return C.int(engine.IDPStackSize()) }

//export idp_stack_clear
func idp_stack_clear(t unsafe.Pointer) C.int { engine.IDPStackClear(); return 0 }

func main() {}
