#ifndef __LOG_H
#define __LOG_H

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

int log_init(graal_isolatethread_t*, char*);

void log_shutdown(graal_isolatethread_t*);

void log_write(graal_isolatethread_t*, char*, char*, char*);

void log_write_end(graal_isolatethread_t*, char*, char*, char*);

void log_set_enabled(graal_isolatethread_t*, char*, int);

int log_is_enabled(graal_isolatethread_t*, char*);

int log_clear_all(graal_isolatethread_t*);

long long int log_size(graal_isolatethread_t*);

int log_cache_lookup(graal_isolatethread_t*, char*, char*, char*, int);

int log_cache_put(graal_isolatethread_t*, char*, char*, char*);

#if defined(__cplusplus)
}
#endif
#endif
