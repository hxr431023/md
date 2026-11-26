import ctypes, json, os, sys

dll_path = r"C:\wpjc\md\go\build\dll\controller.dll"
base = r"C:\wpjc\md\go\build\dll\work"
os.makedirs(base, exist_ok=True)

lib = ctypes.CDLL(dll_path)
lib.graal_create_isolate.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p), ctypes.POINTER(ctypes.c_void_p)]
lib.graal_create_isolate.restype = ctypes.c_int

iso = ctypes.c_void_p()
thr = ctypes.c_void_p()
rc = lib.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(thr))
print("create_isolate rc=", rc, "thread=", bool(thr.value))

lib.app_init.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
lib.app_init.restype = ctypes.c_int
print("init rc=", lib.app_init(thr, base.encode()))

lib.idp_push.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
lib.idp_push.restype = ctypes.c_int
lib.app_idp_process.argtypes = [ctypes.c_void_p]
lib.app_idp_process.restype = ctypes.c_int
lib.idp_pop_json.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]
lib.idp_pop_json.restype = ctypes.c_int

buf = ctypes.create_string_buffer(65536)

def call(t, payload):
    body = json.dumps(payload, ensure_ascii=False)
    lib.idp_push(thr, t.encode(), b"python", body.encode("utf-8"))
    lib.app_idp_process(thr)
    n = lib.idp_pop_json(thr, buf, 65536)
    raw = buf.raw[:n]
    print(f"{t}: n(bytes)={n}")
    return json.loads(raw.decode("utf-8"))

print(call("app_version", {}))
print(call("app_insert_text", {"pos": 0, "text": "# 标题\n中文内容测试"}))
print("content:", call("app_get_content", {}))
print("status:", call("app_get_status", {}))
print("ui chars:", len(call("app_render_ui", {}).get("elements", [])))
print("paged:", list(call("render_paged", {"page_width": 600, "page_height": 800}).keys()))
print("find:", call("render_find", {"query": "中文"}))
# 缓存命中
print("find2:", call("render_find", {"query": "中文"}))
lib.app_shutdown(thr)
print("shutdown ok")
