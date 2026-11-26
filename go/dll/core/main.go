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

//export log_init
func log_init(t unsafe.Pointer, baseDir *C.char) C.int { return C.int(engine.LogInit(cstr(baseDir))) }

//export log_shutdown
func log_shutdown(t unsafe.Pointer) { engine.LogShutdown() }

//export log_write
func log_write(t unsafe.Pointer, params, op, result *C.char) {
	engine.LogWrite(cstr(params), cstr(op), cstr(result))
}

//export log_write_end
func log_write_end(t unsafe.Pointer, params, op, result *C.char) {
	engine.LogWriteEnd(cstr(params), cstr(op), cstr(result))
}

//export log_set_enabled
func log_set_enabled(t unsafe.Pointer, cat *C.char, on C.int) {
	engine.LogSetEnabled(cstr(cat), int(on))
}

//export log_is_enabled
func log_is_enabled(t unsafe.Pointer, cat *C.char) C.int {
	return C.int(engine.LogIsEnabled(cstr(cat)))
}

//export log_clear_all
func log_clear_all(t unsafe.Pointer) C.int { return C.int(engine.LogClearAll()) }

//export log_size
func log_size(t unsafe.Pointer) C.int { return C.int(engine.LogSize()) }

//export log_cache_lookup
func log_cache_lookup(t unsafe.Pointer, op, params *C.char, buf *C.char, size C.int) C.int {
	s, ok := engine.LogCacheLookup(cstr(op), cstr(params))
	if !ok {
		return -1
	}
	return writeBuf(buf, size, s)
}

//export log_cache_put
func log_cache_put(t unsafe.Pointer, op, params, result *C.char) C.int {
	return C.int(engine.LogCachePut(cstr(op), cstr(params), cstr(result)))
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

//export single_try_lock
func single_try_lock(t unsafe.Pointer, name *C.char) C.int {
	return C.int(engine.SingleTryLock(cstr(name)))
}

//export single_release
func single_release(t unsafe.Pointer) { engine.SingleRelease() }

//export single_is_locked
func single_is_locked(t unsafe.Pointer) C.int { return C.int(engine.SingleIsLocked()) }

//export single_lock_path
func single_lock_path(t unsafe.Pointer, name, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.SingleLockPath(cstr(name)))
}

func main() {}
