# MDRenderer

一个自研 Markdown 编辑器：**Go 多 DLL 内核（原 GraalVM 版源码保留）+ Rust 原生前端**。
内核把每个可复用能力拆成独立 DLL，控制层只与 `controller.dll` 打交道；
渲染与 CMD 走内存 IDP 栈，其余模块直接用 C 函数接口。

当前版本：**v0.1.0**（首个发布版本）

> **2026-11 更新：内核已用 Go 重写。**
> - 单文件版：`go/single/md.go` → `go/build/md.exe`（`md.exe selftest` 55/55）
> - 多 DLL 版：`go/dll/*` → `go/build/dll/*.dll`（23 个 DLL、269 个导出，逐符号验证）
> - 成品布局与逐 DLL 使用方法：`dist/HDLL/README.md`
> - 构建与架构说明：`go/README.md`
> - 安装包会把 `dist/HDLL` 装入 `{app}\HDLL`，并注册用户环境变量 `HDLL`

---

## 核心特性

- **细粒度多 DLL 架构**：20 个模块，每个只负责一件事，可单独复用（排序、压缩、日志、配置、文件、TMMF/LERF 等）。
- **单一入口**：前端只加载 `controller.dll`，其余模块由控制器编排。
- **渲染与 CMD 用 IDP**：`idp_push → app_idp_process → idp_pop_json` 内存双向栈，支持同时运行/异步交互；其余模块同步直调 C 函数，不经 IDP。
- **日志驱动缓存**：渲染等纯操作的结果写入 FLF 日志的 `CACHE` 行（Base64 编码参数与结果），相同操作先查日志命中即复用，结果追加 `"cached":true`。
- **扩展语法按文件拆分**：`base` / `emoji` / `dialog` / `link` 各一个 `.tmmf`，规则与样式全部可配置。
  - 表情包：`[文字]` → 图片（单图或动图），设置中关联文字与图片。
  - 对话框：`:"文字"序号` → 气泡样式，序号对应设置中的样式。
  - 超链接：`≈路径≈序号` → 图标区 + 名称/属性区，序号对应样式。
- **查询 / 替换 / 插入**：按位置查询命中处，支持全部替换、左右文本之间批量插入。
- **分页视图与打印**：按页宽页高自动分页，超宽换行、超长换页，**自动标注页码**；打印与预览使用同一套分页结果。
- **文档能力**：撤销/重做、光标行列、插入删除、字数统计、Markdown ↔ HTML ↔ 纯文本互转。
- **原生前端**：Rust + egui（主推），以及 Python + Cython（Tkinter）两套可选外壳。

---

## 架构

```
前端（Rust egui / Python Cython）
        │  仅加载 controller.dll
        ▼
controller.dll  —— 应用控制器，编排全部模块
        │
   ┌────┴──────────────┬───────────────┬────────────────┐
   ▼                   ▼               ▼                ▼
 core 组(6)          doc 组(7)       tools 组(2)     应用层(4)
 idp  log            markdown        tmmf            cmd
 compress config     doc render      lerf            threadstat
 file single         convert-html                    visualize
                     convert-text
                     convert-txt2md
                     quicksort
```

- **IDP 保留范围**：`render.dll`、`markdown.dll`（HTML 渲染）、`cmd.dll`、`controller.dll`。
- **其余 14 个模块**已移除 `*_write_idp` / `*_to_idp` / `*_read_idp` 导出，只保留 C 函数接口。

---

## 目录结构

```
md/
├── native/
│   ├── modules/             # 20 个模块源码（Java + build-*.bat）
│   │   ├── mdcontroller/
│   │   ├── mdrender/  mdmarkdown/  mddoc/ ...
│   ├── DLL/                 # 编译产物落位 + 头文件 + 使用方法文档
│   │   ├── core/  doc/  tools/  app/
│   │   └── README-总索引.md
│   ├── build-modules.bat    # 全量构建入口
│   └── selftest_dlls.py     # 全量回归（62 项）
├── rust_frontend/           # Rust + egui 原生前端
│   ├── src/                 # app / ffi / idp / settings / syntax / extensions
│   └── syntax/              # base / emoji / dialog / link 扩展语法
├── pyfrontend/              # Python + Cython 外壳
├── resources/               # 图标、声音、内置扩展、命令配置
├── extensions/              # 用户扩展语法样例
├── installer/               # Inno Setup 打包脚本
└── .gitignore
```

---

## 构建

### 内核 DLL

前置：GraalVM（`C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1`）、Visual Studio 2026 的 `vcvars64.bat`。

```cmd
call C:\vs\vs\VC\Auxiliary\Build\vcvars64.bat
call native\build-modules.bat
```

> 注意：构建脚本依赖 `C:\Windows\System32` 在 PATH 中；17 个 native-image 构建必须**串行**执行，并行会 OOM。

单模块构建：`native\modules\<模块>\build-<模块>.bat`；合体构建在 `native\DLL\{core,doc,tools}\build-*.bat`。

### Rust 前端

```powershell
cd C:\wpjc\md\rust_frontend
cargo build --release
```

运行目录需存在 `controller.dll`（及其依赖的 DLL）。

### 安装包

```powershell
Copy-Item native\DLL\app\controller.dll pyfrontend\controller.dll -Force
installer\innosetup-nuget\tools\ISCC.exe installer\md_setup.iss
```

产物：`installer\MDRenderer-Setup.exe`。

---

## 验证

```powershell
python native\selftest_dlls.py        # 全量回归：62 PASS / 0 FAIL
python native\test_cache.py           # 缓存命中 / 未命中 / CACHE 行校验
python native\test_log_cache_persist.py put     # 跨进程缓存
python native\test_log_cache_persist.py lookup
```

---

## 文档

| 文档 | 内容 |
|------|------|
| `native/DLL/README-总索引.md` | 22 个 DLL 清单、通信规则、目录约定、缓存协议 |
| `native/DLL/app/CONTROLLER-使用说明.md` | 唯一入口 `app_*` 接口、IDP 流程、缓存规则 |
| `native/DLL/core/LOG-使用说明.md` | FLF 日志格式与 `log_cache_lookup/put` |
| `native/DLL/core/IDP-使用说明.md` | 内存双向栈（渲染 / CMD 专用） |
| `native/DLL/tools/TMMF-使用说明.md` | TMMF 标签文件 |
| `native/DLL/tools/TOOLSPACK-使用说明.md` | 工具包合体 |

每个 DLL 目录下都有同名 `.h`（精确 C 签名）与 `*-使用说明.md`（依赖清单 + 用法）。

---

## v0.1.0 版本说明

首个可发布版本，包含：

1. 20 模块细粒度 DLL 内核与 `controller.dll` 单入口编排。
2. 通信规则重构：渲染 / CMD 保留 IDP，其余模块改为 C 函数接口（14 个模块已清理 IDP 导出并重编）。
3. 日志驱动缓存：6 类渲染操作可缓存，跨进程（`.zst` 压缩还原）仍可命中。
4. 扩展语法文件化：`base` / `emoji` / `dialog` / `link`。
5. 查询 / 替换 / 插入，分页视图、打印、自动页码。
6. Rust egui 原生前端与 Python Cython 外壳。

已知边界：缓存只查当日工作态日志；IDP 栈为进程内单实例；DLL 构建需 GraalVM 与 VS 环境。
