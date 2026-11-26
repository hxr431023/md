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

//export compress_file
func compress_file(t unsafe.Pointer, src, dst *C.char) C.int {
	return C.int(engine.CompressFile(cstr(src), cstr(dst)))
}

//export compress_decompress_file
func compress_decompress_file(t unsafe.Pointer, src, dst *C.char) C.int {
	return C.int(engine.DecompressFile(cstr(src), cstr(dst)))
}

//export compress_string_to_file
func compress_string_to_file(t unsafe.Pointer, content, dst *C.char) C.int {
	return C.int(engine.StringToFile(cstr(dst), cstr(content)))
}

//export compress_file_to_string
func compress_file_to_string(t unsafe.Pointer, src *C.char, buf *C.char, size C.int) C.int {
	s, rc := engine.CompressedFileToString(cstr(src))
	if rc != 0 {
		return -1
	}
	return writeBuf(buf, size, s)
}

//export compress_ratio
func compress_ratio(t unsafe.Pointer, src *C.char) C.int {
	return C.int(engine.CompressPercent(cstr(src)))
}

func main() {}
