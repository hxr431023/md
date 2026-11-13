import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.*;
import java.util.Properties;

/**
 * mdconfig.dll — 设置管理模块（可复用，单一职责）
 *
 * 功能：
 *   - Java Properties 格式设置的加载/保存/读取/写入/存在检测
 *   - 设置文件位于 resources/cmd/settings.tmmf（Properties 格式）
 *   - C ABI 导出
 *
 * 注意：TMMF 标签读写已移至 mdtmmf 模块；
 *       LERF 语言资源查询已移至 mdlerf 模块。
 *       本模块只负责应用设置（key=value Properties）。
 */
public class MDConfig {

    private static String baseDir = ".";
    private static Properties settings = new Properties();

    // ================================================================
    //  C ABI 导出
    // ================================================================

    @CEntryPoint(name = "config_init")
    public static int init(IsolateThread thread, CCharPointer baseDirPtr) {
        baseDir = CTypeConversion.toJavaString(baseDirPtr);
        return 0;
    }

    /** 从磁盘加载设置文件 */
    @CEntryPoint(name = "config_load_settings")
    public static int loadSettings(IsolateThread thread) {
        try {
            File f = getSettingsFile();
            if (!f.exists()) return -1;
            settings.load(new FileInputStream(f));
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 保存设置到磁盘 */
    @CEntryPoint(name = "config_save_settings")
    public static int saveSettings(IsolateThread thread) {
        try {
            File f = getSettingsFile();
            f.getParentFile().mkdirs();
            settings.store(new FileOutputStream(f), "MDRenderer Settings");
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 读取设置值，写入 outBuf，返回值长度 */
    @CEntryPoint(name = "config_get_setting")
    public static int getSetting(IsolateThread thread, CCharPointer keyPtr, CCharPointer outBuf, int bufSize) {
        String key = CTypeConversion.toJavaString(keyPtr);
        String val = settings.getProperty(key, "");
        CTypeConversion.toCString(val, outBuf, WordFactory.unsigned(bufSize));
        return val.length();
    }

    /** 写入设置值（内存中，需调用 save_settings 持久化） */
    @CEntryPoint(name = "config_set_setting")
    public static int setSetting(IsolateThread thread, CCharPointer keyPtr, CCharPointer valPtr) {
        settings.setProperty(CTypeConversion.toJavaString(keyPtr), CTypeConversion.toJavaString(valPtr));
        return 0;
    }

    /** 检查设置键是否存在，返回 1/0 */
    @CEntryPoint(name = "config_has_setting")
    public static int hasSetting(IsolateThread thread, CCharPointer keyPtr) {
        return settings.containsKey(CTypeConversion.toJavaString(keyPtr)) ? 1 : 0;
    }

    /** 删除设置键，返回 0 成功 */
    @CEntryPoint(name = "config_remove_setting")
    public static int removeSetting(IsolateThread thread, CCharPointer keyPtr) {
        settings.remove(CTypeConversion.toJavaString(keyPtr));
        return 0;
    }

    // ===== 线程模式（thread_mode: folder/single/per_dll）=====

    /** 获取线程模式，写入 outBuf（默认 folder） */
    @CEntryPoint(name = "config_get_thread_mode")
    public static int getThreadMode(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String mode = settings.getProperty("thread_mode", "folder");
        CTypeConversion.toCString(mode, outBuf, WordFactory.unsigned(bufSize));
        return mode.length();
    }

    /** 设置线程模式（folder/single/per_dll），返回 0 成功 / -1 非法值 */
    @CEntryPoint(name = "config_set_thread_mode")
    public static int setThreadMode(IsolateThread thread, CCharPointer modePtr) {
        String mode = CTypeConversion.toJavaString(modePtr);
        if (mode.equals("folder") || mode.equals("single") || mode.equals("per_dll")) {
            settings.setProperty("thread_mode", mode);
            return 0;
        }
        return -1;
    }

    // ================================================================
    //  内部实现（供同 DLL 内其他 Java 类直接调用）
    // ================================================================

    public static String getSetting(String key) {
        return settings.getProperty(key, "");
    }

    public static void setSetting(String key, String value) {
        settings.setProperty(key, value);
    }

    public static boolean hasSetting(String key) {
        return settings.containsKey(key);
    }

    private static File getSettingsFile() {
        return new File(baseDir, "resources/cmd/settings.tmmf");
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
