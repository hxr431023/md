# config.dll — 配置副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\core\config.dll` ｜ 配套头文件：`config.h`
> 职责：设置项读写（TMMF 格式）、线程模式读写。

---

## 1. 功能职责

- 键值设置：get/set/has/remove + 整体 load/save。
- 线程模式：`config_get_thread_mode` / `config_set_thread_mode`
  （三模式：每 DLL 文件夹一线程=默认 / 单线程 / 每 DLL 一线程）。
- 设置文件采用 TMMF 格式存储（见 tools 分类）。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 | `config.dll` 自身 |
| 开发期 | `config.h` + `graal_isolate.h` |
| 编译期源码 | `MDConfig.java` + `MDIdp.java`（`build-mdconfig.bat` 的 -cp） |

---

## 3. 导出 API（见 config.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `config_init` | `int config_init(t, char* baseDir)` | 初始化并载入设置，返回 0/-1 |
| `config_load_settings` | `int config_load_settings(t)` | 重新载入 |
| `config_save_settings` | `int config_save_settings(t)` | 写回磁盘 |
| `config_get_setting` | `int config_get_setting(t, char* key, char* outBuf, int size)` | 读键，返回字符串长度 |
| `config_set_setting` | `int config_set_setting(t, char* key, char* value)` | 写键（内存），需 save 落盘 |
| `config_has_setting` | `int config_has_setting(t, char* key)` | 1/0 |
| `config_remove_setting` | `int config_remove_setting(t, char* key)` | 删键 |
| `config_get_thread_mode` | `int config_get_thread_mode(t, char* outBuf, int size)` | 取线程模式名（folder/single/each） |
| `config_set_thread_mode` | `int config_set_thread_mode(t, char* mode)` | 设线程模式 |
| `config_write_idp` / `config_to_idp` / `config_read_idp` | 见 config.h | IDP 包装（filePath 废弃） |

---

## 4. 典型用法

```
config_init(thread, "C:/app/data")
config_set_setting(thread, "font", "Consolas")
config_save_settings(thread)
char mode[16]; config_get_thread_mode(thread, mode, 16)
config_set_thread_mode(thread, "each")
```

---

## 5. 注意事项

1. `config_set_setting` 只改内存，**必须 save_settings 才落盘**。
2. 线程模式可选值：`folder`（默认）/ `single` / `each`。