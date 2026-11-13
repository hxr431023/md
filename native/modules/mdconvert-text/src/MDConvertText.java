import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * mdconvert-text.dll — Markdown → 纯文本转换（可独立复用）
 *
 * 功能：去除所有 Markdown 标记（标题#、引用>、列表-/*、粗体**、斜体*、代码`、删除线~~、链接[]()），输出纯文本。
 * 依赖：无（纯自包含）
 */
public class MDConvertText {

    @CEntryPoint(name = "convert_text")
    public static int toText(IsolateThread thread, CCharPointer mdPtr, CCharPointer outBuf, int bufSize) {
        String md = CTypeConversion.toJavaString(mdPtr);
        String text = convertToPlainText(md);
        CTypeConversion.toCString(text, outBuf, WordFactory.unsigned(bufSize));
        return text.length();
    }

    @CEntryPoint(name = "convert_text_file")
    public static int convertFile(IsolateThread thread, CCharPointer inputPtr, CCharPointer outputPtr) {
        try {
            String md = readFile(CTypeConversion.toJavaString(inputPtr));
            writeFile(CTypeConversion.toJavaString(outputPtr), convertToPlainText(md));
            return 0;
        } catch (Exception e) { return -1; }
    }

    private static String convertToPlainText(String md) {
        StringBuilder text = new StringBuilder();
        boolean inCodeBlock = false;
        for (String line : md.split("\n", -1)) {
            if (line.trim().startsWith("```")) { inCodeBlock = !inCodeBlock; continue; }
            if (inCodeBlock) { text.append(line).append("\n"); continue; }
            String cleaned = line.replaceAll("^#{1,6}\\s*", "");
            cleaned = cleaned.replaceAll("^>\\s*", "");
            cleaned = cleaned.replaceAll("^[-*]\\s+", "");
            cleaned = cleaned.replaceAll("^\\d+\\.\\s+", "");
            cleaned = cleaned.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
            cleaned = cleaned.replaceAll("\\*(.+?)\\*", "$1");
            cleaned = cleaned.replaceAll("`(.+?)`", "$1");
            cleaned = cleaned.replaceAll("~~(.+?)~~", "$1");
            cleaned = cleaned.replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1");
            text.append(cleaned).append("\n");
        }
        return text.toString();
    }

    private static String readFile(String path) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }
    private static void writeFile(String path, String content) throws IOException {
        File f = new File(path); f.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(content); }
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
