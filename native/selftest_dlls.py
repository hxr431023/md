# -*- coding: utf-8 -*-
"""MDRenderer DLL 交付前自测（真实加载 + IDP 通信 + 副 DLL 独立可用）"""
import ctypes, os, sys, json

DLL_DIR = r"C:\wpjc\md\native\DLL"
BASE = r"C:\wpjc\md\native\_selftest_data"
os.makedirs(BASE, exist_ok=True)
P = ctypes.c_void_p
ok = 0
fail = 0


def check(name, cond, detail=""):
    global ok, fail
    if cond:
        ok += 1
        print("[PASS] %s %s" % (name, detail))
    else:
        fail += 1
        print("[FAIL] %s %s" % (name, detail))


def load(path):
    return ctypes.CDLL(path)


def make_isolate(dll):
    dll.graal_create_isolate.restype = ctypes.c_int
    dll.graal_create_isolate.argtypes = [P, ctypes.POINTER(P), ctypes.POINTER(P)]
    iso, th = P(), P()
    rc = dll.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(th))
    return rc, iso, th


def teardown(dll, th):
    try:
        dll.graal_detach_all_threads_and_tear_down_isolate.argtypes = [P]
        dll.graal_detach_all_threads_and_tear_down_isolate.restype = ctypes.c_int
        return dll.graal_detach_all_threads_and_tear_down_isolate(th)
    except Exception as e:
        return -1


# ---------------- controller.dll: 主控三段式 IDP ----------------
print("== controller.dll ==")
ctrl_path = os.path.join(DLL_DIR, "app", "controller.dll")
check("controller.dll 存在", os.path.exists(ctrl_path))
dll = load(ctrl_path)
rc, iso, th = make_isolate(dll)
check("graal_create_isolate", rc == 0 and th, "rc=%s thread=%s" % (rc, th))

dll.app_init.argtypes = [P, ctypes.c_char_p]
dll.app_init.restype = ctypes.c_int
check("app_init", dll.app_init(th, BASE.encode()) == 0)

dll.app_version.argtypes = [P, ctypes.c_char_p, ctypes.c_int]
dll.app_version.restype = ctypes.c_int
buf = ctypes.create_string_buffer(1 << 20)
n = dll.app_version(th, buf, len(buf))
ver = buf.value[:n] if n > 0 else buf.value
check("app_version 直调", n > 0, "len=%s val=%r" % (n, ver[:32]))

# 三段式 IDP
dll.idp_push.argtypes = [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
dll.idp_push.restype = ctypes.c_int
dll.app_idp_process.argtypes = [P]
dll.app_idp_process.restype = ctypes.c_int
dll.idp_pop_json.argtypes = [P, ctypes.c_char_p, ctypes.c_int]
dll.idp_pop_json.restype = ctypes.c_int
dll.idp_stack_size.argtypes = [P]
dll.idp_stack_size.restype = ctypes.c_int


def call(t, j):
    rc1 = dll.idp_push(th, t.encode("utf-8"), b"selftest", j.encode("utf-8"))
    rc2 = dll.app_idp_process(th)
    b = ctypes.create_string_buffer(1 << 20)
    n2 = dll.idp_pop_json(th, b, len(b))
    return rc1, rc2, n2, (b.value[:n2] if n2 > 0 else b.value)


rc1, rc2, n2, out = call("app_render_html", '{"md":"# hi"}')
check("IDP app_render_html", rc1 == 0 and rc2 == 0 and n2 > 0,
      "push=%s process=%s poplen=%s head=%r" % (rc1, rc2, n2, out[:40]))
rc1, rc2, n2, out = call("app_version", "{}")
check("IDP app_version", n2 > 0, "poplen=%s val=%r" % (n2, out[:40]))
rc1, rc2, n2, out = call("render_find", '{"content":"ab c ab","query":"ab"}')
check("IDP render_find", n2 > 0, "poplen=%s val=%r" % (n2, out[:60]))
check("栈已清空", dll.idp_stack_size(th) == 0, "size=%s" % dll.idp_stack_size(th))

dll.app_shutdown.argtypes = [P]
dll.app_shutdown(th)
rc = teardown(dll, th)
check("controller teardown", rc == 0 or rc is None, "rc=%s" % rc)

# ---------------- 副 DLL 独立可用 ----------------
print("== 副 DLL 独立可用 ==")


def test_simple(name, relpath, fn_name, set_args, get_restype, call_args, expect):
    path = os.path.join(DLL_DIR, relpath)
    check("%s 存在" % name, os.path.exists(path))
    try:
        d = load(path)
        rc, iso2, th2 = make_isolate(d)
        check("%s isolate" % name, rc == 0 and th2, "rc=%s" % rc)
        f = getattr(d, fn_name)
        f.argtypes = set_args
        f.restype = get_restype
        r = f(th2, *call_args)
        check("%s %s" % (name, fn_name), expect(r), "ret=%r" % (r,))
        teardown(d, th2)
    except Exception as e:
        check("%s 调用" % name, False, "exception=%r" % (e,))


# idp.dll: 必须在同一个 isolate 内 push → size=1 → pop
idp_path = os.path.join(DLL_DIR, "core", "idp.dll")
check("idp.dll 存在", os.path.exists(idp_path))
idp = load(idp_path)
rc, iso3, th3 = make_isolate(idp)
check("idp.dll isolate", rc == 0 and th3, "rc=%s" % rc)
idp.idp_push.argtypes = [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
idp.idp_push.restype = ctypes.c_int
idp.idp_stack_size.argtypes = [P]
idp.idp_stack_size.restype = ctypes.c_int
idp.idp_pop_json.argtypes = [P, ctypes.c_char_p, ctypes.c_int]
idp.idp_pop_json.restype = ctypes.c_int
check("idp push", idp.idp_push(th3, b"t", b"py", b'{"a":1}') == 0)
check("idp stack_size=1", idp.idp_stack_size(th3) == 1, "size=%s" % idp.idp_stack_size(th3))
ib = ctypes.create_string_buffer(256)
n3 = idp.idp_pop_json(th3, ib, 256)
check("idp pop_json 回读", n3 > 0 and ib.value[:n3] == b'{"a":1}', "n=%s val=%r" % (n3, ib.value[:n3]))
check("idp 栈清空", idp.idp_stack_size(th3) == 0)
teardown(idp, th3)

# 全部 22 个 DLL 加载 + isolate 冒烟
print("== 全量 DLL 加载冒烟 ==")
all_dlls = []
for sub in ("app", "core", "doc", "tools"):
    for fn in os.listdir(os.path.join(DLL_DIR, sub)):
        if fn.lower().endswith(".dll"):
            all_dlls.append(os.path.join(DLL_DIR, sub, fn))
for p in sorted(all_dlls):
    try:
        d = load(p)
        rc, iso4, th4 = make_isolate(d)
        check("smoke " + os.path.basename(p), rc == 0 and th4, "rc=%s" % rc)
        teardown(d, th4)
    except Exception as e:
        check("smoke " + os.path.basename(p), False, repr(e))

# compress.dll: 字符串压缩到文件
test_simple("compress.dll", r"core\compress.dll", "compress_string_to_file",
            [P, ctypes.c_char_p, ctypes.c_char_p], ctypes.c_int,
            [b"hello world", os.path.join(BASE, "h.zst").encode()], lambda r: r == 0)
test_simple("compress.dll", r"core\compress.dll", "compress_decompress_file",
            [P, ctypes.c_char_p, ctypes.c_char_p], ctypes.c_int,
            [os.path.join(BASE, "h.zst").encode(), os.path.join(BASE, "h.txt").encode()],
            lambda r: r == 0)
try:
    with open(os.path.join(BASE, "h.txt"), "rb") as fp:
        content = fp.read()
    check("compress 往返内容", content == b"hello world", repr(content))
except Exception as e:
    check("compress 往返内容", False, repr(e))

# file.dll: 写读
test_simple("file.dll", r"core\file.dll", "file_write",
            [P, ctypes.c_char_p, ctypes.c_char_p], ctypes.c_int,
            [os.path.join(BASE, "f.txt").encode(), b"abc"], lambda r: r == 0)
buf2 = ctypes.create_string_buffer(64)
test_simple("file.dll", r"core\file.dll", "file_read",
            [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int], ctypes.c_int,
            [os.path.join(BASE, "f.txt").encode(), buf2, 64], lambda r: r == 3)

# quicksort.dll: JSON 数组排序
qs_buf = ctypes.create_string_buffer(1024)
test_simple("quicksort.dll", r"doc\quicksort.dll", "quicksort_ints",
            [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int], ctypes.c_int,
            [b"[3,1,2]", b"asc", qs_buf, 1024], lambda r: r >= 0)

# tmmf.dll: 写读标签
tmmf_path = os.path.join(BASE, "t.tmmf").encode()
test_simple("tmmf.dll", r"tools\tmmf.dll", "tmmf_write_tag",
            [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p], ctypes.c_int,
            [tmmf_path, b"emoji", b"\xe9\xab\x98\xe8\x88\x88 a.png b.png"], lambda r: r >= 0)
tbuf = ctypes.create_string_buffer(4096)
test_simple("tmmf.dll", r"tools\tmmf.dll", "tmmf_search",
            [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int], ctypes.c_int,
            [tmmf_path, b"emoji", tbuf, 4096], lambda r: r > 0)

# markdown.dll: render_html
mb = ctypes.create_string_buffer(1 << 16)
test_simple("markdown.dll", r"doc\markdown.dll", "render_html",
            [P, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int], ctypes.c_int,
            [b"# Hello", mb, 1 << 16], lambda r: r > 0)

print("\n==== RESULT: PASS=%d FAIL=%d ====" % (ok, fail))
sys.exit(0 if fail == 0 else 1)
