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

//export convert_html
func convert_html(t unsafe.Pointer, md, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ConvertHTML(cstr(md)))
}

//export convert_html_file
func convert_html_file(t unsafe.Pointer, in, out *C.char) C.int {
	return C.int(engine.ConvertHTMLFile(cstr(in), cstr(out)))
}

//export convert_text
func convert_text(t unsafe.Pointer, md, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ConvertText(cstr(md)))
}

//export convert_text_file
func convert_text_file(t unsafe.Pointer, in, out *C.char) C.int {
	return C.int(engine.ConvertTextFile(cstr(in), cstr(out)))
}

//export convert_txt2md
func convert_txt2md(t unsafe.Pointer, text, buf *C.char, size C.int) C.int {
	return writeBuf(buf, size, engine.ConvertTxt2Md(cstr(text)))
}

//export convert_txt2md_file
func convert_txt2md_file(t unsafe.Pointer, in, out *C.char) C.int {
	return C.int(engine.ConvertTxt2MdFile(cstr(in), cstr(out)))
}

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

func main() {}
