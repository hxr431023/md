#ifndef __COMPRESS_H
#define __COMPRESS_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int compress_file(graal_isolatethread_t*, char*, char*);

int compress_decompress_file(graal_isolatethread_t*, char*, char*);

int compress_string_to_file(graal_isolatethread_t*, char*, char*);

int compress_file_to_string(graal_isolatethread_t*, char*, char*, int);

int compress_ratio(graal_isolatethread_t*, char*);

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
