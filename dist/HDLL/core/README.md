# core 文件夹（基础层）

主控：`core.dll`（以下 7 个模块的静态合并版，函数名完全一致）。
日志子系统主控：`logmain.dll`（管理 `log.dll` + `compress.dll` 的职责，唯一对外）。

**全部依赖**：Windows UCRT。无需 VC++、无需 Go/Java 运行时。

---

## core.dll（主控，41 个导出）

只想“一次加载拿到 core 全部能力”时用它。包含：

- `idp_*` 6 个
- `compress_*` 5 个
- `log_*` 10 个
- `config_*` 9 个
- `file_*` 7 个
- `single_*` 4 个

用法与下面的叶子 DLL 相同：

```python
lib = ctypes.CDLL(HD + r"\core\core.dll")
lib.config_init(None, b"C:\\wpjc\\md")
lib.config_set_setting(None, b"theme", b"dark")
n = lib.config_get_setting(None, b"theme", buf, len(buf))
```

注意：`core.dll` 内部的 IDP 栈是独立的；同一进程若已加载 `controller.dll`，业务只走 controller。

---

## logmain.dll（日志主控，13 个导出）

**依赖**：无；日志 + zlib 压缩内置。
**职责**：日志唯一对外入口；启动自动解压上次的 `.log.zst`，关闭自动压缩（自压缩）。

```python
lib.logmain_init(None, b"C:\\wpjc\\md")
lib.logmain_write(None, b"文件", b"打开", b"成功")
st = json.loads(read_buf(lib.logmain_status(None, buf, len(buf))))
lib.logmain_shutdown(None)        # 压缩 + 删除工作态
```

导出：

| 函数 | 说明 |
|---|---|
| `logmain_version` | 版本号 |
| `logmain_init` | 初始化（自动解压） |
| `logmain_write` / `logmain_write_end` | 写一条日志 / 结束分组 |
| `logmain_shutdown` | 关闭并压缩 |
| `logmain_set_enabled` / `logmain_is_enabled` | 按分类开关 |
| `logmain_clear_all` | 清空日志与缓存 |
| `logmain_size` | 当前工作日志字节数 |
| `logmain_cache_lookup` | 查缓存，-1 = 未命中 |
| `logmain_cache_put` | 写缓存 |
| `logmain_status` | JSON：work / compressed / size / last_error |
| `logmain_compress_now` | 立即压缩 |

文件位置：

```
<base>\cache\log\YYYY-MM-DD.log
<base>\log\YYYY-MM-DD.log.zst
```

**不要**在同一 base 上同时使用 `logmain.dll` 和 `log.dll`，会双写。

---

## log.dll（纯日志，10 个导出）

**依赖**：无。**用于**不想带主控、只要日志能力的宿主。

```python
lib.log_init(None, b"C:\\wpjc\\md")
lib.log_write(None, b"参数", b"操作", b"结果")
hit = lib.log_cache_lookup(None, b"render_find", b'{"q":"中文"}', buf, len(buf))
lib.log_shutdown(None)
```

导出：`log_init`、`log_shutdown`、`log_write`、`log_write_end`、`log_set_enabled`、`log_is_enabled`、`log_clear_all`、`log_size`、`log_cache_lookup`、`log_cache_put`。

---

## compress.dll（纯压缩，5 个导出）

**依赖**：无（Go 标准库 zlib）。

```python
lib.compress_file(None, b"a.txt", b"a.zst")            # 0 成功
lib.compress_decompress_file(None, b"a.zst", b"b.txt")
lib.compress_string_to_file(None, b"内容", b"c.zst")
n = lib.compress_file_to_string(None, b"c.zst", buf, len(buf))
pct = lib.compress_ratio(None, b"a.txt")               # 0-100，失败 -1
```

**注意**：后缀 `.zst` 是历史命名，算法是 **zlib/DEFLATE**，不是 Zstandard。

---

## idp.dll（独立栈，6 个导出）

**依赖**：无。只给“不加载 controller”的宿主。

导出：`idp_push`、`idp_pop_json`、`idp_pop_idp`、`idp_peek_json`、`idp_stack_size`、`idp_stack_clear`。

**与 controller 的栈不共享**；同一进程二选一。

---

## config.dll（9 个导出）

**依赖**：无。文件：`<base>\resources\cmd\settings.tmmf`。

```python
lib.config_init(None, b"C:\\wpjc\\md")      # 只设目录，返回 0
lib.config_load_settings(None)              # 读盘，文件不存在返回 -1
lib.config_set_setting(None, b"font", b"微软雅黑")
lib.config_save_settings(None)
```

导出：`config_init`、`config_load_settings`、`config_save_settings`、`config_get_setting`、`config_set_setting`、`config_has_setting`、`config_remove_setting`、`config_get_thread_mode`、`config_set_thread_mode`。

---

## file.dll（7 个导出）

**依赖**：无。

`file_read(path, buf, size)`、`file_write`、`file_append`、`file_exists`、`file_delete`、`file_name`、`file_extension`。

---

## single.dll（4 个导出）

**依赖**：无。锁文件名做互斥。

```python
if lib.single_try_lock(None, b"mdrenderer"):   # 1 = 拿到锁
    ...
lib.single_release(None)
```

`single_is_locked`、`single_lock_path(name, buf, size)` 用于诊断。
