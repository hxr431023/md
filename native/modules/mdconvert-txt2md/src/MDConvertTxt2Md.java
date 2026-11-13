import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * mdconvert-txt2md.dll — 纯文本 → Markdown 转换（可独立复用）
 *
 * 功能：将纯文本智能转换为 Markdown 格式。
 *   - 检测全大写短行 → 标题（#）
 *   - 检测以数字+点开头的行 → 有序列表
 *   - 检测以 -/* 开头的行 → 无序列表（保留）
 *   - 检测以 > 开头的行 → 引用（保留）
 *   - 空行保留为段落分隔
 *   - 普通行保留为段落文本
 * 这是唯一的"文件转 MD"模块，其他转换都是 MD 转各种格式。
 * 依赖：无（纯自包含）
 */
public class MDConvertTxt2Md {

    @CEntryPoint(name = "convert_txt2md")
    public static int toMarkdown(IsolateThread thread, CCharPointer textPtr, CCharPointer outBuf, int bufSize) {
        String text = CTypeConversion.toJavaString(textPtr);
        String md = convertToMarkdown(text);
        CTypeConversion.toCString(md, outBuf, WordFactory.unsigned(bufSize));
        return md.length();
    }

    @CEntryPoint(name = "convert_txt2md_file")
    public static int convertFile(IsolateThread thread, CCharPointer inputPtr, CCharPointer outputPtr) {
        try {
            String text = readFile(CTypeConversion.toJavaString(inputPtr));
            writeFile(CTypeConversion.toJavaString(outputPtr), convertToMarkdown(text));
            return 0;
        } catch (Exception e) { return -1; }
    }

    private static String convertToMarkdown(String text) {
        StringBuilder md = new StringBuilder();
        String[] lines = text.split("\n", -1);
        int orderedCounter = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // 空行
            if (trimmed.isEmpty()) {
                md.append("\n");
                orderedCounter = 0;
                continue;
            }

            // 已经是 Markdown 标题（以 # 开头），保留
            if (trimmed.startsWith("#")) {
                md.append(trimmed).append("\n");
                continue;
            }

            // 已经是引用，保留
            if (trimmed.startsWith(">")) {
                md.append(trimmed).append("\n");
                continue;
            }

            // 已经是无序列表，保留
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                md.append(trimmed).append("\n");
                continue;
            }

            // 检测有序列表（数字+点+空格）
            if (trimmed.matches("^\\d+\\.\\s.*")) {
                md.append(trimmed).append("\n");
                continue;
            }

            // 检测全大写短行（长度 < 50，全部大写字母）→ H2 标题
            if (trimmed.length() < 50 && trimmed.matches("^[A-Z0-9\\s\\-:]+$") && trimmed.length() > 2) {
                md.append("## ").append(trimmed).append("\n");
                continue;
            }

            // 检测以 "第X章" / "第X节" 开头 → 标题
            if (trimmed.matches("^第[一二三四五六七八九十百千0-9]+[章节部分].*")) {
                md.append("# ").append(trimmed).append("\n");
                continue;
            }

            // 普通段落
            md.append(trimmed).append("\n");
        }

        return md.toString().trim() + "\n";
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
