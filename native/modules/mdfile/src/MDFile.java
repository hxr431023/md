import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * mdfile.dll — 文件 I/O 模块（可独立复用）
 *
 * 功能：
 *   - 读取文件内容（UTF-8）
 *   - 写入文件内容（UTF-8，覆盖/追加）
 *   - 文件存在检测
 *   - 文件大小获取
 *   - 文件删除
 *   - C ABI 导出
 *
 * 纯文件操作，无任何业务逻辑，任何应用可复用。
 */
public class MDFile {

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /** 读取文件内容到 outBuf，返回字符数（失败返回 -1） */
    @CEntryPoint(name = "file_read")
    public static int read(IsolateThread thread, CCharPointer pathPtr, CCharPointer outBuf, int bufSize) {
        try {
            String path = CTypeConversion.toJavaString(pathPtr);
            File f = new File(path);
            if (!f.exists()) return -1;
            byte[] bytes = Files.readAllBytes(f.toPath());
            String content = new String(bytes, StandardCharsets.UTF_8);
            CTypeConversion.toCString(content, outBuf, WordFactory.unsigned(bufSize));
            return content.length();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 写入文件（覆盖），返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "file_write")
    public static int write(IsolateThread thread, CCharPointer pathPtr, CCharPointer contentPtr) {
        try {
            String path = CTypeConversion.toJavaString(pathPtr);
            String content = CTypeConversion.toJavaString(contentPtr);
            File f = new File(path);
            f.getParentFile().mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(content);
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 追加写入文件，返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "file_append")
    public static int append(IsolateThread thread, CCharPointer pathPtr, CCharPointer contentPtr) {
        try {
            String path = CTypeConversion.toJavaString(pathPtr);
            String content = CTypeConversion.toJavaString(contentPtr);
            File f = new File(path);
            f.getParentFile().mkdirs();
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write(content);
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 文件是否存在，返回 1/0 */
    @CEntryPoint(name = "file_exists")
    public static int exists(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        return new File(path).exists() ? 1 : 0;
    }

    /** 获取文件大小（字节），不存在返回 -1 */
    @CEntryPoint(name = "file_size")
    public static long size(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        File f = new File(path);
        return f.exists() ? f.length() : -1;
    }

    /** 删除文件，返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "file_delete")
    public static int delete(IsolateThread thread, CCharPointer pathPtr) {
        String path = CTypeConversion.toJavaString(pathPtr);
        File f = new File(path);
        return f.delete() ? 0 : -1;
    }

    /** 获取文件名（不含路径），写入 outBuf */
    @CEntryPoint(name = "file_name")
    public static int fileName(IsolateThread thread, CCharPointer pathPtr, CCharPointer outBuf, int bufSize) {
        String path = CTypeConversion.toJavaString(pathPtr);
        File f = new File(path);
        String name = f.getName();
        CTypeConversion.toCString(name, outBuf, WordFactory.unsigned(bufSize));
        return name.length();
    }

    /** 获取文件扩展名（不含点），写入 outBuf */
    @CEntryPoint(name = "file_extension")
    public static int extension(IsolateThread thread, CCharPointer pathPtr, CCharPointer outBuf, int bufSize) {
        String path = CTypeConversion.toJavaString(pathPtr);
        File f = new File(path);
        String name = f.getName();
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) ext = name.substring(dot + 1);
        CTypeConversion.toCString(ext, outBuf, WordFactory.unsigned(bufSize));
        return ext.length();
    }

    // ================================================================
    //  内部实现（供同 DLL 内其他 Java 类直接调用）
    // ================================================================

    public static String readFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(new File(path).toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeFile(String path, String content) throws IOException {
        File f = new File(path);
        f.getParentFile().mkdirs();
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(content); }
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
