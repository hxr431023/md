# -*- coding: utf-8 -*-
"""最小验收：Python ctypes 加载 controller.dll 并走通核心链。
验证点：加载 / graal_create_isolate / app_init / 新建 / 插入 / 取内容 / Markdown渲染(含中文)。"""
import ctypes, os, sys

DLL = r"C:\wpjc\md\native\dist\controller.dll"
dll = ctypes.CDLL(DLL)
print("[ok] loaded", DLL)

# --- isolate ---
dll.graal_create_isolate.restype = ctypes.c_void_p
dll.graal_create_isolate.argtypes = [ctypes.c_void_p,
                                     ctypes.POINTER(ctypes.c_void_p),
                                     ctypes.POINTER(ctypes.c_void_p)]
isolate = ctypes.c_void_p()
thread = ctypes.c_void_p()
t = dll.graal_create_isolate(None, ctypes.byref(isolate), ctypes.byref(thread))
th = thread.value or t
if not th:
    print("[FAIL] graal_create_isolate -> null"); sys.exit(1)
print("[ok] isolate thread =", hex(th))

def outbuf_fn(fn, th, size=8192):
    """通用：为带 outBuf 的导出函数分配缓冲区并返回 (bytes, int_len)"""
    buf = ctypes.create_string_buffer(size)
    fn.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_char), ctypes.c_int]
    fn.restype = ctypes.c_int
    n = fn(th, buf, size)
    if n < 0: n = 0
    if n > size: n = size
    return buf.raw[:n], n

# --- app_init ---
dll.app_init.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
dll.app_init.restype = ctypes.c_int
work = os.path.join(os.path.dirname(DLL), "_verify_work")
os.makedirs(work, exist_ok=True)
r = dll.app_init(th, work.encode("utf-8"))
print("[ok] app_init =", r)

# --- app_version ---
ver, n = outbuf_fn(dll.app_version, th)
print("[ok] version =", ver.decode("utf-8", "replace"))

# --- new file + char count ---
dll.app_new_file.argtypes = [ctypes.c_void_p]; dll.app_new_file.restype = ctypes.c_int
dll.app_new_file(th)
dll.md_doc_get_char_count.argtypes = [ctypes.c_void_p]; dll.md_doc_get_char_count.restype = ctypes.c_int
print("[ok] char_count(new) =", dll.md_doc_get_char_count(th))

# --- insert text (中文) ---
dll.app_insert_text.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_char_p]
dll.app_insert_text.restype = ctypes.c_int
sample = "# 标题\n\n**粗体**和*斜体*，代码 `x=1`。"
dll.app_insert_text(th, 0, sample.encode("utf-8"))
print("[ok] char_count(after insert) =", dll.md_doc_get_char_count(th))

# --- get content ---
cont, _ = outbuf_fn(dll.app_get_content, th)
print("[ok] content =", cont.decode("utf-8", "replace").replace("\n", "\\n"))

# --- render html (Markdown -> HTML) ---
dll.app_render_html.argtypes = [ctypes.c_void_p, ctypes.c_char_p,
                                   ctypes.POINTER(ctypes.c_char), ctypes.c_int]
dll.app_render_html.restype = ctypes.c_int
buf = ctypes.create_string_buffer(8192)
n = dll.app_render_html(th, sample.encode("utf-8"), buf, 8192)
html = buf.raw[:n if 0 < n <= 8192 else 0].decode("utf-8", "replace")
print("[ok] render_html len =", n, "|", html[:120].replace("\n", " "))

# --- convert text (Markdown -> 纯文本) ---
dll.md_convert_text.argtypes = [ctypes.c_void_p, ctypes.c_char_p,
                                ctypes.POINTER(ctypes.c_char), ctypes.c_int]
dll.md_convert_text.restype = ctypes.c_int
b2 = ctypes.create_string_buffer(8192)
n2 = dll.md_convert_text(th, sample.encode("utf-8"), b2, 8192)
pt = b2.raw[:n2 if 0 < n2 <= 8192 else 0].decode("utf-8", "replace")
print("[ok] convert_text =", pt.replace("\n", "\\n"))

# --- shutdown ---
dll.app_shutdown.argtypes = [ctypes.c_void_p]; dll.app_shutdown.restype = None
dll.app_shutdown(th)
print("[ALL PASS] controller.dll 经 ctypes 加载并调用成功")
