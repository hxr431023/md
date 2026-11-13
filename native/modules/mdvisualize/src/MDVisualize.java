import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * mdvisualize.dll — 可视化模块（可复用）
 *
 * 功能：
 *   - 将 mdthreadstat 的统计数据（JSON）生成为可视化 HTML 报告
 *   - 包含 CPU 占用柱状图、内存占用仪表盘、磁盘 I/O 趋势、线程列表
 *   - 纯 HTML+CSS+内联JS，无需外部依赖，浏览器直接打开
 *   - 供设置中的"专业人士视图"使用
 *   - C ABI 导出
 *
 * 设计：生成自包含 HTML 文件，使用 CSS 绘制条形图和百分比，
 *       不依赖外部图表库，确保离线可用。
 */
public class MDVisualize {

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /**
     * 将统计 JSON 生成为可视化 HTML 报告，写入文件。
     * @param statsJsonPtr  mdthreadstat 返回的统计 JSON
     * @param outputPathPtr 输出 HTML 文件路径
     * @return 0 成功 / -1 失败
     */
    @CEntryPoint(name = "visualize_stats_to_file")
    public static int statsToFile(IsolateThread thread, CCharPointer statsJsonPtr, CCharPointer outputPathPtr) {
        try {
            String statsJson = CTypeConversion.toJavaString(statsJsonPtr);
            String outputPath = CTypeConversion.toJavaString(outputPathPtr);
            String html = generateStatsHtml(statsJson);
            File f = new File(outputPath);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(html);
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 统计 JSON → 可视化 HTML（String 级，供控制层 dispatch 直调） */
    public static String statsToHtmlInternal(String statsJson) {
        return generateStatsHtml(statsJson);
    }

    /**
     * 将统计 JSON 生成为可视化 HTML 字符串，写入 outBuf。（实时渲染，不写文件）
     */
    @CEntryPoint(name = "visualize_stats_to_html")
    public static int statsToHtml(IsolateThread thread, CCharPointer statsJsonPtr, CCharPointer outBuf, int bufSize) {
        String statsJson = CTypeConversion.toJavaString(statsJsonPtr);
        String html = generateStatsHtml(statsJson);
        CTypeConversion.toCString(html, outBuf, WordFactory.unsigned(bufSize));
        return html.length();
    }

    /**
     * 实时渲染：按指定字段排序后生成 HTML 字符串（默认按 cpu_percent 降序）。
     * 调用 mdquicksort 对 threads 数组排序。
     * @param statsJsonPtr  统计 JSON
     * @param sortFieldPtr  排序字段（如 "cpu_percent"），空则不排序
     * @param ascPtr        "true"=升序，"false"=降序
     * @param outBuf        输出 HTML
     * @return HTML 长度
     */
    @CEntryPoint(name = "visualize_stats_sorted_html")
    public static int statsSortedHtml(IsolateThread thread, CCharPointer statsJsonPtr, CCharPointer sortFieldPtr, CCharPointer ascPtr, CCharPointer outBuf, int bufSize) {
        String statsJson = CTypeConversion.toJavaString(statsJsonPtr);
        String sortField = CTypeConversion.toJavaString(sortFieldPtr);
        boolean asc = Boolean.parseBoolean(CTypeConversion.toJavaString(ascPtr));
        // 提取 threads 数组，排序，再塞回去
        if (sortField != null && !sortField.isEmpty()) {
            int tStart = statsJson.indexOf("\"threads\":[");
            if (tStart >= 0) {
                int tEnd = statsJson.indexOf("]", tStart);
                if (tEnd > tStart) {
                    String threadsArr = statsJson.substring(tStart + 11, tEnd + 1);
                    String sorted = MDQuickSort.sortJsonArrayStatic(threadsArr, sortField, asc);
                    statsJson = statsJson.substring(0, tStart + 11) + sorted + statsJson.substring(tEnd + 1);
                }
            }
        }
        String html = generateStatsHtml(statsJson);
        CTypeConversion.toCString(html, outBuf, WordFactory.unsigned(bufSize));
        return html.length();
    }

    /**
     * 生成线程模式说明 HTML（三种模式对比），写入 outBuf。
     */
    @CEntryPoint(name = "visualize_thread_modes")
    public static int threadModes(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String html = generateThreadModesHtml();
        CTypeConversion.toCString(html, outBuf, WordFactory.unsigned(bufSize));
        return html.length();
    }

    // ================================================================
    //  内部实现
    // ================================================================

    private static String generateStatsHtml(String statsJson) {
        // 简单解析 JSON 中的关键字段（不依赖 JSON 库）
        String mode = extractJsonString(statsJson, "mode");
        String uptime = extractJsonNumber(statsJson, "uptime_ms");
        String threadCount = extractJsonNumber(statsJson, "thread_count");
        String memUsed = extractJsonNumber(statsJson, "memory", "used");
        String memMax = extractJsonNumber(statsJson, "memory", "max");
        String memPercent = extractJsonNumber(statsJson, "memory", "used_percent");
        String diskRead = extractJsonNumber(statsJson, "disk", "read_bytes");
        String diskWrite = extractJsonNumber(statsJson, "disk", "write_bytes");

        long uptimeSec = 0;
        try { uptimeSec = Long.parseLong(uptime) / 1000; } catch (Exception ignored) {}
        String uptimeStr = String.format("%d分%d秒", uptimeSec / 60, uptimeSec % 60);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n<title>MDRenderer 线程统计</title>\n");
        html.append("<style>\n");
        html.append("body{font-family:'Microsoft YaHei',Consolas,monospace;background:#1e1e1e;color:#d4d4d4;margin:0;padding:20px;}\n");
        html.append("h1{color:#569cd6;border-bottom:1px solid #3c3c3c;padding-bottom:10px;}\n");
        html.append("h2{color:#4ec9b0;margin-top:30px;}\n");
        html.append(".card{background:#252526;border:1px solid #3c3c3c;border-radius:6px;padding:15px;margin:10px 0;}\n");
        html.append(".stat-row{display:flex;justify-content:space-between;padding:5px 0;border-bottom:1px solid #3c3c3c;}\n");
        html.append(".stat-label{color:#9cdcfe;}\n");
        html.append(".stat-value{color:#ce9178;font-weight:bold;}\n");
        html.append(".bar-container{background:#3c3c3c;border-radius:4px;height:24px;margin:5px 0;overflow:hidden;}\n");
        html.append(".bar-fill{height:100%;border-radius:4px;transition:width 0.3s;}\n");
        html.append(".bar-cpu{background:linear-gradient(90deg,#4ec9b0,#569cd6);}\n");
        html.append(".bar-mem{background:linear-gradient(90deg,#ce9178,#dcdcaa);}\n");
        html.append(".thread-item{padding:8px;border-left:3px solid #569cd6;margin:5px 0;background:#2d2d30;}\n");
        html.append(".thread-name{color:#dcdcaa;font-weight:bold;}\n");
        html.append(".thread-cat{color:#808080;font-size:12px;margin-left:10px;}\n");
        html.append(".alive{color:#4ec9b0;}\n");
        html.append(".dead{color:#808080;}\n");
        html.append("table{width:100%;border-collapse:collapse;margin:10px 0;}\n");
        html.append("th,td{border:1px solid #3c3c3c;padding:8px;text-align:left;}\n");
        html.append("th{background:#2d2d30;color:#569cd6;}\n");
        html.append("</style>\n</head>\n<body>\n");

        html.append("<h1>MDRenderer 线程统计面板</h1>\n");

        // 概览卡片
        html.append("<div class=\"card\">\n<h2>概览</h2>\n");
        html.append("<div class=\"stat-row\"><span class=\"stat-label\">线程模式</span><span class=\"stat-value\">").append(mode).append("</span></div>\n");
        html.append("<div class=\"stat-row\"><span class=\"stat-label\">运行时间</span><span class=\"stat-value\">").append(uptimeStr).append("</span></div>\n");
        html.append("<div class=\"stat-row\"><span class=\"stat-label\">线程总数</span><span class=\"stat-value\">").append(threadCount).append("</span></div>\n");
        html.append("</div>\n");

        // CPU 占用
        html.append("<div class=\"card\">\n<h2>CPU 占用</h2>\n");
        html.append("<p>各线程 CPU 占用百分比（采样值）：</p>\n");
        // 从 JSON 中提取线程列表（简化处理，直接显示原始 JSON 中的 threads 部分）
        int threadsStart = statsJson.indexOf("\"threads\":[");
        if (threadsStart >= 0) {
            int threadsEnd = statsJson.indexOf("]", threadsStart);
            if (threadsEnd > threadsStart) {
                String threadsStr = statsJson.substring(threadsStart + 11, threadsEnd);
                // 简单解析每个线程对象
                String[] threadObjs = threadsStr.split("\\},\\{");
                for (String tobj : threadObjs) {
                    String name = extractJsonString(tobj, "name");
                    String cat = extractJsonString(tobj, "category");
                    String cpu = extractJsonNumber(tobj, "cpu_percent");
                    String alive = tobj.contains("\"alive\":true") ? "true" : "false";
                    double cpuVal = 0;
                    try { cpuVal = Double.parseDouble(cpu); } catch (Exception ignored) {}
                    String aliveClass = "true".equals(alive) ? "alive" : "dead";
                    html.append("<div class=\"thread-item\">\n");
                    html.append("<span class=\"thread-name\">").append(name).append("</span>");
                    html.append("<span class=\"thread-cat\">[").append(cat).append("]</span>");
                    html.append("<span class=\"").append(aliveClass).append("\" style=\"float:right;\">").append("true".equals(alive) ? "运行中" : "已结束").append("</span>\n");
                    html.append("<div class=\"bar-container\"><div class=\"bar-fill bar-cpu\" style=\"width:").append(String.format("%.0f", cpuVal)).append("%\"></div></div>\n");
                    html.append("<span style=\"font-size:12px;color:#808080;\">CPU: ").append(String.format("%.1f", cpuVal)).append("%</span>\n");
                    html.append("</div>\n");
                }
            }
        }
        html.append("</div>\n");

        // 内存占用
        html.append("<div class=\"card\">\n<h2>内存占用</h2>\n");
        double memPct = 0;
        try { memPct = Double.parseDouble(memPercent); } catch (Exception ignored) {}
        long memUsedBytes = 0, memMaxBytes = 0;
        try { memUsedBytes = Long.parseLong(memUsed); memMaxBytes = Long.parseLong(memMax); } catch (Exception ignored) {}
        html.append("<div class=\"bar-container\"><div class=\"bar-fill bar-mem\" style=\"width:").append(String.format("%.0f", memPct)).append("%\"></div></div>\n");
        html.append("<div class=\"stat-row\"><span class=\"stat-label\">已用</span><span class=\"stat-value\">").append(formatBytes(memUsedBytes)).append("</span></div>\n");
        html.append("<div class=\"stat-row\"><span class=\"stat-label\">最大</span><span class=\"stat-value\">").append(formatBytes(memMaxBytes)).append("</span></div>\n");
        html.append("<div class=\"stat-row\"><span class=\"stat-label\">占用率</span><span class=\"stat-value\">").append(String.format("%.1f%%", memPct)).append("</span></div>\n");
        html.append("</div>\n");

        // 磁盘 I/O
        html.append("<div class=\"card\">\n<h2>磁盘 I/O</h2>\n");
        long dr = 0, dw = 0;
        try { dr = Long.parseLong(diskRead); dw = Long.parseLong(diskWrite); } catch (Exception ignored) {}
        html.append("<table>\n<tr><th>方向</th><th>字节数</th><th>可读格式</th></tr>\n");
        html.append("<tr><td>读取</td><td>").append(dr).append("</td><td>").append(formatBytes(dr)).append("</td></tr>\n");
        html.append("<tr><td>写入</td><td>").append(dw).append("</td><td>").append(formatBytes(dw)).append("</td></tr>\n");
        html.append("<tr><td>总计</td><td>").append(dr + dw).append("</td><td>").append(formatBytes(dr + dw)).append("</td></tr>\n");
        html.append("</table>\n</div>\n");

        // 原始数据
        html.append("<div class=\"card\">\n<h2>原始 JSON 数据</h2>\n");
        html.append("<pre style=\"background:#1e1e1e;padding:10px;border-radius:4px;overflow-x:auto;font-size:12px;\">").append(escapeHtml(statsJson)).append("</pre>\n");
        html.append("</div>\n");

        html.append("</body>\n</html>");
        return html.toString();
    }

    private static String generateThreadModesHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"card\">\n<h2>线程模式说明</h2>\n");
        html.append("<table>\n<tr><th>模式</th><th>说明</th><th>适用场景</th></tr>\n");
        html.append("<tr><td><b>folder（默认）</b></td><td>每个 DLL 分类文件夹一个线程：core/doc/tools/app 各一个线程</td><td>平衡性能和资源占用，推荐</td></tr>\n");
        html.append("<tr><td><b>single</b></td><td>全部单线程，所有 DLL 在一个线程中顺序执行</td><td>调试、低资源设备、兼容性优先</td></tr>\n");
        html.append("<tr><td><b>per_dll</b></td><td>每个独立 DLL 一个线程，最大并行度</td><td>高性能服务器、多核CPU、批量处理</td></tr>\n");
        html.append("</table>\n</div>\n");
        return html.toString();
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return json.substring(start, end);
    }

    private static String extractJsonNumber(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return "0";
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        return json.substring(start, end);
    }

    private static String extractJsonNumber(String json, String parentKey, String key) {
        String pattern = "\"" + parentKey + "\":{";
        int start = json.indexOf(pattern);
        if (start < 0) return "0";
        int end = json.indexOf("}", start);
        if (end < 0) return "0";
        String sub = json.substring(start, end);
        return extractJsonNumber(sub, key);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

            }