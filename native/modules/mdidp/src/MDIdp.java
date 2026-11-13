import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * IDP = Interface Data Packet（接口数据包）
 *
 * 格式规范：XML 头 + JSON 数据
 *   第1行：XML 声明
 *   第2行：<idp> 根标签，含属性（version/type/timestamp/source/size）
 *   第3行：固定分隔符 ===IDP-DATA===
 *   第4行起：JSON 数据
 *
 * 示例：
 *   <?xml version="1.0" encoding="UTF-8"?>
 *   <idp version="1.0" type="render_instructions" timestamp="2026-10-12T12:00:00" source="mdcontroller" size="1234"/>
 *   ===IDP-DATA===
 *   {"lines":[{"n":0,"type":"h1","s":0,"e":12}]}
 *
 * 用途：DLL 与 Python 前端之间的数据交换、渲染缓存、设置快照等。
 * 文件名后缀：.idp
 */
public class MDIdp {

    public static final String VERSION = "1.0";
    public static final String DELIMITER = "===IDP-DATA===";
    public static final String EXTENSION = ".idp";

    /** 解析后的 IDP 数据包 */
    public static class Packet {
        public String version = VERSION;
        public String type = "";
        public String timestamp = "";
        public String source = "";
        public int size = 0;
        public String jsonData = "";

        public Packet() {}
        public Packet(String type, String source, String jsonData) {
            this.type = type;
            this.source = source;
            this.jsonData = jsonData;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
            this.size = jsonData.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    /** 构建 IDP 数据包文本 */
    public static String createPacket(String type, String source, String jsonData) {
        Packet p = new Packet(type, source, jsonData);
        return packetToString(p);
    }

    /** Packet 对象 → IDP 文本 */
    public static String packetToString(Packet p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<idp");
        sb.append(" version=\"").append(escapeXml(p.version)).append("\"");
        sb.append(" type=\"").append(escapeXml(p.type)).append("\"");
        sb.append(" timestamp=\"").append(escapeXml(p.timestamp)).append("\"");
        sb.append(" source=\"").append(escapeXml(p.source)).append("\"");
        sb.append(" size=\"").append(p.size).append("\"");
        sb.append("/>\n");
        sb.append(DELIMITER).append("\n");
        sb.append(p.jsonData);
        return sb.toString();
    }

    /** 解析 IDP 文本 → Packet 对象 */
    public static Packet parsePacket(String content) {
        Packet p = new Packet();
        try {
            int delimPos = content.indexOf(DELIMITER);
            if (delimPos < 0) return p;
            String header = content.substring(0, delimPos);
            String data = content.substring(delimPos + DELIMITER.length()).trim();
            p.jsonData = data;

            // 解析 <idp .../> 标签属性
            int tagStart = header.indexOf("<idp");
            if (tagStart >= 0) {
                int tagEnd = header.indexOf("/>", tagStart);
                if (tagEnd < 0) tagEnd = header.indexOf(">", tagStart);
                if (tagEnd > 0) {
                    String attrs = header.substring(tagStart + 4, tagEnd);
                    Map<String, String> attrMap = parseAttributes(attrs);
                    p.version = attrMap.getOrDefault("version", VERSION);
                    p.type = attrMap.getOrDefault("type", "");
                    p.timestamp = attrMap.getOrDefault("timestamp", "");
                    p.source = attrMap.getOrDefault("source", "");
                    try { p.size = Integer.parseInt(attrMap.getOrDefault("size", "0")); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return p;
    }

    /** 写入 IDP 文件 */
    public static void writePacket(String filePath, String type, String source, String jsonData) {
        try {
            String content = createPacket(type, source, jsonData);
            File f = new File(filePath);
            f.getParentFile().mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(content);
            }
        } catch (Exception ignored) {}
    }

    /** 读取 IDP 文件 */
    public static Packet readPacket(String filePath) {
        try {
            File f = new File(filePath);
            if (!f.exists()) return new Packet();
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String content = new String(bytes, StandardCharsets.UTF_8);
            return parsePacket(content);
        } catch (Exception e) {
            return new Packet();
        }
    }

    /** 读取 IDP 文件中的 JSON 数据部分 */
    public static String readJsonData(String filePath) {
        return readPacket(filePath).jsonData;
    }

    // ================================================================
    //  内存双向栈（Deque）—— 模块间高速通信，替代文件传递
    // ================================================================
    // 以前 IDP 通过文件传递（太慢），现在用内存中的双向队列。
    // 生产者从一端 push，消费者从另一端 pop（FIFO），也可以两端操作（LIFO）。
    // 所有模块共享同一个静态栈实例。

    private static final Deque<Packet> memStack = new ArrayDeque<>();

    /** 从前端（队首）压入数据包 */
    public static synchronized void pushFront(Packet p) { memStack.addFirst(p); }

    /** 从后端（队尾）压入数据包 */
    public static synchronized void pushBack(Packet p) { memStack.addLast(p); }

    /** 从前端弹出数据包（不存在返回 null） */
    public static synchronized Packet popFront() { return memStack.pollFirst(); }

    /** 从后端弹出数据包（不存在返回 null） */
    public static synchronized Packet popBack() { return memStack.pollLast(); }

    /** 查看前端（不弹出） */
    public static synchronized Packet peekFront() { return memStack.peekFirst(); }

    /** 查看后端（不弹出） */
    public static synchronized Packet peekBack() { return memStack.peekLast(); }

    /** 栈中数据包数量 */
    public static synchronized int stackSize() { return memStack.size(); }

    /** 清空栈 */
    public static synchronized void stackClear() { memStack.clear(); }

    /** 便捷方法：构建 Packet 并从后端压入（生产者常用） */
    public static synchronized void enqueue(String type, String source, String jsonData) {
        memStack.addLast(new Packet(type, source, jsonData));
    }

    /** 便捷方法：从前端弹出并返回 JSON 数据（消费者常用），空则返回 "" */
    public static synchronized String dequeueJson() {
        Packet p = memStack.pollFirst();
        return p != null ? p.jsonData : "";
    }

    /** 便捷方法：从前端弹出并返回完整 IDP 文本，空则返回 "" */
    public static synchronized String dequeueIdp() {
        Packet p = memStack.pollFirst();
        return p != null ? packetToString(p) : "";
    }

    // ================================================================
    //  C ABI 导出（内存双向栈操作）
    // ================================================================

    /** 压入数据包到后端（生产者），返回 0 */
    @CEntryPoint(name = "idp_push")
    public static int cPush(IsolateThread thread, CCharPointer typePtr, CCharPointer sourcePtr, CCharPointer jsonDataPtr) {
        enqueue(CTypeConversion.toJavaString(typePtr),
                CTypeConversion.toJavaString(sourcePtr),
                CTypeConversion.toJavaString(jsonDataPtr));
        return 0;
    }

    /** 从前端弹出数据包的 JSON 数据（消费者），写入 outBuf，返回长度 */
    @CEntryPoint(name = "idp_pop_json")
    public static int cPopJson(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String json = dequeueJson();
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 从前端弹出完整 IDP 文本，写入 outBuf，返回长度 */
    @CEntryPoint(name = "idp_pop_idp")
    public static int cPopIdp(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String idp = dequeueIdp();
        CTypeConversion.toCString(idp, outBuf, WordFactory.unsigned(bufSize));
        return idp.length();
    }

    /** 查看前端 JSON 数据（不弹出），写入 outBuf */
    @CEntryPoint(name = "idp_peek_json")
    public static int cPeekJson(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        Packet p = peekFront();
        String json = p != null ? p.jsonData : "";
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 获取栈中数据包数量 */
    @CEntryPoint(name = "idp_stack_size")
    public static int cStackSize(IsolateThread thread) {
        return stackSize();
    }

    /** 清空栈 */
    @CEntryPoint(name = "idp_stack_clear")
    public static int cStackClear(IsolateThread thread) {
        stackClear();
        return 0;
    }

    // ===== 内部工具 =====

    private static Map<String, String> parseAttributes(String attrStr) {
        Map<String, String> map = new HashMap<>();
        // 简单解析 key="value" 对
        int i = 0;
        while (i < attrStr.length()) {
            while (i < attrStr.length() && Character.isWhitespace(attrStr.charAt(i))) i++;
            int eq = attrStr.indexOf('=', i);
            if (eq < 0) break;
            String key = attrStr.substring(i, eq).trim();
            int quoteStart = attrStr.indexOf('"', eq);
            if (quoteStart < 0) break;
            int quoteEnd = attrStr.indexOf('"', quoteStart + 1);
            if (quoteEnd < 0) break;
            String value = attrStr.substring(quoteStart + 1, quoteEnd);
            map.put(key, value);
            i = quoteEnd + 1;
        }
        return map;
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
