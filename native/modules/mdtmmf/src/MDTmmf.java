import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * mdtmmf.dll — TMMF 查看器/搜索器模块（可独立复用）
 *
 * 功能：
 *   - 读取 TMMF 文件全部内容
 *   - 按标签精确读取内容（<tag>content<tag>）
 *   - 多关键词搜索：传入多个搜索词（逗号分隔），返回所有包含任一搜索词的行
 *     不是一个搜索词对应一个内容，而是多个搜索词对应多行结果（多对多）
 *     只要搜索词出现在某一行里，就返回整行内容
 *   - 列出所有标签名
 *   - C ABI 导出
 *
 * TMMF 格式：
 *   第1行：TMMF.utf8
 *   第2行：版本号
 *   空行
 *   1
 *   # 注释行
 *   <tag>content<tag>
 */
public class MDTmmf {

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /** 读取 TMMF 文件全部内容，写入 outBuf，返回字符数 */
    @CEntryPoint(name = "tmmf_read_all")
    public static int readAll(IsolateThread thread, CCharPointer pathPtr, CCharPointer outBuf, int bufSize) {
        try {
            String path = CTypeConversion.toJavaString(pathPtr);
            String content = readFileContent(path);
            CTypeConversion.toCString(content, outBuf, WordFactory.unsigned(bufSize));
            return content.length();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 按标签名精确读取内容，写入 outBuf，返回内容长度 */
    @CEntryPoint(name = "tmmf_read_tag")
    public static int readTag(IsolateThread thread, CCharPointer pathPtr, CCharPointer tagPtr, CCharPointer outBuf, int bufSize) {
        String path = CTypeConversion.toJavaString(pathPtr);
        String tag = CTypeConversion.toJavaString(tagPtr);
        String content = readTagContent(path, tag);
        CTypeConversion.toCString(content, outBuf, WordFactory.unsigned(bufSize));
        return content.length();
    }

    /**
     * 多关键词搜索（多对多）。
     * 传入多个搜索词（英文逗号分隔），返回所有包含任一搜索词的行（JSON数组）。
     * 只要搜索词出现在某一行里，就返回整行。
     * 输出格式：{"matches":[{"line":0,"text":"..."},{"line":2,"text":"..."}]}
     */
    @CEntryPoint(name = "tmmf_search")
    public static int search(IsolateThread thread, CCharPointer pathPtr, CCharPointer keywordsPtr, CCharPointer outBuf, int bufSize) {
        String path = CTypeConversion.toJavaString(pathPtr);
        String keywordsStr = CTypeConversion.toJavaString(keywordsPtr);
        String[] keywords = keywordsStr.split(",");
        // 去除每个关键词的首尾空格，过滤空词
        List<String> kwList = new ArrayList<>();
        for (String kw : keywords) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty()) kwList.add(trimmed);
        }
        String json = searchLines(path, kwList);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 列出 TMMF 文件中所有标签名（JSON数组），写入 outBuf */
    @CEntryPoint(name = "tmmf_list_tags")
    public static int listTags(IsolateThread thread, CCharPointer pathPtr, CCharPointer outBuf, int bufSize) {
        String path = CTypeConversion.toJavaString(pathPtr);
        List<String> tags = extractAllTags(path);
        StringBuilder json = new StringBuilder();
        json.append("{\"tags\":[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(tags.get(i))).append("\"");
        }
        json.append("]}");
        String result = json.toString();
        CTypeConversion.toCString(result, outBuf, WordFactory.unsigned(bufSize));
        return result.length();
    }

    /** 获取 TMMF 文件总行数 */
    @CEntryPoint(name = "tmmf_line_count")
    public static int lineCount(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        try {
            String content = readFileContent(path);
            if (content.isEmpty()) return 0;
            return content.split("\n", -1).length;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 写入 TMMF 标签内容（按题目替换或追加），规范格式 <\题目>内容<题目/>，返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "tmmf_write_tag")
    public static int writeTag(IsolateThread thread, CCharPointer pathPtr, CCharPointer tagPtr, CCharPointer contentPtr) {
        try {
            String path = CTypeConversion.toJavaString(pathPtr);
            String tag = CTypeConversion.toJavaString(tagPtr);
            String newContent = CTypeConversion.toJavaString(contentPtr);
            File f = new File(path);
            String existing = "";
            if (f.exists()) {
                existing = readFileContent(path);
            }

            // 文件不存在或无有效头部：创建标准 TMMF 头部
            if (existing.isEmpty() || !validateHeader(existing)) {
                existing = "TMMF.;utf8\n" +
                           "icon_mdrenderer\n" +
                           " \n" +
                           "0\n" +
                           "…\n";
            }

            String[] lines = existing.split("\n", -1);
            boolean found = false;
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (!found && !isSkippable(line, i)) {
                    String[] parsed = parseDataLine(line);
                    if (parsed != null && parsed[0].equals(tag)) {
                        // 按题目匹配，替换整行内容
                        line = "<\\" + tag + ">" + newContent + "<" + tag + "/>";
                        found = true;
                    }
                }
                result.append(line);
                if (i < lines.length - 1) result.append("\n");
            }

            // 没找到，在末尾 … 之前追加新行
            if (!found) {
                String newLine = "<\\" + tag + ">" + newContent + "<" + tag + "/>";
                String contentStr = result.toString();
                if (contentStr.endsWith("…\n")) {
                    contentStr = contentStr.substring(0, contentStr.length() - 2) + newLine + "\n…\n";
                } else if (contentStr.endsWith("…")) {
                    contentStr = contentStr.substring(0, contentStr.length() - 1) + newLine + "\n…";
                } else {
                    contentStr += "\n" + newLine + "\n…\n";
                }
                result = new StringBuilder(contentStr);
            }

            f.getParentFile().mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(result.toString());
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    // ================================================================
    //  内部实现（TMMF 规范：<\题目>key=value<题目/>，题目为行标识）
    // ================================================================

    private static final String TMMF_MAGIC = "TMMF.";
    /** 开标签正则：<\题目>，捕获题目 */
    private static final java.util.regex.Pattern OPEN_TAG_PATTERN = java.util.regex.Pattern.compile("<\\\\(.*?)>");
    /** 闭标签正则：<题目/> 或 </>，捕获题目 */
    private static final java.util.regex.Pattern CLOSE_TAG_PATTERN = java.util.regex.Pattern.compile("<(.*?)/>");

    /** 读取文件内容 */
    private static String readFileContent(String path) throws IOException {
        java.io.File f = new java.io.File(path);
        if (!f.exists()) return "";
        byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 校验 TMMF 文件头：首行魔数 TMMF. + 第4行文件状态 */
    private static boolean validateHeader(String content) {
        if (content.isEmpty()) return false;
        String[] lines = content.split("\n", -1);
        if (lines.length < 4) return false;
        if (!lines[0].trim().startsWith(TMMF_MAGIC)) return false;
        try {
            int status = Integer.parseInt(lines[3].trim());
            if (status == -1) return false;
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /** 判断是否为可跳过行（前4行头部、注释、空行、末尾…） */
    private static boolean isSkippable(String line, int lineIndex) {
        if (lineIndex < 4) return true;
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("…") || trimmed.equals("…  …");
    }

    /** 解析一行数据，返回 [题目, 内容]，解析失败返回 null */
    private static String[] parseDataLine(String line) {
        String trimmed = line.trim();
        java.util.regex.Matcher openM = OPEN_TAG_PATTERN.matcher(trimmed);
        if (!openM.find()) return null;
        String title = openM.group(1);
        int contentStart = openM.end();
        java.util.regex.Matcher closeM = CLOSE_TAG_PATTERN.matcher(trimmed);
        if (!closeM.find(contentStart)) return null;
        String closeTitle = closeM.group(1);
        // 开标签和闭标签的题目应一致（空题目时都为空）
        if (!title.equals(closeTitle)) return null;
        String content = trimmed.substring(contentStart, closeM.start());
        return new String[]{title, content};
    }

    /** 按题目读取值：在 <\题目>key=value<题目/> 中查找匹配题目，返回内容 */
    private static String readTagContent(String path, String tag) {
        try {
            String content = readFileContent(path);
            if (!validateHeader(content)) return "";
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (isSkippable(lines[i], i)) continue;
                String[] parsed = parseDataLine(lines[i]);
                if (parsed != null && parsed[0].equals(tag)) {
                    return parsed[1];
                }
            }
        } catch (Exception e) {}
        return "";
    }

    /**
     * 多关键词搜索：返回所有包含任一关键词的有效数据行。
     * 多对多：多个搜索词 → 多行结果。只要行内有任一搜索词就匹配。
     */
    private static String searchLines(String path, List<String> keywords) {
        StringBuilder json = new StringBuilder();
        json.append("{\"matches\":[");
        try {
            String content = readFileContent(path);
            if (!validateHeader(content)) {
                json.append("]}");
                return json.toString();
            }
            String[] lines = content.split("\n", -1);
            boolean first = true;
            for (int i = 0; i < lines.length; i++) {
                if (isSkippable(lines[i], i)) continue;
                String line = lines[i];
                boolean matched = false;
                for (String kw : keywords) {
                    if (line.contains(kw)) { matched = true; break; }
                }
                if (matched) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"line\":").append(i);
                    json.append(",\"text\":\"").append(escapeJson(line.trim())).append("\"}");
                }
            }
        } catch (Exception ignored) {}
        json.append("]}");
        return json.toString();
    }

    /** 提取所有数据行的题目（去重） */
    private static List<String> extractAllTags(String path) {
        List<String> tags = new ArrayList<>();
        try {
            String content = readFileContent(path);
            if (!validateHeader(content)) return tags;
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (isSkippable(lines[i], i)) continue;
                String[] parsed = parseDataLine(lines[i]);
                if (parsed != null) {
                    String title = parsed[0];
                    if (!title.isEmpty() && !tags.contains(title)) tags.add(title);
                }
            }
        } catch (Exception ignored) {}
        return tags;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
