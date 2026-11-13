//! MDRenderer - Rust 原生前端入口
//! 调用 GraalVM 原生 DLL (mdcontroller.dll) 作为后端

mod app;
mod extensions;
mod ffi;
mod idp;
mod settings;
mod syntax;

use app::App;
use ffi::MDDll;
use std::path::PathBuf;
use syntax::SyntaxManager;

fn find_dll() -> Option<PathBuf> {
    // 搜索路径：exe同目录、DLL\app、..\native\DLL\app、..\dist
    let candidates = [
        "controller.dll",
        "DLL/app/controller.dll",
        "../native/DLL/app/controller.dll",
        "../native/dist/controller.dll",
        "../../native/DLL/app/controller.dll",
        r"C:\wpjc\md\native\DLL\app\controller.dll",
        r"C:\wpjc\md\native\dist\controller.dll",
    ];
    for c in &candidates {
        let p = PathBuf::from(c);
        if p.exists() {
            return Some(p);
        }
    }
    None
}

fn find_syntax_dir() -> Option<PathBuf> {
    let candidates = [
        "syntax",
        "../syntax",
        "../../rust_frontend/syntax",
        r"C:\wpjc\md\rust_frontend\syntax",
    ];
    for c in &candidates {
        let p = PathBuf::from(c);
        if p.exists() {
            return Some(p);
        }
    }
    None
}

fn main() -> eframe::Result<()> {
    // 1. 加载 DLL
    let dll_path = match find_dll() {
        Some(p) => p,
        None => {
            eprintln!("错误：找不到 mdcontroller.dll");
            eprintln!("请确保 DLL 在程序同目录或 DLL\\app\\ 子目录下");
            std::process::exit(1);
        }
    };
    println!("加载 DLL: {}", dll_path.display());

    let work_dir = std::env::current_dir()
        .unwrap_or_else(|_| PathBuf::from("."))
        .to_string_lossy()
        .to_string();

    let dll = match MDDll::new(&dll_path, &work_dir) {
        Ok(d) => d,
        Err(e) => {
            eprintln!("DLL 初始化失败: {}", e);
            std::process::exit(1);
        }
    };
    println!("DLL 版本: {}", dll.version());

    // 2. 单实例检查
    // single_try_lock 返回 1 = 获取锁成功（第一个实例，可继续运行）
    // 返回 0 = 已有实例在运行 → 提示并退出
    if !dll.single_check() {
        eprintln!("程序已在运行中");
        println!("MDRenderer 已经在运行了，请不要重复启动。");
        std::process::exit(0);
    }

    // 3. 加载语法
    let syntax_dir = find_syntax_dir().unwrap_or_else(|| PathBuf::from("syntax"));
    println!("语法目录: {}", syntax_dir.display());
    let syntax = match SyntaxManager::load(&syntax_dir) {
        Ok(s) => {
            println!("已加载语法: {}", s.names().join(", "));
            s
        }
        Err(e) => {
            eprintln!("语法加载警告: {}", e);
            SyntaxManager {
                base: None,
                extensions: Vec::new(),
                syntax_dir,
            }
        }
    };

    // 4. 启动 GUI
    let app = App::new(dll, syntax);
    let native_options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_title("MDRenderer")
            .with_inner_size([1100.0, 720.0])
            .with_min_inner_size([640.0, 480.0]),
        ..Default::default()
    };

    eframe::run_native(
        "MDRenderer",
        native_options,
        Box::new(|_cc| Ok(Box::new(app))),
    )
}
