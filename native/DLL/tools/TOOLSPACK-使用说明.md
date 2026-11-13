# toolspack.dll — tools 分类合体主控使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\tools\toolspack.dll` ｜ 配套头文件：`toolspack.h`
> 职责：**tools 分类文件夹的主控 DLL**，把 tmmf + lerf 组合成一个 DLL，
> 控制层（前端）只需与主控交互。

---

## 1. 主控与副 DLL 的关系

| 本分类副 DLL | 编入 toolspack.dll 的源码 | 对外导出前缀 |
|--------------|---------------------------|--------------|
| tmmf.dll（TMMF 解析） | MDTmmf.java | `tmmf_*` |
| lerf.dll（LERF 解析） | MDLerf.java | `lerf_*` |
| idp.dll（内存栈） | MDIdp.java | `idp_*` |

**组合方式**：toolspack.dll 编译时把 3 个 Java 源码一并编入（见
`DLL\tools\build-toolspack.bat`）。加载 toolspack.dll 后，tmmf_* 与 lerf_*
同时可用。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `toolspack.dll` 自身 |
| 开发期头文件 | `toolspack.h` + `graal_isolate.h` |
| 编译期源码 | `MDTmmf` + `MDLerf` + `MDIdp`（build-toolspack.bat 的 -cp） |
| 同目录配套 | `toolspack_dynamic.h`、`graal_isolate_dynamic.h` |

> 只要 TMMF 或只要 LERF，可分别只带 tmmf.dll / lerf.dll。

---

## 3. 导出 API 一览（签名见 toolspack.h）

- **TMMF**：`tmmf_read_all` / `tmmf_read_tag` / `tmmf_search` / `tmmf_list_tags` / `tmmf_line_count` / `tmmf_write_tag`
- **LERF**：`lerf_load` / `lerf_get` / `lerf_get_from_file` / `lerf_get_multi` / `lerf_list_all` / `lerf_validate` / `lerf_key_count`

> 工具包只提供 C 函数接口（同步直调）；IDP 仅保留给渲染与 CMD，工具包不提供 IDP 导出。

---

## 4. 典型用法

```
tmmf_search(th, "C:/app/emoji.tmmf", "开心", buf, 4096);
lerf_load(th, "C:/app/icon.lerf");
lerf_get(th, "icon", buf, 256);
```

---

## 5. 通信铁律

toolspack.dll 内部 Java 类为同 DLL 内静态直调；对外一律只走 `idp_*` 内存栈，
严禁文件型 IDP、句柄或其他直通方式。