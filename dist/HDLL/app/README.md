# app 文件夹（总主控）

## controller.dll —— 唯一入口

**依赖**：无。IDP 栈、文档、渲染、日志、配置、CMD、线程统计全部静态内置。
**版本**：1.0.10
**导出**：42 个（`app_*` 31 个 + `idp_*` 6 个 + `graal_*` 2 个 + 其余 IDP 包装 3 个）

### 前端加载步骤

1. `LoadLibrary("%HDLL%\app\controller.dll")`
2. `graal_create_isolate(NULL, &iso, &thr)` —— 兼容桩，返回 0，`thr` 非空但无意义
3. `app_init(thr, baseDir)` —— `baseDir` 是应用资源根目录（例如 `C:\wpjc\md`）
4. 业务：`idp_push(type, "前端名", 参数JSON)` → `app_idp_process(thr)` → `idp_pop_json(thr, buf, size)`
5. 退出：`app_shutdown(thr)`

### 三段式示例

```c
char buf[1 << 20];
idp_push(thr, "app_insert_text", "py", "{\"pos\":0,\"text\":\"# 标题\"}");
app_idp_process(thr);
int n = idp_pop_json(thr, buf, sizeof(buf));   // n = UTF-8 字节数
```

### 支持的 IDP type（39 个）

`app_version`、`app_new_file`、`app_open_file`、`app_save_file`、`app_save_file_as`、`app_get_content`、`app_get_file_path`、`app_is_dirty`、`app_insert_text`、`app_delete_text`、`app_undo`、`app_redo`、`app_can_undo`、`app_can_redo`、`app_set_cursor`、`app_get_cursor`、`app_get_cursor_line`、`app_get_cursor_col`、`app_render_ui`、`app_render_html`、`app_get_status`、`app_get_setting`、`app_set_setting`、`app_save_settings`、`app_log`、`app_easter_detect`、`app_easter_exec`、`config_get_thread_mode`、`config_set_thread_mode`、`doc_char_count`、`threadstat_sample`、`threadstat_get_json`、`visualize_stats_to_html`、`log_clear_all`、`single_try_lock`、`render_find`、`render_replace`、`render_insert`、`render_paged`

### 返回值

- 写缓冲区的函数返回**实际写入字节数**；`0` 空，`-1` 失败。
- `idp_pop_json` 无结果时返回 0。
- `app_get_status` 返回：

```json
{"file_name":"未命名","file_path":"","dirty":false,"char_count":0,"word_count":0,
 "line_count":0,"cursor_pos":0,"cursor_line":0,"cursor_col":0,
 "can_undo":false,"can_redo":false}
```

### 日志缓存

`app_render_html`、`render_find`、`render_replace`、`render_insert`、`render_paged`、`visualize_stats_to_html` 六类请求会先查日志缓存；命中时结果 JSON 末尾带 `"cached":true`。

### 注意

- 不要同时加载 `core\idp.dll`，那是一个独立栈。
- `app_init` 不做单实例拒绝；要互斥请调用 `single_try_lock`。
- `app_shutdown` 会压缩日志并释放锁文件。
