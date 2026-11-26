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

//export tmmf_read_all
func tmmf_read_all(t unsafe.Pointer, path, buf *C.char, size C.int) C.int {
	s, rc := engine.TmmfReadAll(cstr(path))
	if rc != 0 {
		return -1
	}
	return writeBuf(buf, size, s)
}

//export tmmf_read_tag
func tmmf_read_tag(t unsafe.Pointer, path, tag, buf *C.char, size C.int) C.int {
	v, ok := engine.TmmfReadTag(cstr(path), cstr(tag))
	if !ok {
		return -1
	}
	return writeBuf(buf, size, v)
}

//export tmmf_search
func tmmf_search(t unsafe.Pointer, path, kw, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.TmmfSearch(cstr(path), cstr(kw)))
}

//export tmmf_list_tags
func tmmf_list_tags(t unsafe.Pointer, path, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.TmmfListTags(cstr(path)))
}

//export tmmf_line_count
func tmmf_line_count(t unsafe.Pointer, path *C.char) C.int {
	return C.int(engine.TmmfLineCount(cstr(path)))
}

//export tmmf_write_tag
func tmmf_write_tag(t unsafe.Pointer, path, tag, content *C.char) C.int {
	return C.int(engine.TmmfWriteTag(cstr(path), cstr(tag), cstr(content)))
}

func main() {}
