import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

/**
 * mdcmd.dll — 命令行处理器模块（可独立复用）
 *
 * 功能：
 *   - 接收文本命令，解析后输出 IDP 格式数据包（XML头 + JSON数据）
 *   - 支持的命令：help, version, convert, stats, render, settings, log
 *   - 命令格式：command [arg1] [arg2] ...
 *   - 输出 IDP 格式，type 字段为命令名，source 为 "mdcmd"
 *   - C ABI 导出
 *
 * 任何需要命令行交互的应用可复用此模块，只需实现命令处理逻辑。
 */
public class MDCmd {

    public static final String VERSION = "1.0.10";

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /**
     * 处理一条命令，返回 IDP 格式数据包。
     * 输入：命令文本（如 "help"、"convert input.md output.html"、"stats"）
     * 输出：IDP 格式（XML头 + JSON数据），写入 outBuf
     * 返回：IDP 文本长度
     */
    @CEntryPoint(name = "cmd_exec")
    public static int exec(IsolateThread thread, CCharPointer cmdPtr, CCharPointer outBuf, int bufSize) {
        String cmd = CTypeConversion.toJavaString(cmdPtr).trim();
        String idp = processCommand(cmd);
        CTypeConversion.toCString(idp, outBuf, WordFactory.unsigned(bufSize));
        return idp.length();
    }

    /** 获取帮助文本（所有可用命令列表），写入 outBuf */
    @CEntryPoint(name = "cmd_help")
    public static int help(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String helpText = getHelpText();
        CTypeConversion.toCString(helpText, outBuf, WordFactory.unsigned(bufSize));
        return helpText.length();
    }

    // ================================================================
    //  内部实现
    // ================================================================

    /** 供其他模块（如 mdcontroller）直接调用，返回 IDP 字符串 */
    public static String execInternal(String cmd) {
        return processCommand(cmd);
    }

    private static String processCommand(String cmd) {
        if (cmd.isEmpty()) {
            return buildIdp("error", "mdcmd", "{\"error\":\"空命令\",\"hint\":\"输入 help 查看可用命令\"}");
        }

        String[] parts = cmd.split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            case "help":
            case "?":
                return buildIdp("help", "mdcmd", "{\"commands\":" + getCommandsJson() + "}");

            case "version":
            case "ver":
            case "--version":
            case "-v":
                return buildIdp("version", "mdcmd", "{\"version\":\"" + VERSION + "\"}");

            case "convert":
                if (parts.length < 3) {
                    return buildIdp("error", "mdcmd", "{\"error\":\"参数不足\",\"usage\":\"convert <input.md> <output.html>\"}");
                }
                return buildIdp("convert", "mdcmd",
                    "{\"input\":\"" + escapeJson(parts[1]) + "\",\"output\":\"" + escapeJson(parts[2]) + "\",\"action\":\"md_convert_html\"}");

            case "stats":
                return buildIdp("stats", "mdcmd", "{\"action\":\"md_doc_get_stats\"}");

            case "render":
                if (parts.length < 2) {
                    return buildIdp("error", "mdcmd", "{\"error\":\"参数不足\",\"usage\":\"render <file.md>\"}");
                }
                return buildIdp("render", "mdcmd", "{\"file\":\"" + escapeJson(parts[1]) + "\",\"action\":\"md_render_from_text\"}");

            case "settings":
            case "config":
                return buildIdp("settings", "mdcmd", "{\"action\":\"md_config_load_settings\"}");

            case "log":
                if (parts.length >= 2 && parts[1].equalsIgnoreCase("clear")) {
                    return buildIdp("log_clear", "mdcmd", "{\"action\":\"md_log_clear_all\"}");
                }
                return buildIdp("log", "mdcmd", "{\"action\":\"md_log_write\"}");

            case "exit":
            case "quit":
            case "q":
                return buildIdp("exit", "mdcmd", "{\"action\":\"shutdown\"}");

            default:
                // 非内部命令：隐形调用系统 CMD 执行，不区分内部/外部命令
                return execSystemCommand(cmd);
        }
    }

    /** 隐形执行系统 CMD 命令，返回纯文本输出（GBK 解码，含 stderr） */
    private static String execSystemCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            java.io.InputStream is = p.getInputStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            // 带超时读取，避免命令卡死（最长 10 秒）
            long deadline = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < deadline) {
                if (is.available() > 0) {
                    n = is.read(buf);
                    if (n == -1) break;
                    baos.write(buf, 0, n);
                } else if (!p.isAlive()) {
                    // 进程结束，读完剩余输出
                    while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                    break;
                } else {
                    Thread.sleep(50);
                }
            }
            if (p.isAlive()) p.destroy();
            String output = baos.toString("GBK").trim();
            return output.isEmpty() ? "(命令执行完成，无输出)" : output;
        } catch (Exception e) {
            return "[执行失败] " + e.getMessage();
        }
    }

    private static String buildIdp(String type, String source, String jsonData) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<idp version=\"1.0\" type=\"").append(type).append("\"");
        sb.append(" timestamp=\"").append(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date())).append("\"");
        sb.append(" source=\"").append(source).append("\"");
        sb.append(" size=\"").append(jsonData.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).append("\"");
        sb.append("/>\n");
        sb.append("===IDP-DATA===\n");
        sb.append(jsonData);
        return sb.toString();
    }

    private static String getCommandsJson() {
        return "[" +
            "{\"cmd\":\"help\",\"desc\":\"显示帮助信息\"}," +
            "{\"cmd\":\"version\",\"desc\":\"显示版本号\"}," +
            "{\"cmd\":\"convert <in> <out>\",\"desc\":\"转换文件格式\"}," +
            "{\"cmd\":\"stats\",\"desc\":\"显示文档统计\"}," +
            "{\"cmd\":\"render <file>\",\"desc\":\"渲染 Markdown\"}," +
            "{\"cmd\":\"settings\",\"desc\":\"管理设置\"}," +
            "{\"cmd\":\"log [clear]\",\"desc\":\"日志管理/清除所有日志\"}," +
            "{\"cmd\":\"exit\",\"desc\":\"退出程序\"}" +
            "]";
    }

    private static String getHelpText() {
        return "MDRenderer 命令行帮助 V" + VERSION + "\n\n" +
            "可用命令：\n" +
            "  help              显示此帮助信息\n" +
            "  version           显示版本号\n" +
            "  convert <in> <out>  转换文件格式（MD→HTML等）\n" +
            "  stats             显示文档统计信息\n" +
            "  render <file>     渲染 Markdown 文件\n" +
            "  settings          管理设置\n" +
            "  log               日志管理\n" +
            "  log clear         清除所有日志\n" +
            "  exit              退出程序";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ================================================================
    //  IDP 输出（调用 mdidp 模块，所有模块通用通信格式）
    // ================================================================

    /** 将 JSON 数据包装为 IDP 格式并写入文件，返回 0 成功 / -1 失败 */
    @CEntryPoint(name = "cmd_write_idp")
    public static int writeIdp(IsolateThread thread, CCharPointer filePathPtr, CCharPointer typePtr, CCharPointer jsonDataPtr) {
        try {
            String type = CTypeConversion.toJavaString(typePtr);
            String jsonData = CTypeConversion.toJavaString(jsonDataPtr);
            MDIdp.enqueue(type, "mdcmd", jsonData);
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 将 JSON 数据包装为 IDP 格式字符串，写入 outBuf，返回 IDP 文本长度 */
    @CEntryPoint(name = "cmd_to_idp")
    public static int toIdp(IsolateThread thread, CCharPointer typePtr, CCharPointer jsonDataPtr, CCharPointer outBuf, int bufSize) {
        String type = CTypeConversion.toJavaString(typePtr);
        String jsonData = CTypeConversion.toJavaString(jsonDataPtr);
        String idp = MDIdp.createPacket(type, "mdcmd", jsonData);
        CTypeConversion.toCString(idp, outBuf, WordFactory.unsigned(bufSize));
        return idp.length();
    }

    /** 读取 IDP 文件中的 JSON 数据，写入 outBuf，返回数据长度 */
    @CEntryPoint(name = "cmd_read_idp")
    public static int readIdp(IsolateThread thread, CCharPointer filePathPtr, CCharPointer outBuf, int bufSize) {
        String jsonData = MDIdp.dequeueJson();
        CTypeConversion.toCString(jsonData, outBuf, WordFactory.unsigned(bufSize));
        return jsonData.length();
    }
}
