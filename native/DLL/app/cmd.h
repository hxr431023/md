#ifndef __CMD_H
#define __CMD_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int cmd_exec(graal_isolatethread_t*, char*, char*, int);

int cmd_help(graal_isolatethread_t*, char*, int);

int cmd_write_idp(graal_isolatethread_t*, char*, char*, char*);

int cmd_to_idp(graal_isolatethread_t*, char*, char*, char*, int);

int cmd_read_idp(graal_isolatethread_t*, char*, char*, int);

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
