# core.dll — core 分类合体主控使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\core\core.dll` ｜ 配套头文件：`core.h`
> 职责：**core 分类文件夹的主控 DLL**，把该分类下 6 个副 DLL 组合成一个 DLL，
> 控制层（前端）只需与主控交互。

---

## 1. 主控与副 DLL 的关系

| 本分类副 DLL | 编入 core.dll 的源码 | 对外导出前缀 |
|--------------|---------------------|--------------|
| idp.dll | MDIdp.java | `idp_*`（内存双向栈） |
| log.dll（日志主控） | MDLog.java | `log_*` |
| compress.dll（纯压缩） | MDCompress.java | `compress_*` |
| config.dll（配置） | MDConfig.java | `config_*` |
| file.dll（文件） | MDFile.java | `file_*` |
| single.dll（单实例） | MDSingle.java | `single_*` |

**组合方式**：core.dll 在编译时把上述 6 个 Java 源码一并编入（见
`DLL\core\build-mdcore.bat`，javac -cp 6 个 src 目录）。加载 core.dll 后，
这 6 组功能的所有导出**同时可用**，控制层无需再分别加载 6 个副 DLL。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `core.dll` 自身（GraalVM 自包含） |
| 开发期头文件 | `core.h` + `graal_isolate.h`（同目录） |
| 编译期源码 | `MDIdp` + `MDLog` + `MDCompress` + `MDConfig` + `MDFile` + `MDSingle`（6 模块，见 build-mdcore.bat） |
| 同目录配套 | `core_dynamic.h`、`graal_isolate_dynamic.h`（动态加载用） |

> 若只需要其中某一功能（如只要压缩），可直接附带对应副 DLL（compress.dll），
> 不必加载整个 core.dll——副 DLL 各自功能完整可独立直调。

---

## 3. 导出 API 一览（全部 6 组，签名见 core.h）

- **压缩**：`compress_file` / `compress_decompress_file` / `compress_string_to_file` / `compress_file_to_string` / `compress_ratio` + `compress_write_idp` / `compress_to_idp` / `compress_read_idp`
- **配置**：`config_init` / `config_load_settings` / `config_save_settings` / `config_get_setting` / `config_set_setting` / `config_has_setting` / `config_remove_setting` / `config_get_thread_mode` / `config_set_thread_mode` + IDP 三件套
- **文件**：`file_read` / `file_write` / `file_append` / `file_exists` / `file_size` / `file_delete` / `file_name` / `file_extension` + IDP 三件套
- **日志**：`log_init` / `log_shutdown` / `log_write` / `log_write_end` / `log_set_enabled` / `log_is_enabled` / `log_clear_all` / `log_size` + IDP 三件套
- **单实例**：`single_try_lock` / `single_release` / `single_is_locked` / `single_lock_path` + IDP 三件套
- **IDP 栈**：`idp_push` / `idp_pop_json` / `idp_pop_idp` / `idp_peek_json` / `idp_stack_size` / `idp_stack_clear`

详细参数与行为见各副 DLL 的独立使用说明（同目录 `*使用说明.md`）。

---

## 4. 典型用法（控制层只调 core.dll）

```
/* 初始化 */
core 加载 → graal_create_isolate
config_init(th, "C:/app/data")          /* 配置载入 */
log_init(th, "C:/app/data")             /* 日志解压就绪 */
if (single_try_lock(th, "mdrenderer") == 1) { ... }

/* 运行期 */
file_write(th, "C:/app/tmp.txt", "data")
log_write(th, "tmp.txt", "写文件", "成功")
compress_file(th, "C:/app/tmp.txt", "C:/app/tmp.txt.zst")

/* 退出 */
log_shutdown(th)
graal_detach_all_threads_and_tear_down_isolate(th)
```

---

## 5. 调用示例（Python ctypes 只加载 core.dll）

```python
import ctypes
from ctypes import c_char_p, c_int, c_void_p

dll = ctypes.CDLL(r"C:\wpjc\md\native\DLL\core\core.dll")
P = ctypes.c_void_p
isolate, thread = P(), P()
dll.graal_create_isolate(None, ctypes.byref(isolate), ctypes.byref(thread))

dll.log_init.argtypes = [P, c_char_p]
dll.log_init(th := thread, b"C:/app/data")
dll.log_write.argtypes = [P, c_char_p, c_char_p, c_char_p]
dll.log_write(thread, b"a.md", b"save", b"ok")
dll.log_shutdown.argtypes = [P]
dll.log_shutdown(thread)
```

---

## 6. 通信铁律

core.dll 内部各组 Java 类为**同 DLL 内静态直调**（编译期绑定，非跨 DLL 通信）；
对外（前端或其他 DLL）一律只通过本 DLL 导出的 `idp_*` 内存栈收发，
严禁文件型 IDP、句柄或其他直通方式。

---

## 7. 注意事项

1. 日志/配置/单实例都有 init 顺序：config → log → single，退出逆序。
2. 直接使用副 DLL（如只有 compress.dll）时，其 IDP 栈为该 DLL 独立实例，
   与 core.dll 的栈不互通——需要互通必须走同一个 DLL 或经 controller.dll。