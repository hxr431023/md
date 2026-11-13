#ifndef __CORE_H
#define __CORE_H

#include <graal_isolate_dynamic.h>


#if defined(__cplusplus)
extern "C" {
#endif

typedef int (*compress_file_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*compress_decompress_file_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*compress_string_to_file_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*compress_file_to_string_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*compress_ratio_fn_t)(graal_isolatethread_t*, char*);

typedef int (*config_init_fn_t)(graal_isolatethread_t*, char*);

typedef int (*config_load_settings_fn_t)(graal_isolatethread_t*);

typedef int (*config_save_settings_fn_t)(graal_isolatethread_t*);

typedef int (*config_get_setting_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*config_set_setting_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*config_has_setting_fn_t)(graal_isolatethread_t*, char*);

typedef int (*config_remove_setting_fn_t)(graal_isolatethread_t*, char*);

typedef int (*config_get_thread_mode_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*config_set_thread_mode_fn_t)(graal_isolatethread_t*, char*);

typedef int (*file_read_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*file_write_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*file_append_fn_t)(graal_isolatethread_t*, char*, char*);

typedef int (*file_exists_fn_t)(graal_isolatethread_t*, char*);

typedef long long int (*file_size_fn_t)(graal_isolatethread_t*, char*);

typedef int (*file_delete_fn_t)(graal_isolatethread_t*, char*);

typedef int (*file_name_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*file_extension_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*idp_push_fn_t)(graal_isolatethread_t*, char*, char*, char*);

typedef int (*idp_pop_json_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_pop_idp_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_peek_json_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_stack_size_fn_t)(graal_isolatethread_t*);

typedef int (*idp_stack_clear_fn_t)(graal_isolatethread_t*);

typedef int (*log_init_fn_t)(graal_isolatethread_t*, char*);

typedef void (*log_shutdown_fn_t)(graal_isolatethread_t*);

typedef void (*log_write_fn_t)(graal_isolatethread_t*, char*, char*, char*);

typedef void (*log_write_end_fn_t)(graal_isolatethread_t*, char*, char*, char*);

typedef void (*log_set_enabled_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*log_is_enabled_fn_t)(graal_isolatethread_t*, char*);

typedef int (*log_clear_all_fn_t)(graal_isolatethread_t*);

typedef long long int (*log_size_fn_t)(graal_isolatethread_t*);

typedef int (*log_cache_lookup_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*log_cache_put_fn_t)(graal_isolatethread_t*, char*, char*, char*);

typedef int (*single_try_lock_fn_t)(graal_isolatethread_t*, char*);

typedef void (*single_release_fn_t)(graal_isolatethread_t*);

typedef int (*single_is_locked_fn_t)(graal_isolatethread_t*);

typedef int (*single_lock_path_fn_t)(graal_isolatethread_t*, char*, int);

#if defined(__cplusplus)
}
#endif
#endif
