#ifndef __CORE_H
#define __CORE_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int compress_file(graal_isolatethread_t*, char*, char*);

int compress_decompress_file(graal_isolatethread_t*, char*, char*);

int compress_string_to_file(graal_isolatethread_t*, char*, char*);

int compress_file_to_string(graal_isolatethread_t*, char*, char*, int);

int compress_ratio(graal_isolatethread_t*, char*);

int config_init(graal_isolatethread_t*, char*);

int config_load_settings(graal_isolatethread_t*);

int config_save_settings(graal_isolatethread_t*);

int config_get_setting(graal_isolatethread_t*, char*, char*, int);

int config_set_setting(graal_isolatethread_t*, char*, char*);

int config_has_setting(graal_isolatethread_t*, char*);

int config_remove_setting(graal_isolatethread_t*, char*);

int config_get_thread_mode(graal_isolatethread_t*, char*, int);

int config_set_thread_mode(graal_isolatethread_t*, char*);

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

int single_try_lock(graal_isolatethread_t*, char*);

void single_release(graal_isolatethread_t*);

int single_is_locked(graal_isolatethread_t*);

int single_lock_path(graal_isolatethread_t*, char*, int);

#if defined(__cplusplus)
}
#endif
#endif
