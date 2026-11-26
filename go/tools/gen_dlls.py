#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 MDRenderer Go 版各 DLL 的 cgo 适配层（薄封装，业务全在 internal/engine）。
运行：python gen_dlls.py
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DLL = os.path.join(ROOT, "dll")

PRELUDE = '''package main

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
{extra_import})

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

'''

TAIL = '''
func main() {}
'''

IDP_IMPORT = '"github.com/hxr431023/md/go/internal/idpclient"'

# ---------------- core ----------------

IDP = r'''
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
'''

COMPRESS = r'''
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
'''

LOG = r'''
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
'''

CONFIG = r'''
//export config_init
func config_init(t unsafe.Pointer, baseDir *C.char) C.int { return C.int(engine.ConfigInit(cstr(baseDir))) }

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
'''

FILE = r'''
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
'''

SINGLE = r'''
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
'''

# ---------------- doc ----------------

DOC = r'''
//export doc_init
func doc_init(t unsafe.Pointer, baseDir *C.char) C.int { return C.int(engine.DocNew()) }

//export doc_new_document
func doc_new_document(t unsafe.Pointer) C.int { return C.int(engine.DocNew()) }

//export doc_open_document
func doc_open_document(t unsafe.Pointer, path *C.char) C.int {
	return C.int(engine.DocOpen(cstr(path)))
}

//export doc_save_document
func doc_save_document(t unsafe.Pointer) C.int { return C.int(engine.DocSave()) }

//export doc_save_document_as
func doc_save_document_as(t unsafe.Pointer, path *C.char) C.int {
	return C.int(engine.DocSaveAs(cstr(path)))
}

//export doc_get_content
func doc_get_content(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.DocContent())
}

//export doc_set_content
func doc_set_content(t unsafe.Pointer, content *C.char) C.int {
	return C.int(engine.DocSetContent(cstr(content)))
}

//export doc_get_file_path
func doc_get_file_path(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.DocFilePath())
}

//export doc_is_dirty
func doc_is_dirty(t unsafe.Pointer) C.int { return C.int(engine.DocIsDirty()) }

//export doc_set_cursor
func doc_set_cursor(t unsafe.Pointer, pos C.int) { engine.DocSetCursor(int(pos)) }

//export doc_get_cursor
func doc_get_cursor(t unsafe.Pointer) C.int { return C.int(engine.DocCursor()) }

//export doc_get_cursor_line
func doc_get_cursor_line(t unsafe.Pointer) C.int { return C.int(engine.DocCursorLine()) }

//export doc_get_cursor_col
func doc_get_cursor_col(t unsafe.Pointer) C.int { return C.int(engine.DocCursorCol()) }

//export doc_insert_text
func doc_insert_text(t unsafe.Pointer, pos C.int, text *C.char) C.int {
	return C.int(engine.DocInsert(int(pos), cstr(text)))
}

//export doc_delete_text
func doc_delete_text(t unsafe.Pointer, start, end C.int) C.int {
	return C.int(engine.DocDelete(int(start), int(end)))
}

//export doc_get_text
func doc_get_text(t unsafe.Pointer, start, end C.int, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.DocText(int(start), int(end)))
}

//export doc_undo
func doc_undo(t unsafe.Pointer) C.int { return C.int(engine.DocUndo()) }

//export doc_redo
func doc_redo(t unsafe.Pointer) C.int { return C.int(engine.DocRedo()) }

//export doc_can_undo
func doc_can_undo(t unsafe.Pointer) C.int { return C.int(engine.DocCanUndo()) }

//export doc_can_redo
func doc_can_redo(t unsafe.Pointer) C.int { return C.int(engine.DocCanRedo()) }

//export doc_get_char_count
func doc_get_char_count(t unsafe.Pointer) C.int { return C.int(engine.DocCharCount()) }

//export doc_get_word_count
func doc_get_word_count(t unsafe.Pointer) C.int { return C.int(engine.DocWordCount()) }

//export doc_get_line_count
func doc_get_line_count(t unsafe.Pointer) C.int { return C.int(engine.DocLineCount()) }
'''

MARKDOWN = r'''
//export render_html
func render_html(t unsafe.Pointer, md, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.MarkdownHTML(cstr(md)))
}

//export classify_line
func classify_line(t unsafe.Pointer, line *C.char) C.int {
	return C.int(engine.ClassifyLine(cstr(line)))
}

//export count_inline_styles
func count_inline_styles(t unsafe.Pointer, line *C.char) C.int {
	return C.int(engine.CountInlineStyles(cstr(line)))
}

//export count_lines
func count_lines(t unsafe.Pointer, text *C.char) C.int { return C.int(engine.CountLines(cstr(text))) }

//export count_words
func count_words(t unsafe.Pointer, text *C.char) C.int { return C.int(engine.CountWords(cstr(text))) }

//export count_chars
func count_chars(t unsafe.Pointer, text *C.char) C.int { return C.int(engine.CountChars(cstr(text))) }

//export markdown_write_idp
func markdown_write_idp(t unsafe.Pointer, filePath, typ, data *C.char) C.int {
	return C.int(idpclient.Push(cstr(typ), "markdown", cstr(data)))
}

//export markdown_to_idp
func markdown_to_idp(t unsafe.Pointer, typ, data, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.IDPCreatePacket(cstr(typ), "markdown", cstr(data)))
}

//export markdown_read_idp
func markdown_read_idp(t unsafe.Pointer, filePath, buf *C.char, size C.int) C.int {
	s, ok := idpclient.PopJSON()
	if !ok {
		return 0
	}
	return writeBuf(buf, size, s)
}
'''

RENDER = r'''
//export render_ui
func render_ui(t unsafe.Pointer, content, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderUI(cstr(content)))
}

//export render_line
func render_line(t unsafe.Pointer, line, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderLine(cstr(line)))
}

//export render_line_count
func render_line_count(t unsafe.Pointer, content *C.char) C.int {
	return C.int(engine.RenderLineCount(cstr(content)))
}

//export render_get_line
func render_get_line(t unsafe.Pointer, content *C.char, n C.int, buf *C.char, size C.int) C.int {
	s, ok := engine.RenderGetLine(cstr(content), int(n))
	if !ok {
		return -1
	}
	return writeBuf(buf, size, s)
}

//export render_find
func render_find(t unsafe.Pointer, content, query, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderFind(cstr(content), cstr(query)))
}

//export render_replace
func render_replace(t unsafe.Pointer, content, find, repl, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderReplace(cstr(content), cstr(find), cstr(repl)))
}

//export render_insert
func render_insert(t unsafe.Pointer, content, left, right, ins, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderInsert(cstr(content), cstr(left), cstr(right), cstr(ins)))
}

//export render_paged
func render_paged(t unsafe.Pointer, content *C.char, w, h C.int, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.RenderPaged(cstr(content), int(w), int(h)))
}

//export render_write_idp
func render_write_idp(t unsafe.Pointer, filePath, typ, data *C.char) C.int {
	return C.int(idpclient.Push(cstr(typ), "render", cstr(data)))
}

//export render_to_idp
func render_to_idp(t unsafe.Pointer, typ, data, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.IDPCreatePacket(cstr(typ), "render", cstr(data)))
}

//export render_read_idp
func render_read_idp(t unsafe.Pointer, filePath, buf *C.char, size C.int) C.int {
	s, ok := idpclient.PopJSON()
	if !ok {
		return 0
	}
	return writeBuf(buf, size, s)
}
'''

CONVERT_HTML = r'''
//export convert_html
func convert_html(t unsafe.Pointer, md, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ConvertHTML(cstr(md)))
}

//export convert_html_file
func convert_html_file(t unsafe.Pointer, in, out *C.char) C.int {
	return C.int(engine.ConvertHTMLFile(cstr(in), cstr(out)))
}
'''

CONVERT_TEXT = r'''
//export convert_text
func convert_text(t unsafe.Pointer, md, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ConvertText(cstr(md)))
}

//export convert_text_file
func convert_text_file(t unsafe.Pointer, in, out *C.char) C.int {
	return C.int(engine.ConvertTextFile(cstr(in), cstr(out)))
}
'''

CONVERT_TXT2MD = r'''
//export convert_txt2md
func convert_txt2md(t unsafe.Pointer, text, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ConvertTxt2Md(cstr(text)))
}

//export convert_txt2md_file
func convert_txt2md_file(t unsafe.Pointer, in, out *C.char) C.int {
	return C.int(engine.ConvertTxt2MdFile(cstr(in), cstr(out)))
}
'''

QUICKSORT = r'''
//export quicksort_ints
func quicksort_ints(t unsafe.Pointer, nums, asc, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.QuickSortInts(cstr(nums), cstr(asc)))
}

//export quicksort_strings
func quicksort_strings(t unsafe.Pointer, strs, asc, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.QuickSortStrings(cstr(strs), cstr(asc)))
}

//export quicksort_json_by_field
func quicksort_json_by_field(t unsafe.Pointer, j, field, asc, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.QuickSortJSON(cstr(j), cstr(field), cstr(asc)))
}

//export quicksort_info
func quicksort_info(t unsafe.Pointer, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.QuickSortInfo())
}
'''

# ---------------- tools ----------------

TMMF = r'''
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
'''

LERF = r'''
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
'''

CMD = r'''
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
'''

THREADSTAT = r'''
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
'''

VISUALIZE = r'''
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
'''

# ---------------- log 主控 ----------------

LOGMAIN = r'''
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
'''

# 叶子模块：名称 -> (目录名, 导出体, 额外导入)
MODULES = {
    "idp": ("idp", IDP, ""),
    "compress": ("compress", COMPRESS, ""),
    "log": ("log", LOG, ""),
    "config": ("config", CONFIG, ""),
    "file": ("file", FILE, ""),
    "single": ("single", SINGLE, ""),
    "doc": ("doc", DOC, ""),
    "markdown": ("markdown", MARKDOWN, IDP_IMPORT),
    "render": ("render", RENDER, IDP_IMPORT),
    "convert-html": ("convert-html", CONVERT_HTML, ""),
    "convert-text": ("convert-text", CONVERT_TEXT, ""),
    "convert-txt2md": ("convert-txt2md", CONVERT_TXT2MD, ""),
    "quicksort": ("quicksort", QUICKSORT, ""),
    "tmmf": ("tmmf", TMMF, ""),
    "lerf": ("lerf", LERF, ""),
    "cmd": ("cmd", CMD, IDP_IMPORT),
    "threadstat": ("threadstat", THREADSTAT, ""),
    "visualize": ("visualize", VISUALIZE, ""),
    "logmain": ("logmain", LOGMAIN, ""),
}

PACKS = {
    "core": [IDP, COMPRESS, LOG, CONFIG, FILE, SINGLE],
    "docpack": [DOC, RENDER, MARKDOWN, CONVERT_HTML, CONVERT_TEXT, CONVERT_TXT2MD, QUICKSORT],
    "toolspack": [CMD, THREADSTAT, VISUALIZE, TMMF, LERF],
}


def dedupe(body_parts):
    seen = set()
    out = []
    for part in body_parts:
        # 按函数块切分，块以 //export 开头
        blocks = re.split(r"\n(?=//export )", part.strip())
        for blk in blocks:
            m = re.search(r"func ([A-Za-z0-9_]+)\(", blk)
            if not m:
                continue
            name = m.group(1)
            if name in seen:
                continue
            seen.add(name)
            out.append(blk.strip())
    return "\n\n".join(out) + "\n"


def write_module(dirname, body, extra_import):
    d = os.path.join(DLL, dirname)
    os.makedirs(d, exist_ok=True)
    imports = ""
    if extra_import:
        imports = "\t" + extra_import + "\n"
    src = PRELUDE.replace("{extra_import}", imports) + body.strip() + "\n" + TAIL
    with open(os.path.join(d, "main.go"), "w", encoding="utf-8", newline="\n") as f:
        f.write(src)
    print("generated", dirname)


for name, (dirname, body, imp) in MODULES.items():
    write_module(dirname, body, imp)

for name, parts in PACKS.items():
    write_module(name, dedupe(parts), IDP_IMPORT if name in ("docpack", "toolspack") else "")
