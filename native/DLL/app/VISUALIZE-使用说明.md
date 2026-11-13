# visualize.dll — 可视化副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\app\visualize.dll` ｜ 配套头文件：`visualize.h`
> 职责：把线程统计数据渲染成 HTML（实时可视化），可保存快照。

---

## 1. 功能职责

- `visualize_stats_to_html`：统计 JSON → HTML 图表（实时渲染用）。
- `visualize_stats_to_file`：保存当前统计 HTML 快照到文件。
- `visualize_stats_sorted_html`：先按字段快排再生成 HTML（依赖 quicksort）。
- `visualize_thread_modes`：三种线程模式的说明/当前值。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `visualize.dll` 自身（内嵌 MDQuickSort） |
| 开发期头文件 | `visualize.h` + `graal_isolate.h` |
| 编译期源码 | `MDVisualize.java` + `MDQuickSort.java` + `MDIdp.java`（`build-mdvisualize.bat` 的 -cp） |

> **依赖关系**：visualize 需要排序能力，编译时已内嵌 quicksort；
> 单独附带 visualize.dll 即可，不需要再附带 quicksort.dll。

---

## 3. 导出 API（见 visualize.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `visualize_stats_to_html` | `int visualize_stats_to_html(t, char* statsJson, char* outBuf, int size)` | 统计 JSON → HTML |
| `visualize_stats_to_file` | `int visualize_stats_to_file(t, char* statsJson, char* dstPath)` | 保存 HTML 快照 |
| `visualize_stats_sorted_html` | `int visualize_stats_sorted_html(t, char* statsJson, char* field, char* order, char* outBuf, int size)` | 排序后 → HTML |
| `visualize_thread_modes` | `int visualize_thread_modes(t, char* outBuf, int size)` | 线程模式说明 |
| `quicksort_ints` / `quicksort_strings` / `quicksort_json_by_field` / `quicksort_info` | 见 quicksort.h | 内嵌快排 |
| `mdvisualize_write_idp` / `mdvisualize_to_idp` / `mdvisualize_read_idp` | 见 visualize.h | IDP 包装 |

---

## 4. 典型用法

```
visualize_stats_to_html(th, threadstatJson, htmlBuf, 65536);
visualize_stats_sorted_html(th, threadstatJson, "cpu", "desc", htmlBuf, 65536);
visualize_stats_to_file(th, threadstatJson, "C:/app/stats.html");
```

---

## 5. 注意事项

1. 输入必须是 threadstat_get_json 输出格式的 JSON。
2. HTML 为自包含文件（内联 CSS/JS），可直接保存打开。