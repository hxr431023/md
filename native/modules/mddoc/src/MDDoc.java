import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MDRenderer 控制层原生 DLL
 * 负责文档状态管理、撤销/重做栈、操作编排、光标位置、文本统计。
 * 无 Swing/AWT 依赖，纯逻辑。
 * 通过 @CEntryPoint 导出 C ABI 函数，供 Python 前端 ctypes 调用。
 *
 * 编译时需将 MDBackend.java 一同加入 classpath（控制层调用后端层的文件 I/O）：
 *   native-image --shared -cp . MDControl MDBackend -H:Name=mdcontrol
 *   产物：mdcontrol.dll
 */
public class MDDoc {

    // ===== 文档状态 =====
    private static String content = "";
    private static String filePath = null;
    private static boolean dirty = false;
    private static int cursorPos = 0;

    // ===== 撤销/重做栈（纯文本快照）=====
    private static final int MAX_UNDO = 500;
    private static List<String> undoStack = new ArrayList<>();
    private static List<String> redoStack = new ArrayList<>();
    private static boolean duringUndoRedo = false;

    // ===== 后端层引用（同 DLL 内直接调用 Java 方法）=====
    private static String baseDir = ".";

    // ================================================================
    //  C ABI 导出函数
    // ================================================================

    /** 初始化控制层，baseDir 为程序根目录 */
    @CEntryPoint(name = "doc_init")
    public static int init(IsolateThread thread, CCharPointer baseDirPtr) {
        return initInternal(CTypeConversion.toJavaString(baseDirPtr));
    }

    /** 内部 String 版初始化（供同 DLL 的 MDController 直接调用） */
    public static int initInternal(String dir) {
        baseDir = dir;
        content = "";
        filePath = null;
        dirty = false;
        cursorPos = 0;
        undoStack.clear();
        redoStack.clear();
        pushUndo(); // 初始状态入栈
        return 0;
    }

    /** 新建文档（清空内容，重置状态） */
    @CEntryPoint(name = "doc_new_document")
    public static int newDocument(IsolateThread thread) {
        content = "";
        filePath = null;
        dirty = false;
        cursorPos = 0;
        undoStack.clear();
        redoStack.clear();
        pushUndo();
        return 0;
    }

    /** 打开文档：读取文件内容到文档状态，返回字符数（失败 -1） */
    @CEntryPoint(name = "doc_open_document")
    public static int openDocument(IsolateThread thread, CCharPointer pathPtr) {
        return openDocumentInternal(CTypeConversion.toJavaString(pathPtr));
    }

    /** 内部 String 版打开文档（供同 DLL 的 MDController 直接调用） */
    public static int openDocumentInternal(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return -1;
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            content = new String(bytes, StandardCharsets.UTF_8);
            filePath = path;
            dirty = false;
            cursorPos = content.length();
            undoStack.clear();
            redoStack.clear();
            pushUndo();
            return content.length();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 保存当前文档到 filePath，返回 0 成功，-1 失败（无路径时返回 -2） */
    @CEntryPoint(name = "doc_save_document")
    public static int saveDocument(IsolateThread thread) {
        if (filePath == null) return -2;
        try {
            try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(filePath), StandardCharsets.UTF_8)) {
                w.write(content);
            }
            dirty = false;
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 另存为：保存到指定路径并更新 filePath，返回 0 成功 */
    @CEntryPoint(name = "doc_save_document_as")
    public static int saveDocumentAs(IsolateThread thread, CCharPointer pathPtr) {
        return saveDocumentAsInternal(CTypeConversion.toJavaString(pathPtr));
    }

    /** 内部 String 版另存为（供同 DLL 的 MDController 直接调用） */
    public static int saveDocumentAsInternal(String path) {
        try {
            try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(path), StandardCharsets.UTF_8)) {
                w.write(content);
            }
            filePath = path;
            dirty = false;
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 获取文档内容，写入 outBuf，返回字符数 */
    @CEntryPoint(name = "doc_get_content")
    public static int getContent(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        CTypeConversion.toCString(content, outBuf, WordFactory.unsigned(bufSize));
        return content.length();
    }

    /** 内部 String 版获取全文（供同 DLL 的 MDController 直接调用，避免 C 字符串悬空） */
    public static String getContentInternal() {
        return content;
    }

    /** 设置文档内容（整体替换），返回 0 成功 */
    @CEntryPoint(name = "doc_set_content")
    public static int setContent(IsolateThread thread, CCharPointer contentPtr) {
        String newContent = CTypeConversion.toJavaString(contentPtr);
        if (!newContent.equals(content)) {
            pushUndo();
            content = newContent;
            dirty = true;
            redoStack.clear();
        }
        return 0;
    }

    /** 获取当前文件路径，写入 outBuf，返回长度（无路径返回 0） */
    @CEntryPoint(name = "doc_get_file_path")
    public static int getFilePath(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        if (filePath == null) { CTypeConversion.toCString("", outBuf, WordFactory.unsigned(bufSize)); return 0; }
        CTypeConversion.toCString(filePath, outBuf, WordFactory.unsigned(bufSize));
        return filePath.length();
    }

    /** 内部 String 版获取路径（供同 DLL 的 MDController 直接调用，避免 C 字符串悬空） */
    public static String getFilePathInternal() {
        return filePath == null ? "" : filePath;
    }

    /** 文档是否有未保存修改，返回 1/0 */
    @CEntryPoint(name = "doc_is_dirty")
    public static int isDirty(IsolateThread thread) {
        return dirty ? 1 : 0;
    }

    // ===== 光标位置 =====

    /** 设置光标位置（字符偏移） */
    @CEntryPoint(name = "doc_set_cursor")
    public static void setCursor(IsolateThread thread, int pos) {
        cursorPos = Math.max(0, Math.min(pos, content.length()));
    }

    /** 获取光标位置（字符偏移） */
    @CEntryPoint(name = "doc_get_cursor")
    public static int getCursor(IsolateThread thread) {
        return cursorPos;
    }

    /** 获取光标所在行号（从 0 开始） */
    @CEntryPoint(name = "doc_get_cursor_line")
    public static int getCursorLine(IsolateThread thread) {
        int line = 0;
        for (int i = 0; i < cursorPos && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    /** 获取光标所在列号（从 0 开始） */
    @CEntryPoint(name = "doc_get_cursor_col")
    public static int getCursorCol(IsolateThread thread) {
        int col = 0;
        for (int i = cursorPos - 1; i >= 0; i--) {
            if (content.charAt(i) == '\n') break;
            col++;
        }
        return col;
    }

    // ===== 文本编辑 =====

    /** 在指定位置插入文本，返回 0 成功 */
    @CEntryPoint(name = "doc_insert_text")
    public static int insertText(IsolateThread thread, int pos, CCharPointer textPtr) {
        String text = CTypeConversion.toJavaString(textPtr);
        pos = Math.max(0, Math.min(pos, content.length()));
        pushUndo();
        content = content.substring(0, pos) + text + content.substring(pos);
        cursorPos = pos + text.length();
        dirty = true;
        redoStack.clear();
        return 0;
    }

    /** 删除 [start, end) 范围内的文本，返回 0 成功 */
    @CEntryPoint(name = "doc_delete_text")
    public static int deleteText(IsolateThread thread, int start, int end) {
        start = Math.max(0, Math.min(start, content.length()));
        end = Math.max(start, Math.min(end, content.length()));
        if (start == end) return 0;
        pushUndo();
        content = content.substring(0, start) + content.substring(end);
        cursorPos = start;
        dirty = true;
        redoStack.clear();
        return 0;
    }

    /** 获取 [start, end) 范围内的文本，写入 outBuf，返回长度 */
    @CEntryPoint(name = "doc_get_text")
    public static int getText(IsolateThread thread, int start, int end, CCharPointer outBuf, int bufSize) {
        start = Math.max(0, Math.min(start, content.length()));
        end = Math.max(start, Math.min(end, content.length()));
        String sub = content.substring(start, end);
        CTypeConversion.toCString(sub, outBuf, WordFactory.unsigned(bufSize));
        return sub.length();
    }

    // ===== 撤销/重做 =====

    /** 撤销，返回 0 成功，-1 无可撤销 */
    @CEntryPoint(name = "doc_undo")
    public static int undo(IsolateThread thread) {
        if (undoStack.size() <= 1) return -1;
        duringUndoRedo = true;
        redoStack.add(content);
        content = undoStack.remove(undoStack.size() - 1);
        cursorPos = Math.min(cursorPos, content.length());
        dirty = true;
        duringUndoRedo = false;
        return 0;
    }

    /** 重做，返回 0 成功，-1 无可重做 */
    @CEntryPoint(name = "doc_redo")
    public static int redo(IsolateThread thread) {
        if (redoStack.isEmpty()) return -1;
        duringUndoRedo = true;
        undoStack.add(content);
        content = redoStack.remove(redoStack.size() - 1);
        cursorPos = Math.min(cursorPos, content.length());
        dirty = true;
        duringUndoRedo = false;
        return 0;
    }

    /** 是否可撤销，返回 1/0 */
    @CEntryPoint(name = "doc_can_undo")
    public static int canUndo(IsolateThread thread) {
        return undoStack.size() > 1 ? 1 : 0;
    }

    /** 是否可重做，返回 1/0 */
    @CEntryPoint(name = "doc_can_redo")
    public static int canRedo(IsolateThread thread) {
        return redoStack.isEmpty() ? 0 : 1;
    }

    // ===== 统计 =====

    /** 字符数 */
    @CEntryPoint(name = "doc_get_char_count")
    public static int getCharCount(IsolateThread thread) {
        return content.length();
    }

    /** 单词数（空白分隔） */
    @CEntryPoint(name = "doc_get_word_count")
    public static int getWordCount(IsolateThread thread) {
        if (content.trim().isEmpty()) return 0;
        return content.trim().split("\\s+").length;
    }

    /** 行数 */
    @CEntryPoint(name = "doc_get_line_count")
    public static int getLineCount(IsolateThread thread) {
        if (content.isEmpty()) return 0;
        return content.split("\n", -1).length;
    }

    // ================================================================
    //  内部实现
    // ================================================================

    private static void pushUndo() {
        if (duringUndoRedo) return;
        undoStack.add(content);
        if (undoStack.size() > MAX_UNDO) {
            undoStack.remove(0);
        }
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
