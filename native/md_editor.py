# -*- coding: utf-8 -*-
"""
MDRenderer 通用前端（View）—— DLL 为 Model + Controller。

架构约定（通用渲染层）：
  * Python 只通过控制层 app_* 接口与 DLL 交互，不直接调用底层模块接口。
  * 文档内容、状态、渲染指令全部由 DLL 持有；前端只是视图（纯渲染）。
  * 预览渲染按 DLL 输出的渲染指令数据包（位置/类型/文字/颜色）通用渲染：
      渲染指令 JSON = {"lines":[{"n","type","s","e","inline":[{"s","e","st"}]}]}
      - 位置：n(行号) / s,e(字符偏移)
      - 类型：type(块级 h1..code/quote/list/...)、inline.st(内联 bold/italic/code/strike/bolditalic)
      - 文字：由偏移 s:e 从文档内容截取
      - 颜色：前端按类型映射（样式表），数据包决定画什么，格式一致即可用
  * 单实例检测：app_init 返回 2 = 已有实例（MDSingle 互锁），不再调用不存在的函数。
  * 彩蛋：\\> 命令在预览渲染时执行并显示结果，文档仍存原始命令（二次打开可二次执行）。
"""
import ctypes
import json
import os
import sys
import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext

# ============================================================
#  DLL 加载与 isolate（相对路径，可移植，不依赖硬编码绝对路径）
# ============================================================

def _dll_path():
    """查找 controller.dll：优先同目录，其次 DLL\\app、..\\dist（相对路径）"""
    base = os.path.dirname(os.path.abspath(__file__))
    for p in [
        os.path.join(base, "controller.dll"),
        os.path.join(base, "DLL", "app", "controller.dll"),
        os.path.join(base, "..", "dist", "controller.dll"),
        os.path.join(base, "..", "..", "dist", "controller.dll"),
    ]:
        if os.path.exists(p):
            return p
    return os.path.join(base, "controller.dll")


DLL_PATH = _dll_path()
_dll = ctypes.CDLL(DLL_PATH)

# isolate
_dll.graal_create_isolate.restype = ctypes.c_int
_dll.graal_create_isolate.argtypes = [ctypes.c_void_p,
                                       ctypes.POINTER(ctypes.c_void_p),
                                       ctypes.POINTER(ctypes.c_void_p)]
_isolate = ctypes.c_void_p()
_thread = ctypes.c_void_p()
_dll.graal_create_isolate(None, ctypes.byref(_isolate), ctypes.byref(_thread))
TH = _thread.value or _isolate.value


def _py_to_ctypes(a):
    """将 Python 参数转换为 (ctypes类型, 值) 元组"""
    if isinstance(a, str):
        return ctypes.c_char_p, a.encode("utf-8")
    if isinstance(a, int):
        return ctypes.c_int, a
    if isinstance(a, bytes):
        return ctypes.c_char_p, a
    if isinstance(a, float):
        return ctypes.c_double, a
    return type(a), a


def _call_str(fn, *args, size=65536):
    """调用带 outBuf 的导出函数，返回 str（缓冲不足时自动扩容重读）"""
    ctypes_args = [_py_to_ctypes(a) for a in args]
    fn.argtypes = [ctypes.c_void_p] + [t for t, _ in ctypes_args] + \
                  [ctypes.POINTER(ctypes.c_char), ctypes.c_int]
    fn.restype = ctypes.c_int
    cargs = [TH] + [v for _, v in ctypes_args]
    buf = ctypes.create_string_buffer(size)
    n = fn(*cargs, buf, size)
    if n > size:  # 返回完整长度 > 缓冲：扩容重读
        size = n + 1
        buf = ctypes.create_string_buffer(size)
        n = fn(*cargs, buf, size)
    if n < 0:
        n = 0
    return buf.raw[:n].decode("utf-8", "replace")


def _call_int(fn, *args):
    ctypes_args = [_py_to_ctypes(a) for a in args]
    fn.argtypes = [ctypes.c_void_p] + [t for t, _ in ctypes_args]
    fn.restype = ctypes.c_int
    cargs = [TH] + [v for _, v in ctypes_args]
    return fn(*cargs)


def _call_void(fn, *args):
    ctypes_args = [_py_to_ctypes(a) for a in args]
    fn.argtypes = [ctypes.c_void_p] + [t for t, _ in ctypes_args]
    fn.restype = None
    cargs = [TH] + [v for _, v in ctypes_args]
    fn(*cargs)


# ============================================================
#  应用初始化（控制层单实例检测）
# ============================================================

_work_dir = os.path.join(os.path.expanduser("~"), ".mdrenderer")
os.makedirs(_work_dir, exist_ok=True)
# app_init 返回：0=成功 2=已有实例(前端应提示退出) -1=失败
_INIT_RC = _call_int(_dll.app_init, _work_dir)
APP_VERSION = _call_str(_dll.app_version)


# ============================================================
#  渲染样式表（通用渲染器：类型→字体/颜色；内联样式→字体）
#  新增渲染类型只需在此追加映射，前端渲染逻辑不变。
# ============================================================

_BLOCK_STYLE = {
    "h1":        ("黑体", 16, "bold",   "#1a237e"),
    "h2":        ("黑体", 14, "bold",   "#1a237e"),
    "h3":        ("黑体", 12, "bold",   "#303f9f"),
    "h4":        ("黑体", 12, "bold",   "#37474f"),
    "h5":        ("黑体", 11, "bold",   "#37474f"),
    "h6":        ("黑体", 11, "bold",   "#37474f"),
    "paragraph": ("Microsoft YaHei", 10, "normal", "#222222"),
    "quote":     ("Microsoft YaHei", 10, "italic", "#607d8b"),
    "list":      ("Microsoft YaHei", 10, "normal", "#222222"),
    "ordered":   ("Microsoft YaHei", 10, "normal", "#222222"),
    "code":      ("Consolas", 10, "normal", "#c62828"),
    "empty":     ("Microsoft YaHei", 10, "normal", "#222222"),
}

# 内联样式 → 字体权重/效果
def _inline_font(st, base_family, base_size):
    if st == "bold":
        return (base_family, base_size, "bold")
    if st == "italic":
        return (base_family, base_size, "italic")
    if st == "bolditalic":
        return (base_family, base_size, "bold")
    if st == "code":
        return ("Consolas", base_size, "normal")
    if st == "strike":
        return (base_family, base_size, "normal")
    return (base_family, base_size, "normal")


def _inline_overstrike(st):
    return st == "strike"


# ============================================================
#  前端（View）—— 只调控制层 app_*，按数据包通用渲染
# ============================================================

class MDEditor(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("MDRenderer " + APP_VERSION + " - 未命名")
        self.geometry("1000x700")
        self._content = ""          # 前端镜像：始终与 DLL 文档模型一致
        self._in_reload = False     # 程序化回写时抑制事件同步
        self._build_menu()
        self._build_body()
        self._build_status()
        self.protocol("WM_DELETE_WINDOW", self._on_close)
        # 从控制层加载当前文档内容（纯视图，内容在 DLL）
        self._reload_content()
        self._refresh_status()
        self._render_preview()
        # 单实例：app_init 返回 2 = 已有实例
        if _INIT_RC == 2:
            messagebox.showwarning("提示", "程序已经在运行中！")
            self.after(100, self.destroy)

    # ---- 菜单（动作经控制层）----
    def _build_menu(self):
        menubar = tk.Menu(self)
        fm = tk.Menu(menubar, tearoff=0)
        fm.add_command(label="新建", command=self._new, accelerator="Ctrl+N")
        fm.add_command(label="打开", command=self._open, accelerator="Ctrl+O")
        fm.add_command(label="保存", command=self._save, accelerator="Ctrl+S")
        fm.add_command(label="另存为", command=self._save_as)
        fm.add_separator()
        fm.add_command(label="打印", command=self._print)
        fm.add_separator()
        fm.add_command(label="退出", command=self._on_close)
        menubar.add_cascade(label="文件", menu=fm)

        em = tk.Menu(menubar, tearoff=0)
        em.add_command(label="撤销", command=self._undo, accelerator="Ctrl+Z")
        em.add_command(label="重做", command=self._redo, accelerator="Ctrl+Y")
        em.add_separator()
        em.add_command(label="全选", command=lambda: self.text.tag_add("sel", "1.0", "end"))
        menubar.add_cascade(label="编辑", menu=em)

        sm = tk.Menu(menubar, tearoff=0)
        sm.add_command(label="线程模式", command=self._thread_mode_dialog)
        sm.add_command(label="日志设置", command=self._log_settings)
        menubar.add_cascade(label="设置", menu=sm)

        hm = tk.Menu(menubar, tearoff=0)
        hm.add_command(label="帮助", command=self._help)
        hm.add_command(label="关于", command=self._about)
        menubar.add_cascade(label="帮助", menu=hm)
        self.config(menu=menubar)

        self.bind("<Control-n>", lambda e: self._new())
        self.bind("<Control-o>", lambda e: self._open())
        self.bind("<Control-s>", lambda e: self._save())
        self.bind("<Control-z>", lambda e: self._undo())
        self.bind("<Control-y>", lambda e: self._redo())

    # ---- 主体 ----
    def _build_body(self):
        paned = ttk.PanedWindow(self, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True)
        # 左侧编辑器（视图；撤销走 DLL，故不开 tkinter undo）
        left = ttk.Frame(paned)
        self.text = scrolledtext.ScrolledText(left, wrap=tk.WORD, font=("Consolas", 11), undo=False)
        self.text.pack(fill=tk.BOTH, expand=True)
        self.text.bind("<<Modified>>", self._on_change)
        paned.add(left, weight=1)
        # 右侧预览（通用渲染器，按渲染指令数据包画）
        right = ttk.Frame(paned)
        self.preview = scrolledtext.ScrolledText(right, wrap=tk.WORD, font=("Microsoft YaHei", 10),
                                                 state=tk.DISABLED, bg="#fafafa")
        self.preview.pack(fill=tk.BOTH, expand=True)
        paned.add(right, weight=1)

    def _build_status(self):
        self.status = ttk.Label(self, text="就绪", anchor=tk.W, relief=tk.SUNKEN)
        self.status.pack(fill=tk.X, side=tk.BOTTOM)

    # ---- 与 DLL 模型同步 ----
    def _set_text(self, content):
        """程序化设置编辑区文本并同步镜像（不触发 diff 同步）"""
        self._in_reload = True
        self._content = content
        self.text.delete("1.0", tk.END)
        self.text.insert("1.0", content)
        self.text.edit_modified(False)
        self._in_reload = False

    def _reload_content(self):
        """从控制层读取文档内容（内容在 DLL）"""
        try:
            content = _call_str(_dll.app_get_content)
        except Exception:
            content = ""
        self._set_text(content)

    def _sync_to_dll(self):
        """计算前端文本与镜像的差异，增量同步到 DLL 文档模型"""
        content = self.text.get("1.0", "end-1c")
        if content == self._content:
            return
        old, new = self._content, content
        i, n = 0, min(len(old), len(new))
        while i < n and old[i] == new[i]:
            i += 1
        j = 0
        while j < min(len(old) - i, len(new) - i) and old[len(old) - 1 - j] == new[len(new) - 1 - j]:
            j += 1
        old_end = len(old) - j
        new_seg = new[i:len(new) - j]
        try:
            if old_end > i:
                _call_int(_dll.app_delete_text, i, old_end)
            if new_seg:
                _call_int(_dll.app_insert_text, i, new_seg)
        except Exception:
            pass
        self._content = content

    def _sync_cursor(self):
        try:
            pos = len(self.text.get("1.0", "insert"))
            _call_int(_dll.app_set_cursor, pos)
        except Exception:
            pass

    def _on_change(self, event=None):
        self.text.edit_modified(False)
        if self._in_reload:
            return
        self._sync_to_dll()
        self._sync_cursor()
        self._refresh_status()
        self._render_preview()

    # ---- 文件操作（内容在 DLL，前端不直接读写文件）----
    def _current_path(self):
        try:
            p = _call_str(_dll.app_get_file_path)
            return p or None
        except Exception:
            return None

    def _update_title(self):
        p = self._current_path()
        name = os.path.basename(p) if p else "未命名"
        self.title("MDRenderer " + APP_VERSION + " - " + name)

    def _new(self):
        _call_int(_dll.app_new_file)
        self._reload_content()
        self._update_title()
        self._refresh_status()
        self._render_preview()

    def _open(self):
        path = filedialog.askopenfilename(filetypes=[("Markdown", "*.md"), ("文本", "*.txt"), ("所有文件", "*.*")])
        if not path:
            return
        rc = _call_int(_dll.app_open_file, path)
        if rc < 0:
            messagebox.showerror("错误", "无法打开文件：" + path)
            return
        self._reload_content()
        self._update_title()
        self._refresh_status()
        self._render_preview()

    def _save(self):
        self._sync_to_dll()          # 先确保 DLL 模型最新
        if self._current_path():
            rc = _call_int(_dll.app_save_file)
        else:
            rc = self._save_as()
            return
        self.status.config(text="已保存" if rc == 0 else "保存失败")

    def _save_as(self):
        self._sync_to_dll()
        path = filedialog.asksaveasfilename(defaultextension=".md",
                                            filetypes=[("Markdown", "*.md"), ("文本", "*.txt")])
        if not path:
            return None
        rc = _call_int(_dll.app_save_file_as, path)
        self._update_title()
        self.status.config(text="已保存: " + path if rc == 0 else "保存失败")
        return rc

    def _print(self):
        messagebox.showinfo("打印", "打印功能：请使用浏览器打开导出的 HTML 后打印。")

    # ---- 编辑（撤销/重做走 DLL）----
    def _undo(self):
        _call_int(_dll.app_undo)
        self._reload_content()
        self._refresh_status()
        self._render_preview()

    def _redo(self):
        _call_int(_dll.app_redo)
        self._reload_content()
        self._refresh_status()
        self._render_preview()

    # ---- 设置（走控制层 app_get_setting / set_setting）----
    def _thread_mode_dialog(self):
        dlg = tk.Toplevel(self)
        dlg.title("线程模式")
        dlg.geometry("360x180")
        dlg.transient(self)
        dlg.grab_set()
        ttk.Label(dlg, text="选择多线程加速模式：", font=("Microsoft YaHei", 10)).pack(pady=10)
        current = _call_str(_dll.app_get_setting, "thread_mode") or "folder"
        mode = tk.StringVar(value=current)
        for val, desc in [("folder", "每分类一个线程（默认，推荐）"),
                          ("single", "单线程（兼容性优先）"),
                          ("per_dll", "每个DLL一个线程（最大并行）")]:
            ttk.Radiobutton(dlg, text=desc, variable=mode, value=val).pack(anchor=tk.W, padx=30)

        def apply():
            _call_int(_dll.app_set_setting, "thread_mode", mode.get())
            _call_int(_dll.app_save_settings)
            self.status.config(text="线程模式已设置为: " + mode.get())
            dlg.destroy()

        ttk.Button(dlg, text="确定", command=apply).pack(pady=10)

    def _log_settings(self):
        dlg = tk.Toplevel(self)
        dlg.title("日志设置")
        dlg.geometry("400x300")
        dlg.transient(self)
        dlg.grab_set()
        ttk.Label(dlg, text="选择需要记录日志的操作（勾选=记录）：", font=("Microsoft YaHei", 10)).pack(pady=10, anchor=tk.W, padx=10)
        ops = ["打开文件", "保存文件", "新建文件", "渲染", "转换", "设置变更", "彩蛋命令"]
        saved = (_call_str(_dll.app_get_setting, "log_operations") or ",".join(ops)).split(",")
        vars_ = {}
        for op in ops:
            v = tk.IntVar(value=1 if op in saved else 0)
            vars_[op] = v
            ttk.Checkbutton(dlg, text=op, variable=v).pack(anchor=tk.W, padx=30)

        def apply():
            selected = [op for op, v in vars_.items() if v.get()]
            _call_int(_dll.app_set_setting, "log_operations", ",".join(selected))
            _call_int(_dll.app_save_settings)
            self.status.config(text="日志设置已保存，记录: " + ",".join(selected))
            dlg.destroy()

        ttk.Button(dlg, text="确定", command=apply).pack(pady=10)

    # ---- 帮助 ----
    def _help(self):
        dlg = tk.Toplevel(self)
        dlg.title("帮助")
        dlg.geometry("500x400")
        dlg.transient(self)
        txt = scrolledtext.ScrolledText(dlg, wrap=tk.WORD, font=("Microsoft YaHei", 10))
        txt.pack(fill=tk.BOTH, expand=True)
        txt.insert("1.0", """MDRenderer 帮助

快捷键：
  Ctrl+N  新建文件
  Ctrl+O  打开文件
  Ctrl+S  保存文件
  Ctrl+Z  撤销
  Ctrl+Y  重做

彩蛋命令（行首输入，预览时执行显示结果，文档仍存原始命令）：
  \\>help      显示CMD帮助
  \\>version   显示版本
  \\>clear     清屏
  \\>modules   列出所有模块

Markdown 语法：
  # 标题（#后无需空格）
  **粗体**  *斜体*  `代码`
  - 列表项
  > 引用

设置：
  线程模式：folder/single/per_dll
  日志：可选择记录哪些操作
""")
        txt.config(state=tk.DISABLED)

    def _about(self):
        messagebox.showinfo("关于", "MDRenderer " + APP_VERSION +
                            "\n\n通用渲染层：Python 前端只走控制层 app_*，" +
                            "按 DLL 渲染指令数据包（位置/类型/文字/颜色）通用渲染。\n19个模块架构")

    # ---- 通用渲染器：按渲染指令数据包渲染预览 ----
    def _render_preview(self):
        content = self._content
        if not content.strip():
            self.preview.config(state=tk.NORMAL)
            self.preview.delete("1.0", tk.END)
            self.preview.config(state=tk.DISABLED)
            return
        # 从控制层取渲染指令数据包（同时写入 IDP 缓存）
        js = _call_str(_dll.app_get_render_instructions)
        data = {}
        if js:
            try:
                data = json.loads(js)
            except Exception:
                pass
        if not data:
            try:
                data = json.loads(_call_str(_dll.app_read_render_cache) or "{}")
            except Exception:
                data = {}
        lines = data.get("lines", [])

        self.preview.config(state=tk.NORMAL)
        self.preview.delete("1.0", tk.END)
        for ln in lines:
            t = ln.get("type", "paragraph")
            s, e = ln.get("s", 0), ln.get("e", 0)
            if e > len(content):
                e = len(content)
            if s > e:
                s = e
            seg = content[s:e]

            # 彩蛋行：预览时执行并显示结果（不写入文档，保持原始命令）
            if seg.startswith("\\\\>"):
                try:
                    result = _call_str(_dll.app_easter_exec, seg)
                except Exception:
                    result = ""
                self.preview.insert("end", (result if result else seg) + "\n", ("easter",))
                continue

            family, size, weight, color = _BLOCK_STYLE.get(t, _BLOCK_STYLE["paragraph"])
            blk = "blk_" + t
            self.preview.tag_configure(blk, font=(family, size, weight), foreground=color)
            if t == "code":
                self.preview.tag_configure(blk, background="#f3f3f3")

            self.preview.insert("end", seg, (blk,))
            line_index = self.preview.index("end-1c linestart")
            self.preview.insert("end", "\n")

            # 内联样式区间（相对本行偏移 → 加 tag）
            for st in ln.get("inline", []):
                rs, re2 = st.get("s", 0), st.get("e", 0)
                rl = rs - s
                rr = re2 - s
                if rl < 0:
                    rl = 0
                if rr > len(seg):
                    rr = len(seg)
                if rr <= rl:
                    continue
                stname = "st_" + st.get("st", "")
                self.preview.tag_configure(
                    stname,
                    font=_inline_font(st.get("st", ""), family, size),
                    overstrike=_inline_overstrike(st.get("st", "")))
                self.preview.tag_add(stname, "%s+%dc" % (line_index, rl), "%s+%dc" % (line_index, rr))
        self.preview.tag_configure("easter", font=("Microsoft YaHei", 10, "bold"), foreground="#00695c")
        self.preview.config(state=tk.DISABLED)

    # ---- 状态栏（控制层状态数据包）----
    def _refresh_status(self):
        try:
            js = _call_str(_dll.app_get_status)
            st = json.loads(js) if js else {}
        except Exception:
            st = {}
        name = st.get("file_name") or "未命名"
        dirty = st.get("dirty")
        parts = [
            name,
            ("●已修改" if dirty else "已保存") if dirty is not None else "",
            "字符:%s" % st.get("char_count", "?"),
            "词:%s" % st.get("word_count", "?"),
            "行:%s" % st.get("line_count", "?"),
            "光标:%s" % st.get("cursor_pos", "?"),
        ]
        self.status.config(text="  ".join([p for p in parts if p]))

    # ---- 关闭 ----
    def _on_close(self):
        try:
            _call_void(_dll.app_shutdown)
        except Exception:
            pass
        self.destroy()


def main():
    app = MDEditor()
    app.mainloop()


if __name__ == "__main__":
    main()
