#ifndef __DOCPACK_H
#define __DOCPACK_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int convert_html(graal_isolatethread_t*, char*, char*, int);

int convert_html_file(graal_isolatethread_t*, char*, char*);

int convert_text(graal_isolatethread_t*, char*, char*, int);

int convert_text_file(graal_isolatethread_t*, char*, char*);

int convert_txt2md(graal_isolatethread_t*, char*, char*, int);

int convert_txt2md_file(graal_isolatethread_t*, char*, char*);

int doc_init(graal_isolatethread_t*, char*);

int doc_new_document(graal_isolatethread_t*);

int doc_open_document(graal_isolatethread_t*, char*);

int doc_save_document(graal_isolatethread_t*);

int doc_save_document_as(graal_isolatethread_t*, char*);

int doc_get_content(graal_isolatethread_t*, char*, int);

int doc_set_content(graal_isolatethread_t*, char*);

int doc_get_file_path(graal_isolatethread_t*, char*, int);

int doc_is_dirty(graal_isolatethread_t*);

void doc_set_cursor(graal_isolatethread_t*, int);

int doc_get_cursor(graal_isolatethread_t*);

int doc_get_cursor_line(graal_isolatethread_t*);

int doc_get_cursor_col(graal_isolatethread_t*);

int doc_insert_text(graal_isolatethread_t*, int, char*);

int doc_delete_text(graal_isolatethread_t*, int, int);

int doc_get_text(graal_isolatethread_t*, int, int, char*, int);

int doc_undo(graal_isolatethread_t*);

int doc_redo(graal_isolatethread_t*);

int doc_can_undo(graal_isolatethread_t*);

int doc_can_redo(graal_isolatethread_t*);

int doc_get_char_count(graal_isolatethread_t*);

int doc_get_word_count(graal_isolatethread_t*);

int doc_get_line_count(graal_isolatethread_t*);

int idp_push(graal_isolatethread_t*, char*, char*, char*);

int idp_pop_json(graal_isolatethread_t*, char*, int);

int idp_pop_idp(graal_isolatethread_t*, char*, int);

int idp_peek_json(graal_isolatethread_t*, char*, int);

int idp_stack_size(graal_isolatethread_t*);

int idp_stack_clear(graal_isolatethread_t*);

int render_html(graal_isolatethread_t*, char*, char*, int);

int classify_line(graal_isolatethread_t*, char*);

int count_inline_styles(graal_isolatethread_t*, char*);

int count_lines(graal_isolatethread_t*, char*);

int count_words(graal_isolatethread_t*, char*);

int count_chars(graal_isolatethread_t*, char*);

int markdown_write_idp(graal_isolatethread_t*, char*, char*, char*);

int markdown_to_idp(graal_isolatethread_t*, char*, char*, char*, int);

int markdown_read_idp(graal_isolatethread_t*, char*, char*, int);

int quicksort_ints(graal_isolatethread_t*, char*, char*, char*, int);

int quicksort_strings(graal_isolatethread_t*, char*, char*, char*, int);

int quicksort_json_by_field(graal_isolatethread_t*, char*, char*, char*, char*, int);

int quicksort_info(graal_isolatethread_t*, char*, int);

int render_ui(graal_isolatethread_t*, char*, char*, int);

int render_line(graal_isolatethread_t*, char*, char*, int);

int render_line_count(graal_isolatethread_t*, char*);

int render_get_line(graal_isolatethread_t*, char*, int, char*, int);

int render_find(graal_isolatethread_t*, char*, char*, char*, int);

int render_replace(graal_isolatethread_t*, char*, char*, char*, char*, int);

int render_insert(graal_isolatethread_t*, char*, char*, char*, char*, char*, int);

int render_paged(graal_isolatethread_t*, char*, int, int, char*, int);

int render_write_idp(graal_isolatethread_t*, char*, char*, char*);

int render_to_idp(graal_isolatethread_t*, char*, char*, char*, int);

int render_read_idp(graal_isolatethread_t*, char*, char*, int);

#if defined(__cplusplus)
}
#endif
#endif
