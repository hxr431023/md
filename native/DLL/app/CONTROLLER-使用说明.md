# controller.dll — 全量合体主控（控制层唯一入口）使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\app\controller.dll` ｜ 配套头文件：`controller.h`
> 职责：**整个后端的唯一主控**。把全部 19 个模块编入一个 DLL，
> 前端（Python/Rust）只与 controller.dll 打交道：
> ① 通过 `idp_push → app_idp_process → idp_pop_json` 三段式栈通信；
> ② controller 内部按 type 分发到对应模块，返回渲染指令/结果。

---

## 1. 主控与全部模块的关系（19 模块全编）

| 分类 | 编入源码（模块） | 导出前缀 |
|------|------------------|----------|
| core | MDIdp / MDLog / MDCompress / MDConfig / MDFile / MDSingle | `idp_*` `log_*` `compress_*` `config_*` `file_*` `single_*` |
| doc | MDMarkdown / MDDoc / MDRender / MDConvertHtml / MDConvertText / MDConvertTxt2Md / MDQuickSort | `markdown_*` `doc_*` `render_*` `convert_*` `quicksort_*` |
| tools | MDTmmf / MDLerf | `tmmf_*` `lerf_*` |
| app | MDCmd / MDThreadStat / MDVisualize | `cmd_*` `threadstat_*` `visualize_*` |
| 控制层 | MDController | `app_*`（含 `app_idp_process`） |

**组合方式**：controller.dll 编译时把 19 个 Java 源码一并编入（见
`DLL\app\build-mdcontroller.bat`）。加载 controller.dll 后，全部功能导出同时可用；
前端应只使用 `app_*` + `idp_*`，其余为模块直接复用接口。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `controller.dll` 自身（GraalVM 自包含，~12.5 MiB） |
| 开发期头文件 | `controller.h` + `graal_isolate.h` |
| 编译期源码 | 19 模块全部（见 build-mdcontroller.bat 的 -cp） |
| 同目录配套 | `controller_dynamic.h`、`graal_isolate_dynamic.h` |

> 前端部署只需要 controller.dll 一个文件（连同配套头文件）；不需要任何副 DLL。

---

## 3. 三段式 IDP 通信协议（仅渲染 / CMD 使用）

只有**渲染**（`app_render_html` / `app_render_ui` / `render_*`）和 **CMD**（`cmd_*`）
走内存双向栈；其他能力直接调用第 4 节的 `app_*` C 函数，不必经过 IDP。

渲染 / CMD 的调用流程：

```
第 1 步  idp_push(thread, type, source, jsonData)     // 压请求到队尾
第 2 步  app_idp_process(thread)                       // controller 弹出最新请求并分发
第 3 步  idp_pop_json(thread, outBuf, bufSize)         // 取回 "result:<type>" 结果
```

- 结果包 type 为 `result:<原type>`；异常时 `result:error` 且 json 含
  `{"error":"..."}`。
- `app_idp_process` 从队尾（popBack）取**最新**请求，避免多包积压。

---

## 4. 主控 API（app_*，前端使用）

### 4.1 应用级

| 函数 | 说明 |
|------|------|
| `app_init(t, char* baseDir)` | 初始化全部子系统（config/log/file/threadstat/single 等） |
| `app_shutdown(t)` | 收尾（日志压回、锁释放） |
| `app_version(t, buf, size)` | 版本号 |
| `app_new_file(t)` / `app_open_file(t, path)` / `app_save_file(t)` / `app_save_file_as(t, path)` | 文档生命周期 |
| `app_get_content(t, buf, size)` / `app_get_file_path` / `app_is_dirty` | 状态查询 |
| `app_insert_text(t, pos, text)` / `app_delete_text(t, start, end)` | 编辑 |
| `app_undo` / `app_redo` / `app_can_undo` / `app_can_redo` | 撤销重做 |
| `app_set_cursor(t, pos)` / `app_get_cursor` / `app_get_cursor_line` / `app_get_cursor_col` | 光标 |
| `app_get_render_instructions(t, buf, size)` | 渲染指令（前端直接绘制） |
| `app_read_render_cache(t, buf, size)` | 读渲染缓存（复用缓存 txt） |
| `app_render_html(t, md, buf, size)` | MD → HTML 预览 |
| `app_get_status` / `app_get_setting` / `app_set_setting` / `app_save_settings` | 设置 |
| `app_log(t, params, op, result)` | 写日志 |
| `app_easter_detect(t, line)` / `app_easter_exec(t, line, buf, size)` | 彩蛋识别/执行 |

### 4.2 IDP 栈（前端通信）

| 函数 | 说明 |
|------|------|
| `idp_push(t, type, source, jsonData)` | 压请求 |
| `app_idp_process(t)` | 分发最新请求 |
| `idp_pop_json(t, buf, size)` / `idp_pop_idp` / `idp_peek_json` / `idp_stack_size` / `idp_stack_clear` | 取结果/管理栈 |

### 4.3 dispatch 支持的全部 type（走 IDP 时用）

`app_version` / `app_new_file` / `app_open_file{path}` / `app_save_file` /
`app_save_file_as{path}` / `app_get_content` / `app_get_file_path` /
`app_is_dirty` / `app_insert_text{pos,text}` / `app_delete_text{start,end}` /
`app_undo` / `app_redo` / `app_can_undo` / `app_can_redo` /
`app_set_cursor{pos}` / `app_get_cursor` / `app_get_cursor_line` /
`app_get_cursor_col` / `app_render_ui` / `app_render_html{md}` /
`app_get_status` / `app_get_setting{key}` / `app_set_setting{key,value}` /
`app_save_settings` / `app_log{params,op,result}` /
`app_easter_detect{line}` / `app_easter_exec{line}` /
`config_get_thread_mode` / `config_set_thread_mode{mode}` /
`app_get_setting_legacy` / `doc_char_count` / `threadstat_sample` /
`threadstat_get_json` / `visualize_stats_to_html{stats}` /
`log_clear_all` / `single_try_lock` /
`render_find{content,query}` / `render_replace{content,find,replace}` /
`render_insert{content,left,right,insert}` / `render_paged{content,page_width,page_height}`
未知 type 返回 `{"error":"unknown type"}`。

### 4.4 日志驱动缓存（dispatch 自动命中）

对**纯函数操作**，`dispatchInternal` 会先查当日日志缓存，相同操作直接复用结果：

| 可缓存 type | 参数 |
|-------------|------|
| `app_render_html` | `{md}` |
| `render_find` | `{content,query}` |
| `render_replace` | `{content,find,replace}` |
| `render_insert` | `{content,left,right,insert}` |
| `render_paged` | `{content,page_width,page_height}` |
| `visualize_stats_to_html` | `{stats}` |

判定与返回：

- 命中条件：`type` 完全相同 **且** `jsonData` 原始字符串完全相同（字段顺序、空格不同视为不同操作）。
- 命中时返回原结果并追加 `"cached":true`，例如：
  `{"html":"<h1>hi</h1>","cached":true}`；前端只读原字段即可，忽略 `cached` 也兼容。
- 未命中时执行真实计算，并把 `type + jsonData → 结果` 写入当日日志的 `CACHE:` 行。
- 缓存位置：`<baseDir>/cache/log/YYYY-MM-DD.log`，第 5 行起，格式
  `CACHE:<type>:<base64(params)>:<base64(result)>`。
- 失效：跨天（只查当日日志）、`log_clear_all`、日志超过 10 MB 自动重建。
- **不要缓存**的操作（依赖可变状态）：`app_open_file` / `app_save_file` /
  `app_get_content` / `app_get_status` / 编辑/撤销/设置类。

> 另一个 AI / 外部程序也可直接调 `log.dll` 的
> `log_cache_lookup` / `log_cache_put` 使用同一套缓存逻辑（见 `LOG-使用说明.md` 3.2）。

---

## 5. 典型用法（Python ctypes 纯 IDP）

```python
import ctypes
from ctypes import c_char_p, c_int, c_void_p

dll = ctypes.CDLL(r"C:\wpjc\md\native\DLL\app\controller.dll")
P = ctypes.c_void_p
isolate, thread = P(), P()
dll.graal_create_isolate(None, ctypes.byref(isolate), ctypes.byref(thread))

dll.idp_push.argtypes = [P, c_char_p, c_char_p, c_char_p]
dll.idp_push.restype = c_int
dll.app_idp_process.argtypes = [P]
dll.idp_pop_json.argtypes = [P, c_char_p, c_int]
dll.idp_pop_json.restype = c_int

def call(typ, jsondata):
    dll.idp_push(thread, typ.encode(), b"python", jsondata.encode())
    dll.app_idp_process(thread)
    buf = ctypes.create_string_buffer(2 * 1024 * 1024)   # 2 MiB 宽缓冲
    n = dll.idp_pop_json(thread, buf, len(buf))
    return buf.value[:n].decode("utf-8", "replace")

print(call("app_version", "{}"))
print(call("app_render_html", '{"md":"# hi"}'))
dll.app_shutdown(thread)
dll.graal_detach_all_threads_and_tear_down_isolate(thread)
```

---

## 6. Rust 前端（libloading，已重写为纯 IDP）

`rust_frontend\src\ffi.rs` 已整文件重写：只保留
`graal_create_isolate` / `app_init` / `app_shutdown` / `idp_push` /
`app_idp_process` / `idp_pop_json` / `idp_stack_size` 7 个 C 导出绑定，
业务方法统一 `call(type, json) = idp_push → app_idp_process → idp_pop_json`，
自带宽缓冲 BUF_SIZE=2 MiB + 极简 JSON 提取器（不引 serde）。
**渲染/CMD 走 IDP**；其余业务方法应优先改为直接绑定对应 C 函数
（`app_get_content`、`doc_*`、`file_*` 等），不再统一绕 IDP。

---

## 7. 通信规则（本项目最高约束）

1. **渲染**（`render.dll` / `markdown.dll` 的 HTML 渲染、`app_render_*`）与
   **CMD**（`cmd.dll`）：走 `idp_push / app_idp_process / idp_pop_json` 内存双向栈。
2. **其余模块**：一律用直接 C 函数接口（如 `file_read`、`compress_file`、
   `doc_get_content`），同步调用，不经过 IDP。
3. 严禁文件型 IDP、共享句柄、直接函数指针等任何其他通道。
4. controller 内部 19 模块为同 DLL 内静态直调（编译期绑定，非跨 DLL）。
5. 凡不符合以上规则的调用（包括其他 AI 交付的句柄式前端）一律重写。

---

## 8. 注意事项

1. 先 `app_init` 再业务调用，退出前 `app_shutdown`（自动压回日志、释放锁）。
2. `app_idp_process` 一次处理一个最新请求；并发场景请串行调用。
3. 结果缓冲区建议 ≥ 2 MiB（渲染指令可能较大）。
4. 彩蛋前缀为 `\\>`（两个反斜杠 + **右**尖括号，CMD 样式）；命令交给
   cmd 模块执行，渲染替换为结果、保存保留原文可二次执行。