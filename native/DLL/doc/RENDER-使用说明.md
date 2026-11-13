# render.dll — 渲染副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\doc\render.dll` ｜ 配套头文件：`render.h`
> 职责：渲染指令生成（UI 指令/分页/查找/替换/插入），依赖 markdown 解析。

---

## 1. 功能职责

- `render_ui` / `render_line` / `render_line_count` / `render_get_line`：把 MD 行转成
  前端渲染指令（JSON 风格输出），供前端直接绘制。
- `render_find`：查找——返回内容中查询词出现位置/数量。
- `render_replace`：查找替换。
- `render_insert`：按"左边是什么、右边是什么、中间插入什么"规则插入。
- `render_paged`：分页——按页宽/页高把内容切成多页（打印/视图用）。
- 同时导出 markdown_* 组（编译时内嵌 MDMarkdown）。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `render.dll` 自身（内嵌 MDMarkdown + MDIdp） |
| 开发期头文件 | `render.h` + `graal_isolate.h` |
| 编译期源码 | `MDRender.java` + `MDMarkdown.java` + `MDIdp.java`（`build-mdrender.bat` 的 -cp） |

> **依赖关系**：render.dll 需要 markdown 解析能力，编译时已内嵌；
> 单独附带 render.dll 即可，**不需要**再附带 markdown.dll。

---

## 3. 导出 API（见 render.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `render_ui` | `int render_ui(t, char* md, char* outBuf, int size)` | 生成 UI 渲染指令 JSON |
| `render_line` | `int render_line(t, char* line, char* outBuf, int size)` | 单行渲染指令 |
| `render_line_count` | `int render_line_count(t, char* md)` | 渲染行数 |
| `render_get_line` | `int render_get_line(t, char* md, int idx, char* outBuf, int size)` | 取第 idx 行 |
| `render_find` | `int render_find(t, char* content, char* query, char* outBuf, int size)` | 查找：结果 JSON（位置/次数） |
| `render_replace` | `int render_replace(t, char* content, char* find, char* replace, char* outBuf, int size)` | 替换：返回替换后内容 |
| `render_insert` | `int render_insert(t, char* content, char* left, char* right, char* insert, char* outBuf, int size)` | 在 left…right 之间插 insert |
| `render_paged` | `int render_paged(t, char* content, int pageWidth, int pageHeight, char* outBuf, int size)` | 分页：返回页列表 JSON |
| `render_html` | `int render_html(t, char* md, char* outBuf, int size)` | MD → HTML（来自内嵌 markdown） |
| `classify_line` / `count_*` | 见 markdown.h | 内嵌统计 |
| `render_write_idp` / `render_to_idp` / `render_read_idp` | 见 render.h | IDP 包装 |

---

## 4. 典型用法

```
/* 查找 */
char r[4096]; render_find(th, "ab c ab", "ab", r, 4096);   /* JSON: 位置列表 */

/* 替换 */
render_replace(th, "ab c ab", "ab", "XY", r, 4096);        /* "XY c XY" */

/* 插入：左边是 [，右边是 ]，中间插入文字 */
render_insert(th, "a[b]c", "[", "]", "xx", r, 4096);       /* "a[xx]b? 按实现 */ 

/* 分页：宽 80 高 24 */
render_paged(th, "long text...", 80, 24, r, 4096);         /* 多页 JSON，自动页码 */
```

---

## 5. 注意事项

1. 分页自动标页码：内容超出页宽/页高即进第二页。
2. 所有 *Json* 输出为 UTF-8 JSON，前端直接解析。
3. 单独需要 markdown 统计/解析时，可只带 markdown.dll（更小）。