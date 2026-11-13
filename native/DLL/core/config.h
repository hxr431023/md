#ifndef __CONFIG_H
#define __CONFIG_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int config_init(graal_isolatethread_t*, char*);

int config_load_settings(graal_isolatethread_t*);

int config_save_settings(graal_isolatethread_t*);

int config_get_setting(graal_isolatethread_t*, char*, char*, int);

int config_set_setting(graal_isolatethread_t*, char*, char*);

int config_has_setting(graal_isolatethread_t*, char*);

int config_remove_setting(graal_isolatethread_t*, char*);

int config_get_thread_mode(graal_isolatethread_t*, char*, int);

int config_set_thread_mode(graal_isolatethread_t*, char*);

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
