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

//export lerf_load
func lerf_load(t unsafe.Pointer, path *C.char) C.int { return C.int(engine.LerfLoad(cstr(path))) }

//export lerf_get
func lerf_get(t unsafe.Pointer, id, buf *C.char, size C.int) C.int {
	v, ok := engine.LerfGetLoaded(cstr(id))
	if !ok {
		return -1
	}
	return writeBuf(buf, size, v)
}

//export lerf_get_from_file
func lerf_get_from_file(t unsafe.Pointer, path, id, buf *C.char, size C.int) C.int {
	v, ok := engine.LerfGet(cstr(path), cstr(id))
	if !ok {
		return -1
	}
	return writeBuf(buf, size, v)
}

//export lerf_get_multi
func lerf_get_multi(t unsafe.Pointer, ids, buf *C.char, size C.int) C.int {
	p := engine.LerfLoadedPath()
	if p == "" {
		return -1
	}
	return writeBuf(buf, size, engine.LerfGetMulti(p, cstr(ids)))
}

//export lerf_list_all
func lerf_list_all(t unsafe.Pointer, path, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.LerfListAll(cstr(path)))
}

//export lerf_validate
func lerf_validate(t unsafe.Pointer, path *C.char) C.int {
	ok, _ := engine.LerfValidate(cstr(path))
	if ok {
		return 1
	}
	return 0
}

//export lerf_key_count
func lerf_key_count(t unsafe.Pointer, path *C.char) C.int {
	return C.int(engine.LerfKeyCount(cstr(path)))
}

func main() {}
