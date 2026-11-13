#ifndef __RENDER_H
#define __RENDER_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

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
