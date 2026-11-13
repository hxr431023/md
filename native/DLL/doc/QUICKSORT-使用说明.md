# quicksort.dll — 快速排序副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\doc\quicksort.dll` ｜ 配套头文件：`quicksort.h`
> 职责：通用快速排序：整数/字符串/JSON 数组按字段排序。

---

## 1. 功能职责

- `quicksort_ints`：整数数组排序（JSON 数组输入）。
- `quicksort_strings`：字符串数组排序。
- `quicksort_json_by_field`：JSON 对象数组按指定字段排序。
- `quicksort_info`：排序信息/统计。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `quicksort.dll` 自身 |
| 开发期头文件 | `quicksort.h` + `graal_isolate.h` |
| 编译期源码 | `MDQuickSort.java` + `MDIdp.java`（`build-mdquicksort.bat` 的 -cp） |

---

## 3. 导出 API（见 quicksort.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `quicksort_ints` | `int quicksort_ints(t, char* jsonArr, char* order, char* outBuf, int size)` | 排序整数数组，order=asc/desc |
| `quicksort_strings` | `int quicksort_strings(t, char* jsonArr, char* order, char* outBuf, int size)` | 排序字符串数组 |
| `quicksort_json_by_field` | `int quicksort_json_by_field(t, char* jsonObjs, char* field, char* order, char* outBuf, int size)` | 对象数组按字段排 |
| `quicksort_info` | `int quicksort_info(t, char* outBuf, int size)` | 算法信息 |
| `quicksort_write_idp` / `quicksort_to_idp` / `quicksort_read_idp` | 见 quicksort.h | IDP 包装 |

---

## 4. 典型用法

```
quicksort_ints(th, "[3,1,2]", "asc", buf, 4096);           /* [1,2,3] */
quicksort_strings(th, "[\"b\",\"a\"]", "desc", buf, 4096); /* ["b","a"] */
quicksort_json_by_field(th, "[{\"n\":2},{\"n\":1}]", "n", "asc", buf, 4096);
```

---

## 5. 注意事项

1. 输入必须是合法 JSON 数组；字段排序对缺失字段按 null 处理。
2. 排序稳定，不改变原输入。