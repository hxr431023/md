#ifndef __FILE_H
#define __FILE_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int file_read(graal_isolatethread_t*, char*, char*, int);

int file_write(graal_isolatethread_t*, char*, char*);

int file_append(graal_isolatethread_t*, char*, char*);

int file_exists(graal_isolatethread_t*, char*);

long long int file_size(graal_isolatethread_t*, char*);

int file_delete(graal_isolatethread_t*, char*);

int file_name(graal_isolatethread_t*, char*, char*, int);

int file_extension(graal_isolatethread_t*, char*, char*, int);

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
