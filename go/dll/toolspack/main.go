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

//export cmd_exec
func cmd_exec(t unsafe.Pointer, cmd, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.CmdExec(cstr(cmd)))
}

//export cmd_help
func cmd_help(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.CmdHelp())
}

//export cmd_write_idp
func cmd_write_idp(t unsafe.Pointer, filePath, typ, data *C.char) C.int {
	return C.int(idpclient.Push(cstr(typ), "cmd", cstr(data)))
}

//export cmd_to_idp
func cmd_to_idp(t unsafe.Pointer, typ, data, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.IDPCreatePacket(cstr(typ), "cmd", cstr(data)))
}

//export cmd_read_idp
func cmd_read_idp(t unsafe.Pointer, filePath, buf *C.char, size C.int) C.int {
	s, ok := idpclient.PopJSON()
	if !ok {
		return 0
	}
	return writeBuf(buf, size, s)
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

//export visualize_stats_to_file
func visualize_stats_to_file(t unsafe.Pointer, stats, path *C.char) C.int {
	return C.int(engine.VisualizeStatsToFile(cstr(stats), cstr(path)))
}

//export visualize_stats_to_html
func visualize_stats_to_html(t unsafe.Pointer, stats, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.VisualizeStatsToHTML(cstr(stats)))
}

//export visualize_stats_sorted_html
func visualize_stats_sorted_html(t unsafe.Pointer, stats, field, asc, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.VisualizeSortedHTML(cstr(stats), cstr(field), cstr(asc)))
}

//export visualize_thread_modes
func visualize_thread_modes(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.VisualizeThreadModes())
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
