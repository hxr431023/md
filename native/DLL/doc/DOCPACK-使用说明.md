# docpack.dll — doc 分类合体主控使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\doc\docpack.dll` ｜ 配套头文件：`docpack.h`
> 职责：**doc 分类文件夹的主控 DLL**，把该分类 8 个副 DLL 组合成一个 DLL，
> 控制层（前端）只需与主控交互。

---

## 1. 主控与副 DLL 的关系

| 本分类副 DLL | 编入 docpack.dll 的源码 | 对外导出前缀 |
|--------------|-------------------------|--------------|
| markdown.dll（MD 解析） | MDMarkdown.java | `markdown_*` + `render_html`/`classify_line`/`count_*` |
| doc.dll（文档模型） | MDDoc.java | `doc_*` |
| render.dll（渲染指令） | MDRender.java | `render_*`（含内嵌 markdown 组） |
| convert-html.dll（MD→HTML） | MDConvertHtml.java | `convert_html*` |
| convert-text.dll（MD→文本） | MDConvertText.java | `convert_text*` |
| convert-txt2md.dll（文本→MD） | MDConvertTxt2Md.java | `convert_txt2md*` |
| quicksort.dll（快排） | MDQuickSort.java | `quicksort_*` |
| idp.dll（内存栈） | MDIdp.java | `idp_*` |

**组合方式**：docpack.dll 编译时把这 8 个 Java 源码一并编入（见
`DLL\doc\build-docpack.bat`）。加载 docpack.dll 后，8 组功能导出同时可用，
控制层无需再分别加载 7 个副 DLL。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `docpack.dll` 自身（GraalVM 自包含） |
| 开发期头文件 | `docpack.h` + `graal_isolate.h` |
| 编译期源码 | 8 模块（见上表，build-docpack.bat 的 -cp） |
| 同目录配套 | `docpack_dynamic.h`、`graal_isolate_dynamic.h` |

> 若只需要其中某一功能（如只要 MD→HTML），可直接附带对应副 DLL
> （convert-html.dll），不必加载整个 docpack.dll。

---

## 3. 导出 API 一览（全部 8 组，签名见 docpack.h）

- **文档**：`doc_init` / `doc_new_document` / `doc_open_document` / `doc_save_document` / `doc_save_document_as` / `doc_get_content` / `doc_set_content` / `doc_get_file_path` / `doc_is_dirty` / `doc_set_cursor` / `doc_get_cursor*` / `doc_insert_text` / `doc_delete_text` / `doc_get_text` / `doc_undo` / `doc_redo` / `doc_can_undo` / `doc_can_redo` / `doc_get_char_count` / `doc_get_word_count` / `doc_get_line_count` + IDP 三件套
- **渲染**：`render_ui` / `render_line` / `render_line_count` / `render_get_line` / `render_find` / `render_replace` / `render_insert` / `render_paged` + IDP 三件套
- **MD 解析**：`render_html` / `classify_line` / `count_inline_styles` / `count_lines` / `count_words` / `count_chars` + `markdown_*` IDP 三件套
- **转换**：`convert_html` / `convert_html_file`、`convert_text` / `convert_text_file`、`convert_txt2md` / `convert_txt2md_file`（各含 IDP 三件套）
- **快排**：`quicksort_ints` / `quicksort_strings` / `quicksort_json_by_field` / `quicksort_info` + IDP 三件套
- **IDP 栈**：`idp_push` / `idp_pop_json` / `idp_pop_idp` / `idp_peek_json` / `idp_stack_size` / `idp_stack_clear`

详细参数与行为见同目录各副 DLL 的独立使用说明。

---

## 4. 典型用法（控制层只调 docpack.dll）

```
doc_init(th, "C:/app/data");
doc_open_document(th, "C:/app/a.md");
char html[65536];
render_html(th, doc内容, html, 65536);        /* 预览渲染 */
convert_html_file(th, "a.md", "a.html");       /* 导出 HTML */
char pages[65536];
render_paged(th, doc内容, 80, 24, pages, 65536); /* 打印分页（自动页码） */
quicksort_strings(th, "[\"b\",\"a\"]", "asc", buf, 4096);
```

---

## 5. 调用示例（Python ctypes 只加载 docpack.dll）

```python
import ctypes
from ctypes import c_char_p, c_int, c_void_p

dll = ctypes.CDLL(r"C:\wpjc\md\native\DLL\doc\docpack.dll")
P = ctypes.c_void_p
isolate, thread = P(), P()
dll.graal_create_isolate(None, ctypes.byref(isolate), ctypes.byref(thread))

dll.doc_init.argtypes = [P, c_char_p]
dll.doc_init(thread, b"C:/app/data")
dll.doc_open_document.argtypes = [P, c_char_p]
dll.doc_open_document(thread, b"C:/app/a.md")

dll.render_html.argtypes = [P, c_char_p, c_char_p, c_int]
buf = ctypes.create_string_buffer(65536)
dll.render_html(thread, b"# hi", buf, 65536)
print(buf.value)
```

---

## 6. 通信铁律

docpack.dll 内部各组 Java 类为**同 DLL 内静态直调**（编译期绑定，非跨 DLL 通信）；
对外（前端或其他 DLL）一律只通过本 DLL 导出的 `idp_*` 内存栈收发，
严禁文件型 IDP、句柄或其他直通方式。

---

## 7. 注意事项

1. 文档/渲染共用同一内容源（MDDoc 实例），先 doc_open 再 render。
2. 副 DLL 独立使用时，其 IDP 栈独立，与 docpack.dll 不互通。