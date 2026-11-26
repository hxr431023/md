# doc 文件夹（文档与渲染）

主控：`docpack.dll`（文档 + 渲染 + Markdown + 转换 + 排序的合并版，53 个导出）。

**全部依赖**：无；`markdown.dll` / `render.dll` 的 `*_idp` 三个函数额外需要 `%HDLL%\app\controller.dll`（通过 C 接口使用其唯一 IDP 栈）。

---

## docpack.dll（主控，53 个导出）

**推荐给另一个 AI 的数学库/文档工具直接接这个。**

| 组 | 函数 |
|---|---|
| 文档 23 | `doc_init`、`doc_new_document`、`doc_open_document`、`doc_save_document`、`doc_save_document_as`、`doc_get_content`、`doc_set_content`、`doc_get_file_path`、`doc_is_dirty`、`doc_set_cursor`、`doc_get_cursor`、`doc_get_cursor_line`、`doc_get_cursor_col`、`doc_insert_text`、`doc_delete_text`、`doc_get_text`、`doc_undo`、`doc_redo`、`doc_can_undo`、`doc_can_redo`、`doc_get_char_count`、`doc_get_word_count`、`doc_get_line_count` |
| Markdown 6 | `render_html`、`classify_line`、`count_inline_styles`、`count_lines`、`count_words`、`count_chars` |
| 渲染 11 | `render_ui`、`render_line`、`render_line_count`、`render_get_line`、`render_find`、`render_replace`、`render_insert`、`render_paged`、`render_write_idp`、`render_to_idp`、`render_read_idp` |
| Markdown-IDP 3 | `markdown_write_idp`、`markdown_to_idp`、`markdown_read_idp` |
| 转换 6 | `convert_html`、`convert_html_file`、`convert_text`、`convert_text_file`、`convert_txt2md`、`convert_txt2md_file` |
| 排序 4 | `quicksort_ints`、`quicksort_strings`、`quicksort_json_by_field`、`quicksort_info` |

### 最小用法

```python
lib = ctypes.CDLL(HD + r"\doc\docpack.dll")

# Markdown → HTML 片段
n = lib.render_html(None, "# 标题\n**粗体**".encode(), buf, len(buf))

# 文档
lib.doc_set_content(None, "第一行\n第二行".encode())
lib.doc_insert_text(None, 0, "标题\n".encode())
n = lib.doc_get_content(None, buf, len(buf))

# 分页
n = lib.render_paged(None, content.encode(), 794, 1123, buf, len(buf))
# {"view":"paged","pageWidth":794,"pageHeight":1123,"pageCount":N,
#  "pages":[{"page":1,"elements":[...],"footer":{"text":"— 1 —"}}]}
```

### 分页规则

- 行高 22 px
- 页体高 = `max(pageHeight - 90, 100)`
- 行宽按 8 px/列，CJK 记 2 列，最小 8 列
- 超长段落按行切块，列表/代码块可跨页；页码由 `page` 字段驱动

---

## doc.dll（23 个导出）

**依赖**：无。纯文档模型。

```python
lib.doc_set_content(None, "abc".encode())
lib.doc_insert_text(None, 0, "标题\n".encode())
n = lib.doc_get_text(None, 0, 3, buf, len(buf))
```

- 偏移量按 **rune**（Unicode 码点），与 Java 的 UTF-16 在 BMP 内等价。
- undo 栈上限 500，初始状态入栈；`can_undo` 在只有初始状态时为 false。
- `doc_insert_text` 后光标 = 插入位置 + 文本长度。
- `doc_delete_text(start, end)` 区间为 `[start, end)`。

---

## markdown.dll（9 个导出）

**依赖**：无（纯函数）；`markdown_*_idp` 需要 `controller.dll`。

`render_html`、`classify_line`、`count_inline_styles`、`count_lines`、`count_words`、`count_chars`、`markdown_write_idp`、`markdown_to_idp`、`markdown_read_idp`。

---

## render.dll（11 个导出）

**依赖**：无（纯函数）；`render_*_idp` 需要 `controller.dll`。

`render_ui`、`render_line`、`render_line_count`、`render_get_line`、`render_find`、`render_replace`、`render_insert`、`render_paged`、`render_write_idp`、`render_to_idp`、`render_read_idp`。

**返回差异**：

- 直接调用：`render_find` 返回 `{"count":N,"positions":[...]}`；
- 走 controller 的 `idp_push("render_find", ...)`：外面多一层 `{"result": ...}`，命中缓存再带 `"cached":true`。

---

## convert-html.dll（2 个）

- `convert_html(md, buf, size)` → 完整 HTML 文档
- `convert_html_file(in, out)`

## convert-text.dll（2 个）

- `convert_text(md, buf, size)` → 纯文本
- `convert_text_file(in, out)`

## convert-txt2md.dll（2 个）

- `convert_txt2md(text, buf, size)` → Markdown
- `convert_txt2md_file(in, out)`

三个转换 DLL 都**无依赖**。

---

## quicksort.dll（4 个）

**依赖**：无。

```python
n = lib.quicksort_ints(None, b"3,1,2", b"true", buf, len(buf))       # 1,2,3
n = lib.quicksort_strings(None, "b,a,c".encode(), b"true", buf, len(buf))
n = lib.quicksort_json_by_field(None, b'[{"v":2},{"v":1}]', b"v", b"false", buf, len(buf))
n = lib.quicksort_info(None, buf, len(buf))
```

`quicksort_info` 返回：

```json
{"algorithm":"quick_sort","avg_complexity":"O(n log n)","worst_complexity":"O(n^2)","space":"O(log n)","stable":false}
```
