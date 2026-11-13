# convert-txt2md.dll — 纯文本→MD 转换副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\doc\convert-txt2md.dll` ｜ 配套头文件：`convert-txt2md.h`
> 职责：纯文本转 Markdown（唯一"文件转 MD"通道）。

---

## 1. 功能职责

- `convert_txt2md`：纯文本字符串 → MD 字符串（智能识别标题/列表等）。
- `convert_txt2md_file`：文本文件 → MD 文件。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `convert-txt2md.dll` 自身 |
| 开发期头文件 | `convert-txt2md.h` + `graal_isolate.h` |
| 编译期源码 | `MDConvertTxt2Md.java` + `MDIdp.java`（`build-mdconvert-txt2md.bat` 的 -cp） |

---

## 3. 导出 API（见 convert-txt2md.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `convert_txt2md` | `int convert_txt2md(t, char* text, char* outBuf, int size)` | 纯文本 → MD |
| `convert_txt2md_file` | `int convert_txt2md_file(t, char* srcPath, char* dstPath)` | 文件 → MD 文件 |
| `convert_txt2md_write_idp` / `convert_txt2md_to_idp` / `convert_txt2md_read_idp` | 见 convert-txt2md.h | IDP 包装 |

---

## 4. 典型用法

```
convert_txt2md(th, "第一章\n内容", mdBuf, 65536);
convert_txt2md_file(th, "C:/app/a.txt", "C:/app/a.md");
```

---

## 5. 注意事项

1. 转换规则尽力而为：明显标题行加 #，列表行加 -。
2. 空输入返回 0。