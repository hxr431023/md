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
 * render.dll — 通用 UI 渲染指令生成模块（可独立复用）
 *
 * 功能：
 *   - 把文档/界面描述内容解析为【通用渲染指令】（JSON），供前端（Rust/Python）作为通用渲染器渲染
 *   - 覆盖内容块 + 交互控件：heading/paragraph/quote/list/code/separator/image/button/input/menu/dialog/link/table
 *   - 支持 Markdown 块级语法，也支持扩展控件语法（{button:..} {input:..} {menu:..} {image:..} {table:..}）
 *   - 输出 IDP 格式渲染指令，前端按 kind 渲染任意软件界面
 *   - C ABI 导出
 *
 * 通用渲染指令 JSON 格式：
 * {
 *   "view":"ui","layout":"flow",
 *   "elements":[
 *     {"kind":"heading","level":1,"text":"标题"},
 *     {"kind":"paragraph","text":"正文","inline":[{"s":2,"e":5,"st":"bold"}]},
 *     {"kind":"list","ordered":false,"items":["a","b"]},
 *     {"kind":"button","text":"确定","action":"save"}
 *   ]
 * }
 *
 * 控件语法约定：
 *   {button:文本|动作}     {input:占位符}     {menu:菜单>子项,子项}
 *   {image:名称|路径|宽|高} {table:列1,列2|r1c1,r1c2;r2c1,r2c2}
 * 扩展语法： [文字]→表情/图片  :"文字"序号→对话框  ≈路径≈序号→超链接  \\>命令→彩蛋
 */
public class MDRender {

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /**
     * 通用渲染入口：从内容生成【通用 UI 渲染指令】（JSON），写入 outBuf，返回 JSON 长度。
     * 替换旧 Markdown 专用入口 render_from_text。任何软件界面描述都可传入。
     */
    @CEntryPoint(name = "render_ui")
    public static int renderUi(IsolateThread thread, CCharPointer contentPtr, CCharPointer outBuf, int bufSize) {
        String content = CTypeConversion.toJavaString(contentPtr);
        String json = buildUiInstructions(content);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /**
     * 从单行文本生成该行的渲染指令（用于增量/单行渲染）。
     * 输出：{"kind":"paragraph","text":"...","inline":[...]}
     */
    @CEntryPoint(name = "render_line")
    public static int renderLine(IsolateThread thread, CCharPointer linePtr, CCharPointer outBuf, int bufSize) {
        String line = CTypeConversion.toJavaString(linePtr);
        String json = elementForLine(line);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 获取文档总行数 */
    @CEntryPoint(name = "render_line_count")
    public static int lineCount(IsolateThread thread, CCharPointer textPtr) {
        String text = CTypeConversion.toJavaString(textPtr);
        if (text.isEmpty()) return 0;
        return text.split("\n", -1).length;
    }

    /** 获取指定行号的行内容，写入 outBuf */
    @CEntryPoint(name = "render_get_line")
    public static int getLine(IsolateThread thread, CCharPointer textPtr, int lineNum, CCharPointer outBuf, int bufSize) {
        String text = CTypeConversion.toJavaString(textPtr);
        String[] lines = text.split("\n", -1);
        String line = (lineNum >= 0 && lineNum < lines.length) ? lines[lineNum] : "";
        CTypeConversion.toCString(line, outBuf, WordFactory.unsigned(bufSize));
        return line.length();
    }

    // ================================================================
    //  通用文本操作（查询 / 替换 / 插入）—— 业务逻辑全在渲染端
    //  输出结果 JSON：{"count":N,"positions":[偏移...]} 或 {"count":N,"text":"..."}
    // ================================================================

    /** 查询（String 级，供控制层 dispatch 直调）：返回匹配位置 JSON */
    public static String findJsonInternal(String content, String query) {
        return findJson(content, query);
    }

    /** 替换（String 级）：返回替换后全文 JSON */
    public static String replaceJsonInternal(String content, String find, String replace) {
        return replaceJson(content, find, replace);
    }

    /** 插入（String 级）：返回插入后全文 JSON */
    public static String insertJsonInternal(String content, String left, String right, String insert) {
        return insertJson(content, left, right, insert);
    }

    /** 分页渲染（String 级）：content + 页宽/页高 → 分页 IDP JSON */
    public static String pagedJsonInternal(String content, int pageWidth, int pageHeight) {
        return pagedJson(content, pageWidth, pageHeight);
    }

    /** 查询：返回 content 中所有 query 的匹配位置（字符偏移列表）与命中数 */
    @CEntryPoint(name = "render_find")
    public static int findText(IsolateThread thread, CCharPointer contentPtr, CCharPointer queryPtr, CCharPointer outBuf, int bufSize) {
        String content = CTypeConversion.toJavaString(contentPtr);
        String query = CTypeConversion.toJavaString(queryPtr);
        String json = findJson(content, query);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 替换：把 content 中所有 find 替换为 replace，返回替换后全文与命中数 */
    @CEntryPoint(name = "render_replace")
    public static int replaceText(IsolateThread thread, CCharPointer contentPtr, CCharPointer findPtr, CCharPointer replacePtr, CCharPointer outBuf, int bufSize) {
        String content = CTypeConversion.toJavaString(contentPtr);
        String find = CTypeConversion.toJavaString(findPtr);
        String replace = CTypeConversion.toJavaString(replacePtr);
        String json = replaceJson(content, find, replace);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 插入：在 content 中每个 "left...right" 片段中间插入 insert，返回插入后全文与次数 */
    @CEntryPoint(name = "render_insert")
    public static int insertText(IsolateThread thread, CCharPointer contentPtr, CCharPointer leftPtr, CCharPointer rightPtr, CCharPointer insertPtr, CCharPointer outBuf, int bufSize) {
        String content = CTypeConversion.toJavaString(contentPtr);
        String left = CTypeConversion.toJavaString(leftPtr);
        String right = CTypeConversion.toJavaString(rightPtr);
        String insert = CTypeConversion.toJavaString(insertPtr);
        String json = insertJson(content, left, right, insert);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 查询 JSON：{"count":N,"positions":[字符偏移...]} */
    private static String findJson(String content, String query) {        if (query.isEmpty()) return "{\"count\":0,\"positions\":[]}";
        List<Integer> pos = new ArrayList<>();
        int idx = 0;
        while (true) {
            int p = content.indexOf(query, idx);
            if (p < 0) break;
            pos.add(p);
            idx = p + query.length();
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"count\":").append(pos.size()).append(",\"positions\":[");
        for (int k = 0; k < pos.size(); k++) {
            if (k > 0) json.append(",");
            json.append(pos.get(k));
        }
        json.append("]}");
        return json.toString();
    }

    /** 替换 JSON：{"count":N,"text":"替换后全文"} */
    private static String replaceJson(String content, String find, String replace) {
        if (find.isEmpty()) {
            return "{\"count\":0,\"text\":\"" + esc(content) + "\"}";
        }
        String newContent = content.replace(find, replace);
        int count = 0, idx = 0;
        while (true) {
            int p = content.indexOf(find, idx);
            if (p < 0) break;
            count++;
            idx = p + find.length();
        }
        return "{\"count\":" + count + ",\"text\":\"" + esc(newContent) + "\"}";
    }

    /** 插入 JSON：{"count":N,"text":"插入后全文"} */
    private static String insertJson(String content, String left, String right, String insert) {
        if (left.isEmpty() || right.isEmpty()) {
            return "{\"count\":0,\"text\":\"" + esc(content) + "\"}";
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        String rest = content;
        while (true) {
            int li = rest.indexOf(left);
            if (li < 0) { out.append(rest); break; }
            int afterLeft = li + left.length();
            int ri = rest.indexOf(right, afterLeft);
            if (ri < 0) { out.append(rest); break; }
            out.append(rest, 0, afterLeft).append(insert).append(rest, afterLeft, ri + right.length());
            count++;
            rest = rest.substring(ri + right.length());
        }
        return "{\"count\":" + count + ",\"text\":\"" + esc(out.toString()) + "\"}";
    }

    // ================================================================
    //  分页渲染 —— 按自定义页宽/页高把渲染元素分页，超宽换行、超长换页，每页标页码
    //  输出分页 IDP：{"view":"paged","pageWidth":W,"pageHeight":H,"pages":[{page,elements:[...]},...]}
    // ================================================================

    /** 分页渲染入口：content + 页宽(px) + 页高(px) → 分页 IDP */
    @CEntryPoint(name = "render_paged")
    public static int pagedRender(IsolateThread thread, CCharPointer contentPtr, int pageWidth, int pageHeight, CCharPointer outBuf, int bufSize) {
        String content = CTypeConversion.toJavaString(contentPtr);
        String json = pagedJson(content, pageWidth, pageHeight);
        CTypeConversion.toCString(json, outBuf, WordFactory.unsigned(bufSize));
        return json.length();
    }

    /** 估算单个逻辑块占用的物理行数与渲染元素 JSON */
    private static class ElementEstimate {
        String json;
        int lines;
        int nextIndex;
        ElementEstimate(String j, int l, int n) { json = j; lines = l; nextIndex = n; }
    }

    private static ElementEstimate estimateElement(String[] lines, int i, String trimmed, int charsPerLine) {
        // 代码块（``` 围栏聚合）
        if (trimmed.startsWith("```")) {
            List<String> buf = new ArrayList<>();
            int j = i + 1;
            while (j < lines.length && !lines[j].trim().startsWith("```")) { buf.add(lines[j]); j++; }
            j++; // 跳过结束围栏
            int bodyLines = buf.size();
            String json = "{\"kind\":\"code\",\"text\":\"" + esc(String.join("\n", buf)) + "\"}";
            return new ElementEstimate(json, bodyLines + 2, j);
        }
        // 控件标记
        String control = controlElement(trimmed);
        if (control != null) return new ElementEstimate(control, 1, i + 1);
        // 列表（聚合连续列表项）
        if (isListStart(trimmed)) {
            boolean ordered = Pattern.compile("^\\d+\\.\\s+").matcher(trimmed).find();
            List<String> items = new ArrayList<>();
            int j = i;
            while (j < lines.length) {
                String t = lines[j].trim();
                if (isListStart(t)) { items.add(stripListMarker(t)); j++; } else break;
            }
            StringBuilder json = new StringBuilder("{\"kind\":\"list\",\"ordered\":").append(ordered).append(",\"items\":[");
            for (int k = 0; k < items.size(); k++) {
                if (k > 0) json.append(",");
                json.append("\"").append(esc(items.get(k))).append("\"");
            }
            json.append("]}");
            return new ElementEstimate(json.toString(), Math.max(items.size(), 1), j);
        }
        // 标题
        Matcher hm = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(trimmed);
        if (hm.matches()) {
            int lvl = hm.group(1).length();
            String json = "{\"kind\":\"heading\",\"level\":" + lvl + ",\"text\":\"" + esc(hm.group(2)) + "\"}";
            return new ElementEstimate(json, (lvl == 1 ? 2 : 1) + 1, i + 1);
        }
        // 引用
        if (trimmed.startsWith(">")) {
            String t = trimmed.substring(1).trim();
            String json = "{\"kind\":\"quote\",\"text\":\"" + esc(t) + "\"}";
            int el = Math.max(1, (int) Math.ceil(t.length() / (double) charsPerLine));
            return new ElementEstimate(json, el + 1, i + 1);
        }
        // 分隔线
        if (trimmed.equals("---") || trimmed.equals("***")) {
            return new ElementEstimate("{\"kind\":\"separator\"}", 1, i + 1);
        }
        // 表情/图片 [文字]
        Matcher em = Pattern.compile("^\\[(.+?)\\]$").matcher(trimmed);
        if (em.matches()) {
            return new ElementEstimate("{\"kind\":\"image\",\"text\":\"" + esc(em.group(1)) + "\"}", 2, i + 1);
        }
        // 对话框 :"文字"序号
        Matcher dm = Pattern.compile("^:\"(.+?)\"(\\d+)$").matcher(trimmed);
        if (dm.matches()) {
            return new ElementEstimate("{\"kind\":\"dialog\",\"text\":\"" + esc(dm.group(1)) + "\",\"styleIndex\":" + dm.group(2) + "}", 1, i + 1);
        }
        // 超链接 ≈路径≈序号
        Matcher lm = Pattern.compile("^≈(.+?)≈(\\d+)$").matcher(trimmed);
        if (lm.matches()) {
            return new ElementEstimate("{\"kind\":\"link\",\"path\":\"" + esc(lm.group(1)) + "\",\"styleIndex\":" + lm.group(2) + "}", 1, i + 1);
        }
        // 空行：跳过
        if (trimmed.isEmpty()) return new ElementEstimate(null, 0, i + 1);
        // 普通段落
        String json = "{\"kind\":\"paragraph\",\"text\":\"" + esc(lines[i]) + "\"}";
        int el = Math.max(1, (int) Math.ceil(lines[i].length() / (double) charsPerLine));
        return new ElementEstimate(json, el, i + 1);
    }

    /** 分页 JSON：按估算物理行数把元素分到多页，每页附页码 */
    private static String pagedJson(String content, int pageWidth, int pageHeight) {
        double lineHeight = 22.0;
        double pageBody = Math.max(pageHeight - 90, 100); // 上下留白（页眉/页脚）
        int linesPerPage = Math.max((int) (pageBody / lineHeight), 1);
        int charsPerLine = Math.max((int) (pageWidth / 16.0), 8); // 16px/字符

        List<String> pages = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int curLines = 0;
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; ) {
            String trimmed = lines[i].trim();
            ElementEstimate est = estimateElement(lines, i, trimmed, charsPerLine);
            i = est.nextIndex;
            if (est.json == null || est.lines <= 0) continue;
            if (curLines + est.lines > linesPerPage && curLines > 0) {
                pages.add(cur.toString());
                cur = new StringBuilder();
                curLines = 0;
            }
            cur.append(sep(cur.length() == 0)).append(est.json);
            curLines += est.lines;
        }
        if (cur.length() > 0) pages.add(cur.toString());
        if (pages.isEmpty()) pages.add("");

        StringBuilder json = new StringBuilder();
        json.append("{\"view\":\"paged\",\"pageWidth\":").append(pageWidth)
            .append(",\"pageHeight\":").append(pageHeight).append(",\"pages\":[");
        for (int p = 0; p < pages.size(); p++) {
            if (p > 0) json.append(",");
            json.append("{\"page\":").append(p + 1).append(",\"elements\":[")
                .append(pages.get(p)).append("]}");
        }
        json.append("]}");
        return json.toString();
    }

    // ================================================================
    //  内部实现（供同 DLL 内其他 Java 类直接调用）
    // ================================================================

    /** 构建全文通用渲染指令 JSON（内容块 + 交互控件） */
    public static String buildUiInstructions(String content) {
        StringBuilder json = new StringBuilder();
        json.append("{\"view\":\"ui\",\"layout\":\"flow\",\"elements\":[");
        String[] lines = content.split("\n", -1);
        boolean first = true;
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            // ---- 代码块（``` 围栏）----
            if (trimmed.startsWith("```")) {
                List<String> buf = new ArrayList<>();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    buf.add(lines[i]); i++;
                }
                i++; // 跳过结束围栏
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"code\",\"text\":\"").append(esc(String.join("\n", buf))).append("\"}");
                continue;
            }

            // ---- 控件标记 {button}/{input}/{menu}/{image}/{table} ----
            String control = controlElement(trimmed);
            if (control != null) {
                json.append(sep(first)); first = false;
                json.append(control);
                i++;
                continue;
            }

            // ---- 列表（收集连续列表项）----
            if (isListStart(trimmed)) {
                boolean ordered = Pattern.compile("^\\d+\\.\\s+").matcher(trimmed).find();
                List<String> items = new ArrayList<>();
                while (i < lines.length) {
                    String t = lines[i].trim();
                    if (isListStart(t)) { items.add(stripListMarker(t)); i++; } else break;
                }
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"list\",\"ordered\":").append(ordered)
                    .append(",\"items\":[");
                for (int k = 0; k < items.size(); k++) {
                    if (k > 0) json.append(",");
                    json.append("\"").append(esc(items.get(k))).append("\"");
                }
                json.append("]}");
                continue;
            }

            // ---- 标题 ----
            Matcher hm = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(trimmed);
            if (hm.matches()) {
                int lvl = hm.group(1).length();
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"heading\",\"level\":").append(lvl)
                    .append(",\"text\":\"").append(esc(hm.group(2))).append("\"}");
                i++; continue;
            }

            // ---- 引用 ----
            if (trimmed.startsWith(">")) {
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"quote\",\"text\":\"")
                    .append(esc(trimmed.substring(1).trim())).append("\"}");
                i++; continue;
            }

            // ---- 分隔线 ----
            if (trimmed.equals("---") || trimmed.equals("***")) {
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"separator\"}");
                i++; continue;
            }

            // ---- 表情/图片 [文字] ----
            Matcher em = Pattern.compile("^\\[(.+?)\\]$").matcher(trimmed);
            if (em.matches()) {
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"image\",\"text\":\"").append(esc(em.group(1))).append("\"}");
                i++; continue;
            }

            // ---- 对话框 :"文字"序号 ----
            Matcher dm = Pattern.compile("^:\"(.+?)\"(\\d+)$").matcher(trimmed);
            if (dm.matches()) {
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"dialog\",\"text\":\"").append(esc(dm.group(1)))
                    .append("\",\"styleIndex\":").append(dm.group(2)).append("}");
                i++; continue;
            }

            // ---- 超链接 ≈路径≈序号 ----
            Matcher lm = Pattern.compile("^≈(.+?)≈(\\d+)$").matcher(trimmed);
            if (lm.matches()) {
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"link\",\"path\":\"").append(esc(lm.group(1)))
                    .append("\",\"styleIndex\":").append(lm.group(2)).append("}");
                i++; continue;
            }

            // ---- 彩蛋命令 \\>命令 ----
            if (trimmed.startsWith("\\\\>")) {
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"paragraph\",\"text\":\"").append(esc(trimmed)).append("\",\"easter\":true}");
                i++; continue;
            }

            // ---- 空行 ----
            if (line.isEmpty()) {
                json.append(sep(first)); first = false;
                json.append("{\"kind\":\"paragraph\",\"text\":\"\",\"inline\":[]}");
                i++; continue;
            }

            // ---- 普通段落（含 inline 粗体/斜体/代码）----
            json.append(sep(first)); first = false;
            json.append("{\"kind\":\"paragraph\",\"text\":\"").append(esc(line)).append("\",\"inline\":[");
            String type = MDMarkdown.classifyLine(line);
            if (!type.equals("code") && !type.equals("empty")) {
                List<MDMarkdown.InlineStyle> styles = MDMarkdown.analyzeInlineStyles(line);
                boolean fs = true;
                for (MDMarkdown.InlineStyle st : styles) {
                    if (!fs) json.append(",");
                    fs = false;
                    json.append("{\"s\":").append(st.start).append(",\"e\":").append(st.end)
                        .append(",\"st\":\"").append(st.style).append("\"}");
                }
            }
            json.append("]}");
            i++;
        }
        json.append("]}");
        return json.toString();
    }

    /** 单行 → 元素 JSON（供 render_line 增量渲染） */
    private static String elementForLine(String line) {
        String trimmed = line.trim();
        String ctrl = controlElement(trimmed);
        if (ctrl != null) return ctrl;
        Matcher hm = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(trimmed);
        if (hm.matches()) return "{\"kind\":\"heading\",\"level\":" + hm.group(1).length() + ",\"text\":\"" + esc(hm.group(2)) + "\"}";
        if (trimmed.startsWith(">")) return "{\"kind\":\"quote\",\"text\":\"" + esc(trimmed.substring(1).trim()) + "\"}";
        if (isListStart(trimmed)) return "{\"kind\":\"list\",\"ordered\":" + (Pattern.compile("^\\d+\\.\\s+").matcher(trimmed).find()) + ",\"items\":[\"" + esc(stripListMarker(trimmed)) + "\"]}";
        if (trimmed.equals("---")) return "{\"kind\":\"separator\"}";
        Matcher em = Pattern.compile("^\\[(.+?)\\]$").matcher(trimmed);
        if (em.matches()) return "{\"kind\":\"image\",\"text\":\"" + esc(em.group(1)) + "\"}";
        Matcher dm = Pattern.compile("^:\"(.+?)\"(\\d+)$").matcher(trimmed);
        if (dm.matches()) return "{\"kind\":\"dialog\",\"text\":\"" + esc(dm.group(1)) + "\",\"styleIndex\":" + dm.group(2) + "}";
        Matcher lm = Pattern.compile("^≈(.+?)≈(\\d+)$").matcher(trimmed);
        if (lm.matches()) return "{\"kind\":\"link\",\"path\":\"" + esc(lm.group(1)) + "\",\"styleIndex\":" + lm.group(2) + "}";
        if (trimmed.startsWith("\\\\>")) return "{\"kind\":\"paragraph\",\"text\":\"" + esc(trimmed) + "\",\"easter\":true}";
        // 普通段落 + inline
        StringBuilder sb = new StringBuilder("{\"kind\":\"paragraph\",\"text\":\"").append(esc(line)).append("\",\"inline\":[");
        String type = MDMarkdown.classifyLine(line);
        if (!type.equals("code") && !type.equals("empty")) {
            List<MDMarkdown.InlineStyle> styles = MDMarkdown.analyzeInlineStyles(line);
            boolean fs = true;
            for (MDMarkdown.InlineStyle st : styles) {
                if (!fs) sb.append(",");
                fs = false;
                sb.append("{\"s\":").append(st.start).append(",\"e\":").append(st.end).append(",\"st\":\"").append(st.style).append("\"}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---- 控件标记解析 ----

    /** 解析 {button}/{input}/{menu}/{image}/{table}；非控件返回 null */
    private static String controlElement(String trimmed) {
        if (!trimmed.startsWith("{")) return null;
        if (!trimmed.endsWith("}")) return null;
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        int colon = inner.indexOf(':');
        if (colon <= 0) return null;
        String tag = inner.substring(0, colon).trim();
        String payload = inner.substring(colon + 1).trim();

        switch (tag) {
            case "button": {
                int bar = payload.indexOf('|');
                String text = bar < 0 ? payload : payload.substring(0, bar);
                String action = bar < 0 ? "" : payload.substring(bar + 1);
                return "{\"kind\":\"button\",\"text\":\"" + esc(text) + "\",\"action\":\"" + esc(action) + "\"}";
            }
            case "input":
                return "{\"kind\":\"input\",\"placeholder\":\"" + esc(payload) + "\"}";
            case "menu": {
                // 文件>新建,打开;编辑>复制,粘贴
                StringBuilder sb = new StringBuilder("{\"kind\":\"menu\",\"items\":[");
                String[] menus = payload.split(";");
                for (int m = 0; m < menus.length; m++) {
                    if (m > 0) sb.append(",");
                    String[] parts = menus[m].split(">");
                    sb.append("{\"label\":\"").append(esc(parts[0].trim())).append("\",\"sub\":[");
                    if (parts.length > 1) {
                        String[] subs = parts[1].split(",");
                        for (int s = 0; s < subs.length; s++) {
                            if (s > 0) sb.append(",");
                            sb.append("\"").append(esc(subs[s].trim())).append("\"");
                        }
                    }
                    sb.append("]}");
                }
                sb.append("]}");
                return sb.toString();
            }
            case "image": {
                // 名|路径|宽|高
                String[] p = payload.split("\\|");
                String name = p.length > 0 ? p[0].trim() : "";
                String src = p.length > 1 ? p[1].trim() : "";
                String w = p.length > 2 ? p[2].trim() : "0";
                String h = p.length > 3 ? p[3].trim() : "0";
                return "{\"kind\":\"image\",\"text\":\"" + esc(name) + "\",\"src\":\"" + esc(src) +
                       "\",\"width\":" + num(w) + ",\"height\":" + num(h) + "}";
            }
            case "table": {
                // 列1,列2|r1c1,r1c2;r2c1,r2c2
                int bar = payload.indexOf('|');
                String headStr = bar < 0 ? payload : payload.substring(0, bar);
                String rowsStr = bar < 0 ? "" : payload.substring(bar + 1);
                StringBuilder sb = new StringBuilder("{\"kind\":\"table\",\"headers\":[");
                String[] heads = headStr.split(",");
                for (int x = 0; x < heads.length; x++) {
                    if (x > 0) sb.append(",");
                    sb.append("\"").append(esc(heads[x].trim())).append("\"");
                }
                sb.append("],\"rows\":[");
                if (!rowsStr.isEmpty()) {
                    String[] rows = rowsStr.split(";");
                    for (int r = 0; r < rows.length; r++) {
                        if (r > 0) sb.append(",");
                        sb.append("[");
                        String[] cells = rows[r].split(",");
                        for (int c = 0; c < cells.length; c++) {
                            if (c > 0) sb.append(",");
                            sb.append("\"").append(esc(cells[c].trim())).append("\"");
                        }
                        sb.append("]");
                    }
                }
                sb.append("]}");
                return sb.toString();
            }
            default:
                return null;
        }
    }

    // ---- 工具 ----

    private static boolean isListStart(String t) {
        return t.startsWith("- ") || t.startsWith("* ") || Pattern.compile("^\\d+\\.\\s+").matcher(t).find();
    }

    private static String stripListMarker(String t) {
        if (t.startsWith("- ")) return t.substring(2).trim();
        if (t.startsWith("* ")) return t.substring(2).trim();
        return t.replaceFirst("^\\d+\\.\\s+", "").trim();
    }

    private static String sep(boolean first) { return first ? "" : ","; }

    private static String num(String s) {
        try { return String.valueOf(Integer.parseInt(s)); } catch (Exception e) { return "0"; }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ================================================================
    //  IDP 输出（调用 mdidp 模块，所有模块通用通信格式）
    // ================================================================

    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "render_write_idp")
    public static int writeIdp(IsolateThread thread, CCharPointer filePathPtr, CCharPointer typePtr, CCharPointer jsonDataPtr) {
        try {
            String type = CTypeConversion.toJavaString(typePtr);
            String jsonData = CTypeConversion.toJavaString(jsonDataPtr);
            MDIdp.enqueue(type, "render", jsonData);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    @CEntryPoint(name = "render_to_idp")
    public static int toIdp(IsolateThread thread, CCharPointer typePtr, CCharPointer jsonDataPtr, CCharPointer outBuf, int bufSize) {
        String type = CTypeConversion.toJavaString(typePtr);
        String jsonData = CTypeConversion.toJavaString(jsonDataPtr);
        String idp = MDIdp.createPacket(type, "render", jsonData);
        CTypeConversion.toCString(idp, outBuf, WordFactory.unsigned(bufSize));
        return idp.length();
    }

    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    @CEntryPoint(name = "render_read_idp")
    public static int readIdp(IsolateThread thread, CCharPointer filePathPtr, CCharPointer outBuf, int bufSize) {
        String jsonData = MDIdp.dequeueJson();
        CTypeConversion.toCString(jsonData, outBuf, WordFactory.unsigned(bufSize));
        return jsonData.length();
    }
}
