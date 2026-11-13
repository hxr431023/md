import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * mdmarkdown.dll — Markdown 解析与渲染模块（可复用）
 *
 * 功能：
 *   - Markdown → HTML 转换（对预览用）
 *   - 逐行块级样式分类（h1/h2/h3/paragraph/quote/list/ordered/code/empty）
 *   - 内联样式区间分析（bold/italic/code/strike/bolditalic）
 *   - 文本统计（行/词/字符）
 *   - C ABI 导出
 *
 * 内联样式标记：**粗体**、*斜体*、``代码``、~~删除线~~、***粗斜体***
 * 块级标记：# / ## / ### / > / - / 1. / 缩进代码
 */
public class MDMarkdown {

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /** Markdown → HTML，写入 outBuf，返回 HTML 长度 */
    @CEntryPoint(name = "render_html")
    public static int renderHtml(IsolateThread thread, CCharPointer mdPtr, CCharPointer outBuf, int bufSize) {
        String md = CTypeConversion.toJavaString(mdPtr);
        String html = markdownToHtml(md);
        CTypeConversion.toCString(html, outBuf, WordFactory.unsigned(bufSize));
        return html.length();
    }

    /** 分类单行的块级类型，返回类型索引（0=empty,1=h1,2=h2,3=h3,4=quote,5=list,6=ordered,7=code,8=paragraph） */
    @CEntryPoint(name = "classify_line")
    public static int classifyLineC(IsolateThread thread, CCharPointer linePtr) {
        String line = CTypeConversion.toJavaString(linePtr);
        String type = classifyLine(line);
        switch (type) {
            case "empty": return 0;
            case "h1": return 1;
            case "h2": return 2;
            case "h3": return 3;
            case "quote": return 4;
            case "list": return 5;
            case "ordered": return 6;
            case "code": return 7;
            default: return 8;
        }
    }

    /** 分析单行内联样式，返回样式数量；样式详情通过回调缓冲区返回（简化版：返回数量） */
    @CEntryPoint(name = "count_inline_styles")
    public static int countInlineStyles(IsolateThread thread, CCharPointer linePtr) {
        String line = CTypeConversion.toJavaString(linePtr);
        return analyzeInlineStyles(line).size();
    }

    /** 统计行数 */
    @CEntryPoint(name = "count_lines")
    public static int countLines(IsolateThread thread, CCharPointer textPtr) {
        String text = CTypeConversion.toJavaString(textPtr);
        if (text.isEmpty()) return 0;
        return text.split("\n", -1).length;
    }

    /** 统计单词数 */
    @CEntryPoint(name = "count_words")
    public static int countWords(IsolateThread thread, CCharPointer textPtr) {
        String text = CTypeConversion.toJavaString(textPtr);
        if (text.trim().isEmpty()) return 0;
        return text.trim().split("\\s+").length;
    }

    /** 统计字符数 */
    @CEntryPoint(name = "count_chars")
    public static int countChars(IsolateThread thread, CCharPointer textPtr) {
        return CTypeConversion.toJavaString(textPtr).length();
    }

    // ================================================================
    //  内部实现（供同 DLL 内其他 Java 类直接调用）
    // ================================================================

    /** 内联样式区间 */
    public static class InlineStyle {
        public int start;
        public int end;
        public String style; // bold/italic/code/strike/bolditalic
        public InlineStyle(int start, int end, String style) {
            this.start = start; this.end = end; this.style = style;
        }
    }

    public static String classifyLine(String line) {
        if (line.isEmpty()) return "empty";
        if (line.startsWith("### ")) return "h3";
        if (line.startsWith("## ")) return "h2";
        if (line.startsWith("# ")) return "h1";
        if (line.startsWith("> ")) return "quote";
        if (line.startsWith("- ")) return "list";
        if (line.matches("^\\d+\\. ")) return "ordered";
        if (line.startsWith("    ") || line.startsWith("\t")) return "code";
        return "paragraph";
    }

    public static List<InlineStyle> analyzeInlineStyles(String line) {
        List<InlineStyle> list = new ArrayList<>();
        addPattern(list, line, "\\*\\*\\*(.+?)\\*\\*\\*", "bolditalic");
        addPattern(list, line, "\\*\\*(.+?)\\*\\*", "bold");
        addPatternSingle(list, line, "(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "italic");
        addPattern(list, line, "``(.+?)``", "code");
        addPattern(list, line, "~~(.+?)~~", "strike");
        return list;
    }

    private static void addPattern(List<InlineStyle> list, String line, String regex, String style) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(line);
        while (m.find()) {
            list.add(new InlineStyle(m.start(), m.end(), style));
        }
    }

    private static void addPatternSingle(List<InlineStyle> list, String line, String regex, String style) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(line);
        while (m.find()) {
            list.add(new InlineStyle(m.start(), m.end(), style));
        }
    }

    /** Markdown → HTML 完整转换 */
    public static String markdownToHtml(String md) {
        StringBuilder html = new StringBuilder();
        String[] lines = md.split("\n", -1);
        boolean inCodeBlock = false;
        boolean inList = false;
        for (String line : lines) {
            if (line.startsWith("```")) {
                if (inCodeBlock) { html.append("</code></pre>\n"); inCodeBlock = false; }
                else { html.append("<pre><code>"); inCodeBlock = true; }
                continue;
            }
            if (inCodeBlock) { html.append(escapeHtml(line)).append("\n"); continue; }
            if (inList && !line.startsWith("- ") && !line.matches("^\\d+\\. ")) {
                html.append("</ul>\n"); inList = false;
            }
            if (line.startsWith("### ")) { html.append("<h3>").append(inlineMd(line.substring(4))).append("</h3>\n"); }
            else if (line.startsWith("## ")) { html.append("<h2>").append(inlineMd(line.substring(3))).append("</h2>\n"); }
            else if (line.startsWith("# ")) { html.append("<h1>").append(inlineMd(line.substring(2))).append("</h1>\n"); }
            else if (line.startsWith("> ")) { html.append("<blockquote>").append(inlineMd(line.substring(2))).append("</blockquote>\n"); }
            else if (line.startsWith("- ")) {
                if (!inList) { html.append("<ul>\n"); inList = true; }
                html.append("<li>").append(inlineMd(line.substring(2))).append("</li>\n");
            }
            else if (line.matches("^\\d+\\. ")) {
                html.append("<li>").append(inlineMd(line.replaceFirst("^\\d+\\. ", ""))).append("</li>\n");
            }
            else if (line.trim().isEmpty()) { html.append("\n"); }
            else { html.append("<p>").append(inlineMd(line)).append("</p>\n"); }
        }
        if (inList) html.append("</ul>\n");
        if (inCodeBlock) html.append("</code></pre>\n");
        return html.toString();
    }

    private static String inlineMd(String text) {
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<em>$1</em>");
        text = text.replaceAll("``(.+?)``", "<code>$1</code>");
        text = text.replaceAll("~~(.+?)~~", "<del>$1</del>");
        text = text.replaceAll("\\[(.+?)\\]\\((.+?)\\)", "<a href=\"$2\">$1</a>");
        return text;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ================================================================
    //  IDP 输出（调用 mdidp 模块，所有模块通用通信格式）
    // ================================================================

    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "markdown_write_idp")
    public static int writeIdp(IsolateThread thread, CCharPointer filePathPtr, CCharPointer typePtr, CCharPointer jsonDataPtr) {
        try {
            String type = CTypeConversion.toJavaString(typePtr);
            String jsonData = CTypeConversion.toJavaString(jsonDataPtr);
            MDIdp.enqueue(type, "mdmarkdown", jsonData);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    @CEntryPoint(name = "markdown_to_idp")
    public static int toIdp(IsolateThread thread, CCharPointer typePtr, CCharPointer jsonDataPtr, CCharPointer outBuf, int bufSize) {
        String type = CTypeConversion.toJavaString(typePtr);
        String jsonData = CTypeConversion.toJavaString(jsonDataPtr);
        String idp = MDIdp.createPacket(type, "mdmarkdown", jsonData);
        CTypeConversion.toCString(idp, outBuf, WordFactory.unsigned(bufSize));
        return idp.length();
    }

    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    @CEntryPoint(name = "markdown_read_idp")
    public static int readIdp(IsolateThread thread, CCharPointer filePathPtr, CCharPointer outBuf, int bufSize) {
        String jsonData = MDIdp.dequeueJson();
        CTypeConversion.toCString(jsonData, outBuf, WordFactory.unsigned(bufSize));
        return jsonData.length();
    }
}
