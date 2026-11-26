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

//export file_read
func file_read(t unsafe.Pointer, path, buf *C.char, size C.int) C.int {
	s, rc := engine.FileRead(cstr(path))
	if rc != 0 {
		return -1
	}
	return writeBuf(buf, size, s)
}

//export file_write
func file_write(t unsafe.Pointer, path, content *C.char) C.int {
	return C.int(engine.FileWrite(cstr(path), cstr(content)))
}

//export file_append
func file_append(t unsafe.Pointer, path, content *C.char) C.int {
	return C.int(engine.FileAppend(cstr(path), cstr(content)))
}

//export file_exists
func file_exists(t unsafe.Pointer, path *C.char) C.int {
	return C.int(engine.FileExists(cstr(path)))
}

//export file_delete
func file_delete(t unsafe.Pointer, path *C.char) C.int {
	return C.int(engine.FileDelete(cstr(path)))
}

//export file_name
func file_name(t unsafe.Pointer, path, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.FileName(cstr(path)))
}

//export file_extension
func file_extension(t unsafe.Pointer, path, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.FileExtension(cstr(path)))
}

func main() {}
