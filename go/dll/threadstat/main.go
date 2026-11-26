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

//export threadstat_init
func threadstat_init(t unsafe.Pointer, mode *C.char) C.int {
	return C.int(engine.ThreadStatInit(cstr(mode)))
}

//export threadstat_get_mode
func threadstat_get_mode(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ThreadStatMode())
}

//export threadstat_set_mode
func threadstat_set_mode(t unsafe.Pointer, mode *C.char) C.int {
	return C.int(engine.ThreadStatSetMode(cstr(mode)))
}

//export threadstat_register
func threadstat_register(t unsafe.Pointer, name, cat *C.char) C.int {
	return C.int(engine.ThreadStatRegister(cstr(name), cstr(cat)))
}

//export threadstat_unregister
func threadstat_unregister(t unsafe.Pointer, name *C.char) { engine.ThreadStatUnregister(cstr(name)) }

//export threadstat_sample
func threadstat_sample(t unsafe.Pointer) C.int { return C.int(engine.ThreadStatSample()) }

//export threadstat_get_json
func threadstat_get_json(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ThreadStatJSON())
}

//export threadstat_memory
func threadstat_memory(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ThreadStatMemory())
}

//export threadstat_disk_read
func threadstat_disk_read(t unsafe.Pointer, n C.longlong) { engine.ThreadStatDiskRead(int64(n)) }

//export threadstat_disk_write
func threadstat_disk_write(t unsafe.Pointer, n C.longlong) { engine.ThreadStatDiskWrite(int64(n)) }

//export threadstat_disk
func threadstat_disk(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ThreadStatDisk())
}

func main() {}
