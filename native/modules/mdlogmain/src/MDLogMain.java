import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * logmain.dll — 日志系统主控模块（唯一对外入口）
 *
 * 职责：管理并编排 compress.dll（压缩）与 log.dll（日志）两个子模块，
 *       外部程序只与本主控交互，不直接调用 compress/log。
 *
 * 生命周期：
 *   1. logmain_init(baseDir)     启动：解压 log/*.log.zst → cache/log/*.log，再初始化日志
 *   2. logmain_write(...)        写入日志条目（内部调用 log.dll 的 FLF 写入）
 *   3. logmain_shutdown()        关闭：写收尾 → 压缩 cache/log/*.log → log/*.log.zst，删除工作文件
 *
 * 附加：
 *   - logmain_clear_all()        清除所有日志（压缩态 + 工作态）
 *   - logmain_size()             当前日志字节数
 *   - logmain_set_enabled()      按分类开关日志
 *   - logmain_is_enabled()       查询分类开关
 *   - logmain_version()          主控版本
 *   - logmain_last_error()       最近一次错误信息
 *
 * 本 DLL 内同时编译进 MDCompress 与 MDLog 两个 Java 类，
 * 由本类直接调用其 internal 方法完成编排（同一 isolate，无跨 DLL 开销）。
 */
public class MDLogMain {

    public static final String VERSION = "1.0.10";

    private static String baseDir = ".";
    private static File logDir;
    private static File cacheDir;
    private static File logFile;
    private static File compressedFile;
    private static String lastError = "";

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /**
     * 初始化日志系统（主控唯一入口）。
     * 流程：建目录 → 若存在压缩态 .zst 且无工作态 .log，先解压 → 初始化 FLF。
     * @param baseDirPtr 程序工作目录
     * @return 0 成功 / -1 失败
     */
    @CEntryPoint(name = "logmain_init")
    public static int init(IsolateThread thread, CCharPointer baseDirPtr) {
        return initInternal(CTypeConversion.toJavaString(baseDirPtr));
    }

    /** 内部 String 版初始化（供同 DLL 调用） */
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

            // 1) 解压：压缩态存在且工作态不存在时
            if (compressedFile.exists() && !logFile.exists()) {
                int rc = MDCompress.decompressFileInternal(compressedFile.getAbsolutePath(), logFile.getAbsolutePath());
                if (rc != 0) { lastError = "解压日志失败: " + compressedFile; }
            }
            // 2) 初始化 FLF（log.dll 内部方法）
            return MDLog.initInternal(base);
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            return -1;
        }
    }

    /**
     * 写入日志条目。
     * @param paramsPtr  参数（如文件名/路径）
     * @param opPtr      操作名称（如 打开文件/保存文件）
     * @param resultPtr  结果（如 成功/失败/行数）
     */
    @CEntryPoint(name = "logmain_write")
    public static void write(IsolateThread thread, CCharPointer paramsPtr, CCharPointer opPtr, CCharPointer resultPtr) {
        MDLog.log(CTypeConversion.toJavaString(paramsPtr), CTypeConversion.toJavaString(opPtr), CTypeConversion.toJavaString(resultPtr));
    }

    /** 写收尾条目（FLF 用 ; 结尾标记一组操作结束） */
    @CEntryPoint(name = "logmain_write_end")
    public static void writeEnd(IsolateThread thread, CCharPointer paramsPtr, CCharPointer opPtr, CCharPointer resultPtr) {
        MDLog.logEnd(CTypeConversion.toJavaString(paramsPtr), CTypeConversion.toJavaString(opPtr), CTypeConversion.toJavaString(resultPtr));
    }

    /**
     * 关闭日志系统（主控唯一出口）。
     * 流程：log.dll 收尾 → 压缩工作态 → 删除工作文件。
     */
    @CEntryPoint(name = "logmain_shutdown")
    public static void shutdown(IsolateThread thread) {
        shutdownInternal();
    }

    /** 内部 String 版关闭（供同 DLL 调用） */
    public static void shutdownInternal() {
        try {
            MDLog.shutdownInternal();
            if (logFile != null && logFile.exists()) {
                int rc = MDCompress.compressFileInternal(logFile.getAbsolutePath(), compressedFile.getAbsolutePath());
                if (rc != 0) { lastError = "压缩日志失败: " + compressedFile; }
                else { logFile.delete(); }
            }
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
        }
    }

    /** 清除所有日志：删除 log/ 下 .zst 与 cache/log/ 下 .log，返回删除数量 */
    @CEntryPoint(name = "logmain_clear_all")
    public static int clearAll(IsolateThread thread) {
        return MDLog.clearAllInternal();
    }

    /** 当前日志字节数 */
    @CEntryPoint(name = "logmain_size")
    public static long size(IsolateThread thread) {
        return MDLog.logSizeInternal();
    }

    /** 按分类开关日志（startup/openfile/savefile/newfile/error/singleinstance） */
    @CEntryPoint(name = "logmain_set_enabled")
    public static void setEnabled(IsolateThread thread, CCharPointer categoryPtr, int enabled) {
        MDLog.setEnabledInternal(CTypeConversion.toJavaString(categoryPtr), enabled != 0);
    }

    /** 查询分类开关，返回 1/0 */
    @CEntryPoint(name = "logmain_is_enabled")
    public static int isEnabled(IsolateThread thread, CCharPointer categoryPtr) {
        return MDLog.isEnabledInternal(CTypeConversion.toJavaString(categoryPtr)) ? 1 : 0;
    }

    /** 主控版本号，写入 outBuf */
    @CEntryPoint(name = "logmain_version")
    public static int version(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String v = "MDLogMain v" + VERSION;
        CTypeConversion.toCString(v, outBuf, WordFactory.unsigned(bufSize));
        return v.length();
    }

    /** 最近一次错误信息（无错误则空串），写入 outBuf */
    @CEntryPoint(name = "logmain_last_error")
    public static int lastError(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        CTypeConversion.toCString(lastError, outBuf, WordFactory.unsigned(bufSize));
        return lastError.length();
    }
}
