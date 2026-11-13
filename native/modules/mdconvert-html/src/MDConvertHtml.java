import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * mdconvert-html.dll — Markdown → 完整 HTML 文档转换（可独立复用）
 *
 * 功能：
 *   - MD 文本 → 完整 HTML 文档（含 <!DOCTYPE>/<html>/<head>/<body>/内联CSS）
 *   - 支持标题、段落、引用、列表、代码块、水平线、内联样式（粗体/斜体/代码/删除线/链接）
 *   - 直接从文件转换并保存
 *   - C ABI 导出
 *
 * 依赖：无（纯自包含）
 */
public class MDConvertHtml {

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /** MD 文本 → 完整 HTML 文档，写入 outBuf，返回长度 */
    @CEntryPoint(name = "convert_html")
    public static int toHtml(IsolateThread thread, CCharPointer mdPtr, CCharPointer outBuf, int bufSize) {
        String md = CTypeConversion.toJavaString(mdPtr);
        String html = convertToFullHtml(md);
        CTypeConversion.toCString(html, outBuf, WordFactory.unsigned(bufSize));
        return html.length();
    }

    /** 从 MD 文件转换为 HTML 文件，返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "convert_html_file")
    public static int convertFile(IsolateThread thread, CCharPointer inputPtr, CCharPointer outputPtr) {
        String input = CTypeConversion.toJavaString(inputPtr);
        String output = CTypeConversion.toJavaString(outputPtr);
        try {
            String md = readFile(input);
            String html = convertToFullHtml(md);
            writeFile(output, html);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    // ================================================================
    //  内部实现
    // ================================================================

    private static String convertToFullHtml(String md) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Converted Document</title>\n");
        html.append("<style>\n");
        html.append("body{font-family:'Microsoft YaHei',sans-serif;max-width:800px;margin:40px auto;padding:0 20px;line-height:1.6;color:#333;}\n");
        html.append("h1{border-bottom:2px solid #eee;padding-bottom:10px;}\n");
        html.append("h2{border-bottom:1px solid #eee;padding-bottom:5px;}\n");
        html.append("code{background:#f4f4f4;padding:2px 6px;border-radius:3px;font-family:Consolas,monospace;}\n");
        html.append("pre{background:#f4f4f4;padding:15px;border-radius:5px;overflow-x:auto;}\n");
        html.append("blockquote{border-left:4px solid #ddd;margin:0;padding-left:15px;color:#666;}\n");
        html.append("table{border-collapse:collapse;width:100%;}\n");
        html.append("th,td{border:1px solid #ddd;padding:8px;text-align:left;}\n");
        html.append("th{background:#f4f4f4;}\n");
        html.append("</style>\n</head>\n<body>\n");
        html.append(convertToHtmlBody(md));
        html.append("\n</body>\n</html>");
        return html.toString();
    }

    static String convertToHtmlBody(String md) {
        StringBuilder html = new StringBuilder();
        String[] lines = md.split("\n", -1);
        boolean inCodeBlock = false;
        boolean inParagraph = false;

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (inParagraph) { html.append("</p>\n"); inParagraph = false; }
                if (inCodeBlock) { html.append("</code></pre>\n"); inCodeBlock = false; }
                else { html.append("<pre><code>"); inCodeBlock = true; }
                continue;
            }
            if (inCodeBlock) { html.append(escapeHtml(line)).append("\n"); continue; }
            if (line.trim().isEmpty()) {
                if (inParagraph) { html.append("</p>\n"); inParagraph = false; }
                continue;
            }
            if (line.startsWith("#")) {
                if (inParagraph) { html.append("</p>\n"); inParagraph = false; }
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                String content = line.substring(level).trim();
                html.append("<h").append(level).append(">").append(applyInlineStyles(content)).append("</h").append(level).append(">\n");
                continue;
            }
            if (line.trim().startsWith(">")) {
                if (inParagraph) { html.append("</p>\n"); inParagraph = false; }
                html.append("<blockquote>").append(applyInlineStyles(line.trim().substring(1).trim())).append("</blockquote>\n");
                continue;
            }
            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                if (inParagraph) { html.append("</p>\n"); inParagraph = false; }
                html.append("<ul><li>").append(applyInlineStyles(line.trim().substring(2))).append("</li></ul>\n");
                continue;
            }
            if (line.trim().matches("^\\d+\\.\\s.*")) {
                if (inParagraph) { html.append("</p>\n"); inParagraph = false; }
                int dot = line.trim().indexOf('.');
                html.append("<ol><li>").append(applyInlineStyles(line.trim().substring(dot + 1).trim())).append("</li></ol>\n");
                continue;
            }
            if (line.trim().equals("---") || line.trim().equals("***")) {
                if (inParagraph) { html.append("</p>\n"); inParagraph = false; }
                html.append("<hr>\n");
                continue;
            }
            if (!inParagraph) { html.append("<p>"); inParagraph = true; } else { html.append("\n"); }
            html.append(applyInlineStyles(line));
        }
        if (inParagraph) html.append("</p>\n");
        if (inCodeBlock) html.append("</code></pre>\n");
        return html.toString();
    }

    static String applyInlineStyles(String text) {
        String result = escapeHtml(text);
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        result = result.replaceAll("__(.+?)__", "<strong>$1</strong>");
        result = result.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        result = result.replaceAll("_(.+?)_", "<em>$1</em>");
        result = result.replaceAll("`(.+?)`", "<code>$1</code>");
        result = result.replaceAll("~~(.+?)~~", "<del>$1</del>");
        result = result.replaceAll("\\[(.+?)\\]\\((.+?)\\)", "<a href=\"$2\">$1</a>");
        return result;
    }

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String readFile(String path) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(new File(path).toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeFile(String path, String content) throws IOException {
        File f = new File(path);
        f.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(content); }
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
