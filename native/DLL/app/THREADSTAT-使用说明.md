# threadstat.dll — 线程统计副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\app\threadstat.dll` ｜ 配套头文件：`threadstat.h`
> 职责：统计各线程/模块的 CPU、内存、磁盘占用，供可视化/专业诊断。

---

## 1. 功能职责

- 线程注册/注销（按模块名）。
- 采样：CPU/内存/磁盘累计（读/写字节）。
- `threadstat_get_json`：输出当前全部统计 JSON。
- 线程模式查询/设置（folder/single/each）。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `threadstat.dll` 自身 |
| 开发期头文件 | `threadstat.h` + `graal_isolate.h` |
| 编译期源码 | `MDThreadStat.java` + `MDIdp.java`（`build-mdthreadstat.bat` 的 -cp） |

---

## 3. 导出 API（见 threadstat.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `threadstat_init` | `int threadstat_init(t, char* baseDir)` | 初始化 |
| `threadstat_register` | `int threadstat_register(t, char* name, char* group)` | 注册线程（名+所属组） |
| `threadstat_unregister` | `int threadstat_unregister(t, char* name)` | 注销 |
| `threadstat_sample` | `int threadstat_sample(t)` | 采样一次 |
| `threadstat_get_json` | `int threadstat_get_json(t, char* outBuf, int size)` | 统计 JSON（CPU/内存/磁盘） |
| `threadstat_memory` | `int threadstat_memory(t, char* outBuf, int size)` | 内存统计 |
| `threadstat_disk` | `int threadstat_disk(t, char* outBuf, int size)` | 磁盘统计 |
| `threadstat_disk_read` / `threadstat_disk_write` | `void ...(t, long long bytes)` | 累计读/写字节 |
| `threadstat_get_mode` / `threadstat_set_mode` | `int / int(t, char*)` | 线程模式 |
| `mdthreadstat_write_idp` / `mdthreadstat_to_idp` / `mdthreadstat_read_idp` | 见 threadstat.h | IDP 包装 |

---

## 4. 典型用法

```
threadstat_init(th, "C:/app/data");
threadstat_register(th, "render", "doc");
threadstat_disk_read(th, 1024);
threadstat_sample(th);
char json[65536]; threadstat_get_json(th, json, 65536);
```

---

## 5. 注意事项

1. 统计按模块名聚合；未注册模块自动归 "unknown"。
2. 磁盘累计由调用方在读写后主动上报字节数。