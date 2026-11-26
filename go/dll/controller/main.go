package main

// MDRenderer 控制层主控 DLL（Go 版）
// 唯一对外入口：生命周期 + IDP 三段式 + 全部 app_* C ABI。
// 所有业务实现在 internal/engine（与单文件版同一份代码）。
// 通信约束：跨 DLL 一律走 IDP 内存双向栈（idp_push → app_idp_process → idp_pop_json）；
// 渲染与 CMD 不允许文件/句柄/函数指针通道。

/*
#include <stdlib.h>
#include <stdint.h>
*/
import "C"

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"unsafe"

	"github.com/hxr431023/md/go/internal/engine"
)

// ---------- 基础工具 ----------

func cstr(p *C.char) string {
	if p == nil {
		return ""
	}
	return C.GoString(p)
}

// writeBuf 把 UTF-8 字符串写入调用方缓冲区，返回实际写入的字节数。
// 说明：Java 版返回 String.length()（UTF-16 码元数），Rust 前端按字节截取 buf[..n]；
// 含中文时会被截断，Go 版改为返回真实字节数（对旧调用方也是更正确的语义）。
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

func obj(kv ...any) string {
	m := map[string]any{}
	for i := 0; i+1 < len(kv); i += 2 {
		m[kv[i].(string)] = kv[i+1]
	}
	b, _ := json.Marshal(m)
	return string(b)
}

// ---------- GraalVM isolate 兼容桩 ----------

//export graal_create_isolate
func graal_create_isolate(params unsafe.Pointer, isolate **C.void, thread **C.void) C.int {
	if thread != nil {
		*thread = (*C.void)(unsafe.Pointer(uintptr(1)))
	}
	if isolate != nil {
		*isolate = (*C.void)(unsafe.Pointer(uintptr(1)))
	}
	return 0
}

//export graal_tear_down_isolate
func graal_tear_down_isolate(thread unsafe.Pointer) C.int { return 0 }

// ---------- 生命周期 ----------

//export app_init
func app_init(t unsafe.Pointer, baseDir *C.char) C.int {
	engine.AppInit(cstr(baseDir), "folder")
	return 0
}

//export app_shutdown
func app_shutdown(t unsafe.Pointer) { engine.AppShutdown() }

// ---------- 文档 ----------

//export app_version
func app_version(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.AppVersion())
}

//export app_new_file
func app_new_file(t unsafe.Pointer) C.int { return C.int(engine.DocNew()) }

//export app_open_file
func app_open_file(t unsafe.Pointer, path *C.char) C.int {
	return C.int(engine.DocOpen(cstr(path)))
}

//export app_save_file
func app_save_file(t unsafe.Pointer) C.int { return C.int(engine.DocSave()) }

//export app_save_file_as
func app_save_file_as(t unsafe.Pointer, path *C.char) C.int {
	return C.int(engine.DocSaveAs(cstr(path)))
}

//export app_get_content
func app_get_content(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.DocContent())
}

//export app_get_file_path
func app_get_file_path(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.DocFilePath())
}

//export app_is_dirty
func app_is_dirty(t unsafe.Pointer) C.int { return C.int(engine.DocIsDirty()) }

//export app_insert_text
func app_insert_text(t unsafe.Pointer, pos C.int, text *C.char) C.int {
	return C.int(engine.DocInsert(int(pos), cstr(text)))
}

//export app_delete_text
func app_delete_text(t unsafe.Pointer, start, end C.int) C.int {
	return C.int(engine.DocDelete(int(start), int(end)))
}

//export app_undo
func app_undo(t unsafe.Pointer) C.int { return C.int(engine.DocUndo()) }

//export app_redo
func app_redo(t unsafe.Pointer) C.int { return C.int(engine.DocRedo()) }

//export app_can_undo
func app_can_undo(t unsafe.Pointer) C.int { return C.int(engine.DocCanUndo()) }

//export app_can_redo
func app_can_redo(t unsafe.Pointer) C.int { return C.int(engine.DocCanRedo()) }

//export app_set_cursor
func app_set_cursor(t unsafe.Pointer, pos C.int) { engine.DocSetCursor(int(pos)) }

//export app_get_cursor
func app_get_cursor(t unsafe.Pointer) C.int { return C.int(engine.DocCursor()) }

//export app_get_cursor_line
func app_get_cursor_line(t unsafe.Pointer) C.int { return C.int(engine.DocCursorLine()) }

//export app_get_cursor_col
func app_get_cursor_col(t unsafe.Pointer) C.int { return C.int(engine.DocCursorCol()) }

// ---------- 渲染 ----------

//export app_get_render_instructions
func app_get_render_instructions(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderUI(engine.DocContent()))
}

//export app_read_render_cache
func app_read_render_cache(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	s, _ := engine.IDPPopJSON()
	return writeBuf(buf, size, s)
}

//export app_render_html
func app_render_html(t unsafe.Pointer, md *C.char, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderHTMLFromMD(cstr(md)))
}

// ---------- 状态 ----------

//export app_get_status
func app_get_status(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	path := engine.DocFilePath()
	name := "未命名"
	if path != "" {
		if i := strings.LastIndexAny(path, "/\\"); i >= 0 {
			name = path[i+1:]
		} else {
			name = path
		}
	}
	j := fmt.Sprintf(`{"file_name":%q,"file_path":%q,"dirty":%t,"char_count":%d,"word_count":%d,"line_count":%d,"cursor_pos":%d,"cursor_line":%d,"cursor_col":%d,"can_undo":%t,"can_redo":%t}`,
		name, path, engine.DocIsDirty() == 1, engine.DocCharCount(), engine.DocWordCount(), engine.DocLineCount(),
		engine.DocCursor(), engine.DocCursorLine(), engine.DocCursorCol(),
		engine.DocCanUndo() == 1, engine.DocCanRedo() == 1)
	return writeBuf(buf, size, j)
}

// ---------- 设置 / 日志 ----------

//export app_get_setting
func app_get_setting(t unsafe.Pointer, key *C.char, buf *C.char, size C.int) C.int {
	k := cstr(key)
	if engine.ConfigHas(k) == 0 {
		writeBuf(buf, size, "")
		return 0
	}
	return writeBuf(buf, size, engine.ConfigGet(k))
}

//export app_set_setting
func app_set_setting(t unsafe.Pointer, key, val *C.char) C.int {
	k, v := cstr(key), cstr(val)
	rc := engine.ConfigSet(k, v)
	engine.LogWrite(k, "修改设置", v)
	return C.int(rc)
}

//export app_save_settings
func app_save_settings(t unsafe.Pointer) C.int { return C.int(engine.ConfigSave()) }

//export app_log
func app_log(t unsafe.Pointer, params, op, result *C.char) {
	engine.LogWrite(cstr(params), cstr(op), cstr(result))
}

// ---------- 彩蛋命令 ----------

//export app_easter_detect
func app_easter_detect(t unsafe.Pointer, line *C.char) C.int {
	if strings.HasPrefix(cstr(line), `\\>`) {
		return 1
	}
	return 0
}

//export app_easter_exec
func app_easter_exec(t unsafe.Pointer, line *C.char, buf *C.char, size C.int) C.int {
	s := cstr(line)
	if !strings.HasPrefix(s, `\\>`) {
		writeBuf(buf, size, "")
		return 0
	}
	cmd := strings.TrimSpace(strings.TrimPrefix(s, `\\>`))
	if cmd == "" {
		writeBuf(buf, size, "")
		return 0
	}
	res := engine.CmdExec(cmd)
	engine.LogWrite(cmd, "彩蛋命令", "执行完成")
	return writeBuf(buf, size, res)
}

// ---------- IDP ----------

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

//export app_write_idp
func app_write_idp(t unsafe.Pointer, filePath, typ, data *C.char) C.int {
	return C.int(engine.IDPPush(cstr(typ), "mdcontroller", cstr(data)))
}

//export app_to_idp
func app_to_idp(t unsafe.Pointer, typ, data, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.IDPCreatePacket(cstr(typ), "mdcontroller", cstr(data)))
}

//export app_read_idp
func app_read_idp(t unsafe.Pointer, filePath, buf *C.char, size C.int) C.int {
	s, _ := engine.IDPPopJSON()
	return writeBuf(buf, size, s)
}

//export app_idp_process
func app_idp_process(t unsafe.Pointer) C.int { return C.int(engine.IDPAppProcess()) }

func main() {
	// 允许把 controller.dll 当作独立探针运行：`controller.dll --selftest`
	if len(os.Args) > 1 && os.Args[1] == "--version" {
		fmt.Println(engine.AppVersion())
	}
}
