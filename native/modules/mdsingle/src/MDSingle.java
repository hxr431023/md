import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

/**
 * mdsingle.dll — 单实例互锁模块（可独立复用）
 *
 * 功能：
 *   - 基于 Java NIO FileLock 的跨进程单实例锁
 *   - 程序崩溃时 OS 自动释放锁
 *   - 第二个实例启动时检测到锁，返回拒绝状态
 *   - 锁文件位于系统临时目录，可自定义锁名
 *   - C ABI 导出
 *
 * 任何需要"禁止重复启动"的应用都可复用此模块。
 */
public class MDSingle {

    private static FileLock lock;
    private static RandomAccessFile lockFile;
    private static String lockName = "app";
    private static boolean locked = false;

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /**
     * 尝试获取单实例锁。
     * @param lockNamePtr 锁名称（如 "mdrenderer"），用于区分不同应用
     * @return 1=获取成功（第一个实例），0=获取失败（已有实例在运行）
     */
    @CEntryPoint(name = "single_try_lock")
    public static int tryLock(IsolateThread thread, CCharPointer lockNamePtr) {
        return tryLockInternal(CTypeConversion.toJavaString(lockNamePtr));
    }

    /** 内部 String 版获取单实例锁（供同 DLL 的 MDController 直接调用，避免 C 字符串悬空） */
    public static int tryLockInternal(String name) {
        try {
            lockName = name;
            String tmpDir = System.getProperty("java.io.tmpdir");
            File lockFileObj = new File(tmpDir, lockName + ".lock");
            lockFile = new RandomAccessFile(lockFileObj, "rw");
            lock = lockFile.getChannel().tryLock();
            if (lock == null) {
                lockFile.close();
                locked = false;
                return 0; // 已有实例
            }
            // 写入进程标识，便于排查
            lockFile.setLength(0);
            lockFile.writeUTF(lockName + "|" + System.getProperty("user.dir") + "|" + System.currentTimeMillis());
            locked = true;
            // 注册 ShutdownHook 释放锁
            Runtime.getRuntime().addShutdownHook(new Thread(() -> releaseLock()));
            return 1; // 获取成功
        } catch (Exception e) {
            locked = false;
            return 0;
        }
    }

    /** 主动释放单实例锁（程序正常退出时调用） */
    @CEntryPoint(name = "single_release")
    public static void release(IsolateThread thread) {
        releaseLock();
    }

    /** 当前是否持有锁（即是否为第一个实例），返回 1/0 */
    @CEntryPoint(name = "single_is_locked")
    public static int isLocked(IsolateThread thread) {
        return locked ? 1 : 0;
    }

    /** 获取锁文件路径，写入 outBuf */
    @CEntryPoint(name = "single_lock_path")
    public static int lockPath(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String path = new File(tmpDir, lockName + ".lock").getAbsolutePath();
        CTypeConversion.toCString(path, outBuf, WordFactory.unsigned(bufSize));
        return path.length();
    }

    // ================================================================
    //  内部实现
    // ================================================================

    private static void releaseLock() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
            if (lockFile != null) {
                lockFile.close();
            }
            locked = false;
        } catch (Exception ignored) {}
    }


    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    
    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    
    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    }
