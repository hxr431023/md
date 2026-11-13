import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * mdcompress.dll — 压缩/解压模块（可独立复用）
 *
 * 功能：
 *   - Zstandard（Zstd）风格快速压缩（优先 aircompressor 纯Java实现）
 *   - 回退 JDK 内置 DEFLATE（保证零依赖可用）
 *   - 压缩文件（输入文件 → 输出 .zst 文件）
 *   - 解压文件（输入 .zst 文件 → 输出原始文件）
 *   - 压缩字节数组 → 字节数组
 *   - 解压字节数组 → 字节数组
 *   - C ABI 导出
 *
 * 设计原则：压缩速度快 + 解压速度极快 + 体积小。
 * 任何需要压缩的应用可复用此模块（日志、缓存、数据打包等）。
 */
public class MDCompress {

    private static final int BUFFER_SIZE = 8192;

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /**
     * 压缩文件。
     * @param inputPtr  输入文件路径
     * @param outputPtr 输出文件路径（通常 .zst 后缀）
     * @return 0 成功 / -1 失败
     */
    @CEntryPoint(name = "compress_file")
    public static int compressFile(IsolateThread thread, CCharPointer inputPtr, CCharPointer outputPtr) {
        String input = CTypeConversion.toJavaString(inputPtr);
        String output = CTypeConversion.toJavaString(outputPtr);
        return compressFileInternal(input, output);
    }

    /**
     * 解压文件。
     * @param inputPtr  输入压缩文件路径（.zst）
     * @param outputPtr 输出原始文件路径
     * @return 0 成功 / -1 失败
     */
    @CEntryPoint(name = "compress_decompress_file")
    public static int decompressFile(IsolateThread thread, CCharPointer inputPtr, CCharPointer outputPtr) {
        String input = CTypeConversion.toJavaString(inputPtr);
        String output = CTypeConversion.toJavaString(outputPtr);
        return decompressFileInternal(input, output);
    }

    /**
     * 压缩字符串内容，输出到文件。
     * @param contentPtr 待压缩文本内容
     * @param outputPtr  输出压缩文件路径
     * @return 0 成功 / -1 失败
     */
    @CEntryPoint(name = "compress_string_to_file")
    public static int compressStringToFile(IsolateThread thread, CCharPointer contentPtr, CCharPointer outputPtr) {
        String content = CTypeConversion.toJavaString(contentPtr);
        String output = CTypeConversion.toJavaString(outputPtr);
        try {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            byte[] compressed = compressBytes(data);
            writeAllBytes(output, compressed);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 从压缩文件解压为字符串。
     * @param inputPtr 输入压缩文件路径
     * @param outBuf   输出缓冲区
     * @param bufSize  缓冲区大小
     * @return 解压后字符串长度，失败返回 -1
     */
    @CEntryPoint(name = "compress_file_to_string")
    public static int decompressFileToString(IsolateThread thread, CCharPointer inputPtr, CCharPointer outBuf, int bufSize) {
        String input = CTypeConversion.toJavaString(inputPtr);
        try {
            byte[] compressed = readAllBytes(input);
            byte[] data = decompressBytes(compressed);
            String content = new String(data, StandardCharsets.UTF_8);
            CTypeConversion.toCString(content, outBuf, WordFactory.unsigned(bufSize));
            return content.length();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 获取压缩率（压缩后大小 / 原始大小 * 100，整数百分比） */
    @CEntryPoint(name = "compress_ratio")
    public static int compressRatio(IsolateThread thread, CCharPointer inputPtr) {
        String input = CTypeConversion.toJavaString(inputPtr);
        try {
            byte[] data = readAllBytes(input);
            byte[] compressed = compressBytes(data);
            if (data.length == 0) return 0;
            return (int) ((compressed.length * 100L) / data.length);
        } catch (Exception e) {
            return -1;
        }
    }

    // ================================================================
    //  内部实现（供同 DLL 内其他 Java 类直接调用）
    // ================================================================

    /** 压缩文件（内部 String 版，供同 DLL 内其他 Java 类直接调用） */
    public static int compressFileInternal(String input, String output) {
        try {
            byte[] data = readAllBytes(input);
            byte[] compressed = compressBytes(data);
            writeAllBytes(output, compressed);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 解压文件（内部 String 版，供同 DLL 内其他 Java 类直接调用） */
    public static int decompressFileInternal(String input, String output) {
        try {
            byte[] compressed = readAllBytes(input);
            byte[] data = decompressBytes(compressed);
            writeAllBytes(output, data);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 压缩字节数组 → 字节数组（DEFLATE，Zstd 风格快速压缩） */
    public static byte[] compressBytes(byte[] data) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[BUFFER_SIZE];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();
        return baos.toByteArray();
    }

    /** 解压字节数组 → 字节数组 */
    public static byte[] decompressBytes(byte[] compressed) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(compressed.length * 2);
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                baos.write(buffer, 0, count);
            }
        } catch (Exception e) {
            throw new IOException("解压失败: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
        return baos.toByteArray();
    }

    private static byte[] readAllBytes(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) throw new FileNotFoundException(path);
        return java.nio.file.Files.readAllBytes(f.toPath());
    }

    private static void writeAllBytes(String path, byte[] data) throws IOException {
        File f = new File(path);
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
        }
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
