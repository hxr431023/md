#ifndef __DOCPACK_H
#define __DOCPACK_H

#include <graal_isolate_dynamic.h>


#if defined(__cplusplus)
extern "C" {
#endif

typedef int (*convert_html_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*convert_html_file_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*convert_text_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*convert_text_file_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*convert_txt2md_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*convert_txt2md_file_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*doc_init_fn_t)(graal_isolatethread_t*, char*);

typedef int (*doc_new_document_fn_t)(graal_isolatethread_t*);

typedef int (*doc_open_document_fn_t)(graal_isolatethread_t*, char*);

typedef int (*doc_save_document_fn_t)(graal_isolatethread_t*);

typedef int (*doc_save_document_as_fn_t)(graal_isolatethread_t*, char*);

typedef int (*doc_get_content_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*doc_set_content_fn_t)(graal_isolatethread_t*, char*);

typedef int (*doc_get_file_path_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*doc_is_dirty_fn_t)(graal_isolatethread_t*);

typedef void (*doc_set_cursor_fn_t)(graal_isolatethread_t*, int);

typedef int (*doc_get_cursor_fn_t)(graal_isolatethread_t*);

typedef int (*doc_get_cursor_line_fn_t)(graal_isolatethread_t*);

typedef int (*doc_get_cursor_col_fn_t)(graal_isolatethread_t*);

typedef int (*doc_insert_text_fn_t)(graal_isolatethread_t*, int, char*);

typedef int (*doc_delete_text_fn_t)(graal_isolatethread_t*, int, int);

typedef int (*doc_get_text_fn_t)(graal_isolatethread_t*, int, int, char*, int);

typedef int (*doc_undo_fn_t)(graal_isolatethread_t*);

typedef int (*doc_redo_fn_t)(graal_isolatethread_t*);

typedef int (*doc_can_undo_fn_t)(graal_isolatethread_t*);

typedef int (*doc_can_redo_fn_t)(graal_isolatethread_t*);

typedef int (*doc_get_char_count_fn_t)(graal_isolatethread_t*);

typedef int (*doc_get_word_count_fn_t)(graal_isolatethread_t*);

typedef int (*doc_get_line_count_fn_t)(graal_isolatethread_t*);

typedef int (*idp_push_fn_t)(graal_isolatethread_t*, char*, char*, char*);

typedef int (*idp_pop_json_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_pop_idp_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_peek_json_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_stack_size_fn_t)(graal_isolatethread_t*);

typedef int (*idp_stack_clear_fn_t)(graal_isolatethread_t*);

typedef int (*render_html_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*classify_line_fn_t)(graal_isolatethread_t*, char*);

typedef int (*count_inline_styles_fn_t)(graal_isolatethread_t*, char*);

typedef int (*count_lines_fn_t)(graal_isolatethread_t*, char*);

typedef int (*count_words_fn_t)(graal_isolatethread_t*, char*);

typedef int (*count_chars_fn_t)(graal_isolatethread_t*, char*);

typedef int (*markdown_write_idp_fn_t)(graal_isolatethread_t*, char*, char*, char*);

typedef int (*markdown_to_idp_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*markdown_read_idp_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*quicksort_ints_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*quicksort_strings_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*quicksort_json_by_field_fn_t)(graal_isolatethread_t*, char*, char*, char*, char*, int);

typedef int (*quicksort_info_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*render_ui_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*render_line_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*render_line_count_fn_t)(graal_isolatethread_t*, char*);

typedef int (*render_get_line_fn_t)(graal_isolatethread_t*, char*, int, char*, int);

typedef int (*render_find_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*render_replace_fn_t)(graal_isolatethread_t*, char*, char*, char*, char*, int);

typedef int (*render_insert_fn_t)(graal_isolatethread_t*, char*, char*, char*, char*, char*, int);

typedef int (*render_paged_fn_t)(graal_isolatethread_t*, char*, int, int, char*, int);

typedef int (*render_write_idp_fn_t)(graal_isolatethread_t*, char*, char*, char*);

typedef int (*render_to_idp_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*render_read_idp_fn_t)(graal_isolatethread_t*, char*, char*, int);

#if defined(__cplusplus)
}
#endif
#endif
