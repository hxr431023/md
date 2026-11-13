#ifndef __TOOLSPACK_H
#define __TOOLSPACK_H

#include <graal_isolate_dynamic.h>


#if defined(__cplusplus)
extern "C" {
#endif

typedef int (*idp_push_fn_t)(graal_isolatethread_t*, char*, char*, char*);

typedef int (*idp_pop_json_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_pop_idp_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_peek_json_fn_t)(graal_isolatethread_t*, char*, int);

typedef int (*idp_stack_size_fn_t)(graal_isolatethread_t*);

typedef int (*idp_stack_clear_fn_t)(graal_isolatethread_t*);

typedef int (*lerf_load_fn_t)(graal_isolatethread_t*, char*);

typedef int (*lerf_get_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*lerf_get_from_file_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*lerf_get_multi_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*lerf_list_all_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*lerf_validate_fn_t)(graal_isolatethread_t*, char*);

typedef int (*lerf_key_count_fn_t)(graal_isolatethread_t*, char*);

typedef int (*tmmf_read_all_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*tmmf_read_tag_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*tmmf_search_fn_t)(graal_isolatethread_t*, char*, char*, char*, int);

typedef int (*tmmf_list_tags_fn_t)(graal_isolatethread_t*, char*, char*, int);

typedef int (*tmmf_line_count_fn_t)(graal_isolatethread_t*, char*);

typedef int (*tmmf_write_tag_fn_t)(graal_isolatethread_t*, char*, char*, char*);

#if defined(__cplusplus)
}
#endif
#endif
