import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import java.util.*;

/**
 * mdquicksort.dll — 快速排序模块（可复用）
 *
 * 功能：
 *   - 通用快速排序（整数/字符串/对象比较）
 *   - 支持升序/降序
 *   - 支持按 JSON 数组中某个字段排序（供可视化排序线程统计数据用）
 *   - 支持排序后返回 JSON
 *   - C ABI 导出
 *
 * 分类：doc（数据处理类）
 */
public class MDQuickSort {

    // ================================================================
    //  C ABI 导出
    // ================================================================

    /**
     * 对逗号分隔的整数字符串排序，返回排序后的逗号分隔字符串。
     * @param numsPtr  逗号分隔的整数，如 "5,2,8,1,9"
     * @param ascPtr   "true"=升序，"false"=降序
     * @param outBuf   输出缓冲区
     * @return 输出长度
     */
    @CEntryPoint(name = "quicksort_ints")
    public static int sortInts(IsolateThread thread, CCharPointer numsPtr, CCharPointer ascPtr, CCharPointer outBuf, int bufSize) {
        String numsStr = CTypeConversion.toJavaString(numsPtr);
        boolean asc = Boolean.parseBoolean(CTypeConversion.toJavaString(ascPtr));
        String[] parts = numsStr.split(",");
        List<Integer> nums = new ArrayList<>();
        for (String p : parts) {
            try { nums.add(Integer.parseInt(p.trim())); } catch (Exception ignored) {}
        }
        Integer[] arr = nums.toArray(new Integer[0]);
        quickSort(arr, 0, arr.length - 1, asc);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        CTypeConversion.toCString(sb.toString(), outBuf, WordFactory.unsigned(bufSize));
        return sb.length();
    }

    /**
     * 对逗号分隔的字符串排序（字典序），返回排序后的逗号分隔字符串。
     */
    @CEntryPoint(name = "quicksort_strings")
    public static int sortStrings(IsolateThread thread, CCharPointer strsPtr, CCharPointer ascPtr, CCharPointer outBuf, int bufSize) {
        String strsStr = CTypeConversion.toJavaString(strsPtr);
        boolean asc = Boolean.parseBoolean(CTypeConversion.toJavaString(ascPtr));
        String[] arr = strsStr.split(",");
        for (int i = 0; i < arr.length; i++) arr[i] = arr[i].trim();
        quickSortStrings(arr, 0, arr.length - 1, asc);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        CTypeConversion.toCString(sb.toString(), outBuf, WordFactory.unsigned(bufSize));
        return sb.length();
    }

    /**
     * 对 JSON 数组按指定数值字段排序，返回排序后的 JSON 数组字符串。
     * 输入格式：JSON 数组，如 [{"name":"a","cpu":10},{"name":"b","cpu":5}]
     * @param jsonArrayPtr  JSON 数组字符串
     * @param fieldPtr      排序字段名，如 "cpu"
     * @param ascPtr        "true"=升序，"false"=降序
     * @param outBuf        输出缓冲区
     * @return 输出长度
     */
    @CEntryPoint(name = "quicksort_json_by_field")
    public static int sortJsonByField(IsolateThread thread, CCharPointer jsonArrayPtr, CCharPointer fieldPtr, CCharPointer ascPtr, CCharPointer outBuf, int bufSize) {
        String json = CTypeConversion.toJavaString(jsonArrayPtr);
        String field = CTypeConversion.toJavaString(fieldPtr);
        boolean asc = Boolean.parseBoolean(CTypeConversion.toJavaString(ascPtr));
        String result = sortJsonArray(json, field, asc);
        CTypeConversion.toCString(result, outBuf, WordFactory.unsigned(bufSize));
        return result.length();
    }

    /** 返回排序算法信息 */
    @CEntryPoint(name = "quicksort_info")
    public static int info(IsolateThread thread, CCharPointer outBuf, int bufSize) {
        String info = "{\"algorithm\":\"quick_sort\",\"avg_complexity\":\"O(n log n)\",\"worst_complexity\":\"O(n^2)\",\"space\":\"O(log n)\",\"stable\":false}";
        CTypeConversion.toCString(info, outBuf, WordFactory.unsigned(bufSize));
        return info.length();
    }

            
    // ================================================================
    //  内部实现
    // ================================================================

    private static <T extends Comparable<T>> void quickSort(T[] arr, int low, int high, boolean asc) {
        if (low < high) {
            int pi = partition(arr, low, high, asc);
            quickSort(arr, low, pi - 1, asc);
            quickSort(arr, pi + 1, high, asc);
        }
    }

    private static <T extends Comparable<T>> int partition(T[] arr, int low, int high, boolean asc) {
        T pivot = arr[high];
        int i = low - 1;
        for (int j = low; j < high; j++) {
            int cmp = arr[j].compareTo(pivot);
            if (asc ? cmp <= 0 : cmp >= 0) {
                i++;
                T temp = arr[i]; arr[i] = arr[j]; arr[j] = temp;
            }
        }
        T temp = arr[i + 1]; arr[i + 1] = arr[high]; arr[high] = temp;
        return i + 1;
    }

    private static void quickSortStrings(String[] arr, int low, int high, boolean asc) {
        if (low < high) {
            int pi = partitionStrings(arr, low, high, asc);
            quickSortStrings(arr, low, pi - 1, asc);
            quickSortStrings(arr, pi + 1, high, asc);
        }
    }

    private static int partitionStrings(String[] arr, int low, int high, boolean asc) {
        String pivot = arr[high];
        int i = low - 1;
        for (int j = low; j < high; j++) {
            int cmp = arr[j].compareTo(pivot);
            if (asc ? cmp <= 0 : cmp >= 0) {
                i++;
                String temp = arr[i]; arr[i] = arr[j]; arr[j] = temp;
            }
        }
        String temp = arr[i + 1]; arr[i + 1] = arr[high]; arr[high] = temp;
        return i + 1;
    }

    /**
     * 公开静态方法：对 JSON 数组按指定数值字段排序（供其他模块调用）。
     */
    public static String sortJsonArrayStatic(String json, String field, boolean asc) {
        return sortJsonArray(json, field, asc);
    }

    /**
     * 简单 JSON 数组排序：解析每个对象的指定数值字段，排序后重组。
     * 不依赖 JSON 库，用简单字符串解析。
     */
    private static String sortJsonArray(String json, String field, boolean asc) {
        // 提取每个对象
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        if (objects.isEmpty()) return json;

        // 提取每个对象的字段值
        final String f = "\"" + field + "\":";
        objects.sort((a, b) -> {
            double va = extractNumber(a, f);
            double vb = extractNumber(b, f);
            int cmp = Double.compare(va, vb);
            return asc ? cmp : -cmp;
        });

        // 重组
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < objects.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(objects.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private static double extractNumber(String obj, String fieldPattern) {
        int idx = obj.indexOf(fieldPattern);
        if (idx < 0) return 0;
        idx += fieldPattern.length();
        StringBuilder num = new StringBuilder();
        while (idx < obj.length() && (Character.isDigit(obj.charAt(idx)) || obj.charAt(idx) == '.' || obj.charAt(idx) == '-')) {
            num.append(obj.charAt(idx));
            idx++;
        }
        try { return Double.parseDouble(num.toString()); } catch (Exception e) { return 0; }
    }
}
