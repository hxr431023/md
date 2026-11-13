# doc.dll — 文档模型副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\doc\doc.dll` ｜ 配套头文件：`doc.h`
> 职责：编辑器文档核心模型：打开/保存/内容/光标/编辑/撤销重做/统计。

---

## 1. 功能职责

- 文档生命周期：init/new/open/save/save_as。
- 内容与光标：get/set content、get/set cursor、行列换算。
- 编辑：插入/删除/取区间、undo/redo/can_undo/can_redo。
- 统计：字符/单词/行数。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `doc.dll` 自身 |
| 开发期头文件 | `doc.h` + `graal_isolate.h` |
| 编译期源码 | `MDDoc.java` + `MDIdp.java`（`build-mddoc.bat` 的 -cp） |

---

## 3. 导出 API（见 doc.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `doc_init` | `int doc_init(t, char* baseDir)` | 初始化文档子系统 |
| `doc_new_document` | `int doc_new_document(t)` | 新建空文档 |
| `doc_open_document` | `int doc_open_document(t, char* path)` | 打开文件（UTF-8） |
| `doc_save_document` | `int doc_save_document(t)` | 保存当前文档 |
| `doc_save_document_as` | `int doc_save_document_as(t, char* path)` | 另存为 |
| `doc_get_content` | `int doc_get_content(t, char* outBuf, int size)` | 全文 |
| `doc_set_content` | `int doc_set_content(t, char* text)` | 整篇替换 |
| `doc_get_file_path` | `int doc_get_file_path(t, char* outBuf, int size)` | 当前文件路径 |
| `doc_is_dirty` | `int doc_is_dirty(t)` | 有未保存修改？ |
| `doc_set_cursor` | `void doc_set_cursor(t, int pos)` | 设光标（字节偏移） |
| `doc_get_cursor` | `int doc_get_cursor(t)` | 取光标 |
| `doc_get_cursor_line` / `doc_get_cursor_col` | `int ...(t)` | 光标行列 |
| `doc_insert_text` | `int doc_insert_text(t, int pos, char* text)` | 插入 |
| `doc_delete_text` | `int doc_delete_text(t, int start, int end)` | 删除区间 |
| `doc_get_text` | `int doc_get_text(t, int start, int end, char* outBuf, int size)` | 取区间 |
| `doc_undo` / `doc_redo` / `doc_can_undo` / `doc_can_redo` | `int ...(t)` | 撤销重做 |
| `doc_get_char_count` / `doc_get_word_count` / `doc_get_line_count` | `int ...(t)` | 统计 |
| `doc_write_idp` / `doc_to_idp` / `doc_read_idp` | 见 doc.h | IDP 包装 |

---

## 4. 典型用法

```
doc_init(th, "C:/app/data");
doc_open_document(th, "C:/app/a.md");
char buf[65536]; doc_get_content(th, buf, 65536);
doc_insert_text(th, doc_get_cursor(th), "new");
doc_save_document(th);
doc_undo(th);
```

---

## 5. 注意事项

1. 位置参数为**字节偏移**（UTF-8），不是字符数。
2. `doc_open_document` 失败返回 -1（文件不存在/读失败）。