# compress.dll — 纯压缩副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\core\compress.dll` ｜ 配套头文件：`compress.h`
> 职责：快速压缩/解压（DEFLATE 快速档，后缀 `.zst`），供其他模块复用。

---

## 1. 功能职责

- 文件 ↔ 文件压缩/解压、字符串 ↔ 文件压缩/解压、压缩率查询。
- 算法：JDK `Deflater` BEST_SPEED（快速档），产物后缀 `.zst`。
- 独立副 DLL：控制层不经过它时，其他模块（文档缓存、日志归档等）可直接调用。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 | `compress.dll` 自身（自带 MDIdp 内存栈导出，无需外部 IDP DLL） |
| 开发期 | `compress.h` + `graal_isolate.h` |
| 编译期源码 | `MDCompress.java` + `MDIdp.java`（`build-mdcompress.bat` 的 -cp） |

---

## 3. 导出 API（见 compress.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `compress_file` | `int compress_file(t, char* src, char* dst)` | 压缩文件 src → dst（.zst） |
| `compress_decompress_file` | `int compress_decompress_file(t, char* src, char* dst)` | 解压 .zst → 文件 |
| `compress_string_to_file` | `int compress_string_to_file(t, char* text, char* dst)` | 字符串压缩到文件 |
| `compress_file_to_string` | `int compress_file_to_string(t, char* src, char* buf, int size)` | 文件解压到字符串缓冲区 |
| `compress_ratio` | `int compress_ratio(t, char* src)` | 压缩率（百分比整数） |
| `compress_write_idp` | `int compress_write_idp(t, char* filePath, char* type, char* jsonData)` | IDP 入栈（filePath 已废弃） |
| `compress_to_idp` | `int compress_to_idp(t, char* filePath, char* type, char* json, char* outBuf, int size)` | 处理并直接把结果写入 outBuf |
| `compress_read_idp` | `int compress_read_idp(t, char* filePath, char* outBuf, int size)` | IDP 出栈（filePath 已废弃） |

---

## 4. 典型用法

```
compress_file(thread, "data.json", "data.json.zst")
compress_decompress_file(thread, "data.json.zst", "data.json")
compress_string_to_file(thread, "hello", "h.zst")
int ratio = compress_ratio(thread, "data.json")
```

---

## 5. 调用示例（C）

```c
#include "graal_isolate.h"
#include "compress.h"

int main(void) {
    graal_isolate_t* iso = NULL; graal_isolatethread_t* th = NULL;
    graal_create_isolate(NULL, &iso, &th);
    compress_file(th, "C:/app/data.json", "C:/app/data.json.zst");
    compress_decompress_file(th, "C:/app/data.json.zst", "C:/app/out.json");
    graal_detach_all_threads_and_tear_down_isolate(th);
    return 0;
}
```

---

## 6. 注意事项

1. 压缩是**同步阻塞**调用，大数据量时注意耗时。
2. `.zst` 是内部约定后缀（实际为 Deflater 快速档），与标准 zstd 无关。
3. `compress_ratio` 返回整数百分比；解压失败返回 -1。