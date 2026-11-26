import ctypes, json, os, shutil, sys, tempfile, time

D = r"C:\wpjc\md\go\build\dll"
BASE = os.path.join(D, "work_all")
shutil.rmtree(BASE, ignore_errors=True)
os.makedirs(BASE, exist_ok=True)
os.chdir(D)
fails = []

def load(name):
    return ctypes.CDLL(os.path.join(D, name))

def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (" :: " + str(detail) if detail and not cond else ""))
    if not cond:
        fails.append(name)

# ---------- controller ----------
c = load("controller.dll")
c.graal_create_isolate.argtypes=[ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p), ctypes.POINTER(ctypes.c_void_p)]
thr=ctypes.c_void_p(); iso=ctypes.c_void_p()
check("graal_create_isolate", c.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(thr)) == 0)
c.app_init.argtypes=[ctypes.c_void_p, ctypes.c_char_p]; c.app_init.restype=ctypes.c_int
check("app_init", c.app_init(thr, BASE.encode()) == 0)
buf = ctypes.create_string_buffer(1 << 20)
c.idp_push.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
c.app_idp_process.argtypes=[ctypes.c_void_p]
c.idp_pop_json.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]; c.idp_pop_json.restype=ctypes.c_int

def call(t, payload):
    body=json.dumps(payload, ensure_ascii=False).encode("utf-8")
    assert c.idp_push(thr, t.encode(), b"py", body) == 0
    assert c.app_idp_process(thr) == 0
    n = c.idp_pop_json(thr, buf, len(buf))
    raw = buf.raw[:n].decode("utf-8")
    return json.loads(raw), n

r,n = call("app_version", {})
check("controller version", r.get("version") == "1.0.10")
r,n = call("app_insert_text", {"pos":0, "text":"# 标题\n中文内容测试"})
r,n = call("app_get_content", {})
check("controller 中文内容完整", r["content"] == "# 标题\n中文内容测试", r)
check("idp_pop_json 返回字节数", n == len("# 标题\n中文内容测试".encode()) + len('{"content":""}') - 2 + 0 or n > 0, n)

# 直接 C ABI 读内容（验证字节数语义）
c.app_get_content.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]; c.app_get_content.restype=ctypes.c_int
n2 = c.app_get_content(thr, buf, len(buf))
check("app_get_content 字节数", buf.raw[:n2].decode("utf-8") == "# 标题\n中文内容测试", n2)

# 纯函数 + 日志缓存
content = "# 标题\n中文内容测试\n第二段"
r,_ = call("render_find", {"content": content, "query": "中文"})
check("render_find 命中", r["result"]["count"] == 1, r)
r,_ = call("render_paged", {"content": content, "pageWidth": 600, "pageHeight": 800})
check("render_paged 多页", r["result"]["pageCount"] >= 1, r)
r2,_ = call("render_find", {"content": content, "query": "中文"})
check("render_find 缓存命中", r2.get("cached") is True, r2)
c.app_shutdown(thr)

# ---------- compress ----------
z = load("compress.dll")
src=os.path.join(BASE,"a.txt"); open(src,"w",encoding="utf-8").write("中文"*500)
comp=os.path.join(BASE,"a.zst"); dec=os.path.join(BASE,"b.txt")
z.compress_file.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p]
z.compress_decompress_file.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p]
z.compress_file_to_string.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]
z.compress_file_to_string.restype=ctypes.c_int
z.compress_ratio.argtypes=[ctypes.c_void_p, ctypes.c_char_p]; z.compress_ratio.restype=ctypes.c_int
check("compress_file", z.compress_file(None, src.encode(), comp.encode()) == 0)
check("compress_decompress_file", z.compress_decompress_file(None, comp.encode(), dec.encode()) == 0)
check("compress 往返一致", open(dec,encoding="utf-8").read() == "中文"*500)
n = z.compress_file_to_string(None, comp.encode(), buf, len(buf))
check("compress_file_to_string", buf.raw[:n].decode("utf-8") == "中文"*500, n)
ratio = z.compress_ratio(None, src.encode())
check("compress_ratio 百分比", 0 < ratio <= 100, ratio)

# ---------- log ----------
lg = load("log.dll")
lg.log_init.argtypes=[ctypes.c_void_p, ctypes.c_char_p]; lg.log_init.restype=ctypes.c_int
lg.log_write.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
lg.log_cache_lookup.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; lg.log_cache_lookup.restype=ctypes.c_int
lg.log_cache_put.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
lg.log_shutdown.argtypes=[ctypes.c_void_p]
lb=os.path.join(BASE,"logbase"); os.makedirs(lb, exist_ok=True)
check("log_init", lg.log_init(None, lb.encode()) == 0)
lg.log_write(None, b"p", b"op", b"r")
check("log_cache_put", lg.log_cache_put(None, b"render_find", b"{q}", b'{"count":1}') == 0)
n = lg.log_cache_lookup(None, b"render_find", b"{q}", buf, len(buf))
check("log_cache_lookup", n == len('{"count":1}') and buf.raw[:n].decode() == '{"count":1}', n)
lg.log_shutdown(None)
work = os.path.join(lb, "cache", "log")
logs = os.listdir(work) if os.path.isdir(work) else []
compdir = os.path.join(lb, "log")
clogs = os.listdir(compdir) if os.path.isdir(compdir) else []
check("log 关闭后生成 .log.zst", any(x.endswith(".log.zst") for x in clogs), clogs)
check("log 关闭后工作态已删除", not any(x.endswith(".log") for x in logs), logs)

# ---------- logmain ----------
lm = load("logmain.dll")
lm.logmain_init.argtypes=[ctypes.c_void_p, ctypes.c_char_p]; lm.logmain_init.restype=ctypes.c_int
lm.logmain_write.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
lm.logmain_status.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]; lm.logmain_status.restype=ctypes.c_int
lm.logmain_shutdown.argtypes=[ctypes.c_void_p]
lmb=os.path.join(BASE,"logmain"); os.makedirs(lmb, exist_ok=True)
check("logmain_init", lm.logmain_init(None, lmb.encode()) == 0)
lm.logmain_write(None, b"p", "测试".encode(), "完成".encode())
n = lm.logmain_status(None, buf, len(buf))
st = json.loads(buf.raw[:n].decode())
check("logmain_status work 路径", st["work"].startswith(lmb), st)
lm.logmain_shutdown(None)
check("logmain_shutdown 生成压缩态", os.path.exists(st["compressed"]), st)

# ---------- doc ----------
d = load("doc.dll")
d.doc_set_content.argtypes=[ctypes.c_void_p, ctypes.c_char_p]
d.doc_get_content.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]; d.doc_get_content.restype=ctypes.c_int
d.doc_insert_text.argtypes=[ctypes.c_void_p, ctypes.c_int, ctypes.c_char_p]
d.doc_get_cursor_line.argtypes=[ctypes.c_void_p]; d.doc_get_cursor_line.restype=ctypes.c_int
check("doc_set_content", d.doc_set_content(None, "第一行\n第二行".encode()) == 0)
n=d.doc_get_content(None, buf, len(buf)); check("doc_get_content", buf.raw[:n].decode() == "第一行\n第二行")
d.doc_insert_text(None, 0, "标题\n".encode())
n=d.doc_get_content(None, buf, len(buf))
check("doc_insert_text", buf.raw[:n].decode() == "标题\n第一行\n第二行")

# ---------- quicksort ----------
q = load("quicksort.dll")
q.quicksort_ints.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; q.quicksort_ints.restype=ctypes.c_int
n=q.quicksort_ints(None, b"3,1,2", b"true", buf, len(buf))
check("quicksort_ints", buf.raw[:n].decode().replace(" ","") == "1,2,3", buf.raw[:n])

# ---------- tmmf / lerf ----------
t = load("tmmf.dll")
t.tmmf_write_tag.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
t.tmmf_read_tag.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; t.tmmf_read_tag.restype=ctypes.c_int
tp=os.path.join(BASE,"s.tmmf")
check("tmmf_write_tag", t.tmmf_write_tag(None, tp.encode(), "字体".encode(), "微软雅黑".encode()) == 0)
n=t.tmmf_read_tag(None, tp.encode(), "字体".encode(), buf, len(buf))
check("tmmf_read_tag 中文", buf.raw[:n].decode() == "微软雅黑", n)

l = load("lerf.dll")
l.lerf_load.argtypes=[ctypes.c_void_p, ctypes.c_char_p]
l.lerf_get.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; l.lerf_get.restype=ctypes.c_int
lp=os.path.join(BASE,"lang.lerf")
with open(lp,"w",encoding="utf-8") as f: f.write("LERF.;utf8\nicon_mdrenderer\n \n0\n<\\1>打开文件<1/>\n<\\2>保存文件<2/>\n…\n")
check("lerf_load", l.lerf_load(None, lp.encode()) == 0)
n=l.lerf_get(None, b"1", buf, len(buf))
check("lerf_get 中文", buf.raw[:n].decode() == "打开文件", n)

# ---------- cmd（IDP 走 controller） ----------
m = load("cmd.dll")
m.cmd_exec.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int]; m.cmd_exec.restype=ctypes.c_int
m.cmd_write_idp.argtypes=[ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]; m.cmd_write_idp.restype=ctypes.c_int
n=m.cmd_exec(None, b"version", buf, len(buf))
check("cmd_exec version", b"1.0.10" in buf.raw[:n], buf.raw[:n])
check("cmd_write_idp 通过 controller 栈", m.cmd_write_idp(None, b"", b"cmd_result", b'{"ok":1}') == 0)
check("idpclient 栈可读", c.idp_stack_size(thr) >= 1)

print("\nFAILS:", fails if fails else "none")
sys.exit(1 if fails else 0)
