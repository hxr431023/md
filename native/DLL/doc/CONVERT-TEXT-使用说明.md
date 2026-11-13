# convert-text.dll — MD→纯文本 转换副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\doc\convert-text.dll` ｜ 配套头文件：`convert-text.h`
> 职责：MD 转纯文本（去掉标记符号），导出文件或字符串。

---

## 1. 功能职责

- `convert_text`：MD 字符串 → 纯文本字符串（剥离 #、*、[ ] 等标记）。
- `convert_text_file`：MD 文件 → 纯文本文件。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `convert-text.dll` 自身 |
| 开发期头文件 | `convert-text.h` + `graal_isolate.h` |
| 编译期源码 | `MDConvertText.java` + `MDIdp.java`（`build-mdconvert-text.bat` 的 -cp） |

---

## 3. 导出 API（见 convert-text.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `convert_text` | `int convert_text(t, char* md, char* outBuf, int size)` | MD → 纯文本 |
| `convert_text_file` | `int convert_text_file(t, char* srcPath, char* dstPath)` | MD 文件 → 纯文本文件 |
| `convert_text_write_idp` / `convert_text_to_idp` / `convert_text_read_idp` | 见 convert-text.h | IDP 包装 |

---

## 4. 典型用法

```
convert_text(th, "# 标题", txtBuf, 65536);        /* "标题" */
convert_text_file(th, "C:/app/a.md", "C:/app/a.txt");
```

---

## 5. 注意事项

1. 纯文本保留正文与换行，去掉渲染标记。