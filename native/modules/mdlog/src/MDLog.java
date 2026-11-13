import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

/**
 * log.dll — FLF 纯日志模块（可独立复用）
 *
 * 功能：
 *   - FLF 格式日志写入（行头部 + [时间]参数{操作}结果 + 结尾…）
 *   - 日志分类开关（启动/打开/保存/新建/错误/单实例）
 *   - 清除所有日志（删除 log/ 和 cache/log/ 下所有文件）
 *   - 日志大小满自动删除（超过阈值时自动删除当日日志并重新初始化）
 *   - 压缩/解压由 logmain.dll 主控负责编排，本模块为纯日志
 *   - C ABI 导出
 *
 * FLF 格式：
 *   第1行：FLF.;utf8
 *   第2行：程序标识（icon_mdrenderer）
 *   第3行：版本号（MD 编辑器 V1.0.10）
 *   第4行：固定20位文件大小（字节数，0填充）
 *   第5行起：[时间] 参数{操作名称}结果
 *   结尾：…
 *
 * 配合 logmain.dll 使用：
 *   logmain_init  → 先解压 log/YYYY-MM-DD.log.zst → cache/log/YYYY-MM-DD.log，再调本模块 init
 *   logmain_shutdown → 调本模块 shutdown 后，压缩回 log/YYYY-MM-DD.log.zst
 */
public class MDLog {

    public static final String VERSION = "1.0.10";
    /** 日志大小阈值：超过此字节数自动删除重建（10MB） */
    private static final long MAX_LOG_SIZE = 10 * 1024 * 1024;

    private static String baseDir = ".";
    private static File logDir;
    private static File cacheDir;
    private static File logFile;
    private static File compressedFile;
    private static final SimpleDateFormat logSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static boolean logStartup = true, logOpenFile = true, logSaveFile = true;
    private static boolean logNewFile = true, logError = true, logSingleInstance = true;

    // ================================================================
    //  C ABI 导出
    // ================================================================

    @CEntryPoint(name = "log_init")
    public static int init(IsolateThread thread, CCharPointer baseDirPtr) {
        return initInternal(CTypeConversion.toJavaString(baseDirPtr));
    }

    /** 内部 String 版初始化（自压缩主控：存在压缩态先解压到工作态） */
    public static int initInternal(String base) {
        try {
            baseDir = base;
            logDir = new File(baseDir, "log");
            cacheDir = new File(baseDir, "cache/log");
            if (!logDir.exists()) logDir.mkdirs();
            if (!cacheDir.exists()) cacheDir.mkdirs();

            String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            logFile = new File(cacheDir, dateStr + ".log");
            compressedFile = new File(logDir, dateStr + ".log.zst");

            // 自压缩：压缩态存在且无工作态时，先解压到工作态
            if (compressedFile.exists() && !logFile.exists()) {
                MDCompress.decompressFileInternal(compressedFile.getAbsolutePath(), logFile.getAbsolutePath());
            }
            if (!logFile.exists() || logFile.length() == 0) {
                writeFlfHeader();
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    @CEntryPoint(name = "log_shutdown")
    public static void shutdown(IsolateThread thread) {
        shutdownInternal();
    }

    /** 内部 String 版关闭（自压缩主控：工作态压缩回 log/ 并删除工作文件） */
    public static void shutdownInternal() {
        try {
            if (logFile != null && logFile.exists()) {
                MDCompress.compressFileInternal(logFile.getAbsolutePath(), compressedFile.getAbsolutePath());
                logFile.delete();
            }
        } catch (Exception ignored) {}
    }

    @CEntryPoint(name = "log_write")
    public static void writeLog(IsolateThread thread, CCharPointer paramsPtr, CCharPointer opPtr, CCharPointer resultPtr) {
        log(CTypeConversion.toJavaString(paramsPtr), CTypeConversion.toJavaString(opPtr), CTypeConversion.toJavaString(resultPtr), false);
    }

    @CEntryPoint(name = "log_write_end")
    public static void writeLogEnd(IsolateThread thread, CCharPointer paramsPtr, CCharPointer opPtr, CCharPointer resultPtr) {
        log(CTypeConversion.toJavaString(paramsPtr), CTypeConversion.toJavaString(opPtr), CTypeConversion.toJavaString(resultPtr), true);
    }

    @CEntryPoint(name = "log_set_enabled")
    public static void setEnabled(IsolateThread thread, CCharPointer categoryPtr, int enabled) {
        setEnabledInternal(CTypeConversion.toJavaString(categoryPtr), enabled != 0);
    }

    /** 内部 String 版设置开关（供主控调用） */
    public static void setEnabledInternal(String cat, boolean en) {
        switch (cat) {
            case "startup": logStartup = en; break;
            case "openfile": logOpenFile = en; break;
            case "savefile": logSaveFile = en; break;
            case "newfile": logNewFile = en; break;
            case "error": logError = en; break;
            case "singleinstance": logSingleInstance = en; break;
        }
    }

    @CEntryPoint(name = "log_is_enabled")
    public static int isEnabled(IsolateThread thread, CCharPointer categoryPtr) {
        return isEnabledInternal(CTypeConversion.toJavaString(categoryPtr)) ? 1 : 0;
    }

    /** 内部 String 版查询开关（供主控调用） */
    public static boolean isEnabledInternal(String cat) {
        switch (cat) {
            case "startup": return logStartup;
            case "openfile": return logOpenFile;
            case "savefile": return logSaveFile;
            case "newfile": return logNewFile;
            case "error": return logError;
            case "singleinstance": return logSingleInstance;
            default: return false;
        }
    }

    /**
     * 清除所有日志：删除 log/ 目录下所有 .zst 文件和 cache/log/ 下所有 .log 文件。
     * 返回删除的文件数量。
     */
    @CEntryPoint(name = "log_clear_all")
    public static int clearAll(IsolateThread thread) {
        return clearAllInternal();
    }

    /** 内部 String 版清除所有日志（供主控调用），返回删除文件数量 */
    public static int clearAllInternal() {
        int count = 0;
        try {
            // 删除压缩态日志
            if (logDir != null && logDir.exists()) {
                File[] files = logDir.listFiles((d, name) -> name.endsWith(".zst") || name.endsWith(".log"));
                if (files != null) {
                    for (File f : files) { if (f.delete()) count++; }
                }
            }
            // 删除工作态缓存
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles((d, name) -> name.endsWith(".log"));
                if (files != null) {
                    for (File f : files) { if (f.delete()) count++; }
                }
            }
            // 重新初始化当日日志
            if (logFile != null) {
                writeFlfHeader();
            }
        } catch (Exception ignored) {}
        return count;
    }

    @CEntryPoint(name = "log_size")
    public static long logSize(IsolateThread thread) {
        return logSizeInternal();
    }

    /** 内部 String 版获取当前日志文件大小（供主控调用） */
    public static long logSizeInternal() {
        if (logFile != null && logFile.exists()) return logFile.length();
        return 0;
    }

    // ================================================================
    //  操作结果缓存（日志驱动的命中复用）
    //  缓存行：<\时间>CACHE:{op}:{base64(params)}:{base64(result)}。<时间/>
    //  params/result 用 Base64，避免 JSON 的 {}、换行、。<\ 破坏行解析；
    //  只查当日工作态日志；相同 op + params 命中最近一条结果。
    // ================================================================
    private static final String CACHE_PREFIX = "CACHE:";

    @CEntryPoint(name = "log_cache_lookup")
    public static int cacheLookup(IsolateThread thread, CCharPointer opPtr, CCharPointer paramsPtr,
                                  CCharPointer outBuf, int bufSize) {
        String result = cacheLookupInternal(CTypeConversion.toJavaString(opPtr),
                CTypeConversion.toJavaString(paramsPtr));
        if (result == null) return -1; // 未命中
        CTypeConversion.toCString(result, outBuf, WordFactory.unsigned(bufSize));
        return result.length();
    }

    /** 相同 op + params 的最近一条缓存结果；未命中返回 null（供主控调用） */
    public static String cacheLookupInternal(String op, String params) {
        try {
            if (logFile == null || !logFile.exists()) return null;
            String content = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            String wantParam = b64(params);
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i];
                int idx = line.indexOf(CACHE_PREFIX);
                if (idx < 0) continue;
                String payload = line.substring(idx + CACHE_PREFIX.length());
                int end = payload.indexOf("。<");
                if (end >= 0) payload = payload.substring(0, end);
                int c1 = payload.indexOf(':');
                if (c1 < 0) continue;
                int c2 = payload.indexOf(':', c1 + 1);
                if (c2 < 0) continue;
                String op2 = payload.substring(0, c1);
                String p2 = payload.substring(c1 + 1, c2);
                String r2 = payload.substring(c2 + 1);
                if (op2.equals(op) && p2.equals(wantParam)) {
                    return new String(Base64.getDecoder().decode(r2), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @CEntryPoint(name = "log_cache_put")
    public static int cachePut(IsolateThread thread, CCharPointer opPtr, CCharPointer paramsPtr,
                               CCharPointer resultPtr) {
        return cachePutInternal(CTypeConversion.toJavaString(opPtr),
                CTypeConversion.toJavaString(paramsPtr),
                CTypeConversion.toJavaString(resultPtr));
    }

    /** 追加一条缓存行；返回 0 成功 / -1 失败（供主控调用） */
    public static int cachePutInternal(String op, String params, String result) {
        try {
            if (logFile == null) initInternal(baseDir);
            if (logFile == null) return -1;
            String time = logSdf.format(new Date());
            String entry = "<\\" + time + ">" + CACHE_PREFIX + op + ":" + b64(params) + ":" + b64(result)
                    + "。<" + time + "/>";
            appendEntryLine(entry);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Base64（标准字母表不含 ':'，可安全作字段分隔符） */
    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    // ================================================================
    //  内部实现
    // ================================================================

    public static void log(String params, String operation, String result) {
        log(params, operation, result, false);
    }

    public static void logEnd(String params, String operation, String result) {
        log(params, operation, result, true);
    }

    private static boolean isLogEnabled(String operation) {
        switch (operation) {
            case "启动": case "退出": case "版本": case "Java版本": case "操作系统":
            case "后端初始化": case "后端关闭": case "应用启动": case "应用关闭": return logStartup;
            case "打开文件": case "读取文件": return logOpenFile;
            case "保存文件": case "写入文件": case "另存为": return logSaveFile;
            case "新建文件": return logNewFile;
            case "单实例检测": return logSingleInstance;
            default: return logError;
        }
    }

    private static void log(String params, String operation, String result, boolean endGroup) {
        if (!isLogEnabled(operation)) return;
        try {
            if (logFile == null) return;

            // FLF 规范：<\开始时间>起始参数{操作}结束结果。<结尾时间/>
            String startTime = logSdf.format(new Date());
            String endTime = logSdf.format(new Date());
            String entry = "<\\" + startTime + ">" + params + "{" + operation + "}" + result + "。<" + endTime + "/>";
            if (endGroup) entry += ";";
            appendEntryLine(entry);
        } catch (Exception ignored) {}
    }

    /** 追加一行并更新 FLF 第 4 行（固定 20 位文件大小）；超过阈值先重建 */
    private static void appendEntryLine(String entry) throws IOException {
        if (logFile == null) return;
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            logFile.delete();
            writeFlfHeader();
        }
        String content = "";
        if (logFile.exists()) {
            content = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        } else {
            writeFlfHeader();
            content = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
        }
        // 移除末尾的 … 占位符，追加新条目后再加回
        if (content.endsWith("…\n")) content = content.substring(0, content.length() - 2);
        else if (content.endsWith("…")) content = content.substring(0, content.length() - 1);
        content += entry + "\n…\n";

        // 更新第4行（固定20位文件大小）
        String[] lines = content.split("\n", -1);
        if (lines.length >= 4) {
            lines[3] = String.format("%020d", content.getBytes(StandardCharsets.UTF_8).length);
            content = String.join("\n", lines);
        }
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private static void writeFlfHeader() throws IOException {
        String header = "FLF.;utf8\nicon_mdrenderer\nMD 编辑器 V" + VERSION + "\n00000000000000000000\n…\n";
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8)) { w.write(header); }
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
