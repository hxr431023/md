# file.dll — 文件操作副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\core\file.dll` ｜ 配套头文件：`file.h`
> 职责：纯文件读写与元信息，任何模块可复用。

---

## 1. 功能职责

- 读写/追加、存在性、大小、删除、取文件名/扩展名。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 | `file.dll` 自身 |
| 开发期 | `file.h` + `graal_isolate.h` |
| 编译期源码 | `MDFile.java` + `MDIdp.java`（`build-mdfile.bat` 的 -cp） |

---

## 3. 导出 API（见 file.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `file_read` | `int file_read(t, char* path, char* outBuf, int size)` | 读整个文件到缓冲区 |
| `file_write` | `int file_write(t, char* path, char* text)` | 覆写 |
| `file_append` | `int file_append(t, char* path, char* text)` | 追加 |
| `file_exists` | `int file_exists(t, char* path)` | 1/0 |
| `file_size` | `long long file_size(t, char* path)` | 字节数 |
| `file_delete` | `int file_delete(t, char* path)` | 删除，0 成功 |
| `file_name` | `int file_name(t, char* path, char* outBuf, int size)` | 取文件名 |
| `file_extension` | `int file_extension(t, char* path, char* outBuf, int size)` | 取扩展名 |
| `file_write_idp` / `file_to_idp` / `file_read_idp` | 见 file.h | IDP 包装（filePath 废弃） |

---

## 4. 典型用法

```
file_write(thread, "C:/app/out.txt", "hello")
char buf[256]; int n = file_read(thread, "C:/app/out.txt", buf, 256)
file_append(thread, "C:/app/out.txt", " world")
long long sz = file_size(thread, "C:/app/out.txt")
```

---

## 5. 注意事项

1. 读写均 UTF-8 文本语义。
2. 缓冲区过小返回 -1 或截断，请按实际大小预分配。