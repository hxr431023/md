#ifndef __TOOLSPACK_H
#define __TOOLSPACK_H

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

int lerf_load(graal_isolatethread_t*, char*);

int lerf_get(graal_isolatethread_t*, char*, char*, int);

int lerf_get_from_file(graal_isolatethread_t*, char*, char*, char*, int);

int lerf_get_multi(graal_isolatethread_t*, char*, char*, int);

int lerf_list_all(graal_isolatethread_t*, char*, char*, int);

int lerf_validate(graal_isolatethread_t*, char*);

int lerf_key_count(graal_isolatethread_t*, char*);

int tmmf_read_all(graal_isolatethread_t*, char*, char*, int);

int tmmf_read_tag(graal_isolatethread_t*, char*, char*, char*, int);

int tmmf_search(graal_isolatethread_t*, char*, char*, char*, int);

int tmmf_list_tags(graal_isolatethread_t*, char*, char*, int);

int tmmf_line_count(graal_isolatethread_t*, char*);

int tmmf_write_tag(graal_isolatethread_t*, char*, char*, char*);

#if defined(__cplusplus)
}
#endif
#endif
