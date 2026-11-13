# convert-html.dll — MD→HTML 转换副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\doc\convert-html.dll` ｜ 配套头文件：`convert-html.h`
> 职责：MD 转 HTML+CSS 的独立转换器（导出文件或字符串）。

---

## 1. 功能职责

- `convert_html`：MD 字符串 → 完整 HTML（含 CSS）字符串。
- `convert_html_file`：MD 文件 → HTML 文件。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `convert-html.dll` 自身 |
| 开发期头文件 | `convert-html.h` + `graal_isolate.h` |
| 编译期源码 | `MDConvertHtml.java` + `MDIdp.java`（`build-mdconvert-html.bat` 的 -cp） |

---

## 3. 导出 API（见 convert-html.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `convert_html` | `int convert_html(t, char* md, char* outBuf, int size)` | MD → HTML（含 CSS） |
| `convert_html_file` | `int convert_html_file(t, char* srcPath, char* dstPath)` | MD 文件 → HTML 文件 |
| `convert_html_write_idp` / `convert_html_to_idp` / `convert_html_read_idp` | 见 convert-html.h | IDP 包装 |

---

## 4. 典型用法

```
convert_html(th, "# 标题", htmlBuf, 65536);
convert_html_file(th, "C:/app/a.md", "C:/app/a.html");
```

---

## 5. 注意事项

1. 输出为**完整 HTML+CSS**，可直接保存为 .html 打开。
2. 转换失败返回 -1。