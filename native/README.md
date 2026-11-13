# MDRenderer 原生 DLL 架构说明（19 模块细粒度拆分）

## 整体架构

```
┌──────────────────────────────────────────────────────────────┐
│              Python 前端（View）                               │
│         Tkinter / PyQt 图形界面 + ctypes                       │
│         只做两件事：① 调用 app_* 函数  ② 照指令渲染         │
└────────────────────────────┬─────────────────────────────────┘
                             │ ctypes 加载 controller.dll
                             │ 调用 app_* 系列函数
┌────────────────────────────▼─────────────────────────────────┐
│              controller.dll（应用控制器，编排全部模块）        │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  MDController（应用控制器）                              │ │
│  │  · 前端唯一入口，编排所有操作（app_*）                  │ │
│  │  · 单实例互锁（MDSingle）                                │ │
│  │  · 文件操作（MDDoc + MDFile + MDLog）                    │ │
│  │  · 渲染指令（MDRender → MDMarkdown）                     │ │
│  │  · 渲染缓存（MDIdp 写入 .idp 文件）                      │ │
│  │  · 状态信息（MDDoc 汇总）                                │ │
│  │  · 设置/日志（MDConfig / MDLog）                         │ │
│  └─────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
         │          │          │          │          │
    ┌────▼───┐ ┌───▼────┐ ┌──▼─────┐ ┌──▼─────┐ ┌──▼──────┐
    │ 可独立复用的基础模块（每个可单独编译为 DLL，跨项目复用）  │
    └────────┴────────┴────────┴────────┴────────┴──────────┘

    按职责分组：
    core  组（6）：idp  log  compress  config  file  single
    doc   组（7）：markdown  doc  render  convert-html  convert-text  convert-txt2md  quicksort
    tools 组（2）：tmmf  lerf
    应用层（4）：controller（编排）  cmd（命令行/彩蛋）  threadstat（线程统计）  visualize（可视化）
```

**核心原则：Python 只加载一个 DLL（controller.dll），只调用 `app_*` 函数。其余模块均可独立编译复用，controller 编排它们。**

## 模块清单（19 个，按分组排列）

| 分组 | 模块 | DLL名 | 职责 | 可独立复用 | 依赖 |
|------|------|-------|------|-----------|------|
| core | **idp** | idp.dll | IDP 接口数据包格式（XML 头 + JSON 数据），文件后缀 .idp | ✅ 任何应用 | 无 |
| core | **log** | log.dll | FLF 日志系统 + 压缩（启动解压到 cache/，关闭压回 log/） | ✅ 任何应用 | compress |
| core | **compress** | compress.dll | DEFLATE/Zstd 风格快速压缩（文件/字符串/字节数组） | ✅ 任何应用 | 无 |
| core | **config** | config.dll | TMMF 标签、LERF 语言资源、Java Properties 设置管理 | ✅ 任何应用 | 无 |
| core | **file** | file.dll | 纯文件 I/O（读/写/追加/存在/大小/删除/文件名/扩展名） | ✅ 任何应用 | 无 |
| core | **single** | single.dll | 单实例互锁（FileLock，重复开启检测，OS 自动释放） | ✅ 任何应用 | 无 |
| doc | **markdown** | markdown.dll | Markdown→HTML、逐行样式分类、内联区间分析、文本统计 | ✅ 任何MD工具 | 无 |
| doc | **doc** | doc.dll | 文档模型（内容/路径/脏标记、撤销重做500步、光标行/列、插入删除、统计） | ✅ 任何文本编辑器 | 无 |
| doc | **render** | render.dll | 渲染指令生成（全文→JSON 逐行样式+内联区间，供前端照画） | ✅ 任何MD编辑器 | markdown |
| doc | **convert-html** | convert-html.dll | Markdown→完整 HTML 文档（含 <!DOCTYPE>/CSS），文件转换 | ✅ 任何MD工具 | 无 |
| doc | **convert-text** | convert-text.dll | Markdown→纯文本（去除全部 MD 标记），文件转换 | ✅ 任何MD工具 | 无 |
| doc | **convert-txt2md** | convert-txt2md.dll | 纯文本→Markdown（识别标题/列表/引用），文件转换 | ✅ 任何MD工具 | 无 |
| doc | **quicksort** | quicksort.dll | 通用快速排序（整数/字符串/JSON 字段，升/降序，返回 JSON） | ✅ 任何应用 | 无 |
| tools | **tmmf** | tmmf.dll | TMMF 标签文件（读/搜索/列标签/写标签） | ✅ 任何应用 | 无 |
| tools | **lerf** | lerf.dll | LERF 语言资源文件（#注释、=键值、{0}占位，第2-3行程序标识区） | ✅ 任何应用 | 无 |
| 应用 | **cmd** | cmd.dll | 命令行/彩蛋命令执行（如 `\`+路径 命令） | ✅ 可复用 | 各模块 |
| 应用 | **controller** | controller.dll | 应用控制器（编排全部模块，前端唯一入口，app_* 导出） | ❌ 应用相关 | 全部18个 |
| 应用 | **threadstat** | threadstat.dll | 线程/内存/磁盘统计 + 线程模式（folder/single/per_dll），输出 JSON | ✅ 可复用 | 无 |
| 应用 | **visualize** | visualize.dll | 统计 JSON→自包含 HTML 可视化报告（CPU/内存/I/O），离线可用 | ✅ 可复用 | quicksort |

## 目录结构

```
native/
├── build-modules.bat            # ★ 统一入口：转发 build-mdcontroller.bat → dist/controller.dll
├── python_client_demo.py        # 已实测通过的 Python ctypes 调用示例
├── dist/                        # 统一 DLL 产物（controller.dll + .h/.lib）
├── modules/                     # 19 个模块，每个含 src/*.java + build-<模块>.bat
└── DLL/                         # 分组产物目录
    ├── core/   build-mdcore.bat     # core 组各 DLL 打包
    ├── doc/    build-mddocpack.bat  # doc 组各 DLL 打包
    └── tools/  build-mdtoolspack.bat# tools 组各 DLL 打包
```

> 旧三层 `backend/` `control/` `controller/` 已被 modules 细粒度拆分取代，已移除。

## 编译前提

- GraalVM JDK（含 native-image），当前路径：`C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1`
- `native-image --shared` 链接 DLL 需要 MSVC `cl.exe`（本机 `C:\vs\vs`）。

## 编译命令

```bat
:: ★ 统一编译：全部 19 模块 → dist\controller.dll（前端只需这一个）
::   build-modules.bat 只是统一入口，真正构建逻辑（单一权威）
::   在 modules\mdcontroller\build-mdcontroller.bat
cd native
build-modules.bat

:: 单个模块独立编译（每个模块自带 build-<模块>.bat，产物在 DLL\<分组>\）
cd native\modules\doc
build-doc.bat

:: 分组打包（core/doc/tools 各出一个 DLL）
cd native\DLL\core  && build-mdcore.bat
cd native\DLL\doc   && build-mddocpack.bat
cd native\DLL\tools && build-mdtoolspack.bat
```

> native-image 语法要点（本机 GraalVM 25.3.4.1 实测）：用 `--shared` 而非 `-H:Kind`（该选项已移除）；**不要**在命令末尾放类名（会被当 main class 要求 `main()`），也不要传 `-H:Class`（`@CEntryPoint` 自动发现）；`-H:Name` 需配 `-H:+UnlockExperimentalVMOptions`；调用 native-image 必须加 `call`（它是 .cmd batch，否则控制权不返回）。

## C ABI 函数清单

> **所有导出函数的 C 签名第一个参数都是 `IsolateThread`（GraalVM isolate 线程句柄），Python 必须显式传入。以下表格的参数列均省略该首参。**
>
> **★ 完整函数签名见 `dist/controller.h`（编译自动生成，最权威）。**

### app_*（应用控制器，前端唯一入口）

#### 应用生命周期
| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `app_init` | 初始化整个应用 | `(base_dir)` | `int` 0成功/-1失败 |
| `app_shutdown` | 关闭应用，写退出日志 | `()` | `void` |
| `app_version` | 版本号 | `(out_buf, buf_size)` | `int` 字符数 |

#### 文件操作
| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `app_new_file` | 新建文件 | `()` | `int` 0 |
| `app_open_file` | 打开文件 | `(path)` | `int` 字符数/-1 |
| `app_save_file` | 保存文件 | `()` | `int` 0成功/-1失败/-2无路径 |
| `app_save_file_as` | 另存为 | `(path)` | `int` 0/-1 |

#### 内容与状态
| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `app_get_content` | 获取文档全文 | `(out_buf, buf_size)` | `int` 字符数 |
| `app_get_file_path` | 当前文件路径 | `(out_buf, buf_size)` | `int` 长度 |
| `app_is_dirty` | 有未保存修改？ | `()` | `int` 1/0 |

#### 编辑操作
| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `app_insert_text` | 插入文本 | `(pos, text)` | `int` 0 |
| `app_delete_text` | 删除文本 | `(start, end)` | `int` 0 |
| `app_undo` | 撤销 | `()` | `int` 0/-1 |
| `app_redo` | 重做 | `()` | `int` 0/-1 |
| `app_can_undo` | 可撤销？ | `()` | `int` 1/0 |
| `app_can_redo` | 可重做？ | `()` | `int` 1/0 |

#### 光标
| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `app_set_cursor` | 设置光标位置 | `(pos)` | `void` |
| `app_get_cursor` | 获取光标位置 | `()` | `int` |
| `app_get_cursor_line` | 光标行号 | `()` | `int` |
| `app_get_cursor_col` | 光标列号 | `()` | `int` |

#### 渲染指令（控制层核心：分析全文，输出样式指令，Python 照画）
| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `app_get_render_instructions` | 获取全文渲染指令（JSON） | `(out_buf, buf_size)` | `int` JSON长度 |
| `app_read_render_cache` | 读取渲染缓存（.idp） | `(out_buf, buf_size)` | `int` 长度 |
| `app_render_html` | Markdown→HTML（导出用） | `(markdown, out_buf, buf_size)` | `int` HTML长度 |

**渲染指令 JSON 格式：**
```json
{
  "lines": [
    {"n":0, "type":"h1", "s":0, "e":12, "inline":[{"s":2,"e":7,"st":"bold"}]},
    {"n":1, "type":"empty", "s":13, "e":13, "inline":[]},
    {"n":2, "type":"paragraph", "s":14, "e":40, "inline":[{"s":17,"e":23,"st":"bold"}]}
  ]
}
```
- `type`：`h1`/`h2`/`h3`/`paragraph`/`quote`/`list`/`ordered`/`code`/`empty`
- `s`/`e`：字符偏移（相对于全文）
- `inline.st`：`bold`/`italic`/`code`/`strike`/`bolditalic`

#### 状态信息（一次调用更新整个状态栏+菜单）
| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `app_get_status` | 获取全量状态（JSON） | `(out_buf, buf_size)` | `int` JSON长度 |

**状态 JSON 格式：**
```json
{
  "file_name":"test.md", "file_path":"C:/.../test.md", "dirty":true,
  "char_count":1024, "word_count":150, "line_count":30,
  "cursor_pos":100, "cursor_line":5, "cursor_col":10,
  "can_undo":true, "can_redo":false
}
```

#### 设置 / 日志
| 函数名 | 功能 | 参数 | 返回值 |
|--------|------|------|--------|
| `app_get_setting` | 读设置值 | `(key, out_buf, buf_size)` | `int` 值长度 |
| `app_set_setting` | 写设置值 | `(key, value)` | `int` 0 |
| `app_save_settings` | 保存设置到磁盘 | `()` | `int` 0 |
| `app_log` | 写 FLF 日志 | `(params, operation, result)` | `void` |

### 各模块导出前缀（均编译进 controller.dll）

| 前缀 | 覆盖函数（示例） | 说明 |
|------|---------|------|
| `app_*` | init / shutdown / version / new_file / open_file / save_file(_as) / get_content / get_file_path / is_dirty / insert_text / delete_text / undo / redo / can_undo / can_redo / set_cursor / get_cursor(_line/_col) / get_render_instructions / read_render_cache / render_html / get_status / get_setting / set_setting / save_settings / log | 应用控制器（前端唯一入口） |
| `cmd_*` | exec / help / write_idp / to_idp / read_idp | 命令行/彩蛋 |
| `compress_*` | file / decompress_file / string_to_file / file_to_string / ratio / write_idp / to_idp / read_idp | 压缩解压 |
| `config_*` | init / load_settings / save_settings / get_setting / set_setting / has_setting / remove_setting / write_idp / to_idp / read_idp | 设置管理 |
| `convert_html_*` | file / write_idp / to_idp / read_idp | Markdown→HTML |
| `convert_text_*` | file / write_idp / to_idp / read_idp | Markdown→纯文本 |
| `convert_txt2md_*` | file / write_idp / to_idp / read_idp | 纯文本→Markdown |
| `doc_*` | init / new_document / open_document / save_document(_as) / get_content / set_content / get_file_path / is_dirty / cursor / insert/delete/get_text / undo / redo / can_undo / can_redo / char/word/line count / write_idp / to_idp / read_idp | 文档模型 |
| `file_*` | read / write / append / exists / size / delete / name / extension / write_idp / to_idp / read_idp | 文件I/O |
| `idp_*` | 读/写 IDP 数据包 | IDP 数据包 |
| `lerf_*` | load / get / get_from_file / get_multi / list_all / validate / key_count / write_idp / to_idp / read_idp | LERF 语言资源 |
| `log_*` | init / shutdown / write / write_end / set_enabled / is_enabled / clear_all / size / write_idp / to_idp / read_idp | FLF 日志 |
| `markdown_*` | render_html / classify_line / count_inline_styles / count_lines / count_words / count_chars / write_idp / to_idp / read_idp | Markdown 渲染 |
| `quicksort_*` | sort / sort_asc / sort_desc / sort_by_field / to_idp / read_idp | 排序 |
| `render_*` | from_text / line / line_count / get_line / write_idp / to_idp / read_idp | 渲染指令 |
| `single_*` | try_lock / release / is_locked / lock_path / write_idp / to_idp / read_idp | 单实例 |
| `threadstat_*` | 线程/内存/磁盘统计 + 线程模式 | 性能统计 |
| `tmmf_*` | read_all / read_tag / search / list_tags / line_count / write_tag / write_idp / to_idp / read_idp | TMMF 标签 |
| `visualize_*` | 统计 JSON→HTML 报告 | 可视化 |

> 完整签名以 `dist/controller.h` 为准（含 `graal_create_isolate` / `graal_teardown_isolate` 生命周期函数）。

## Python ctypes 调用示例

> `python_client_demo.py`（native/ 下）已实测通过：加载 → `graal_create_isolate` → `app_init` → 新建/插入/取内容 → `app_render_html`（含中文 UTF-8）→ `convert_text`。

```python
import ctypes, os

DLL = r"C:\wpjc\md\native\dist\controller.dll"
app = ctypes.CDLL(DLL)

# ── 隔离线程句柄（--shared 下每个函数首参都是它）──
app.graal_create_isolate.restype = ctypes.c_void_p
app.graal_create_isolate.argtypes = [ctypes.c_void_p,
                                     ctypes.POINTER(ctypes.c_void_p),
                                     ctypes.POINTER(ctypes.c_void_p)]
iso = ctypes.c_void_p(); th = ctypes.c_void_p()
t = app.graal_create_isolate(None, ctypes.byref(iso), ctypes.byref(th))
thread = th.value or t  # 不透明指针，避免 64 位截断

def declare(name, restype, *argtypes):
    f = getattr(app, name); f.argtypes = (ctypes.c_void_p,) + argtypes; f.restype = restype
    return f

def read_str(fn, *args, size=65536):
    buf = ctypes.create_string_buffer(size)
    n = fn(thread, *args, buf, size)
    if n > size:
        buf = ctypes.create_string_buffer(n + 1); n = fn(thread, *args, buf, n + 1)
    return buf.raw[:n if n >= 0 else 0].decode("utf-8", "replace")

md_init    = declare("app_init", ctypes.c_int, ctypes.c_char_p)
md_insert  = declare("app_insert_text", ctypes.c_int, ctypes.c_int, ctypes.c_char_p)
md_render  = declare("app_render_html", ctypes.c_int, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_int)
md_content = declare("app_get_content", ctypes.c_int, ctypes.c_char_p, ctypes.c_int)
md_version = declare("app_version", ctypes.c_int, ctypes.c_char_p, ctypes.c_int)
md_shutdown = declare("app_shutdown", None)

md_init(thread, b"C:/wpjc/md")
md_insert(thread, 0, "# 标题\n\n**粗体**".encode("utf-8"))
print(read_str(md_content))          # 全文
print(read_str(md_render, b"# 标题")) # <h1>标题</h1> ...
print(read_str(md_version))          # 1.0.10
md_shutdown(thread)
```

## 注意事项

1. **字符串编码**：所有 C 字符串使用 UTF-8，Python 端 `.encode("utf-8")` / `.decode("utf-8")`。
2. **缓冲区模式**：返回字符串的函数采用"调用者分配缓冲区"（`out_buf` + `buf_size`），避免跨语言内存管理。
3. **返回长度语义**：缓冲区函数返回**未截断的完整长度**。若返回值 > `buf_size`，输出被截断，应扩容后重读；绝不能按返回长度直接读 `out_buf`（会越界）。示例 `read_str` 已处理。
4. **IsolateThread 首参**：每个导出函数第一个参数都是 isolate 线程句柄，Python 必须先 `graal_create_isolate`，结束时 `graal_teardown_isolate`。忘记传 thread 会导致崩溃。
5. **前端只加载一个 DLL**：`controller.dll` 已包含全部 19 模块，Python 不需要加载其他 DLL。
6. **渲染指令模式**：控制层输出 JSON 样式指令（逐行块级 + 内联区间），Python 纯渲染无业务逻辑。
7. **前端层不动**：`MDRenderer.java`（Swing 前端）保持不变，未来用 Python 重写后可删除。
8. **jlink+jpackage 备选**：若暂不写 Python 前端，`final\MDRenderer\MDRenderer.exe`（jlink 裁剪运行时 + jpackage）仍可直接用，Java 逻辑不编译原生。
9. **编译前提**：`native-image --shared` 链接需要 MSVC `cl.exe`；`build-modules.bat` 转发 `build-mdcontroller.bat`（单一权威构建逻辑）。
