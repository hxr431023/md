import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.File;
import java.util.List;

/**
 * mdcontroller.dll — 应用控制器（MVC 中的 Controller，前端唯一入口）
 *
 * 编排以下模块：
 *   - MDDoc（文档模型：状态/撤销/光标/编辑/统计）
 *   - MDLog（FLF日志 + Zstd压缩）
 *   - MDConfig（TMMF/LERF/设置）
 *   - MDMarkdown（Markdown解析/渲染/样式分析）
 *   - MDIdp（IDP接口数据包格式：XML头+JSON数据）
 *
 * 前端（Python）只加载这一个 DLL，只调用 md_app_* 系列函数。
 * 控制器输出渲染指令（JSON）和状态信息（JSON），Python 纯渲染。
 * 渲染缓存使用 IDP 格式（.idp 文件），替代之前的缓存 txt。
 */
public class MDController {

    private static String baseDir = ".";
    private static boolean initialized = false;

    // ================================================================
    //  C ABI 导出（前端唯一调用入口）
    // ================================================================

    // ===== 应用生命周期 =====

    @CEntryPoint(name = "app_init")
    public static int appInit(IsolateThread thread, CCharPointer baseDirPtr) {
        try {
            baseDir = CTypeConversion.toJavaString(baseDirPtr);
            MDLog.init(thread, baseDirPtr);
            MDConfig.init(thread, baseDirPtr);
            MDConfig.loadSettings(thread);
            MDDoc.init(thread, baseDirPtr);
            // 单实例互锁：第二个实例会被拒绝（内部 String 方法，避免 C 字符串悬空）
            int lockResult = MDSingle.tryLockInternal("mdrenderer");
            if (lockResult == 0) {
                MDLog.log("", "单实例检测", "拒绝启动：已有实例在运行");
                return 2; // 2 = 已有实例，前端应提示并退出
            }
            initialized = true;
            MDLog.log("", "应用启动", "控制层初始化完成，baseDir=" + baseDir);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    @CEntryPoint(name = "app_shutdown")
    public static void appShutdown(IsolateThread thread) {
        if (initialized) {
            MDLog.log("", "应用关闭", "正常退出");
            MDLog.shutdown(thread); // 压缩日志回日志目录
        }
        initialized = false;
    }

    @CEntryPoint(name = "app_version")
    public static int version(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        CTypeConversion.toCString("1.0.10", outBuf, WordFactory.unsigned(bufSize));
        return "1.0.10".length();
    }

    // ===== 文件操作 =====

    @CEntryPoint(name = "app_new_file")
    public static int newFile(IsolateThread thread) {
        MDDoc.newDocument(thread);
        MDLog.log("", "新建文件", "成功");
        return 0;
    }

    @CEntryPoint(name = "app_open_file")
    public static int openFile(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        int result = MDDoc.openDocument(thread, pathPtr);
        if (result >= 0) {
            MDLog.log(path, "打开文件", "成功，" + result + "字符");
        } else {
            MDLog.log(path, "打开文件", "失败：文件不存在");
        }
        return result;
    }

    @CEntryPoint(name = "app_save_file")
    public static int saveFile(IsolateThread thread) {
        int result = MDDoc.saveDocument(thread);
        if (result == 0) MDLog.log("", "保存文件", "成功");
        else if (result == -2) MDLog.log("", "保存文件", "失败：无文件路径（需另存为）");
        else MDLog.log("", "保存文件", "失败：写入错误");
        return result;
    }

    @CEntryPoint(name = "app_save_file_as")
    public static int saveFileAs(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        int result = MDDoc.saveDocumentAs(thread, pathPtr);
        if (result == 0) MDLog.log(path, "另存为", "成功");
        else MDLog.log(path, "另存为", "失败");
        return result;
    }

    // ===== 内容与状态 =====

    @CEntryPoint(name = "app_get_content")
    public static int getContent(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        return MDDoc.getContent(thread, outBuf, bufSize);
    }

    @CEntryPoint(name = "app_get_file_path")
    public static int getFilePath(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        return MDDoc.getFilePath(thread, outBuf, bufSize);
    }

    @CEntryPoint(name = "app_is_dirty")
    public static int isDirty(IsolateThread thread) {
        return MDDoc.isDirty(thread);
    }

    // ===== 编辑操作 =====

    @CEntryPoint(name = "app_insert_text")
    public static int insertText(IsolateThread thread, int pos, CCharPointer textPtr) {
        return MDDoc.insertText(thread, pos, textPtr);
    }

    @CEntryPoint(name = "app_delete_text")
    public static int deleteText(IsolateThread thread, int start, int end) {
        return MDDoc.deleteText(thread, start, end);
    }

    @CEntryPoint(name = "app_undo")
    public static int undo(IsolateThread thread) { return MDDoc.undo(thread); }

    @CEntryPoint(name = "app_redo")
    public static int redo(IsolateThread thread) { return MDDoc.redo(thread); }

    @CEntryPoint(name = "app_can_undo")
    public static int canUndo(IsolateThread thread) { return MDDoc.canUndo(thread); }

    @CEntryPoint(name = "app_can_redo")
    public static int canRedo(IsolateThread thread) { return MDDoc.canRedo(thread); }

    // ===== 光标 =====

    @CEntryPoint(name = "app_set_cursor")
    public static void setCursor(IsolateThread thread, int pos) { MDDoc.setCursor(thread, pos); }
    @CEntryPoint(name = "app_get_cursor")
    public static int getCursor(IsolateThread thread) { return MDDoc.getCursor(thread); }
    @CEntryPoint(name = "app_get_cursor_line")
    public static int getCursorLine(IsolateThread thread) { return MDDoc.getCursorLine(thread); }
    @CEntryPoint(name = "app_get_cursor_col")
    public static int getCursorCol(IsolateThread thread) { return MDDoc.getCursorCol(thread); }

    // ===== 渲染指令（控制层核心价值：分析全文，输出样式指令，Python 照画）=====

    /**
     * 获取全文渲染指令（JSON 格式）。
     * 渲染指令经 IDP 双向栈返回：前端 idp_push("app_render_ui",...) → app_idp_process → idp_pop_json。
     * 本函数保留 C ABI 直返（outBuf 兼容旧前端），不再触碰任何文件。
     */
    @CEntryPoint(name = "app_get_render_instructions")
    public static int getRenderInstructions(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        try {
            // 从文档模型获取全文（内部 String 方法，避免 C 字符串悬空）
            String content = MDDoc.getContentInternal();

            // 构建渲染指令 JSON（调用 mdrender 模块）
            String json = MDRender.buildUiInstructions(content);

            CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
            return json.length();
        } catch (Exception e) {
            CTypeConversion.toCString("{\"view\":\"ui\",\"layout\":\"flow\",\"elements\":[]}", outBuf, WordFactory.unsigned(bufSize));
            return -1;
        }
    }

    /** 从内存双向栈读取最近一次渲染指令的 JSON 数据部分（弹队首），写入 outBuf */
    @CEntryPoint(name = "app_read_render_cache")
    public static int readRenderCache(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String json = MDIdp.dequeueJson();
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** Markdown → HTML（导出/预览用） */
    @CEntryPoint(name = "app_render_html")
    public static int renderHtml(IsolateThread thread, CCharPointer mdPtr, CCharPointer outBuf, int bufSize) {
        return MDMarkdown.renderHtml(thread, mdPtr, outBuf, bufSize);
    }

    // ===== 状态信息（一次调用更新整个状态栏+菜单）=====

    @CEntryPoint(name = "app_get_status")
    public static int getStatus(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        try {
            // 文件路径（内部 String 方法，避免 C 字符串悬空）
            String filePath = MDDoc.getFilePathInternal();
            String fileName = "未命名";
            if (!filePath.isEmpty()) {
                int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
                fileName = slash >= 0 ? filePath.substring(slash + 1) : filePath;
            }

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"file_name\":\"").append(escapeJson(fileName)).append("\",");
            json.append("\"file_path\":\"").append(escapeJson(filePath)).append("\",");
            json.append("\"dirty\":").append(MDDoc.isDirty(thread) == 1).append(",");
            json.append("\"char_count\":").append(MDDoc.getCharCount(thread)).append(",");
            json.append("\"word_count\":").append(MDDoc.getWordCount(thread)).append(",");
            json.append("\"line_count\":").append(MDDoc.getLineCount(thread)).append(",");
            json.append("\"cursor_pos\":").append(MDDoc.getCursor(thread)).append(",");
            json.append("\"cursor_line\":").append(MDDoc.getCursorLine(thread)).append(",");
            json.append("\"cursor_col\":").append(MDDoc.getCursorCol(thread)).append(",");
            json.append("\"can_undo\":").append(MDDoc.canUndo(thread) == 1).append(",");
            json.append("\"can_redo\":").append(MDDoc.canRedo(thread) == 1);
            json.append("}");

            String result = json.toString();
            CTypeConversion.toCString(result, outBuf, WordFactory.unsigned(bufSize));
            return result.length();
        } catch (Exception e) {
            CTypeConversion.toCString("{}", outBuf, WordFactory.unsigned(bufSize));
            return -1;
        }
    }

    // ===== 设置 =====

    @CEntryPoint(name = "app_get_setting")
    public static int getSetting(IsolateThread thread, CCharPointer keyPtr, CCharPointer outBuf, int bufSize) {
        return MDConfig.getSetting(thread, keyPtr, outBuf, bufSize);
    }

    @CEntryPoint(name = "app_set_setting")
    public static int setSetting(IsolateThread thread, CCharPointer keyPtr, CCharPointer valPtr) {
        int result = MDConfig.setSetting(thread, keyPtr, valPtr);
        MDLog.log(CTypeConversion.toJavaString(keyPtr), "修改设置", CTypeConversion.toJavaString(valPtr));
        return result;
    }

    @CEntryPoint(name = "app_save_settings")
    public static int saveSettings(IsolateThread thread) { return MDConfig.saveSettings(thread); }

    // ===== 日志（透传）=====

    @CEntryPoint(name = "app_log")
    public static void log(IsolateThread thread, CCharPointer paramsPtr, CCharPointer opPtr, CCharPointer resultPtr) {
        MDLog.writeLog(thread, paramsPtr, opPtr, resultPtr);
    }

    // ===== 彩蛋：\\>命令 → CMD DLL 执行 → 返回结果 =====
    // 前缀为两个反斜杠 + 右尖括号（模仿 CMD 的 > 输出重定向风格）
    // 如：\\>help  →  CMD help 信息
    //     \\>version → 版本号
    // 前端检测到行首为 "\\>" 时调用此函数，结果替换显示但不保存到文档。
    // 保存时仍存原始 "\\>命令"，二次打开可二次执行。

    @CEntryPoint(name = "app_easter_exec")
    public static int easterExec(IsolateThread thread, CCharPointer linePtr, CCharPointer outBuf, int bufSize) {
        String line = CTypeConversion.toJavaString(linePtr);
        String prefix = "\\\\>";
        if (!line.startsWith(prefix)) {
            CTypeConversion.toCString("", outBuf, WordFactory.unsigned(bufSize));
            return 0;
        }
        String command = line.substring(prefix.length()).trim();
        if (command.isEmpty()) {
            CTypeConversion.toCString("", outBuf, WordFactory.unsigned(bufSize));
            return 0;
        }
        String result = MDCmd.execInternal(command);
        CTypeConversion.toCString(result, outBuf, WordFactory.unsigned(bufSize));
        MDLog.log(command, "彩蛋命令", "执行完成");
        return result.length();
    }

    /** 检测一行是否为彩蛋命令行（前缀 \\>），返回 1/0 */
    @CEntryPoint(name = "app_easter_detect")
    public static int easterDetect(IsolateThread thread, CCharPointer linePtr) {
        String line = CTypeConversion.toJavaString(linePtr);
        return line.startsWith("\\\\>") ? 1 : 0;
    }

    // ===== IDP 通用输出（所有模块间通信 + Python 前端渲染指令都用内存双向栈 IDP）=====
    // filePath 参数已废弃（兼容旧签名保留），实际只走内存双向栈，不落任何文件。

    @CEntryPoint(name = "app_write_idp")
    public static int writeIdp(IsolateThread thread, CCharPointer filePathPtr, CCharPointer typePtr, CCharPointer jsonDataPtr) {
        try {
            MDIdp.enqueue(CTypeConversion.toJavaString(typePtr),
                "mdcontroller", CTypeConversion.toJavaString(jsonDataPtr));
            return 0;
        } catch (Exception e) { return -1; }
    }

    @CEntryPoint(name = "app_to_idp")
    public static int toIdp(IsolateThread thread, CCharPointer typePtr, CCharPointer jsonDataPtr, CCharPointer outBuf, int bufSize) {
        String idp = MDIdp.createPacket(CTypeConversion.toJavaString(typePtr), "mdcontroller", CTypeConversion.toJavaString(jsonDataPtr));
        CTypeConversion.toCString(idp, outBuf, WordFactory.unsigned(bufSize));
        return idp.length();
    }

    @CEntryPoint(name = "app_read_idp")
    public static int readIdp(IsolateThread thread, CCharPointer filePathPtr, CCharPointer outBuf, int bufSize) {
        String json = MDIdp.dequeueJson();
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    // ===== IDP 统一处理入口（前端唯一业务通道）=====
    // 双向栈协议（内存，无文件/句柄）：
    //   请求：前端 idp_push（enqueue 压队尾）
    //   处理：app_idp_process 从队尾弹出最新请求包（popBack），按 type 分发到内部业务，
    //         结果以 pushFront 压回队首（前端 pop 总能拿到最新结果，遗留包不会被误取）
    //   结果：前端 idp_pop_json（dequeueJson 弹队首）
    @CEntryPoint(name = "app_idp_process")
    public static int idpProcess(IsolateThread thread) {
        try {
            MDIdp.Packet req = MDIdp.popBack();
            if (req == null) return -1; // 无请求包
            String result = dispatchInternal(thread, req.type, req.jsonData);
            MDIdp.pushFront(new MDIdp.Packet("result:" + req.type, "mdcontroller", result));
            return 0;
        } catch (Exception e) {
            MDIdp.pushFront(new MDIdp.Packet("result:error", "mdcontroller",
                "{\"error\":\"" + escapeJson(String.valueOf(e.getMessage())) + "\"}"));
            return -1;
        }
    }

    /**
     * 按 type 分发 IDP 请求。请求包 jsonData 为参数 JSON（{"arg1":...,"arg2":...}）。
     * 返回结果 JSON。
     */
    private static String dispatchInternal(IsolateThread thread, String type, String jsonData) {
        // 日志驱动缓存：相同 type + 相同参数直接复用上次结果（渲染类纯函数）
        if (isCacheable(type)) {
            String cached = MDLog.cacheLookupInternal(type, jsonData);
            if (cached != null) return withCachedFlag(cached);
            String fresh = dispatchNoCache(thread, type, jsonData);
            if (fresh != null && !fresh.startsWith("{\"error\"")) {
                MDLog.cachePutInternal(type, jsonData, fresh);
            }
            return fresh;
        }
        return dispatchNoCache(thread, type, jsonData);
    }

    /** 可缓存操作：纯函数（同样输入必得同样输出，不依赖文档/设置等可变状态） */
    private static boolean isCacheable(String type) {
        switch (type) {
            case "app_render_html":
            case "render_find":
            case "render_replace":
            case "render_insert":
            case "render_paged":
            case "visualize_stats_to_html":
                return true;
            default:
                return false;
        }
    }

    /** 命中缓存时在结果 JSON 末尾追加 "cached":true（原字段不变，前端可忽略） */
    private static String withCachedFlag(String json) {
        if (json == null) return null;
        String t = json.trim();
        if (!t.startsWith("{") || !t.endsWith("}")) return json;
        String body = t.substring(1, t.length() - 1).trim();
        return body.isEmpty() ? "{\"cached\":true}" : "{" + body + ",\"cached\":true}";
    }

    private static String dispatchNoCache(IsolateThread thread, String type, String jsonData) {
        switch (type) {
            case "app_version":
                return "{\"version\":\"1.0.10\"}";
            case "app_new_file":
                MDDoc.newDocument(thread);
                MDLog.log("", "新建文件", "成功");
                return "{\"ok\":0}";
            case "app_open_file": {
                String path = jsonString(jsonData, "path");
                int result = MDDoc.openDocument(thread, toCString(path));
                MDLog.log(path, "打开文件", result >= 0 ? ("成功，" + result + "字符") : "失败：文件不存在");
                return "{\"ok\":" + result + "}";
            }
            case "app_save_file": {
                int result = MDDoc.saveDocument(thread);
                MDLog.log("", "保存文件", result == 0 ? "成功" : "失败");
                return "{\"ok\":" + result + "}";
            }
            case "app_save_file_as": {
                String path = jsonString(jsonData, "path");
                int result = MDDoc.saveDocumentAs(thread, toCString(path));
                MDLog.log(path, "另存为", result == 0 ? "成功" : "失败");
                return "{\"ok\":" + result + "}";
            }
            case "app_get_content":
                return "{\"content\":\"" + escapeJson(MDDoc.getContentInternal()) + "\"}";
            case "app_get_file_path":
                return "{\"path\":\"" + escapeJson(MDDoc.getFilePathInternal()) + "\"}";
            case "app_is_dirty":
                return "{\"dirty\":" + (MDDoc.isDirty(thread) == 1) + "}";
            case "app_insert_text": {
                int pos = jsonInt(jsonData, "pos");
                String text = jsonString(jsonData, "text");
                int result = MDDoc.insertText(thread, pos, toCString(text));
                return "{\"ok\":" + result + "}";
            }
            case "app_delete_text": {
                int start = jsonInt(jsonData, "start");
                int end = jsonInt(jsonData, "end");
                int result = MDDoc.deleteText(thread, start, end);
                return "{\"ok\":" + result + "}";
            }
            case "app_undo":
                return "{\"ok\":" + MDDoc.undo(thread) + "}";
            case "app_redo":
                return "{\"ok\":" + MDDoc.redo(thread) + "}";
            case "app_can_undo":
                return "{\"can\":" + (MDDoc.canUndo(thread) == 1) + "}";
            case "app_can_redo":
                return "{\"can\":" + (MDDoc.canRedo(thread) == 1) + "}";
            case "app_set_cursor": {
                MDDoc.setCursor(thread, jsonInt(jsonData, "pos"));
                return "{\"ok\":0}";
            }
            case "app_get_cursor":
                return "{\"pos\":" + MDDoc.getCursor(thread) + "}";
            case "app_get_cursor_line":
                return "{\"line\":" + MDDoc.getCursorLine(thread) + "}";
            case "app_get_cursor_col":
                return "{\"col\":" + MDDoc.getCursorCol(thread) + "}";
            case "app_render_ui": {
                String content = MDDoc.getContentInternal();
                return MDRender.buildUiInstructions(content);
            }
            case "app_render_html": {
                String md = jsonString(jsonData, "md");
                return "{\"html\":\"" + escapeJson(MDMarkdown.markdownToHtml(md)) + "\"}";
            }
            case "app_get_status": {
                StringBuilder json = new StringBuilder();
                String filePath = MDDoc.getFilePathInternal();
                String fileName = "未命名";
                if (!filePath.isEmpty()) {
                    int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
                    fileName = slash >= 0 ? filePath.substring(slash + 1) : filePath;
                }
                json.append("{");
                json.append("\"file_name\":\"").append(escapeJson(fileName)).append("\",");
                json.append("\"file_path\":\"").append(escapeJson(filePath)).append("\",");
                json.append("\"dirty\":").append(MDDoc.isDirty(thread) == 1).append(",");
                json.append("\"char_count\":").append(MDDoc.getCharCount(thread)).append(",");
                json.append("\"word_count\":").append(MDDoc.getWordCount(thread)).append(",");
                json.append("\"line_count\":").append(MDDoc.getLineCount(thread)).append(",");
                json.append("\"cursor_pos\":").append(MDDoc.getCursor(thread)).append(",");
                json.append("\"cursor_line\":").append(MDDoc.getCursorLine(thread)).append(",");
                json.append("\"cursor_col\":").append(MDDoc.getCursorCol(thread)).append(",");
                json.append("\"can_undo\":").append(MDDoc.canUndo(thread) == 1).append(",");
                json.append("\"can_redo\":").append(MDDoc.canRedo(thread) == 1);
                json.append("}");
                return json.toString();
            }
            case "app_get_setting": {
                String key = jsonString(jsonData, "key");
                return "{\"key\":\"" + escapeJson(key) + "\",\"value\":\"" + escapeJson(MDConfig.getSetting(key)) + "\"}";
            }
            case "app_set_setting": {
                String key = jsonString(jsonData, "key");
                String value = jsonString(jsonData, "value");
                MDConfig.setSetting(key, value);
                MDLog.log(key, "修改设置", value);
                return "{\"ok\":0}";
            }
            case "app_save_settings":
                MDConfig.saveSettings(thread);
                return "{\"ok\":0}";
            case "app_log": {
                String params = jsonString(jsonData, "params");
                String op = jsonString(jsonData, "op");
                String result = jsonString(jsonData, "result");
                MDLog.writeLog(thread, toCString(params), toCString(op), toCString(result));
                return "{\"ok\":0}";
            }
            case "app_easter_detect": {
                String line = jsonString(jsonData, "line");
                return "{\"is_easter\":" + line.startsWith("\\\\>") + "}";
            }
            case "app_easter_exec": {
                String line = jsonString(jsonData, "line");
                String result = "";
                if (line.startsWith("\\\\>")) {
                    String command = line.substring(3).trim();
                    if (!command.isEmpty()) {
                        result = MDCmd.execInternal(command);
                        MDLog.log(command, "彩蛋命令", "执行完成");
                    }
                }
                return "{\"result\":\"" + escapeJson(result) + "\"}";
            }
            case "config_get_thread_mode": {
                String mode = MDConfig.getSetting("thread_mode");
                if (mode.isEmpty()) mode = "folder";
                return "{\"mode\":\"" + escapeJson(mode) + "\"}";
            }
            case "config_set_thread_mode": {
                String mode = jsonString(jsonData, "mode");
                MDConfig.setSetting("thread_mode", mode);
                return "{\"ok\":0}";
            }
            case "app_get_setting_legacy":
            case "doc_char_count":
                return "{\"count\":" + MDDoc.getCharCount(thread) + "}";
            case "threadstat_sample":
                MDThreadStat.sample(thread);
                return "{\"ok\":0}";
            case "threadstat_get_json":
                return "{\"stats\":" + MDThreadStat.getJsonInternal() + "}";
            case "visualize_stats_to_html": {
                String statsJson = jsonString(jsonData, "stats");
                return "{\"html\":\"" + escapeJson(MDVisualize.statsToHtmlInternal(statsJson)) + "\"}";
            }
            case "log_clear_all": {
                int r = MDLog.clearAllInternal();
                return "{\"ok\":" + r + "}";
            }
            case "single_try_lock": {
                int r = MDSingle.tryLockInternal("mdrenderer");
                return "{\"locked\":" + (r == 1) + "}";
            }
            case "render_find": {
                String content = jsonString(jsonData, "content");
                String query = jsonString(jsonData, "query");
                return "{\"result\":" + MDRender.findJsonInternal(content, query) + "}";
            }
            case "render_replace": {
                String content = jsonString(jsonData, "content");
                String find = jsonString(jsonData, "find");
                String replace = jsonString(jsonData, "replace");
                return "{\"result\":" + MDRender.replaceJsonInternal(content, find, replace) + "}";
            }
            case "render_insert": {
                String content = jsonString(jsonData, "content");
                String left = jsonString(jsonData, "left");
                String right = jsonString(jsonData, "right");
                String insert = jsonString(jsonData, "insert");
                return "{\"result\":" + MDRender.insertJsonInternal(content, left, right, insert) + "}";
            }
            case "render_paged": {
                String content = jsonString(jsonData, "content");
                int pageWidth = jsonInt(jsonData, "page_width");
                int pageHeight = jsonInt(jsonData, "page_height");
                return "{\"result\":" + MDRender.pagedJsonInternal(content, pageWidth, pageHeight) + "}";
            }
            default:
                return "{\"error\":\"unknown type: " + escapeJson(type) + "\"}";
        }
    }

    // ===== 内部辅助（String 级，避免在 Java 内部造 C 字符串）=====

    /** String → CCharPointer（GraalVM 返回 Holder，取 .get() 供 C ABI 参数使用；同步调用期内安全） */
    private static CCharPointer toCString(String s) {
        return CTypeConversion.toCString(s).get();
    }

    private static String jsonString(String json, String key) {
        // 极简 JSON 字符串取值：{"key":"value"}（处理转义）
        String k = "\"" + key + "\":\"";
        int i = json.indexOf(k);
        if (i < 0) return "";
        i += k.length();
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                if (n == 'n') { sb.append('\n'); i += 2; continue; }
                if (n == 't') { sb.append('\t'); i += 2; continue; }
                if (n == 'r') { sb.append('\r'); i += 2; continue; }
                if (n == 'u' && i + 5 < json.length()) {
                    try { sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16)); i += 6; continue; }
                    catch (Exception ignored) {}
                }
                sb.append(n); i += 2; continue;
            }
            if (c == '"') break;
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int jsonInt(String json, String key) {
        String k = "\"" + key + "\":";
        int i = json.indexOf(k);
        if (i < 0) return 0;
        i += k.length();
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']') break;
            sb.append(c);
            i++;
        }
        try { return Integer.parseInt(sb.toString().trim()); } catch (Exception e) { return 0; }
    }

    // ================================================================
    //  内部实现
    // ================================================================

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
