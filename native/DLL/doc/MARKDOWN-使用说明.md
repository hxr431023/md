# markdown.dll — MD 解析/统计副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\doc\markdown.dll` ｜ 配套头文件：`markdown.h`
> 职责：MD 文本解析核心：行分类、统计、HTML 渲染基础能力，供渲染/转换模块复用。

---

## 1. 功能职责

- `render_html`：MD 文本 → HTML（基础渲染引擎）。
- `classify_line`：判断一行 MD 文本的类型（标题/列表/引用/代码/普通等）。
- 统计：内联样式数、行数、单词数、字符数。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `markdown.dll` 自身（自带 MDIdp 内存栈导出） |
| 开发期头文件 | `markdown.h` + `graal_isolate.h` |
| 编译期源码 | `MDMarkdown.java` + `MDIdp.java`（`build-mdmarkdown.bat` 的 -cp） |

---

## 3. 导出 API（见 markdown.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `render_html` | `int render_html(t, char* md, char* outBuf, int size)` | MD → HTML |
| `classify_line` | `int classify_line(t, char* line)` | 行类型码（见源码 MDMarkdown 常量） |
| `count_inline_styles` | `int count_inline_styles(t, char* md)` | 内联样式（加粗/斜体/代码等）总数 |
| `count_lines` | `int count_lines(t, char* md)` | 行数 |
| `count_words` | `int count_words(t, char* md)` | 单词数 |
| `count_chars` | `int count_chars(t, char* md)` | 字符数 |
| `markdown_write_idp` | `int markdown_write_idp(t, char* filePath, char* type, char* jsonData)` | IDP 入栈（filePath 废弃） |
| `markdown_to_idp` | `int markdown_to_idp(t, char* filePath, char* type, char* json, char* outBuf, int size)` | 处理并把结果写入 outBuf |
| `markdown_read_idp` | `int markdown_read_idp(t, char* filePath, char* outBuf, int size)` | IDP 出栈 |

---

## 4. 典型用法

```
char html[65536];
render_html(th, "# Hello\n**bold**", html, 65536);
int n = count_words(th, "a b c");      /* 3 */
int t = classify_line(th, "## Title"); /* 标题类 */
```

---

## 5. 注意事项

1. `render_html` 输出完整 HTML 文档（含 CSS），不是片段。
2. 统计函数对空字符串返回 0，不会崩。