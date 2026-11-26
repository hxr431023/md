# tools 文件夹（命令、统计、配置格式）

主控：`toolspack.dll`（`cmd + threadstat + visualize + tmmf + lerf` 的合并版，33 个导出）。

**依赖**：`cmd.dll` 的 `cmd_*_idp` 需要 `%HDLL%\app\controller.dll`；其余全部无依赖。

---

## toolspack.dll（主控，33 个导出）

函数名与下面叶子 DLL 完全一致。只想加载一个 DLL 拿全 tools 能力时用它：

```python
lib = ctypes.CDLL(HD + r"\tools\toolspack.dll")
lib.tmmf_write_tag(None, b"x.tmmf", "键".encode(), "值".encode())
n = lib.tmmf_read_tag(None, b"x.tmmf", "键".encode(), buf, len(buf))
```

---

## cmd.dll（5 个导出）

**依赖**：`controller.dll`（CMD 必须走唯一 IDP 栈）。

| 函数 | 说明 |
|---|---|
| `cmd_exec(cmd, buf, size)` | 执行内置命令，返回输出 |
| `cmd_help(buf, size)` | 命令列表 |
| `cmd_write_idp(filePath, type, json)` | 把结果压入 controller 的 IDP 队尾（`filePath` 已废弃，传空串） |
| `cmd_to_idp(type, json, buf, size)` | 只生成 IDP 数据包文本，不压栈 |
| `cmd_read_idp(filePath, buf, size)` | 从 controller 的 IDP 队首弹出 |

```python
lib.cmd_write_idp(None, b"", b"cmd_result", b'{"ok":1}')
# controller 侧：app_idp_process → idp_pop_json 拿到 {"ok":1}
```

**注意**：叶子 DLL 不导出 `idp_*`，也不允许用文件/句柄替代；找不到 `controller.dll` 时 `cmd_*_idp` 返回 -1。

---

## threadstat.dll（11 个导出）

**依赖**：无。

`threadstat_init(mode)`、`threadstat_get_mode`、`threadstat_set_mode`、`threadstat_register(name, category)`、`threadstat_unregister`、`threadstat_sample`、`threadstat_get_json`、`threadstat_memory`、`threadstat_disk_read`、`threadstat_disk_write`、`threadstat_disk`。

模式：`folder`（默认，按文件夹归组）/ `single`（单线程）/ `per_dll`（按 DLL 归组）。

```python
lib.threadstat_init(None, b"folder")
lib.threadstat_register(None, b"render", b"doc")
lib.threadstat_sample(None)
n = lib.threadstat_get_json(None, buf, len(buf))
```

---

## visualize.dll（4 个导出）

**依赖**：无。

`visualize_stats_to_file(statsJson, path)`、`visualize_stats_to_html(statsJson, buf, size)`、`visualize_stats_sorted_html(statsJson, field, asc, buf, size)`、`visualize_thread_modes(buf, size)`。

---

## tmmf.dll（6 个导出）

**依赖**：无。TMMF 标签文件（设置/题目式键值）。

```python
lib.tmmf_write_tag(None, b"s.tmmf", "字体".encode(), "微软雅黑".encode())
n = lib.tmmf_read_tag(None, b"s.tmmf", "字体".encode(), buf, len(buf))
```

导出：`tmmf_read_all`、`tmmf_read_tag`、`tmmf_search`、`tmmf_list_tags`、`tmmf_line_count`、`tmmf_write_tag`。

文件头：

```
TMMF.;utf8
icon_mdrenderer
 
0
…
```

正文：`<题目>内容<题目/>`。

---

## lerf.dll（7 个导出）

**依赖**：无。LERF 语言文件。

```python
lib.lerf_load(None, b"lang.lerf")
n = lib.lerf_get(None, b"1", buf, len(buf))                 # 先 load 再 get
n = lib.lerf_get_from_file(None, b"lang.lerf", b"1", buf, len(buf))
n = lib.lerf_get_multi(None, b"1,2", buf, len(buf))          # JSON 数组，需先 load
n = lib.lerf_list_all(None, b"lang.lerf", buf, len(buf))
ok = lib.lerf_validate(None, b"lang.lerf")                   # 1 合法 / 0 错误
cnt = lib.lerf_key_count(None, b"lang.lerf")
```

文件头：

```
LERF.;utf8
icon_mdrenderer
 
0
…
```

正文：`<数字>文本<数字ID/>`；`#`、空行、`…` 跳过。
