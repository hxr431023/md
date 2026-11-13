# single.dll — 单实例互锁副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\core\single.dll` ｜ 配套头文件：`single.h`
> 职责：进程单实例互锁（"程序已在运行"提示）。

---

## 1. 功能职责

- 基于锁文件的互斥：`single_try_lock` 尝试锁定，返回 1=锁定成功 / 0=已有实例。
- 提供释放、查询、锁路径查询。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 | `single.dll` 自身 |
| 开发期 | `single.h` + `graal_isolate.h` |
| 编译期源码 | `MDSingle.java` + `MDIdp.java`（`build-mdsingle.bat` 的 -cp） |

---

## 3. 导出 API（见 single.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `single_try_lock` | `int single_try_lock(t, char* name)` | 尝试锁定；返回 1=拿到锁，0=已存在实例 |
| `single_release` | `void single_release(t)` | 释放锁 |
| `single_is_locked` | `int single_is_locked(t)` | 当前是否仍持有 |
| `single_lock_path` | `int single_lock_path(t, char* outBuf, int size)` | 锁文件路径 |
| `single_write_idp` / `single_to_idp` / `single_read_idp` | 见 single.h | IDP 包装 |

---

## 4. 典型用法（程序启动时）

```
if (single_try_lock(thread, "mdrenderer") == 0) {
    /* 已有实例：提示并退出 */
} else {
    /* 正常启动；退出前 single_release(thread) */
}
```

---

## 5. 注意事项

1. 锁文件位置由实现决定（进程临时/缓存目录），`single_lock_path` 可查。
2. 退出前务必 release，否则锁残留（下次会误判"已在运行"）。