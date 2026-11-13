import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mdlerf.dll — LERF 解释器模块（可独立复用）
 *
 * 功能：
 *   - 解析 LERF 格式文件（# 注释 + key=value 键值对）
 *   - 按键查询值
 *   - 多键批量查询
 *   - 列出所有键值对（JSON）
 *   - 验证 LERF 文件格式合法性
 *   - C ABI 导出
 *
 * LERF 格式：
 *   # 注释行（以 # 开头）
 *   key=value
 *   空行忽略
 */
public class MDLerf {

    private static Map<String, String> cache = new HashMap<>();
    private static String cachedPath = "";

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /** 加载并缓存 LERF 文件，返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "lerf_load")
    public static int load(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        try {
            cache = parseLerfFile(path);
            cachedPath = path;
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 按键查询值（从缓存），写入 outBuf，未找到返回原 key */
    @CEntryPoint(name = "lerf_get")
    public static int get(IsolateThread thread, CCharPointer keyPtr, CCharPointer outBuf, int bufSize) {
        String key = CTypeConversion.toJavaString(keyPtr);
        String val = cache.getOrDefault(key, key);
        CTypeConversion.toCString(val, outBuf, WordFactory.unsigned(bufSize));
        return val.length();
    }

    /** 直接从文件按键查询（不经过缓存），写入 outBuf */
    @CEntryPoint(name = "lerf_get_from_file")
    public static int getFromFile(IsolateThread thread, CCharPointer pathPtr, CCharPointer keyPtr, CCharPointer outBuf, int bufSize) {
        String path = CTypeConversion.toJavaString(pathPtr);
        String key = CTypeConversion.toJavaString(keyPtr);
        try {
            Map<String, String> map = parseLerfFile(path);
            String val = map.getOrDefault(key, key);
            CTypeConversion.toCString(val, outBuf, WordFactory.unsigned(bufSize));
            return val.length();
        } catch (Exception e) {
            CTypeConversion.toCString(key, outBuf, WordFactory.unsigned(bufSize));
            return key.length();
        }
    }

    /** 多键批量查询（逗号分隔多个 key），返回 JSON 对象 */
    @CEntryPoint(name = "lerf_get_multi")
    public static int getMulti(IsolateThread thread, CCharPointer keysPtr, CCharPointer outBuf, int bufSize) {
        String keysStr = CTypeConversion.toJavaString(keysPtr);
        String[] keys = keysStr.split(",");
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (String key : keys) {
            String trimmed = key.trim();
            if (trimmed.isEmpty()) continue;
            if (!first) json.append(",");
            first = false;
            String val = cache.getOrDefault(trimmed, trimmed);
            json.append("\"").append(escapeJson(trimmed)).append("\":\"").append(escapeJson(val)).append("\"");
        }
        json.append("}");
        String result = json.toString();
        CTypeConversion.toCString(result, outBuf, WordFactory.unsigned(bufSize));
        return result.length();
    }

    /** 列出所有键值对（JSON数组），写入 outBuf */
    @CEntryPoint(name = "lerf_list_all")
    public static int listAll(IsolateThread thread, CCharPointer pathPtr, CCharPointer outBuf, int bufSize) {
        String path = CTypeConversion.toJavaString(pathPtr);
        try {
            Map<String, String> map = parseLerfFile(path);
            StringBuilder json = new StringBuilder();
            json.append("{\"entries\":[");
            boolean first = true;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (!first) json.append(",");
                first = false;
                json.append("{\"key\":\"").append(escapeJson(entry.getKey())).append("\"");
                json.append(",\"value\":\"").append(escapeJson(entry.getValue())).append("\"}");
            }
            json.append("]}");
            String result = json.toString();
            CTypeConversion.toCString(result, outBuf, WordFactory.unsigned(bufSize));
            return result.length();
        } catch (Exception e) {
            CTypeConversion.toCString("{\"entries\":[]}", outBuf, WordFactory.unsigned(bufSize));
            return -1;
        }
    }

    /** 验证 LERF 文件格式，返回 1=合法 / 0=不合法（无 = 号的非注释非空行） */
    @CEntryPoint(name = "lerf_validate")
    public static int validate(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        try {
            List<String> errors = validateLerfFile(path);
            return errors.isEmpty() ? 1 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 获取键的数量 */
    @CEntryPoint(name = "lerf_key_count")
    public static int keyCount(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        try {
            return parseLerfFile(path).size();
        } catch (Exception e) {
            return -1;
        }
    }

    // ================================================================
    //  内部实现（LERF 规范：<\数字>text<数字ID/>）
    // ================================================================

    private static final String LERF_MAGIC = "LERF.";

    /** 校验 LERF 文件头：首行魔数 LERF. + 第4行文件状态 */
    private static boolean validateHeader(String[] lines) {
        if (lines.length < 4) return false;
        if (!lines[0].trim().startsWith(LERF_MAGIC)) return false;
        try {
            int status = Integer.parseInt(lines[3].trim());
            if (status == -1) return false; // 无效文件
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

    /** 解析 LERF 文件：<\数字>text<数字ID/> → Map<ID, text> */
    private static Map<String, String> parseLerfFile(String path) throws IOException {
        Map<String, String> map = new HashMap<>();
        File f = new File(path);
        if (!f.exists()) return map;
        byte[] bytes = Files.readAllBytes(f.toPath());
        String content = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\n", -1);
        if (!validateHeader(lines)) return map;

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<\\\\(\\d+)>(.*?)<(\\d+)/>");
        for (int i = 0; i < lines.length; i++) {
            if (isSkippable(lines[i], i)) continue;
            java.util.regex.Matcher m = p.matcher(lines[i].trim());
            if (m.find()) {
                String id = m.group(1);
                String text = m.group(2);
                map.put(id, text);
            }
        }
        return map;
    }

    /** 验证 LERF 文件格式：检查魔数、文件状态、数据行标签格式 */
    private static List<String> validateLerfFile(String path) throws IOException {
        List<String> errors = new ArrayList<>();
        File f = new File(path);
        if (!f.exists()) { errors.add("文件不存在"); return errors; }
        byte[] bytes = Files.readAllBytes(f.toPath());
        String content = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\n", -1);

        if (lines.length < 4) { errors.add("文件不足4行，缺少头部"); return errors; }
        if (!lines[0].trim().startsWith(LERF_MAGIC)) {
            errors.add("第1行魔数错误，应为 LERF.;编码");
        }
        try {
            int status = Integer.parseInt(lines[3].trim());
            if (status == -1) errors.add("第4行文件状态为-1（无效文件）");
        } catch (NumberFormatException e) {
            errors.add("第4行文件状态不是有效数字（0使用/1不使用/-1无效）");
        }

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<\\\\\\d+>.*?<\\d+/>");
        for (int i = 4; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("…") || trimmed.equals("…  …")) continue;
            if (!p.matcher(trimmed).find()) {
                errors.add("第" + (i + 1) + "行数据格式错误，应为 <\\数字>文本<数字ID/>: " + trimmed);
            }
        }
        return errors;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
