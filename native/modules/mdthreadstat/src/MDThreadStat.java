import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * mdthreadstat.dll — 线程统计模块（可复用）
 *
 * 功能：
 *   - 统计所有线程的 CPU 占用（CPU时间/总时间）
 *   - 统计内存占用（堆/非堆/总量）
 *   - 统计磁盘 I/O（读写字节数，通过跟踪文件操作估算）
 *   - 线程模式管理：folder（每分类一个线程，默认）/ single（单线程）/ per_dll（每个DLL一个线程）
 *   - 输出统计数据为 JSON，供 mdvisualize 模块可视化
 *   - C ABI 导出
 *
 * 线程模式：
 *   - folder: 每个 DLL 分类文件夹一个线程（core/doc/tools/app），默认
 *   - single: 全部单线程
 *   - per_dll: 每个独立 DLL 一个线程
 */
public class MDThreadStat {

    public static final String MODE_FOLDER = "folder";
    public static final String MODE_SINGLE = "single";
    public static final String MODE_PER_DLL = "per_dll";

    private static String threadMode = MODE_FOLDER;
    private static final Map<String, ThreadInfo> threads = new LinkedHashMap<>();
    private static long startTime = System.currentTimeMillis();
    private static long totalDiskRead = 0;
    private static long totalDiskWrite = 0;

    static class ThreadInfo {
        String name;
        String category; // core/doc/tools/app
        long cpuTimeNs;
        long lastCpuTimeNs;
        long lastSampleMs;
        double cpuPercent;
        boolean alive;
        long threadId;
    }

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /** 初始化线程统计，设置线程模式（folder/single/per_dll） */
    @CEntryPoint(name = "threadstat_init")
    public static int init(IsolateThread thread, CCharPointer modePtr) {
        String mode = CTypeConversion.toJavaString(modePtr);
        if (mode.equals(MODE_FOLDER) || mode.equals(MODE_SINGLE) || mode.equals(MODE_PER_DLL)) {
            threadMode = mode;
        }
        startTime = System.currentTimeMillis();
        registerThreadInternal("main", "app");
        return 0;
    }

    /** 获取当前线程模式，写入 outBuf */
    @CEntryPoint(name = "threadstat_get_mode")
    public static int getMode(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        CTypeConversion.toCString(threadMode, outBuf, WordFactory.unsigned(bufSize));
        return threadMode.length();
    }

    /** 设置线程模式 */
    @CEntryPoint(name = "threadstat_set_mode")
    public static int setMode(IsolateThread thread, CCharPointer modePtr) {
        String mode = CTypeConversion.toJavaString(modePtr);
        if (mode.equals(MODE_FOLDER) || mode.equals(MODE_SINGLE) || mode.equals(MODE_PER_DLL)) {
            threadMode = mode;
            return 0;
        }
        return -1;
    }

    /** 注册一个线程（名称+分类） */
    @CEntryPoint(name = "threadstat_register")
    public static int registerThread(IsolateThread thread, CCharPointer namePtr, CCharPointer categoryPtr) {
        return registerThreadInternal(CTypeConversion.toJavaString(namePtr), CTypeConversion.toJavaString(categoryPtr));
    }

    /** 内部 String 版注册线程（供同 DLL 内其他 Java 类直接调用） */
    public static int registerThreadInternal(String name, String category) {
        ThreadInfo info = new ThreadInfo();
        info.name = name;
        info.category = category;
        info.cpuTimeNs = 0;
        info.lastCpuTimeNs = 0;
        info.lastSampleMs = System.currentTimeMillis();
        info.cpuPercent = 0;
        info.alive = true;
        info.threadId = Thread.currentThread().getId();
        threads.put(name, info);
        return 0;
    }

    /** 标记线程结束 */
    @CEntryPoint(name = "threadstat_unregister")
    public static int unregisterThread(IsolateThread thread, CCharPointer namePtr) {
        String name = CTypeConversion.toJavaString(namePtr);
        ThreadInfo info = threads.get(name);
        if (info != null) info.alive = false;
        return 0;
    }

    /** 采样所有线程状态（更新CPU/内存/磁盘数据），返回采样数量 */
    @CEntryPoint(name = "threadstat_sample")
    public static int sample(IsolateThread thread) {
        long now = System.currentTimeMillis();
        long elapsedMs = now - startTime;

        // CPU 采样（通过 ThreadMXBean，如果可用）
        try {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            if (bean.isThreadCpuTimeSupported()) {
                for (ThreadInfo info : threads.values()) {
                    if (!info.alive) continue;
                    // 近似：用线程ID查找CPU时间
                    long cpuNs = bean.getThreadCpuTime(info.threadId);
                    if (cpuNs < 0) cpuNs = info.cpuTimeNs; // 线程已结束，保持上次值
                    long deltaNs = cpuNs - info.lastCpuTimeNs;
                    long deltaMs = now - info.lastSampleMs;
                    if (deltaMs > 0) {
                        info.cpuPercent = (deltaNs / 1_000_000.0) / deltaMs * 100.0;
                        if (info.cpuPercent > 100) info.cpuPercent = 100;
                    }
                    info.cpuTimeNs = cpuNs;
                    info.lastCpuTimeNs = cpuNs;
                    info.lastSampleMs = now;
                }
            }
        } catch (Throwable ignored) {}

        return threads.size();
    }

    /** 获取统计数据 JSON（String 级，供控制层 dispatch 直调） */
    public static String getJsonInternal() {
        return buildStatsJson();
    }

    /** 获取统计数据 JSON（所有线程CPU+内存+磁盘），写入 outBuf */
    @CEntryPoint(name = "threadstat_get_json")
    public static int getJson(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String json = buildStatsJson();
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 获取内存使用情况 JSON，写入 outBuf */
    @CEntryPoint(name = "threadstat_memory")
    public static int memory(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = total - free;
        String json = String.format(
            "{\"max\":%d,\"total\":%d,\"used\":%d,\"free\":%d,\"used_percent\":%.1f}",
            max, total, used, free, max > 0 ? (used * 100.0 / max) : 0);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 记录磁盘读取字节数 */
    @CEntryPoint(name = "threadstat_disk_read")
    public static void diskRead(IsolateThread thread, long bytes) {
        totalDiskRead += bytes;
    }

    /** 记录磁盘写入字节数 */
    @CEntryPoint(name = "threadstat_disk_write")
    public static void diskWrite(IsolateThread thread, long bytes) {
        totalDiskWrite += bytes;
    }

    /** 获取磁盘 I/O 统计 JSON */
    @CEntryPoint(name = "threadstat_disk")
    public static int disk(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String json = String.format("{\"read_bytes\":%d,\"write_bytes\":%d,\"total_bytes\":%d}",
            totalDiskRead, totalDiskWrite, totalDiskRead + totalDiskWrite);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    // ================================================================
    //  内部实现
    // ================================================================

    private static String buildStatsJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"mode\":\"").append(threadMode).append("\"");
        json.append(",\"uptime_ms\":").append(System.currentTimeMillis() - startTime);
        json.append(",\"thread_count\":").append(threads.size());
        json.append(",\"threads\":[");
        boolean first = true;
        for (ThreadInfo info : threads.values()) {
            if (!first) json.append(",");
            first = false;
            json.append("{\"name\":\"").append(escapeJson(info.name)).append("\"");
            json.append(",\"category\":\"").append(info.category).append("\"");
            json.append(",\"cpu_percent\":").append(String.format("%.1f", info.cpuPercent));
            json.append(",\"cpu_time_ms\":").append(info.cpuTimeNs / 1_000_000);
            json.append(",\"alive\":").append(info.alive ? "true" : "false");
            json.append("}");
        }
        json.append("]");
        // 内存
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        json.append(",\"memory\":{\"used\":").append(used);
        json.append(",\"max\":").append(rt.maxMemory());
        json.append(",\"used_percent\":").append(String.format("%.1f", rt.maxMemory() > 0 ? used * 100.0 / rt.maxMemory() : 0));
        json.append("}");
        // 磁盘
        json.append(",\"disk\":{\"read_bytes\":").append(totalDiskRead);
        json.append(",\"write_bytes\":").append(totalDiskWrite);
        json.append("}");
        json.append("}");
        return json.toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

            }