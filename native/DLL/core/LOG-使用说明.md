# log.dll — 日志主控使用说明（自压缩版）

> 版本：V1.0.10 ｜ 模块：`C:\wpjc\md\native\DLL\core\`
> 适用范围：**主控 log.dll**（控制层只与主控交互）+ 副 DLL compress.dll（纯压缩，可复用）

---

## 1. 两个 DLL 的分工

| DLL | 职责 | 对外接口 | 是否可直接调用 |
|-----|------|----------|----------------|
| **log.dll**（压缩+主控，自压缩） | 日志 + 压缩合体：启动解压、写日志、关闭自动压回 | `log_*` | ✅ **唯一入口** |
| compress.dll（纯压缩） | 快速压缩/解压（DEFLATE 快速档，后缀 `.zst`），供其他模块复用 | `compress_*` | ✅ 需要压缩时直接调用 |

**为什么日志不需要独立主控**：不存在"不需要压缩的日志"，所以压缩和日志
合成一个 DLL，由 log.dll 自己完成压缩（自压缩）。控制层只需
`log_init → log_write → log_shutdown` 三件事，无需关心文件在哪、何时压缩。

**主控调用副 DLL 的方式**：log.dll 内部直接调用 MDCompress 类（同 DLL 编译），
控制层不需要知道压缩细节；若其他模块（如文档转换、缓存）需要独立压缩，
直接调 compress.dll 的 `compress_*` 接口即可。

---

## 2. 文件位置与目录约定

```
<baseDir>/
├── log/            ← 压缩态日志：log/YYYY-MM-DD.log.zst
└── cache/log/      ← 工作态日志：cache/log/YYYY-MM-DD.log（运行期间）
```

- `log_init`：若存在 `log/日期.log.zst` 且无工作态文件，自动解压到 `cache/log/日期.log`。
- `log_shutdown`：把工作态日志压缩回 `log/日期.log.zst`，删除工作文件。
- 日志超过 10 MB 自动删除当日文件并重建（FLF 规范）。

---

## 3. 主控 API（log.dll 的 C 导出，见 log.h）

### 3.1 日志接口（控制层使用）

| 函数 | 签名 | 说明 |
|------|------|------|
| `log_init` | `int log_init(graal_isolatethread_t*, char* baseDir)` | 初始化：建目录→解压→就绪。返回 0 成功 / -1 失败 |
| `log_write` | `void log_write(graal_isolatethread_t*, char* params, char* op, char* result)` | 写一条日志：`[时间]参数{操作}结果` |
| `log_write_end` | `void log_write_end(graal_isolatethread_t*, char* params, char* op, char* result)` | 写收尾条目（末尾追加 `;` 结束符） |
| `log_shutdown` | `void log_shutdown(graal_isolatethread_t*)` | 关闭：日志收尾→压缩回→删工作文件 |
| `log_clear_all` | `int log_clear_all(graal_isolatethread_t*)` | 删除全部日志（压缩态+工作态），返回删除数量 |
| `log_size` | `long long log_size(graal_isolatethread_t*)` | 当前日志字节数 |
| `log_set_enabled` | `void log_set_enabled(graal_isolatethread_t*, char* cat, int enabled)` | 分类开关：startup/openfile/savefile/newfile/error/singleinstance |
| `log_is_enabled` | `int log_is_enabled(graal_isolatethread_t*, char* cat)` | 查询分类开关，返回 1/0 |

### 3.2 操作结果缓存接口（日志驱动的命中复用）

缓存机制：把纯函数操作（渲染/查找替换/分页等）的**参数与结果**写进当日日志；
下次相同 `操作 + 参数` 时先查日志，命中就直接复用结果，不再重复计算。

| 函数 | 签名 | 说明 |
|------|------|------|
| `log_cache_lookup` | `int log_cache_lookup(graal_isolatethread_t*, char* op, char* params, char* outBuf, int bufSize)` | 查相同 op+params 的最近结果；命中写 outBuf 并返回长度，**未命中返回 -1** |
| `log_cache_put` | `int log_cache_put(graal_isolatethread_t*, char* op, char* params, char* result)` | 追加一条缓存记录；返回 0 成功 / -1 失败 |

缓存行写在当日 FLF 日志（第 5 行起），格式固定：

```
<\2026-11-06 10:12:21>CACHE:app_render_html:<base64(params)>:<base64(result)>。<2026-11-06 10:12:21/>
```

- `params` / `result` 用 **Base64** 编码，避免 JSON 的 `{}`、换行、`。<\` 破坏行解析；
- 匹配规则：`操作名` 完全相同 + `params` 的 Base64 完全相同；逆序扫描取**最近一条**；
- 只查**当日工作态**日志；跨天或 `log_clear_all` 后缓存失效（重新计算并写新缓存）；
- 缓存行不受日志分类开关影响（保证命中率），日志超过 10 MB 重建时缓存一并清空。

```c
char buf[65536];
int n = log_cache_lookup(thread, "app_render_html", "{\"md\":\"# hi\"}", buf, sizeof(buf));
if (n < 0) {
    /* 未命中：执行真实运算，再把结果写回缓存 */
    log_cache_put(thread, "app_render_html", "{\"md\":\"# hi\"}", "{\"html\":\"<h1>hi</h1>\"}");
}
```

### 3.3 副 DLL 接口（compress.dll，其他模块复用）

| 函数 | 签名 | 说明 |
|------|------|------|
| `compress_file` | `int compress_file(graal_isolatethread_t*, char* src, char* dst)` | 压缩文件 |
| `compress_decompress_file` | `int compress_decompress_file(graal_isolatethread_t*, char* src, char* dst)` | 解压文件 |
| `compress_string_to_file` | `int compress_string_to_file(graal_isolatethread_t*, char* text, char* dst)` | 字符串压缩到文件 |
| `compress_file_to_string` | `int compress_file_to_string(graal_isolatethread_t*, char* src, char* buf, int size)` | 文件解压到字符串 |
| `compress_ratio` | `int compress_ratio(graal_isolatethread_t*, char* src)` | 压缩率（百分比） |
| `compress_write_idp` / `compress_to_idp` / `compress_read_idp` | 见 compress.h | IDP 包装的压缩读写 |

> 日志分类可选值：`startup`（启动）、`openfile`（打开文件）、`savefile`（保存文件）、
> `newfile`（新建）、`error`（错误）、`singleinstance`（单实例提示）。

---

## 4. 典型生命周期（外部程序唯一需要掌握的流程）

```
1. log_init(thread, "C:\app\data")      ← 启动时一次
2. log_write(thread, "readme.md", "打开文件", "成功")
   log_write(thread, "readme.md", "保存文件", "3 行")
   ...                                     ← 运行期间任意次
3. log_shutdown(thread)                 ← 退出前一次（自动压缩回 .zst）
```

辅助调用（任意时刻）：

```
log_set_enabled(thread, "error", 1)     ← 打开错误日志分类
int n = log_is_enabled(thread, "error")
long long sz = log_size(thread)
int n = log_clear_all(thread)           ← 清空全部日志
```

---

## 5. 调用示例

### 5.1 C（与 log.h 一起链接）

```c
#include "graal_isolate.h"
#include "log.h"

int main(void) {
    graal_isolate_t* isolate = NULL;
    graal_isolatethread_t* thread = NULL;
    if (graal_create_isolate(NULL, &isolate, &thread) != 0) return 1;

    log_init(thread, "C:/app/data");
    log_write(thread, "main.c", "打开文件", "成功");
    log_shutdown(thread);

    graal_detach_all_threads_and_tear_down_isolate(thread);
    return 0;
}
```

### 5.2 Python（ctypes，前端可直接使用）

```python
import ctypes
from ctypes import c_char_p, c_int, c_longlong, c_void_p

dll = ctypes.CDLL(r"C:\wpjc\md\native\DLL\core\log.dll")
P = ctypes.c_void_p

# 声明函数签名
dll.graal_create_isolate.restype = c_int
dll.graal_create_isolate.argtypes = [c_void_p, ctypes.POINTER(P), ctypes.POINTER(P)]
dll.graal_detach_all_threads_and_tear_down_isolate.restype = c_int
dll.graal_detach_all_threads_and_tear_down_isolate.argtypes = [P]
dll.log_init.restype = c_int
dll.log_init.argtypes = [P, c_char_p]
dll.log_write.argtypes = [P, c_char_p, c_char_p, c_char_p]
dll.log_shutdown.argtypes = [P]
dll.log_size.restype = c_longlong
dll.log_size.argtypes = [P]
dll.log_set_enabled.argtypes = [P, c_char_p, c_int]
dll.log_is_enabled.restype = c_int
dll.log_is_enabled.argtypes = [P, c_char_p]

# GraalVM DLL：先创建 isolate，拿到线程句柄
isolate = P()
thread = P()
dll.graal_create_isolate(None, ctypes.byref(isolate), ctypes.byref(thread))

dll.log_init(thread, b"C:/app/data")
dll.log_write(thread, b"main.py", b"open", b"ok")
print("日志大小:", dll.log_size(thread))
dll.log_shutdown(thread)

# 退出前销毁
dll.graal_detach_all_threads_and_tear_down_isolate(thread)
```

> `graal_create_isolate` / `graal_detach_all_threads_and_tear_down_isolate` 由
> log.dll 自身导出（与 log.h 同目录的 graal_isolate.h 提供声明），
> Python 直接加载本 DLL 调用即可，无需额外文件。

### 5.3 Rust（libloading，本项目前端用法）

```rust
use libloading::{Library, Symbol};

pub struct Log { lib: Library }

impl Log {
    pub fn new(path: &str) -> Result<Self, Box<dyn std::error::Error>> {
        Ok(Self { lib: unsafe { Library::new(path)? } })
    }

    pub unsafe fn init(&self, thread: *mut c_void, base_dir: &str) -> i32 {
        let f: Symbol<unsafe extern "C" fn(*mut c_void, *const c_char) -> i32> =
            self.lib.get(b"log_init")?;
        f(thread, base_dir.as_ptr() as _)
    }

    pub unsafe fn write(&self, thread: *mut c_void, p: &str, op: &str, r: &str) {
        let f: Symbol<unsafe extern "C" fn(*mut c_void, *const c_char, *const c_char, *const c_char)> =
            self.lib.get(b"log_write")?;
        f(thread, p.as_ptr() as _, op.as_ptr() as _, r.as_ptr() as _)
    }

    pub unsafe fn shutdown(&self, thread: *mut c_void) {
        let f: Symbol<unsafe extern "C" fn(*mut c_void)> = self.lib.get(b"log_shutdown")?;
        f(thread)
    }
}
```

---

## 6. 错误处理

- `log_init` 失败返回 -1（如 .zst 损坏无法解压），此时会尝试按空日志初始化当日文件。
- 写入阶段通常不返回错误码（追加写失败即磁盘问题），退出前可查
  `log_size` 是否增长作为健康检查。
- 压缩失败时 `log_shutdown` 保留工作文件（不删除），下次启动可重新压回。

---

## 7. 注意事项

1. **控制层只调 log.dll**：压缩已内嵌（自压缩），无需也不应直接调 compress.dll
   来处理日志；compress.dll 只给其他模块（文档转换、缓存等）做通用压缩。
2. `log_init` 必须在 `log_write` 之前，`log_shutdown` 必须在程序退出前，否则
   当日日志停留在 cache/log 未压回。
3. 每次进程启动/退出各调用一次 init/shutdown，天然支持"每日一个 .zst"归档。
4. 日志分类开关默认全部开启；`error` 分类建议始终开启。
5. FLF 格式详情（头 4 行规范）见 MDLog.java 源码注释。
6. 本目录结构原则：每个分类文件夹（core/doc/app/tools）内一个主控 + 若干副
   DLL，控制层只与主控交互；主控文档说明对其他 DLL 的调用方式。

---

## 8. 依赖清单（log.dll / compress.dll）

| 项 | log.dll（主控） | compress.dll（副 DLL） |
|----|-----------------|------------------------|
| 运行时 DLL | 仅 `log.dll` 自身 | 仅 `compress.dll` 自身 |
| 开发期头文件 | `log.h` + `graal_isolate.h` | `compress.h` + `graal_isolate.h` |
| 编译期源码 | `MDLog.java` + `MDCompress.java` + `MDIdp.java` | `MDCompress.java` + `MDIdp.java` |
| 通信方式 | 内部静态直调 MDCompress（同 DLL 内）；对外全部走内存 IDP 栈 | 对外全部走内存 IDP 栈（idp_* 导出） |

> 说明：log.dll 是"压缩+主控"形态，编译时把 MDCompress 编入自身实现自压缩；
> 单独要压缩功能时附带 compress.dll 即可（不依赖 log.dll）。
