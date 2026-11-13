#ifndef __SINGLE_H
#define __SINGLE_H

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

int single_try_lock(graal_isolatethread_t*, char*);

void single_release(graal_isolatethread_t*);

int single_is_locked(graal_isolatethread_t*);

int single_lock_path(graal_isolatethread_t*, char*, int);

#if defined(__cplusplus)
}
#endif
#endif
