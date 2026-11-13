# cmd.dll — CMD 命令执行副 DLL 使用说明

> 版本：V1.0.10 ｜ 位置：`DLL\app\cmd.dll` ｜ 配套头文件：`cmd.h`
> 职责：执行系统命令（CMD），供彩蛋 `\\>` 前缀使用。

---

## 1. 功能职责

- `cmd_exec`：执行命令字符串，捕获输出。
- `cmd_help`：CMD 帮助信息。
- 彩蛋协议：编辑器输入行以 `\\>`（两个反斜杠 + 右尖括号，CMD 样式）开头时，
  该行被识别为命令，交给 cmd.dll 执行；渲染时该行替换为结果，但**保存保留原文**
  （二次打开可二次执行）。

---

## 2. 依赖清单

| 项 | 说明 |
|----|------|
| 运行时 DLL | 仅 `cmd.dll` 自身 |
| 开发期头文件 | `cmd.h` + `graal_isolate.h` |
| 编译期源码 | `MDCmd.java` + `MDIdp.java`（`build-mdcmd.bat` 的 -cp） |

---

## 3. 导出 API（见 cmd.h）

| 函数 | 签名 | 说明 |
|------|------|------|
| `cmd_exec` | `int cmd_exec(t, char* command, char* outBuf, int size)` | 执行命令，输出到 outBuf |
| `cmd_help` | `int cmd_help(t, char* outBuf, int size)` | 帮助文本 |
| `cmd_write_idp` | `int cmd_write_idp(t, char* filePath, char* type, char* jsonData)` | IDP 入栈 |
| `cmd_to_idp` | `int cmd_to_idp(t, char* filePath, char* type, char* json, char* outBuf, int size)` | 处理并写入 outBuf |
| `cmd_read_idp` | `int cmd_read_idp(t, char* filePath, char* outBuf, int size)` | IDP 出栈 |

---

## 4. 典型用法

```
cmd_exec(th, "dir /b C:\\app", buf, 4096);
cmd_exec(th, "notepad.exe", buf, 4096);   /* 打开程序，支持传参 */
cmd_help(th, buf, 4096);
```

---

## 5. 注意事项

1. 命令输出为 UTF-8；超长输出截断到缓冲区。
2. 彩蛋行格式：`\\>` 后跟命令（如 `\\>help`、`\\>notepad.exe readme.txt`）。
3. 渲染替换为结果、保存保留原文——这是设计，不是 bug。