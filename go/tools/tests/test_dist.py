import ctypes, json, os, shutil, sys, tempfile

HD = os.environ["HDLL"]
WORK = os.path.join(tempfile.gettempdir(), "md_dll_dist_test")
shutil.rmtree(WORK, ignore_errors=True)
os.makedirs(WORK)
os.chdir(tempfile.gettempdir())  # 关键：不在 HDLL 目录内，验证环境变量寻址

fails = []
def check(n, c, d=""):
    print(("PASS " if c else "FAIL ") + n + ((" :: " + str(d)) if d and not c else ""))
    if not c: fails.append(n)

def load(rel):
    return ctypes.CDLL(os.path.join(HD, rel))

buf = ctypes.create_string_buffer(1 << 20)

# controller (app/) + cmd (tools/) 跨目录、跨 DLL 共用同一 IDP 栈
c = load(r"app\controller.dll")
c.graal_create_isolate.argtypes=[ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p), ctypes.POINTER(ctypes.c_void_p)]
thr=ctypes.c_void_p(); iso=ctypes.c_void_p()
check("isolate", c.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(thr)) == 0)
c.app_init.argtypes=[ctypes.c_void_p, ctypes.c_char_p]
check("app_init", c.app_init(thr, WORK.encode()) == 0)
c.idp_stack_size.argtypes=[ctypes.c_void_p]; c.idp_stack_size.restype=ctypes.c_int

m = load(r"tools\cmd.dll")
m.cmd_write_idp.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
m.cmd_write_idp.restype=ctypes.c_int
check("cmd.dll 在 tools/ 找到 app/controller.dll", m.cmd_write_idp(None, b"", b"cmd_result", b'{"ok":1}') == 0)
check("IDP 栈由 controller 持有", c.idp_stack_size(thr) == 1, c.idp_stack_size(thr))
m.cmd_exec.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; m.cmd_exec.restype=ctypes.c_int
check("cmd_exec 可用", m.cmd_exec(None, b"version", buf, len(buf)) > 0)

# 日志主控（core/logmain.dll）
lm = load(r"core\logmain.dll")
lm.logmain_init.argtypes=[ctypes.c_void_p, ctypes.c_char_p]; lm.logmain_init.restype=ctypes.c_int
lm.logmain_status.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]; lm.logmain_status.restype=ctypes.c_int
lm.logmain_write.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
base2 = os.path.join(WORK, "log")
check("logmain_init", lm.logmain_init(None, base2.encode()) == 0)
lm.logmain_write(None, b"p", "渲染".encode(), "完成".encode())
n = lm.logmain_status(None, buf, len(buf))
check("logmain_status", json.loads(buf.raw[:n].decode())["work"].startswith(base2))

# docpack 主控
dp = load(r"doc\docpack.dll")
dp.render_html.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; dp.render_html.restype=ctypes.c_int
n = dp.render_html(None, "# 标题\n**粗体**".encode(), buf, len(buf))
html = buf.raw[:n].decode()
check("docpack render_html", "<strong>粗体</strong>" in html, html[:80])
dp.convert_txt2md.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; dp.convert_txt2md.restype=ctypes.c_int
n = dp.convert_txt2md(None, "第一行\n第二行".encode(), buf, len(buf))
check("docpack convert_txt2md", buf.raw[:n].decode().startswith("# 第一行"), buf.raw[:n])

# core 主控
cp = load(r"core\core.dll")
cp.config_init.argtypes=[ctypes.c_void_p, ctypes.c_char_p]; cp.config_init.restype=ctypes.c_int
cp.config_set_setting.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p]
cp.config_get_setting.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; cp.config_get_setting.restype=ctypes.c_int
check("core config_init", cp.config_init(None, WORK.encode()) == 0)
check("core config_set", cp.config_set_setting(None, b"theme", b"dark") == 0)
n = cp.config_get_setting(None, b"theme", buf, len(buf))
check("core config_get", buf.raw[:n].decode() == "dark")

# toolspack 主控
tp = load(r"tools\toolspack.dll")
tp.tmmf_write_tag.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
tp.tmmf_read_tag.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; tp.tmmf_read_tag.restype=ctypes.c_int
p = os.path.join(WORK, "x.tmmf")
check("toolspack write", tp.tmmf_write_tag(None, p.encode(), "键".encode(), "值".encode()) == 0)
n = tp.tmmf_read_tag(None, p.encode(), "键".encode(), buf, len(buf))
check("toolspack read", buf.raw[:n].decode() == "值")

print("\nFAILS:", fails if fails else "none")
sys.exit(1 if fails else 0)
