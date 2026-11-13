#ifndef __MARKDOWN_H
#define __MARKDOWN_H

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

#if defined(__cplusplus)
}
#endif
#endif
