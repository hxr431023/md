# lerf.dll — LERF 格式解析副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\tools\lerf.dll` ｜ 配套头文件：`lerf.h`
> 职责：LERF（轻量键值资源文件）解析，项目保留的唯一非 TMMF 格式。

---

## 1. 功能职责

- LERF 格式：`#` 注释、`=` 键值对、`{0}` 占位符（项目早期 .grf 演进而来）。
- `lerf_load` / `lerf_get` / `lerf_get_from_file` / `lerf_get_multi` / `lerf_list_all` / `lerf_validate` / `lerf_key_count`。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `lerf.dll` 自身 |
| 开发期头文件 | `lerf.h` + `graal_isolate.h` |
| 编译期源码 | `MDLerf.java` + `MDIdp.java`（`build-mdlerf.bat` 的 -cp） |

---

## 3. 导出 API（见 lerf.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `lerf_load` | `int lerf_load(t, char* path)` | 载入文件到内存 |
| `lerf_get` | `int lerf_get(t, char* key, char* outBuf, int size)` | 取值（已 load 后） |
| `lerf_get_from_file` | `int lerf_get_from_file(t, char* path, char* key, char* outBuf, int size)` | 直接从文件取值 |
| `lerf_get_multi` | `int lerf_get_multi(t, char* keys, char* outBuf, int size)` | 多键取值（JSON） |
| `lerf_list_all` | `int lerf_list_all(t, char* path, char* outBuf, int size)` | 列出全部键值 |
| `lerf_validate` | `int lerf_validate(t, char* path)` | 校验格式，合法返回 0 |
| `lerf_key_count` | `int lerf_key_count(t, char* path)` | 键数量 |
| `lerf_write_idp` / `lerf_to_idp` / `lerf_read_idp` | 见 lerf.h | IDP 包装 |

---

## 4. 典型用法

```
lerf_validate(th, "C:/app/a.lerf");            /* 0=合法 */
lerf_load(th, "C:/app/a.lerf");
lerf_get(th, "icon", buf, 256);
lerf_get_from_file(th, "C:/app/a.lerf", "name", buf, 256);
```

---

## 5. 注意事项

1. 唯一例外格式：用户明确 LERF 不统一为 TMMF，其余配置一律 TMMF。
2. `{0}` 占位符供调用方格式化，本 DLL 原样返回。