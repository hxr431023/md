#ifndef __DOC_H
#define __DOC_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

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

#if defined(__cplusplus)
}
#endif
#endif
