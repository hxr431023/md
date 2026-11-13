#ifndef __THREADSTAT_H
#define __THREADSTAT_H

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

int threadstat_init(graal_isolatethread_t*, char*);

int threadstat_get_mode(graal_isolatethread_t*, char*, int);

int threadstat_set_mode(graal_isolatethread_t*, char*);

int threadstat_register(graal_isolatethread_t*, char*, char*);

int threadstat_unregister(graal_isolatethread_t*, char*);

int threadstat_sample(graal_isolatethread_t*);

int threadstat_get_json(graal_isolatethread_t*, char*, int);

int threadstat_memory(graal_isolatethread_t*, char*, int);

void threadstat_disk_read(graal_isolatethread_t*, long long int);

void threadstat_disk_write(graal_isolatethread_t*, long long int);

int threadstat_disk(graal_isolatethread_t*, char*, int);

#if defined(__cplusplus)
}
#endif
#endif
