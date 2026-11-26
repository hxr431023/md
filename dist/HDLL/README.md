# MDRenderer Go 版 DLL 使用说明（总索引）

本目录是 Go 重写后的成品：**主体 + 多个 DLL**。所有业务逻辑来自同一份 `internal/engine`，每个 DLL 只是薄 C ABI 适配层。

- 单文件版源码：`go/single/md.go`（约 3300 行，可独立编译成 `md.exe`）
- 多 DLL 版源码：`go/dll/<模块>/main.go`（由 `go/tools/gen_dlls.py` 生成）
- 构建脚本：`go/tools/build-dlls.ps1`
- 全部 DLL 导出符号共 269 个，已逐个 `GetProcAddress` 验证

---

## 1. 目录结构（每个文件夹都有一个主控）

```
HDLL\
├─ app\
│   ├─ controller.dll      ← 全局唯一入口（总主控）
│   └─ controller.h
├─ core\
│   ├─ core.dll            ← core 文件夹主控（等于下面 7 个模块的合并版）
│   ├─ logmain.dll         ← 日志子系统主控（管理日志 + 压缩，唯一对外）
│   ├─ idp.dll             ← 独立 IDP 栈（只在“不加载 controller”的宿主里用）
│   ├─ log.dll             ← 纯日志（无主控形态）
│   ├─ compress.dll        ← 纯压缩
│   ├─ config.dll
│   ├─ file.dll
│   └─ single.dll
├─ doc\
│   ├─ docpack.dll         ← doc 文件夹主控（文档 + 渲染 + 转换 + 排序）
│   ├─ doc.dll
│   ├─ markdown.dll
│   ├─ render.dll
│   ├─ convert-html.dll
│   ├─ convert-text.dll
│   ├─ convert-txt2md.dll
│   └─ quicksort.dll
└─ tools\
    ├─ toolspack.dll       ← tools 文件夹主控（CMD + 线程统计 + 可视化 + TMMF/LERF）
    ├─ tmmf.dll
    ├─ lerf.dll
    ├─ cmd.dll
    ├─ threadstat.dll
    └─ visualize.dll
```

控制层（Rust / Python / C# 前端）**只需要认识主控**：

- 启动加载 `app\controller.dll`，后续所有业务只走它的 `app_idp_process` + `idp_pop_json`；
- 需要单独用某个子系统时，才直接加载该文件夹的主控（`core.dll`、`docpack.dll`、`toolspack.dll`、`logmain.dll`）；
- 叶子 DLL 是给“只想用单个功能、不想带主控”的场景，以及主控的静态构建单元。

---

## 2. 依赖

| 项目 | 要求 |
|---|---|
| 操作系统 | Windows 10 / 11 x64（UCRT 自带） |
| 运行库 | 仅 `KERNEL32.dll` + `api-ms-win-crt-*.dll`；**不需要 VC++ Redist、不需要 GraalVM、不需要 Go 运行时** |
| 构建工具 | Go 1.26.7 windows/amd64 + mingw64 gcc（`CGO_ENABLED=1`） |
| 调用方式 | 纯 C ABI，可用 `LoadLibrary/GetProcAddress`、Python `ctypes`、C# `DllImport`、Rust `libloading` |

DLL 内已静态链入 Go 运行时，所以单个 DLL 约 2–3.6 MB；这是正常的，不是调试符号（已 `-s -w`）。

---

## 3. 环境变量与查找顺序

加载器通过环境变量定位 DLL 目录，变量名三者任选其一（优先级从高到低）：

1. `HDLL`
2. `HDLL_DIR`
3. `MD_HDLL`

前端解析 `controller.dll` 的推荐写法：

```
%HDLL%\app\controller.dll
```

叶子 DLL（`cmd.dll`、`render.dll`、`markdown.dll`）需要回调 controller 的 IDP 栈时，按以下顺序查找：

```
%HDLL%\
%HDLL%\app\  %HDLL%\core\  %HDLL%\doc\  %HDLL%\tools\
当前进程 exe 目录
当前工作目录
```

设置示例（PowerShell，永久）：

```powershell
[Environment]::SetEnvironmentVariable('HDLL', 'C:\wpjc\md\dist\HDLL', 'User')
```

---

## 4. 通信规则（强制）

1. **全进程只有一个 IDP 内存双向栈，位于 `controller.dll`。**
   - 请求：`idp_push(type, source, json)` → 压队尾
   - 处理：`app_idp_process()` → 弹队尾最新请求，按 `type` 分发，结果压回队首
   - 结果：`idp_pop_json(buf, size)` → 弹队首
2. **渲染与 CMD 必须走 IDP**：`app_render_ui`、`app_render_html`、`render_*`、`cmd_*` 的跨模块调用不允许用文件、共享内存句柄、函数指针或其它通道。
3. **其余模块用 C 函数接口**（`doc_*`、`config_*`、`file_*`、`tmmf_*`、`lerf_*`、`quicksort_*` 等）。
4. 叶子 DLL **不再各自导出 `idp_*`**，避免出现多个栈；需要 IDP 的叶子（`cmd.dll`、`render.dll`、`markdown.dll`）通过 `idpclient` 用 C 接口调用 `controller.dll` 的栈。
5. `idp.dll` 是“独立宿主”用的备用栈：**加载了 controller 的进程不要再用 idp.dll**，两者互不相通。

### 缓冲区返回值语义（重要）

所有写缓冲区的函数返回 **实际写入的 UTF-8 字节数**，`0` 表示空，`-1` 表示失败（个别函数用 `0` 表示未命中）。

> 与旧 Java 版的差异：Java 返回 `String.length()`（UTF-16 码元数），Rust 前端按 `buf[..n]` 当字节截取，含中文会被截断。Go 版统一返回字节数，这是有意修正。旧调用方若按“字符数”使用返回值，需要改为按字节处理。

缓冲区不足时按 UTF-8 rune 边界截断，不会写出半个汉字。

---

## 5. 各 DLL 说明

### 5.1 app\controller.dll（总主控，42 个导出）

**依赖**：无同级 DLL；IDP、文档、渲染、日志、配置、CMD 全部静态内置。

**职责**：唯一入口。生命周期 + 三段式 IDP + 全部 `app_*` C ABI。

**关键导出**：

| 类别 | 函数 |
|---|---|
| isolate 兼容 | `graal_create_isolate`、`graal_tear_down_isolate`（返回成功，线程指针是伪句柄，Go 侧忽略） |
| 生命周期 | `app_init(thread, baseDir)`、`app_shutdown(thread)`、`app_version` |
| 文档 | `app_new_file`、`app_open_file`、`app_save_file`、`app_save_file_as`、`app_get_content`、`app_get_file_path`、`app_is_dirty`、`app_insert_text`、`app_delete_text`、`app_undo`、`app_redo`、`app_can_undo`、`app_can_redo`、`app_set_cursor`、`app_get_cursor`、`app_get_cursor_line`、`app_get_cursor_col` |
| 渲染 | `app_get_render_instructions`、`app_read_render_cache`、`app_render_html` |
| 状态/设置 | `app_get_status`、`app_get_setting`、`app_set_setting`、`app_save_settings` |
| 日志 | `app_log` |
| 彩蛋命令 | `app_easter_detect`、`app_easter_exec` |
| IDP | `idp_push`、`idp_pop_json`、`idp_pop_idp`、`idp_peek_json`、`idp_stack_size`、`idp_stack_clear`、`app_write_idp`、`app_to_idp`、`app_read_idp`、`app_idp_process` |

**最小用法（C）**：

```c
HMODULE h = LoadLibraryA("C:\\wpjc\\md\\dist\\HDLL\\app\\controller.dll");
void* thr = NULL; void* iso = NULL;
int (*create)(void*, void**, void**) = (int(*)(void*,void**,void**))GetProcAddress(h, "graal_create_isolate");
create(NULL, &iso, &thr);
int (*init)(void*, const char*) = (int(*)(void*,const char*))GetProcAddress(h, "app_init");
init(thr, "C:\\wpjc\\md\\dist\\HDLL");
```

**Python 完整示例**见第 6 节。

**注意**：

- `app_get_status` 的 `cursor_line` / `cursor_col` 从 0 开始。
- `app_get_setting` 未命中返回 `0` 且写入空串（与 Java 行为一致）。
- `app_render_html` 输入是 Markdown 文本，输出 HTML 片段（不是整篇文档）。

### 5.2 core\logmain.dll（日志子系统主控，13 个导出）

**依赖**：无同级 DLL；日志与 zlib 压缩已内置（不需要 `log.dll` / `compress.dll`）。
**职责**：日志的唯一对外入口。初始化时若只有压缩态 `.log.zst`，会自动解压恢复工作态；关闭时自动 zlib 压缩并删除工作态（自压缩）。

**导出**：`logmain_version`、`logmain_init`、`logmain_write`、`logmain_write_end`、`logmain_shutdown`、`logmain_set_enabled`、`logmain_is_enabled`、`logmain_clear_all`、`logmain_size`、`logmain_cache_lookup`、`logmain_cache_put`、`logmain_status`、`logmain_compress_now`。

**日志布局**：

```
<base>\cache\log\YYYY-MM-DD.log       工作态（当日）
<base>\log\YYYY-MM-DD.log.zst         压缩态（关闭后）
```

- 首 4 行固定：`FLF.;utf8` / `icon_mdrenderer` / `MD 编辑器 V1.0.10` / 20 位字节数
- 正文：`<\时间>参数{操作}结果。<时间/>`
- 缓存行：`<\时间>CACHE:op:b64(params):b64(result)。<时间/>`
- 超过 10 MB 时删除旧文件并重建（保留 4 行头）

**用法**：

```python
lib.logmain_init(None, b"C:\\wpjc\\md")
lib.logmain_write(None, b"文件", b"打开", b"成功")
lib.logmain_shutdown(None)          # 触发压缩
```

**注意**：`logmain.dll` 与 `log.dll` 都会写同一套路径，**同一进程/同一 base 只能选一个**，否则会双写。`logmain_status` 返回：

```json
{"work":"...","compressed":"...","size":123,"last_error":""}
```

### 5.3 core\log.dll（纯日志，10 个导出）

**依赖**：无。**职责**：不带主控的日志读写 + 缓存查询。
**导出**：`log_init`、`log_shutdown`、`log_write`、`log_write_end`、`log_set_enabled`、`log_is_enabled`、`log_clear_all`、`log_size`、`log_cache_lookup`、`log_cache_put`。

**用法**：

```python
lib.log_init(None, b"C:\\wpjc\\md")
lib.log_write(None, b"参数", b"操作", b"结果")
n = lib.log_cache_lookup(None, b"render_find", b'{"q":"中文"}', buf, len(buf))  # -1 = 未命中
lib.log_shutdown(None)
```

### 5.4 core\compress.dll（纯压缩，5 个导出）

**依赖**：无（Go 标准库 `compress/zlib`）。**职责**：单文件压缩/解压，不依赖日志。

**导出**：

- `compress_file(src, dst)`：0 成功 / -1 失败
- `compress_decompress_file(src, dst)`
- `compress_string_to_file(content, dst)`
- `compress_file_to_string(src, buf, size)`：解压到文本，返回字节数 / -1
- `compress_ratio(src)`：压缩后百分比（0–100），失败 -1

**注意**：扩展名沿用了历史的 `.zst`，但算法是 **zlib/DEFLATE**，不是 Zstandard。用 Go/Python 的 zlib 即可解开。

### 5.5 core\idp.dll（独立内存栈，6 个导出）

**依赖**：无。**职责**：只给“不加载 controller”的宿主提供一套独立 IDP 栈。

**导出**：`idp_push`、`idp_pop_json`、`idp_pop_idp`、`idp_peek_json`、`idp_stack_size`、`idp_stack_clear`。

**注意**：与 `controller.dll` 的栈**不共享**。已有 controller 的进程禁止使用它，否则请求会进错栈。

### 5.6 core\config.dll（9 个导出）

**依赖**：无。**职责**：`resources\cmd\settings.tmmf` 的读写。

**导出**：`config_init`、`config_load_settings`、`config_save_settings`、`config_get_setting`、`config_set_setting`、`config_has_setting`、`config_remove_setting`、`config_get_thread_mode`、`config_set_thread_mode`。

**语义**：`config_init` 只设置 baseDir 并返回 0（与 Java 一致），要读盘再调 `config_load_settings`（文件不存在返回 -1）。

### 5.7 core\file.dll（7 个导出）

**依赖**：无。**职责**：文本文件读写与路径工具。

**导出**：`file_read`、`file_write`、`file_append`、`file_exists`、`file_delete`、`file_name`、`file_extension`。

### 5.8 core\single.dll（4 个导出）

**依赖**：无。**职责**：基于锁文件的单实例互斥。

**导出**：`single_try_lock`（1 成功 / 0 已有实例）、`single_release`、`single_is_locked`、`single_lock_path`。

### 5.9 core\core.dll（core 主控，41 个导出）

**依赖**：无（静态合并 `idp + compress + log + config + file + single`）。
**职责**：只想“加载一个 DLL 就拿到 core 全部能力”时用它；函数名与叶子 DLL 完全一致。

**注意**：`core.dll` 内部的 IDP 栈仍是独立栈；如果同时加载 `controller.dll`，业务请只走 controller。

### 5.10 doc\docpack.dll（doc 主控，53 个导出）

**依赖**：无（静态合并文档、渲染、Markdown、三种转换、排序）。
**职责**：文档模型 + 渲染 + 转换 + 排序的一站式入口，**另一个 AI 的数学库/文档工具优先接这个**。

包含的导出组：

- `doc_*`（23）：`doc_init`、`doc_new_document`、`doc_open_document`、`doc_save_document`、`doc_save_document_as`、`doc_get_content`、`doc_set_content`、`doc_get_file_path`、`doc_is_dirty`、`doc_set_cursor`、`doc_get_cursor`、`doc_get_cursor_line`、`doc_get_cursor_col`、`doc_insert_text`、`doc_delete_text`、`doc_get_text`、`doc_undo`、`doc_redo`、`doc_can_undo`、`doc_can_redo`、`doc_get_char_count`、`doc_get_word_count`、`doc_get_line_count`
- `render_*` + Markdown（20）：`render_html`、`classify_line`、`count_inline_styles`、`count_lines`、`count_words`、`count_chars`、`render_ui`、`render_line`、`render_line_count`、`render_get_line`、`render_find`、`render_replace`、`render_insert`、`render_paged`、`render_write_idp`、`render_to_idp`、`render_read_idp`、`markdown_write_idp`、`markdown_to_idp`、`markdown_read_idp`
- 转换（6）：`convert_html`、`convert_html_file`、`convert_text`、`convert_text_file`、`convert_txt2md`、`convert_txt2md_file`
- 排序（4）：`quicksort_ints`、`quicksort_strings`、`quicksort_json_by_field`、`quicksort_info`

**分页输出**：`{"view":"paged","pageWidth":W,"pageHeight":H,"pageCount":N,"pages":[{"page":1,"elements":[...],"footer":{"text":"— 1 —"}}]}`；自动折行，CJK 按 2 列。

### 5.11 doc\doc.dll（23 个导出）

**依赖**：无。**职责**：纯文档模型（内容、光标、undo/redo、行列统计）。
**用法**：`doc_set_content` → `doc_insert_text` / `doc_delete_text` → `doc_get_content`；`doc_get_text(start, end, buf, size)` 取区间。

### 5.12 doc\markdown.dll（9 个导出）

**依赖**：`controller.dll`（仅 `markdown_*_idp` 三个函数需要；纯函数不需要）。

**导出**：`render_html`、`classify_line`、`count_inline_styles`、`count_lines`、`count_words`、`count_chars`、`markdown_write_idp`、`markdown_to_idp`、`markdown_read_idp`。

### 5.13 doc\render.dll（11 个导出）

**依赖**：`controller.dll`（仅 `render_*_idp` 需要）。

**导出**：`render_ui`、`render_line`、`render_line_count`、`render_get_line`、`render_find`、`render_replace`、`render_insert`、`render_paged`、`render_write_idp`、`render_to_idp`、`render_read_idp`。

**注意**：`render_find` 等直接返回内部 JSON（`{"count":N,"positions":[...]}`），不带 `{"result":...}` 外壳；走 controller 的 `idp_push("render_find", ...)` 才带外壳。

### 5.14 doc\convert-html.dll / convert-text.dll / convert-txt2md.dll（各 2 个导出）

- `convert_html(md, buf, size)` / `convert_html_file(in, out)`
- `convert_text(md, buf, size)` / `convert_text_file(in, out)`
- `convert_txt2md(text, buf, size)` / `convert_txt2md_file(in, out)`

**依赖**：无。

### 5.15 doc\quicksort.dll（4 个导出）

**依赖**：无。**职责**：给数学库/统计模块用的通用排序。

- `quicksort_ints("3,1,2", "true", buf, size)` → `1,2,3`
- `quicksort_strings("b,a", "true", ...)`
- `quicksort_json_by_field(jsonArray, field, asc, ...)`
- `quicksort_info(...)`

### 5.16 tools\cmd.dll（5 个导出）

**依赖**：`controller.dll`（IDP 三段式）。

**导出**：`cmd_exec`、`cmd_help`、`cmd_write_idp`、`cmd_to_idp`、`cmd_read_idp`。

**规则**：CMD 属于“必须走 IDP”的模块。`cmd_write_idp` 把结果压入 controller 的栈；`cmd_read_idp` 从同一栈弹出。不要用返回值以外的通道传结果。

### 5.17 tools\threadstat.dll（11 个导出）

**依赖**：无。**职责**：线程模式与资源统计。

**导出**：`threadstat_init`、`threadstat_get_mode`、`threadstat_set_mode`、`threadstat_register`、`threadstat_unregister`、`threadstat_sample`、`threadstat_get_json`、`threadstat_memory`、`threadstat_disk_read`、`threadstat_disk_write`、`threadstat_disk`。

### 5.18 tools\visualize.dll（4 个导出）

**依赖**：无。**职责**：把统计 JSON 渲染成 HTML。

**导出**：`visualize_stats_to_file`、`visualize_stats_to_html`、`visualize_stats_sorted_html`、`visualize_thread_modes`。

### 5.19 tools\tmmf.dll（6 个导出）

**依赖**：无。**职责**：TMMF 标签文件。

**导出**：`tmmf_read_all`、`tmmf_read_tag`、`tmmf_search`、`tmmf_list_tags`、`tmmf_line_count`、`tmmf_write_tag`。

格式：前 4 行头（`TMMF.;utf8` / `icon_mdrenderer` / 空格行 / `0` / `…`），正文 `<\题目>内容<题目/>`。

### 5.20 tools\lerf.dll（7 个导出）

**依赖**：无。**职责**：LERF 语言文件。

**导出**：`lerf_load`、`lerf_get`、`lerf_get_from_file`、`lerf_get_multi`、`lerf_list_all`、`lerf_validate`、`lerf_key_count`。

**注意**：`lerf_load` 后 `lerf_get(id)` 才有效；`lerf_get_multi` 的 `ids` 用逗号分隔，返回 JSON 数组。

### 5.21 tools\toolspack.dll（tools 主控，33 个导出）

**依赖**：`controller.dll`（仅 CMD 的 IDP 函数）。
**职责**：CMD + 线程统计 + 可视化 + TMMF/LERF 的合并版，函数名与叶子一致。

---

## 6. Python 调用完整示例（controller + 缓存）

```python
import ctypes, json

HD = r"C:\wpjc\md\dist\HDLL"
lib = ctypes.CDLL(HD + r"\app\controller.dll")

lib.graal_create_isolate.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p), ctypes.POINTER(ctypes.c_void_p)]
iso, thr = ctypes.c_void_p(), ctypes.c_void_p()
assert lib.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(thr)) == 0

lib.app_init.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
assert lib.app_init(thr, b"C:\\wpjc\\md") == 0

lib.idp_push.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p]
lib.app_idp_process.argtypes = [ctypes.c_void_p]
lib.idp_pop_json.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]
lib.idp_pop_json.restype = ctypes.c_int
buf = ctypes.create_string_buffer(1 << 20)

def call(t, payload):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    lib.idp_push(thr, t.encode(), b"python", body)
    lib.app_idp_process(thr)
    n = lib.idp_pop_json(thr, buf, len(buf))       # n = 字节数
    return json.loads(buf.raw[:n].decode("utf-8"))

print(call("app_version", {}))
print(call("app_insert_text", {"pos": 0, "text": "# 标题\n中文内容"}))
content = call("app_get_content", {})["content"]
print(call("render_find", {"content": content, "query": "中文"}))
print(call("render_find", {"content": content, "query": "中文"}))   # 第二次带 "cached": true
```

---

## 7. 渲染/操作结果缓存

`controller.dll` 的 `app_idp_process` 对 **6 类纯操作**启用日志缓存：

```
app_render_html  render_find  render_replace  render_insert  render_paged  visualize_stats_to_html
```

流程：

1. 用 `type + 参数 JSON` 查当日日志里的 `CACHE:` 行；
2. 命中：直接返回上次结果，并在原 JSON 末尾追加 `"cached":true`；
3. 未命中：执行，并把 `op + b64(params) + b64(result)` 写入日志。

因此“同样的渲染/操作第二次几乎零成本”。文档编辑类操作（插入、删除）不进缓存，避免拿到过期结果。

---

## 8. 给另一个 AI 的数学库/文档工具

直接复用这三组，不需要带 controller：

| 需求 | DLL | 函数 |
|---|---|---|
| 排序、TopN、字段排序 | `doc\quicksort.dll` | `quicksort_ints/strings/json_by_field/info` |
| 文本 ↔ Markdown ↔ HTML | `doc\docpack.dll` 或三个 convert 叶子 | `convert_txt2md`、`convert_html`、`convert_text` |
| 文档模型/撤销 | `doc\docpack.dll` | `doc_*` |
| 分页排版 | `doc\docpack.dll` | `render_paged` |

如果数学库要发请求给渲染层，则加载 `app\controller.dll`，用 `idp_push("render_paged", {...}) → app_idp_process → idp_pop_json`，不要直接调叶子 DLL 的 `render_paged`（那是纯函数入口，不经过统一的日志缓存）。

---

## 9. 构建与验证

```powershell
cd C:\wpjc\md\go
python tools\gen_dlls.py                       # 由 engine 生成全部适配层
.\tools\build-dlls.ps1                         # 构建 23 个 DLL 到 build\dll
python build\dll\test_all.py                   # 33 项功能自检
python build\dll\check_exports.py              # 269 个导出逐个核对
$env:HDLL='C:\wpjc\md\dist\HDLL'; python build\dll\test_dist.py   # 部署布局 + 环境变量寻址
```

已验证结果：

- `test_all.py`：33 通过 / 0 失败（含中文、字节数、缓存命中、压缩往返、日志压缩、TMMF/LERF、CMD 跨 DLL IDP）
- `check_exports.py`：23 个 DLL、269 个符号全部可解析
- `test_dist.py`：在非 HDLL 工作目录下，靠 `%HDLL%` 成功跨目录加载

---

## 10. 与 Java 版的差异（迁移必读）

1. **返回值按字节**：旧 Java 返回字符数，Go 返回 UTF-8 字节数；Rust 前端不需要再改。
2. **叶子 DLL 不再带 IDP 栈**：`idp_*` 只在 `controller.dll` 与 `idp.dll` 出现；`cmd/markdown/render` 通过 C 接口回调 controller。
3. **`compress` 算法是 zlib**：扩展名仍是 `.zst`，内容不是 Zstandard。
4. **`graal_create_isolate` 是兼容桩**：返回 0，线程指针非空但无意义，所有 `app_*` 忽略它。
5. **`app_init` 不做单实例拒绝**：单实例由 `single.dll` / 前端自行调用 `single_try_lock` 决定。
6. **`config_init` 只设目录**：要读设置再调 `config_load_settings`。
