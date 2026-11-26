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

func main() {}
