# -*- coding: utf-8 -*-
"""缓存机制验证：相同操作第二次应命中日志缓存（cached:true）"""
import ctypes, os, json, shutil

DLL = r"C:\wpjc\md\native\DLL\app\controller.dll"
BASE = r"C:\wpjc\md\native\_cache_test"
if os.path.exists(BASE):
    shutil.rmtree(BASE)
os.makedirs(BASE, exist_ok=True)
P = ctypes.c_void_p

dll = ctypes.CDLL(DLL)
dll.graal_create_isolate.restype = ctypes.c_int
dll.graal_create_isolate.argtypes = [P, ctypes.POINTER(P), ctypes.POINTER(P)]
iso, th = P(), P()
rc = dll.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(th))
print("isolate rc=", rc)

dll.app_init.argtypes = [P, ctypes.c_char_p]
dll.app_init.restype = ctypes.c_int
print("app_init rc=", dll.app_init(th, BASE.encode()))

dll.idp_push.argtypes = [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
dll.idp_push.restype = ctypes.c_int
dll.app_idp_process.argtypes = [P]
dll.app_idp_process.restype = ctypes.c_int
dll.idp_pop_json.argtypes = [P, ctypes.c_char_p, ctypes.c_int]
dll.idp_pop_json.restype = ctypes.c_int


def call(t, j):
    assert dll.idp_push(th, t.encode(), b"cachetest", j.encode()) == 0
    assert dll.app_idp_process(th) == 0
    b = ctypes.create_string_buffer(2 << 20)
    n = dll.idp_pop_json(th, b, len(b))
    return b.value[:n].decode("utf-8", "replace")


print("\n--- 1) 第一次 app_render_html ---")
r1 = call("app_render_html", '{"md":"# cache test"}')
print("r1 =", r1)

print("\n--- 2) 第二次相同参数（应命中）---")
r2 = call("app_render_html", '{"md":"# cache test"}')
print("r2 =", r2)

print("\n--- 3) 不同参数（不应命中）---")
r3 = call("app_render_html", '{"md":"# other"}')
print("r3 =", r3)

print("\n--- 4) render_find 两次 ---")
f1 = call("render_find", '{"content":"ab c ab","query":"ab"}')
f2 = call("render_find", '{"content":"ab c ab","query":"ab"}')
print("f1 =", f1)
print("f2 =", f2)

print("\n--- 5) 日志中的 CACHE 行 ---")
logdir = os.path.join(BASE, "cache", "log")
files = os.listdir(logdir) if os.path.isdir(logdir) else []
print("log files:", files)
if files:
    p = os.path.join(logdir, files[0])
    txt = open(p, "r", encoding="utf-8").read()
    cache_lines = [l for l in txt.splitlines() if "CACHE:" in l]
    print("CACHE 行数:", len(cache_lines))
    for l in cache_lines[:5]:
        print("  ", l[:120] + ("..." if len(l) > 120 else ""))
    print("第4行(大小):", txt.splitlines()[3] if len(txt.splitlines()) > 3 else "N/A")

print("\n--- 6) 判定 ---")
try:
    j2 = json.loads(r2)
    print("第二次命中:", j2.get("cached") is True)
except Exception as e:
    print("JSON 解析失败:", e)
print("第一次未命中:", '"cached"' not in r1)
print("不同参数未命中:", '"cached"' not in r3)
try:
    jf2 = json.loads(f2)
    print("render_find 第二次命中:", jf2.get("cached") is True)
except Exception as e:
    print("render_find JSON 解析失败:", e)

dll.app_shutdown.argtypes = [P]
dll.app_shutdown(th)
dll.graal_detach_all_threads_and_tear_down_isolate.argtypes = [P]
dll.graal_detach_all_threads_and_tear_down_isolate.restype = ctypes.c_int
print("teardown rc=", dll.graal_detach_all_threads_and_tear_down_isolate(th))
