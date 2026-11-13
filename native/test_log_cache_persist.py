# -*- coding: utf-8 -*-
"""跨进程缓存验证：put 后 shutdown（压缩回），新进程 init（解压）再 lookup"""
import ctypes, sys, os

DLL = r"C:\wpjc\md\native\DLL\core\log.dll"
BASE = r"C:/wpjc/md/native/_cache_persist"
P = ctypes.c_void_p

d = ctypes.CDLL(DLL)
d.graal_create_isolate.restype = ctypes.c_int
d.graal_create_isolate.argtypes = [ctypes.c_void_p, ctypes.POINTER(P), ctypes.POINTER(P)]
iso, th = P(), P()
rc = d.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(th))

d.log_init.argtypes = [P, ctypes.c_char_p]; d.log_init.restype = ctypes.c_int
print("log_init rc=", d.log_init(th, BASE.encode()))

mode = sys.argv[1]
OP = b"math_solve"
PARAMS = b'{"a":1,"b":2}'
RESULT = b'{"x":3}'

if mode == "put":
    d.log_cache_put.argtypes = [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
    d.log_cache_put.restype = ctypes.c_int
    print("cache_put rc=", d.log_cache_put(th, OP, PARAMS, RESULT))
else:
    d.log_cache_lookup.argtypes = [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]
    d.log_cache_lookup.restype = ctypes.c_int
    buf = ctypes.create_string_buffer(4096)
    n = d.log_cache_lookup(th, OP, PARAMS, buf, 4096)
    print("cache_lookup n=", n, "value=", buf.value[:n].decode() if n >= 0 else "MISS")

d.log_shutdown.argtypes = [P]
d.log_shutdown(th)
d.graal_detach_all_threads_and_tear_down_isolate.argtypes = [P]
print("teardown rc=", d.graal_detach_all_threads_and_tear_down_isolate(th))

if mode == "put":
    print("zst exists:", os.path.exists(r"C:\wpjc\md\native\_cache_persist\log"))
    for f in sorted(os.listdir(r"C:\wpjc\md\native\_cache_persist\log")):
        print("  ", f)
