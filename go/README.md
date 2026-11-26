# MDRenderer Go 版

本目录是 MDRenderer 的 Go 重写：**一份业务内核 + 单文件版 + 多 DLL 版**。

```
go\
├─ go.mod
├─ single\
│   └─ md.go                 # 单文件版（约 3300 行，可直接编译 EXE）
├─ internal\
│   ├─ engine\
│   │   ├─ engine.go         # 业务内核（由 single/md.go 提取，包名 engine）
│   │   └─ api.go            # 供 DLL 调用的导出 API
│   ├─ idpclient\            # 叶子 DLL → controller 的唯一 IDP 栈（C 接口）
├─ dll\
│   ├─ controller\           # 总主控（42 导出）
│   ├─ core\  docpack\  toolspack\   # 三个文件夹主控
│   ├─ idp\ log\ compress\ config\ file\ single\ logmain\
│   ├─ doc\ markdown\ render\ convert-html\ convert-text\ convert-txt2md\ quicksort\
│   └─ cmd\ threadstat\ visualize\ tmmf\ lerf\
├─ tools\
│   ├─ gen_dlls.py           # 由 engine 生成全部 DLL 适配层
│   └─ build-dlls.ps1        # 构建 23 个 DLL
└─ build\
    ├─ md.exe                # 单文件版
    └─ dll\*.dll             # 多 DLL 产物
```

## 1. 单文件版

```powershell
cd C:\wpjc\md\go
& C:\wpjc\go\bin\go.exe build -trimpath -ldflags "-s -w" -o build\md.exe .\single\md.go
.\build\md.exe selftest        # 55 项
```

## 2. 多 DLL 版

环境要求：

- Go 1.26.7 windows/amd64
- mingw64 gcc（`C:\wpjc\mingw64\bin\gcc.exe`）
- `CGO_ENABLED=1`，`CC` 指向 gcc

```powershell
cd C:\wpjc\md\go
python tools\gen_dlls.py
.\tools\build-dlls.ps1
```

产物在 `build\dll`，部署布局由 `dist\HDLL` 承载（详见 `dist\HDLL\README.md`）。

单独构建某个：

```powershell
.\tools\build-dlls.ps1 -Only controller,core,docpack
```

## 3. 架构约束

1. 业务只在 `internal/engine` 实现，DLL 是薄适配层，避免多份逻辑漂移。
2. **IDP 唯一栈在 controller.dll**；叶子 DLL 不再各自建栈。
3. 渲染与 CMD 必须走 IDP；其余模块用 C 函数接口。
4. 缓冲区返回值统一为 UTF-8 **字节数**（修复 Java 版返回字符数导致 Rust 中文截断的问题）。
5. DLL 通过环境变量 `HDLL`（兼容 `HDLL_DIR` / `MD_HDLL`）定位。

## 4. 验证

脚本同时保存在 `go\tools\tests\`：

```powershell
python build\dll\test_all.py       # 33 项功能
python build\dll\check_exports.py  # 23 个 DLL / 269 个导出
$env:HDLL='C:\wpjc\md\dist\HDLL'
python build\dll\test_dist.py      # 部署布局 + 环境变量寻址
```

## 5. 已知差异

- `compress` 使用 Go 标准库 zlib（DEFLATE），扩展名仍沿用 `.zst`。
- `graal_create_isolate` 为兼容桩，线程指针无实际意义。
- `app_init` 不负责单实例互斥；由 `single.dll` 承担。
