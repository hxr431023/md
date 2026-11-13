# idp.dll — 内存双向栈 IDP 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\core\idp.dll` ｜ 配套头文件：`idp.h`
> 职责：**渲染与 CMD 的通信通道**。渲染（`render.dll` / `markdown.dll`）和
> CMD（`cmd.dll`）走本 DLL 的内存双向栈；其余模块直接用各自的 C 函数接口。
> 严禁文件型 IDP、句柄或其他直通方式。

---

## 1. 功能职责

- 提供**单内存双向栈**（ArrayDeque 静态实例）：
  - 队尾入队（pushBack / enqueue）= 发请求
  - 队首出队（popFront）= 取请求（先入先出）
  - 队首压入（pushFront）= 放结果（后入先取）
  - 队尾弹出（popBack）= 取最新请求（controller 分发用）
- 数据包结构：`{version, type, timestamp, source, size, jsonData}`。
- 文件型接口（writePacket/readPacket/readJsonData）已废弃，仅保留定义，业务模块不再调用。

**使用范围**：只有渲染和 CMD 走 `idp_push`（入栈）与
`idp_pop_json` / `idp_pop_idp`（出栈取结果）；其他模块同步直调 C 函数，
不允许文件中间件、共享句柄、直接函数指针直通。

---

## 2. 依赖清单

要使用 idp.dll，**不需要任何其他 DLL**（GraalVM 自包含运行时）。

| 项 | 说明 |
|----|------|
| 运行时 | 仅 `idp.dll` 自身（含 GraalVM isolate 运行时） |
| 开发期 | `idp.h` + `graal_isolate.h`（同目录） |
| 编译期源码 | 仅 `MDIdp.java`（`modules\mdidp\src`） |

> 注意：其他每个 DLL 在编译时都把 MDIdp 编入自身（各自自带 idp_* 导出），
> 因此**副 DLL 单独用时同样自带栈**；但当控制层（controller.dll）被前端加载时，
> 19 个模块共享 controller.dll 内部的那一份内存栈，实现"单内存"。

---

## 3. 导出 API（见 idp.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `idp_push` | `int idp_push(graal_isolatethread_t*, char* type, char* source, char* jsonData)` | 压一个请求包到队尾（enqueue），返回 0 |
| `idp_pop_json` | `int idp_pop_json(graal_isolatethread_t*, char* outBuf, int bufSize)` | 从队首弹出一个包并写入其 JSON 数据，返回长度 |
| `idp_pop_idp` | `int idp_pop_idp(graal_isolatethread_t*, char* outBuf, int bufSize)` | 从队首弹出一个完整包（含 type/source/json），返回长度 |
| `idp_peek_json` | `int idp_peek_json(graal_isolatethread_t*, char* outBuf, int bufSize)` | 只读队首 JSON 不弹出 |
| `idp_stack_size` | `int idp_stack_size(graal_isolatethread_t*)` | 当前栈内包数量 |
| `idp_stack_clear` | `int idp_stack_clear(graal_isolatethread_t*)` | 清空整个栈 |

---

## 4. 典型用法（前端 → 控制层）

```
1. idp_push(thread, "app_render_html", "python", "{\"md\":\"# hi\"}")
2. app_idp_process(thread)            ← controller 分发处理（见 controller 文档）
3. idp_pop_json(thread, buf, size)     ← 取回 "result:app_render_html" 的结果 JSON
```

---

## 5. 调用示例（Python ctypes）

```python
import ctypes
from ctypes import c_char_p, c_int, c_void_p

dll = ctypes.CDLL(r"C:\wpjc\md\native\DLL\core\idp.dll")
P = ctypes.c_void_p
dll.graal_create_isolate.restype = c_int
dll.graal_create_isolate.argtypes = [c_void_p, ctypes.POINTER(P), ctypes.POINTER(P)]
dll.idp_push.restype = c_int
dll.idp_push.argtypes = [P, c_char_p, c_char_p, c_char_p]
dll.idp_pop_json.restype = c_int
dll.idp_pop_json.argtypes = [P, c_char_p, c_int]

isolate, thread = P(), P()
dll.graal_create_isolate(None, ctypes.byref(isolate), ctypes.byref(thread))
dll.idp_push(thread, b"app_version", b"python", b"{}")
dll.idp_pop_json(thread, buf, 4096)   # 若直接弹回即刚压的请求包
```

---

## 6. 注意事项

1. 栈是**进程内单实例**（static 同步栈）：同一 isolate 内所有模块共享。
2. `idp_pop_json` 弹出的若是请求包（未被 controller 处理），前端需自行区分
   type 前缀；controller 处理后结果包 type 为 `result:<原type>`。
3. 严禁把 JSON 写文件再让另一端读文件——那是被废除的文件型 IDP。