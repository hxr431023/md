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

//export config_init
func config_init(t unsafe.Pointer, baseDir *C.char) C.int {
	return C.int(engine.ConfigInit(cstr(baseDir)))
}

//export config_load_settings
func config_load_settings(t unsafe.Pointer) C.int { return C.int(engine.ConfigLoad()) }

//export config_save_settings
func config_save_settings(t unsafe.Pointer) C.int { return C.int(engine.ConfigSave()) }

//export config_get_setting
func config_get_setting(t unsafe.Pointer, key, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ConfigGet(cstr(key)))
}

//export config_set_setting
func config_set_setting(t unsafe.Pointer, key, val *C.char) C.int {
	return C.int(engine.ConfigSet(cstr(key), cstr(val)))
}

//export config_has_setting
func config_has_setting(t unsafe.Pointer, key *C.char) C.int {
	return C.int(engine.ConfigHas(cstr(key)))
}

//export config_remove_setting
func config_remove_setting(t unsafe.Pointer, key *C.char) C.int {
	return C.int(engine.ConfigRemove(cstr(key)))
}

//export config_get_thread_mode
func config_get_thread_mode(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ConfigThreadMode())
}

//export config_set_thread_mode
func config_set_thread_mode(t unsafe.Pointer, mode *C.char) C.int {
	return C.int(engine.ConfigSetThreadMode(cstr(mode)))
}

func main() {}
