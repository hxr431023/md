# MDRenderer DLL 交付总索引

> 版本：V1.0.10 ｜ 根目录：`C:\wpjc\md\native\DLL\`
> 本索引面向直接使用这些 DLL 的外部程序 / 其他 AI。
> 每个 DLL 的详细说明见同目录 `*-使用说明.md`。

---

## 0. 先看这三条（否则一定调不通）

1. **每个 DLL 都是 GraalVM isolate 型 DLL**。加载后必须先调用
   `graal_create_isolate(NULL, &isolate, &thread)`，之后所有函数第一个参数
   传 `thread`。退出前调用
   `graal_detach_all_threads_and_tear_down_isolate(thread)`。
2. **每个 DLL 运行时自包含**（GraalVM 运行时 + 所需模块已编入），
   不依赖任何其他 DLL 文件；开发期只需要对应 `.h` 和 `graal_isolate.h`。
3. **通信方式按场景区分**：
   - **渲染**（`render.dll`、`markdown.dll` 的 HTML 渲染）和 **CMD**（`cmd.dll`）：
     用内存双向栈 IDP —— `idp_push(type, source, jsonData)` → `app_idp_process()` →
     `idp_pop_json(outBuf, size)`，用于同时运行/异步交互。
   - **其余模块**：直接用 **C 函数接口**（如 `file_read`、`compress_file`、
     `tmmf_search`、`doc_get_content`），同步调用，不经过 IDP。
   - 各模块的 IDP 栈互相独立；只有渲染/CMD/controller 保留 IDP 栈。

---

## 1. 全部 DLL 一览（22 个）

### `app\`（应用级 / 控制层）

| DLL | 职责 | 编译期依赖 | 文档 |
|-----|------|-----------|------|
| `controller.dll` | **全量主控**：19 模块合体、`app_idp_process` 分发、渲染指令 | 19 模块全部 | `CONTROLLER-使用说明.md` |
| `cmd.dll` | 执行 CMD 命令（彩蛋 `\\>` 前缀） | MDCmd + MDIdp | `CMD-使用说明.md` |
| `threadstat.dll` | 线程/模块 CPU、内存、磁盘统计 | MDThreadStat + MDIdp | `THREADSTAT-使用说明.md` |
| `visualize.dll` | 统计 JSON → 可视化 HTML（可保存快照） | MDVisualize + MDQuickSort + MDIdp | `VISUALIZE-使用说明.md` |

### `core\`（基础能力）

| DLL | 职责 | 编译期依赖 | 文档 |
|-----|------|-----------|------|
| `core.dll` | **core 主控**：6 模块合体 | MDIdp+MDLog+MDCompress+MDConfig+MDFile+MDSingle | `CORE-使用说明.md` |
| `idp.dll` | 内存双向栈（渲染 / CMD 的通信通道；其他模块走 C 函数） | MDIdp | `IDP-使用说明.md` |
| `log.dll` | 日志主控（自压缩，FLF 格式，启动解压/退出压回） | MDLog + MDCompress + MDIdp | `LOG-使用说明.md` |
| `compress.dll` | 纯压缩/解压（DEFLATE 快速档，`.zst`） | MDCompress + MDIdp | `COMPRESS-使用说明.md` |
| `config.dll` | 键值设置 + 线程模式（TMMF 存储） | MDConfig + MDIdp | `CONFIG-使用说明.md` |
| `file.dll` | 文件读写/追加/大小/删除/文件名 | MDFile + MDIdp | `FILE-使用说明.md` |
| `single.dll` | 单实例互锁（"程序已在运行"） | MDSingle + MDIdp | `SINGLE-使用说明.md` |

### `doc\`（文档与转换）

| DLL | 职责 | 编译期依赖 | 文档 |
|-----|------|-----------|------|
| `docpack.dll` | **doc 主控**：8 模块合体 | MDIdp+MDMarkdown+MDDoc+MDRender+MDConvertHtml+MDConvertText+MDConvertTxt2Md+MDQuickSort | `DOCPACK-使用说明.md` |
| `markdown.dll` | MD 解析：行分类、统计、MD→HTML | MDMarkdown + MDIdp | `MARKDOWN-使用说明.md` |
| `doc.dll` | 文档模型：打开/保存/光标/编辑/撤销 | MDDoc + MDIdp | `DOC-使用说明.md` |
| `render.dll` | 渲染指令、查找/替换/插入、分页（自动页码） | MDRender + MDMarkdown + MDIdp | `RENDER-使用说明.md` |
| `convert-html.dll` | MD → HTML+CSS（完整文档） | MDConvertHtml + MDIdp | `CONVERT-HTML-使用说明.md` |
| `convert-text.dll` | MD → 纯文本 | MDConvertText + MDIdp | `CONVERT-TEXT-使用说明.md` |
| `convert-txt2md.dll` | 纯文本 → MD | MDConvertTxt2Md + MDIdp | `CONVERT-TXT2MD-使用说明.md` |
| `quicksort.dll` | 快排：整数 / 字符串 / JSON 按字段 | MDQuickSort + MDIdp | `QUICKSORT-使用说明.md` |

### `tools\`（配置格式）

| DLL | 职责 | 编译期依赖 | 文档 |
|-----|------|-----------|------|
| `toolspack.dll` | **tools 主控**：tmmf + lerf 合体 | MDIdp+MDTmmf+MDLerf | `TOOLSPACK-使用说明.md` |
| `tmmf.dll` | TMMF：一行多搜索词 ↔ 多结果 | MDTmmf + MDIdp | `TMMF-使用说明.md` |
| `lerf.dll` | LERF：`#` 注释 / `=` 键值 / `{0}` 占位 | MDLerf + MDIdp | `LERF-使用说明.md` |

---

## 2. 依赖怎么带（"要有才附带它"）

| 你要用的功能 | 最少需要带 |
|--------------|-----------|
| 只压缩 | `compress.dll` |
| 只排序 | `quicksort.dll` |
| 只读 TMMF | `tmmf.dll` |
| 只读 LERF | `lerf.dll` |
| 只做 MD→HTML | `convert-html.dll` |
| 只做 MD→文本 | `convert-text.dll` |
| 只做文本→MD | `convert-txt2md.dll` |
| 只做 MD 解析/统计 | `markdown.dll` |
| 只做文档编辑模型 | `doc.dll` |
| 日志（含自动压缩） | `log.dll`（压缩已内嵌，**不要**再带 compress.dll） |
| 渲染 + 分页/查找替换 | `render.dll`（markdown 已内嵌，**不要**再带 markdown.dll） |
| 线程统计可视化 | `visualize.dll`（排序已内嵌，**不要**再带 quicksort.dll） |
| core 全部基础能力 | `core.dll` |
| doc 全部文档能力 | `docpack.dll` |
| 前端只需要一个入口 | `controller.dll` |

> 关键：合体主控（`core/docpack/toolspack/controller`）已把副 DLL 源码编入自身，
> **只需带主控一个文件**；不要再额外带这些副 DLL（重复但各自栈独立，不互通）。

---

## 3. 最小调用模板（Python ctypes，可直接复制）

```python
import ctypes
from ctypes import c_char_p, c_int, c_void_p

DLL = r"C:\wpjc\md\native\DLL\app\controller.dll"
dll = ctypes.CDLL(DLL)
P = ctypes.c_void_p

# 1) 创建 isolate
dll.graal_create_isolate.restype = c_int
dll.graal_create_isolate.argtypes = [c_void_p, ctypes.POINTER(P), ctypes.POINTER(P)]
isolate, thread = P(), P()
dll.graal_create_isolate(None, ctypes.byref(isolate), ctypes.byref(thread))

# 2) 初始化
dll.app_init.argtypes = [P, c_char_p]
dll.app_init.restype = c_int
dll.app_init(thread, b"C:/app/data")

# 3) 三段式 IDP
dll.idp_push.argtypes = [P, c_char_p, c_char_p, c_char_p]
dll.idp_push.restype = c_int
dll.app_idp_process.argtypes = [P]
dll.app_idp_process.restype = c_int
dll.idp_pop_json.argtypes = [P, c_char_p, c_int]
dll.idp_pop_json.restype = c_int

def call(typ, jsondata="{}"):
    dll.idp_push(thread, typ.encode(), b"myapp", jsondata.encode())
    dll.app_idp_process(thread)
    buf = ctypes.create_string_buffer(2 * 1024 * 1024)   # 建议 2 MiB
    n = dll.idp_pop_json(thread, buf, len(buf))
    return buf.value[:n].decode("utf-8", "replace")

print(call("app_version"))
print(call("app_render_html", '{"md":"# hi"}'))
print(call("render_find", '{"content":"ab c ab","query":"ab"}'))

# 4) 收尾
dll.app_shutdown.argtypes = [P]
dll.app_shutdown(thread)
dll.graal_detach_all_threads_and_tear_down_isolate.argtypes = [P]
dll.graal_detach_all_threads_and_tear_down_isolate(thread)
```

---

## 4. 已验证结果（2026-11-06 实测，脚本 `native\selftest_dlls.py`）

| 项目 | 结果 |
|------|------|
| 22 个 DLL 全部加载 + 创建 isolate | 22/22 通过 |
| controller 直调 `app_version` | `1.0.10` |
| IDP `app_render_html` | `{"html":"<h1>hi</h1>\n"}` |
| IDP `app_version` | `{"version":"1.0.10"}` |
| IDP `render_find` | `{"result":{"count":2,"positions":[0,5]}}` |
| 副 DLL 独立：compress 压缩/解压往返 | `hello world` 无损 |
| 副 DLL 独立：file 写/读 | 3 字节一致 |
| 副 DLL 独立：quicksort / tmmf / markdown | 均返回正常 |
| 总计 | **PASS=62 FAIL=0** |

复跑命令：

```powershell
python C:\wpjc\md\native\selftest_dlls.py
```

---

## 5. 给数学库项目的复用建议

按需只带单个副 DLL，避免把 11–12 MB 的主控拖进来：

| 数学库需要 | 建议 DLL | 关键导出 |
|-----------|----------|----------|
| 排序 / Top-N | `quicksort.dll` | `quicksort_ints` / `quicksort_strings` / `quicksort_json_by_field` |
| 结果压缩归档 | `compress.dll` | `compress_file` / `compress_decompress_file` / `compress_string_to_file` |
| 配置持久化 | `config.dll` | `config_get_setting` / `config_set_setting` / `config_save_settings` |
| 文件读写 | `file.dll` | `file_read` / `file_write` / `file_append` |
| 模块间同步调用 | 各模块自身的 C 函数 | 直接调用，不经 IDP（IDP 仅渲染/CMD 使用） |
| 单实例互锁 | `single.dll` | `single_try_lock` / `single_release` |

> 注意：除渲染（`render.dll`、`markdown.dll`）和 `cmd.dll` 外，其余模块都直接用
> C 函数接口，同一进程内可直接调用，无需经过 IDP 栈。

---

## 6. 目录约定

```
C:\wpjc\md\native\DLL\
├── app\     controller.dll  cmd.dll  threadstat.dll  visualize.dll   (+ 同名 .h、*-使用说明.md)
├── core\    core.dll  idp.dll  log.dll  compress.dll  config.dll  file.dll  single.dll
├── doc\     docpack.dll  markdown.dll  doc.dll  render.dll
│            convert-html.dll  convert-text.dll  convert-txt2md.dll  quicksort.dll
└── tools\   toolspack.dll  tmmf.dll  lerf.dll
```

每个 DLL 旁边都有：同名 `.h`（精确 C 签名）、`*-使用说明.md`（依赖清单 + 用法）。

---

## 7. 安装包（已验证编译）

| 项 | 值 |
|----|----|
| 产物 | `C:\wpjc\md\installer\MDRenderer-Setup.exe` |
| 大小 | 15,023,211 B（14.3 MB） |
| 版本 | MDRenderer 1.0.10 |
| SHA256 | `B4E8A37909E4EC083AAF83D0E8CB996B52592D36DEA3C315111C0BF371BD1E4C` |
| 打包时间 | 2026-11-8 15:16:49（C 函数接口版：仅渲染/CMD 保留 IDP） |
| 编译器 | Inno Setup 6.7.3（本机 `installer\innosetup-nuget\tools\ISCC.exe`） |
| 安装模式 | 按用户安装（`PrivilegesRequired=lowest`，无需管理员/UAC） |
| 安装位置 | `%LOCALAPPDATA%\Programs\MDRenderer` |
| 打包内容 | `pyfrontend\` 全部（Python 3.11 + controller.dll + md_editor.pyd + tcl/Lib/DLLs） |
| 文件关联 | 可选勾选 `.md` 关联（HKCR，lowest 模式自动写入当前用户） |
| 卸载数据 | 清理 `{userappdata}\.mdrenderer` |

静默安装：

```powershell
C:\wpjc\md\installer\MDRenderer-Setup.exe /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP-
```

卸载：控制面板 → 程序和功能 → MDRenderer，或运行安装目录下 `unins000.exe`。

> 说明：若需要装到 `Program Files`（全机安装），把 `md_setup.iss` 的
> `PrivilegesRequired=lowest` 改回 `admin` 后重新编译即可（会重新出现 per-user 警告，
> 但功能正常）。

---

## 8. 日志驱动缓存协议（外部程序 / 另一个 AI 也可直接复用）

**思路**：把纯函数操作的「参数 + 结果」写进当日日志；下次相同操作先查日志，
命中就直接返回结果，不再重复计算。

### 8.1 两个使用入口

| 入口 | 适用 |
|------|------|
| `controller.dll` 的 IDP | 走 `app_idp_process` 的 6 个可缓存 type，**自动**命中并返回 `"cached":true` |
| `log.dll` 的 `log_cache_lookup` / `log_cache_put` | 外部程序（如数学库）自己实现同一套缓存逻辑 |

可缓存 type（controller 自动）：`app_render_html`、`render_find`、
`render_replace`、`render_insert`、`render_paged`、`visualize_stats_to_html`。

### 8.2 缓存行格式（写在当日 FLF 日志第 5 行起）

```
<\2026-11-06 10:12:21>CACHE:app_render_html:<base64(params)>:<base64(result)>。<2026-11-06 10:12:21/>
```

- `params` / `result` 一律 Base64，避免 JSON 的 `{}`、换行、`。<\` 破坏解析；
- 匹配：`op` 相同 + `params` 的 Base64 相同；逆序扫描取最近一条；
- 只查当日工作态日志；跨天 / `log_clear_all` / 超过 10 MB 重建即失效。

### 8.3 log.dll 直调示例（Python ctypes）

```python
import ctypes
from ctypes import c_char_p, c_int, c_void_p

P = ctypes.c_void_p
d = ctypes.CDLL(r"C:\wpjc\md\native\DLL\core\log.dll")
iso, th = P(), P()
d.graal_create_isolate.restype = c_int
d.graal_create_isolate.argtypes = [c_void_p, ctypes.POINTER(P), ctypes.POINTER(P)]
d.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(th))
d.log_init.argtypes = [P, c_char_p]; d.log_init.restype = c_int
d.log_init(th, b"C:/math/data")

d.log_cache_lookup.argtypes = [P, c_char_p, c_char_p, c_char_p, c_int]
d.log_cache_lookup.restype = c_int
d.log_cache_put.argtypes = [P, c_char_p, c_char_p, c_char_p]
d.log_cache_put.restype = c_int

buf = ctypes.create_string_buffer(65536)
op, params = b"matrix_solve", b'{"a":[[1,2],[3,4]],"b":[5,6]}'
n = d.log_cache_lookup(th, op, params, buf, 65536)
if n < 0:                                  # 未命中：算一次并写缓存
    result = b'{"x":[-1.0,2.0]}'
    d.log_cache_put(th, op, params, result)
else:
    result = buf.value[:n]
print(result)
```

### 8.4 注意事项（给另一个 AI）

1. **参数必须逐字节一致**：JSON 字段顺序或空格变化都会被视为不同操作，建议
   固定序列化（如 Python `json.dumps(obj, sort_keys=True, separators=(",",":"))`）。
2. 缓存只对**纯函数**有意义；依赖文件、时间、随机数、外部状态的操作不要缓存。
3. 结果过大（如整篇 HTML）会撑大日志；日志上限 10 MB，超限自动重建并清缓存。
4. 缓存与日志同生命周期：`log_shutdown` 会压缩回 `.zst`，下次 `log_init` 解压后仍可命中
   （仅限同一天）。


