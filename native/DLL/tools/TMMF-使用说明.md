# tmmf.dll — TMMF 格式解析副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\tools\tmmf.dll` ｜ 配套头文件：`tmmf.h`
> 职责：TMMF（Tag-Multiple-Match Format）解析：一行内多个搜索词对应多个结果。

---

## 1. 功能职责

- **TMMF 格式**：每行 = 一个标签区；同一行内一个搜索词可对应多个内容
  （多搜索词 ↔ 多结果），用于扩展语法、设置、词汇表等。
- `tmmf_search`：按关键词查该行全部结果。
- `tmmf_read_tag` / `tmmf_read_all` / `tmmf_list_tags` / `tmmf_line_count`：读取。
- `tmmf_write_tag`：写入。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `tmmf.dll` 自身 |
| 开发期头文件 | `tmmf.h` + `graal_isolate.h` |
| 编译期源码 | `MDTmmf.java` + `MDIdp.java`（`build-mdtmmf.bat` 的 -cp） |

---

## 3. 导出 API（见 tmmf.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `tmmf_read_all` | `int tmmf_read_all(t, char* path, char* outBuf, int size)` | 读整个 TMMF 文件 |
| `tmmf_read_tag` | `int tmmf_read_tag(t, char* path, char* tag, char* outBuf, int size)` | 读指定标签 |
| `tmmf_search` | `int tmmf_search(t, char* path, char* keyword, char* outBuf, int size)` | 关键词 → 该行全部结果（JSON 数组） |
| `tmmf_list_tags` | `int tmmf_list_tags(t, char* path, char* outBuf, int size)` | 列出所有标签 |
| `tmmf_line_count` | `int tmmf_line_count(t, char* path)` | 行数 |
| `tmmf_write_tag` | `int tmmf_write_tag(t, char* path, char* tag, char* content)` | 写标签行 |

> 本模块只用上述 C 函数接口（同步直调），不提供 IDP 包装。

---

## 4. 典型用法

```
tmmf_search(th, "C:/app/emoji.tmmf", "开心", buf, 4096);
/* → ["emoji:smile.png","emoji:happy.gif"] 该行多个结果 */
tmmf_write_tag(th, "C:/app/emoji.tmmf", "开心", "emoji:smile.png emoji:happy.gif");
```

---

## 5. 注意事项

1. 搜索命中规则：只要搜索词出现在该行，返回该行全部内容（不只是一对一）。
2. 文件不存在时读取函数返回 -1。