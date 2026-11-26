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

//export logmain_version
func logmain_version(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.AppVersion())
}

//export logmain_init
func logmain_init(t unsafe.Pointer, baseDir *C.char) C.int {
	return C.int(engine.LogInit(cstr(baseDir)))
}

//export logmain_write
func logmain_write(t unsafe.Pointer, params, op, result *C.char) {
	engine.LogWrite(cstr(params), cstr(op), cstr(result))
}

//export logmain_write_end
func logmain_write_end(t unsafe.Pointer, params, op, result *C.char) {
	engine.LogWriteEnd(cstr(params), cstr(op), cstr(result))
}

//export logmain_shutdown
func logmain_shutdown(t unsafe.Pointer) { engine.LogShutdown() }

//export logmain_set_enabled
func logmain_set_enabled(t unsafe.Pointer, cat *C.char, on C.int) {
	engine.LogSetEnabled(cstr(cat), int(on))
}

//export logmain_is_enabled
func logmain_is_enabled(t unsafe.Pointer, cat *C.char) C.int {
	return C.int(engine.LogIsEnabled(cstr(cat)))
}

//export logmain_clear_all
func logmain_clear_all(t unsafe.Pointer) C.int { return C.int(engine.LogClearAll()) }

//export logmain_size
func logmain_size(t unsafe.Pointer) C.int { return C.int(engine.LogSize()) }

//export logmain_cache_lookup
func logmain_cache_lookup(t unsafe.Pointer, op, params, buf *C.char, size C.int) C.int {
	s, ok := engine.LogCacheLookup(cstr(op), cstr(params))
	if !ok {
		return -1
	}
	return writeBuf(buf, size, s)
}

//export logmain_cache_put
func logmain_cache_put(t unsafe.Pointer, op, params, result *C.char) C.int {
	return C.int(engine.LogCachePut(cstr(op), cstr(params), cstr(result)))
}

//export logmain_status
func logmain_status(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	j := `{"work":` + engine.JSONQuote(engine.LogWorkPath()) +
		`,"compressed":` + engine.JSONQuote(engine.LogCompressedPath()) +
		`,"size":` + engine.Int64String(engine.LogSize()) +
		`,"last_error":` + engine.JSONQuote(engine.LogLastError()) + `}`
	return writeBuf(buf, size, j)
}

//export logmain_compress_now
func logmain_compress_now(t unsafe.Pointer) C.int {
	engine.LogShutdown()
	if engine.LogLastError() != "" {
		return -1
	}
	return 0
}

func main() {}
