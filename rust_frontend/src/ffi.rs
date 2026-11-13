//! FFI bindings to controller.dll (GraalVM native-image shared library)
//!
//! 通信铁律（用户强制要求）：前端与 DLL 之间一律走"单内存双向栈 IDP"，
//! 严禁句柄式直调业务函数、严禁文件型 IDP、严禁任何其他通道。
//!
//! 本文件只保留 5 类 C 导出入口：
//!   graal_create_isolate / app_init / app_shutdown   —— isolate 生命周期
//!   idp_push / app_idp_process / idp_pop_json / idp_stack_size —— 内存双向栈
//!
//! 所有业务调用统一为三段式栈通信：
//!   call(type, json) = idp_push(type, "rust", json) -> app_idp_process() -> idp_pop_json()
//! 请求压队尾（enqueue），app_idp_process 从队尾取最新请求、把结果压回队首，
//! 前端 idp_pop_json 弹队首，永远拿到最新结果；不依赖任何句柄/文件。

use libloading::Library;
use std::ffi::{c_char, c_int, c_void, CString};
use std::path::Path;

pub type IsolateThread = *mut c_void;

type FnCreateIsolate =
    unsafe extern "C" fn(*const c_void, *mut *mut c_void, *mut *mut c_void) -> c_int;
type FnAppInit = unsafe extern "C" fn(IsolateThread, *const c_char) -> c_int;
type FnAppShutdown = unsafe extern "C" fn(IsolateThread);
type FnThreeStrIn =
    unsafe extern "C" fn(IsolateThread, *const c_char, *const c_char, *const c_char) -> c_int;
type FnProcess = unsafe extern "C" fn(IsolateThread) -> c_int;
type FnStrOut = unsafe extern "C" fn(IsolateThread, *mut c_char, c_int) -> c_int;
type FnInt = unsafe extern "C" fn(IsolateThread) -> c_int;

/// 结果缓冲：渲染指令/HTML 可能较大，统一 2 MiB。
const BUF_SIZE: usize = 2 * 1024 * 1024;

pub struct MDDll {
    _lib: Library,
    thread: IsolateThread,
    _isolate: *mut c_void,
    idp_push: FnThreeStrIn,
    app_idp_process: FnProcess,
    idp_pop_json: FnStrOut,
    idp_stack_size: FnInt,
    app_shutdown: FnAppShutdown,
}

unsafe impl Send for MDDll {}
unsafe impl Sync for MDDll {}

impl MDDll {
    pub fn new(dll_path: &Path, work_dir: &str) -> Result<Self, String> {
        let lib = unsafe { Library::new(dll_path) }
            .map_err(|e| format!("加载 DLL 失败: {} (路径: {})", e, dll_path.display()))?;

        macro_rules! load_fn {
            ($name:expr, $ty:ty) => {{
                let sym: libloading::Symbol<$ty> = unsafe {
                    lib.get($name.as_bytes())
                        .map_err(|e| format!("找不到导出函数 {}: {}", $name, e))?
                };
                *sym
            }};
        }

        let create_isolate: FnCreateIsolate = load_fn!("graal_create_isolate", FnCreateIsolate);
        let app_init: FnAppInit = load_fn!("app_init", FnAppInit);
        let app_shutdown: FnAppShutdown = load_fn!("app_shutdown", FnAppShutdown);

        let mut isolate: *mut c_void = std::ptr::null_mut();
        let mut thread: *mut c_void = std::ptr::null_mut();
        let rc = unsafe { create_isolate(std::ptr::null(), &mut isolate, &mut thread) };
        if rc != 0 || thread.is_null() {
            return Err(format!("graal_create_isolate 失败: rc={}", rc));
        }

        let work_c = CString::new(work_dir).unwrap();
        unsafe { app_init(thread, work_c.as_ptr()) };

        Ok(Self {
            idp_push: load_fn!("idp_push", FnThreeStrIn),
            app_idp_process: load_fn!("app_idp_process", FnProcess),
            idp_pop_json: load_fn!("idp_pop_json", FnStrOut),
            idp_stack_size: load_fn!("idp_stack_size", FnInt),
            app_shutdown,
            _lib: lib,
            thread,
            _isolate: isolate,
        })
    }

    // =================================================================
    //  核心：三段式内存双向栈通信（唯一业务通道）
    // =================================================================

    /// 压入请求包（队尾）
    fn idp_push(&self, typ: &str, json: &str) {
        let t = CString::new(typ).unwrap();
        let s = CString::new("rust").unwrap();
        let j = CString::new(json).unwrap();
        unsafe { (self.idp_push)(self.thread, t.as_ptr(), s.as_ptr(), j.as_ptr()) };
    }

    /// 触发控制层处理队尾最新请求（app_idp_process）
    fn idp_process(&self) {
        unsafe { (self.app_idp_process)(self.thread) };
    }

    /// 弹出队首结果 JSON
    fn idp_pop(&self) -> String {
        let mut buf = vec![0u8; BUF_SIZE];
        let n = unsafe { (self.idp_pop_json)(self.thread, buf.as_mut_ptr() as *mut c_char, BUF_SIZE as c_int) };
        let n = n.max(0).min(BUF_SIZE as c_int) as usize;
        String::from_utf8_lossy(&buf[..n]).to_string()
    }

    /// 通用调用：idp_push(type, json) → app_idp_process → idp_pop_json
    /// 返回控制层 dispatch 的结果 JSON。
    fn call(&self, typ: &str, params: &str) -> String {
        self.idp_push(typ, params);
        self.idp_process();
        self.idp_pop()
    }

    // =================================================================
    //  极简 JSON 提取（本工程不引入 serde，保持轻量）
    // =================================================================

    /// 取字符串字段值（含转义还原 \n \t \r \\ \" \uXXXX）
    fn json_get_str(json: &str, key: &str) -> String {
        let k = format!("\"{}\":\"", key);
        if let Some(i) = json.find(&k) {
            let bytes = json[i + k.len()..].as_bytes();
            let mut out = String::new();
            let mut j = 0;
            while j < bytes.len() {
                let c = bytes[j];
                if c == b'"' {
                    break;
                }
                if c == b'\\' && j + 1 < bytes.len() {
                    let n = bytes[j + 1];
                    match n {
                        b'n' => { out.push('\n'); j += 2; continue; }
                        b't' => { out.push('\t'); j += 2; continue; }
                        b'r' => { out.push('\r'); j += 2; continue; }
                        b'\\' => { out.push('\\'); j += 2; continue; }
                        b'"' => { out.push('"'); j += 2; continue; }
                        b'u' => {
                            if j + 5 < bytes.len() {
                                let hex = &json[i + k.len() + j + 2..i + k.len() + j + 6];
                                if let Ok(code) = u32::from_str_radix(hex, 16) {
                                    if let Some(ch) = char::from_u32(code) {
                                        out.push(ch);
                                        j += 6;
                                        continue;
                                    }
                                }
                            }
                            out.push('\\');
                            j += 2;
                            continue;
                        }
                        _ => { j += 2; continue; }
                    }
                }
                out.push(c as char);
                j += 1;
            }
            return out;
        }
        String::new()
    }

    /// 取任意类型字段的原始 JSON 子串（string/object/array/number/bool），保留原样
    fn json_get_raw(json: &str, key: &str) -> String {
        let k = format!("\"{}\":", key);
        if let Some(i) = json.find(&k) {
            let rest = json[i + k.len()..].trim_start();
            let bytes = rest.as_bytes();
            if bytes.is_empty() {
                return String::new();
            }
            let first = bytes[0];
            if first == b'"' {
                let mut j = 1;
                while j < bytes.len() {
                    if bytes[j] == b'\\' { j += 2; continue; }
                    if bytes[j] == b'"' { return rest[..=j].to_string(); }
                    j += 1;
                }
                return rest.to_string();
            }
            if first == b'{' || first == b'[' {
                let mut depth = 0i32;
                let mut in_str = false;
                let mut j = 0;
                while j < bytes.len() {
                    let c = bytes[j];
                    if in_str {
                        if c == b'\\' { j += 2; continue; }
                        if c == b'"' { in_str = false; }
                    } else {
                        match c {
                            b'"' => in_str = true,
                            b'{' | b'[' => depth += 1,
                            b'}' | b']' => {
                                depth -= 1;
                                if depth == 0 { return rest[..=j].to_string(); }
                            }
                            _ => {}
                        }
                    }
                    j += 1;
                }
                return rest.to_string();
            }
            // number / bool / null：读到分隔符
            let mut j = 0;
            while j < bytes.len() && bytes[j] != b',' && bytes[j] != b'}' && bytes[j] != b']' {
                j += 1;
            }
            return rest[..j].trim().to_string();
        }
        String::new()
    }

    fn json_get_int(json: &str, key: &str) -> i32 {
        MDDll::json_get_raw(json, key).trim().parse().unwrap_or(0)
    }

    fn json_get_bool(json: &str, key: &str) -> bool {
        MDDll::json_get_raw(json, key).trim() == "true"
    }

    /// 字符串值 → JSON 转义（放入参数 JSON）
    fn json_escape(s: &str) -> String {
        let mut out = String::with_capacity(s.len() + 8);
        for c in s.chars() {
            match c {
                '"' => out.push_str("\\\""),
                '\\' => out.push_str("\\\\"),
                '\n' => out.push_str("\\n"),
                '\r' => out.push_str("\\r"),
                '\t' => out.push_str("\\t"),
                c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
                c => out.push(c),
            }
        }
        out
    }

    // =================================================================
    //  业务方法（全部走 call() 三段式栈通信，无句柄直调）
    // =================================================================

    pub fn version(&self) -> String {
        let r = self.call("app_version", "{}");
        MDDll::json_get_str(&r, "version")
    }

    pub fn new_file(&self) {
        self.call("app_new_file", "{}");
    }

    pub fn insert_text(&self, pos: i32, text: &str) {
        let params = format!("{{\"pos\":{},\"text\":\"{}\"}}", pos, MDDll::json_escape(text));
        self.call("app_insert_text", &params);
    }

    pub fn char_count(&self) -> i32 {
        let r = self.call("doc_char_count", "{}");
        MDDll::json_get_int(&r, "count")
    }

    pub fn render_html(&self, md: &str) -> String {
        let params = format!("{{\"md\":\"{}\"}}", MDDll::json_escape(md));
        let r = self.call("app_render_html", &params);
        MDDll::json_get_str(&r, "html")
    }

    /// 通用渲染入口：控制层从文档模型生成通用 IDP 渲染指令（view/ui/elements）
    pub fn render_ui(&self) -> String {
        self.call("app_render_ui", "{}")
    }

    pub fn easter_detect(&self, line: &str) -> bool {
        let params = format!("{{\"line\":\"{}\"}}", MDDll::json_escape(line));
        let r = self.call("app_easter_detect", &params);
        MDDll::json_get_bool(&r, "is_easter")
    }

    pub fn easter_exec(&self, line: &str) -> String {
        let params = format!("{{\"line\":\"{}\"}}", MDDll::json_escape(line));
        let r = self.call("app_easter_exec", &params);
        MDDll::json_get_str(&r, "result")
    }

    pub fn get_setting(&self, key: &str) -> String {
        let params = format!("{{\"key\":\"{}\"}}", MDDll::json_escape(key));
        let r = self.call("app_get_setting", &params);
        MDDll::json_get_str(&r, "value")
    }

    pub fn set_setting(&self, key: &str, value: &str) {
        let params = format!(
            "{{\"key\":\"{}\",\"value\":\"{}\"}}",
            MDDll::json_escape(key),
            MDDll::json_escape(value)
        );
        self.call("app_set_setting", &params);
    }

    pub fn thread_mode(&self) -> String {
        let r = self.call("config_get_thread_mode", "{}");
        MDDll::json_get_str(&r, "mode")
    }

    pub fn set_thread_mode(&self, mode: &str) {
        let params = format!("{{\"mode\":\"{}\"}}", MDDll::json_escape(mode));
        self.call("config_set_thread_mode", &params);
    }

    pub fn save_settings(&self) {
        self.call("app_save_settings", "{}");
    }

    pub fn threadstat_sample(&self) {
        self.call("threadstat_sample", "{}");
    }

    pub fn threadstat_json(&self) -> String {
        let r = self.call("threadstat_get_json", "{}");
        MDDll::json_get_raw(&r, "stats")
    }

    pub fn visualize_html(&self, stats_json: &str) -> String {
        let params = format!("{{\"stats\":\"{}\"}}", MDDll::json_escape(stats_json));
        let r = self.call("visualize_stats_to_html", &params);
        MDDll::json_get_str(&r, "html")
    }

    pub fn clear_logs(&self) -> i32 {
        let r = self.call("log_clear_all", "{}");
        MDDll::json_get_int(&r, "ok")
    }

    pub fn single_check(&self) -> bool {
        let r = self.call("single_try_lock", "{}");
        MDDll::json_get_bool(&r, "locked")
    }

    /// 查询：返回 content 中所有 query 的匹配位置 JSON（业务逻辑在控制层内编排 mdrender）
    pub fn render_find(&self, content: &str, query: &str) -> String {
        let params = format!(
            "{{\"content\":\"{}\",\"query\":\"{}\"}}",
            MDDll::json_escape(content),
            MDDll::json_escape(query)
        );
        let r = self.call("render_find", &params);
        MDDll::json_get_raw(&r, "result")
    }

    /// 替换：把 content 中所有 find 替换为 replace，返回替换后全文 JSON
    pub fn render_replace(&self, content: &str, find: &str, replace: &str) -> String {
        let params = format!(
            "{{\"content\":\"{}\",\"find\":\"{}\",\"replace\":\"{}\"}}",
            MDDll::json_escape(content),
            MDDll::json_escape(find),
            MDDll::json_escape(replace)
        );
        let r = self.call("render_replace", &params);
        MDDll::json_get_raw(&r, "result")
    }

    /// 插入：在 content 中每个 "left...right" 片段中间插入 insert，返回插入后全文 JSON
    pub fn render_insert(&self, content: &str, left: &str, right: &str, insert: &str) -> String {
        let params = format!(
            "{{\"content\":\"{}\",\"left\":\"{}\",\"right\":\"{}\",\"insert\":\"{}\"}}",
            MDDll::json_escape(content),
            MDDll::json_escape(left),
            MDDll::json_escape(right),
            MDDll::json_escape(insert)
        );
        let r = self.call("render_insert", &params);
        MDDll::json_get_raw(&r, "result")
    }

    /// 分页渲染：content + 页宽/页高(px) → 分页 IDP（每页 elements + 页码）
    pub fn render_paged(&self, content: &str, page_width: i32, page_height: i32) -> String {
        let params = format!(
            "{{\"content\":\"{}\",\"page_width\":{},\"page_height\":{}}}",
            MDDll::json_escape(content),
            page_width,
            page_height
        );
        let r = self.call("render_paged", &params);
        MDDll::json_get_raw(&r, "result")
    }
}

impl Drop for MDDll {
    fn drop(&mut self) {
        unsafe { (self.app_shutdown)(self.thread) };
    }
}
