//! 主应用窗口：编辑器 + 预览 + 菜单栏 + 自定义语法渲染

use crate::idp::{Element, MenuItem, UiPacket};
use crate::ffi::MDDll;
use crate::settings::SettingsDialog;
use crate::syntax::SyntaxManager;
use egui::{Color32, RichText, ScrollArea};
use std::path::PathBuf;
use std::sync::Arc;

#[derive(PartialEq, Clone, Copy)]
enum ViewMode {
    EditOnly,
    PreviewOnly,
    Split,
}

pub struct App {
    pub dll: Arc<MDDll>,
    pub syntax: SyntaxManager,
    pub settings: SettingsDialog,
    // 编辑器状态
    pub editor_text: String,
    pub file_path: Option<PathBuf>,
    pub dirty: bool,
    // 通用 IDP 输入控件状态
    pub input_text: String,
    // 视图模式：单编辑框 / 单渲染框 / 双视图
    pub view_mode: ViewMode,
    // 状态栏
    pub status: String,
    // 关于弹窗
    pub show_about: bool,
    // 帮助弹窗
    pub show_help: bool,
    // 查询 / 替换 / 插入 对话框（业务逻辑在渲染端 DLL）
    pub show_find: bool,
    pub find_tab: FindTab,
    pub find_query: String,
    pub find_replace: String,
    pub find_left: String,
    pub find_right: String,
    pub find_insert: String,
    pub find_result: String,
    // 打印对话框
    pub show_print: bool,
}

#[derive(PartialEq, Clone, Copy)]
enum FindTab {
    Query,
    Replace,
    Insert,
}

impl App {
    pub fn new(dll: MDDll, syntax: SyntaxManager) -> Self {
        let version = dll.version();
        Self {
            dll: Arc::new(dll),
            syntax,
            settings: SettingsDialog::default(),
            editor_text: String::new(),
            file_path: None,
            dirty: false,
            input_text: String::new(),
            view_mode: ViewMode::PreviewOnly,
            status: format!("MDRenderer v{} 就绪", version),
            show_about: false,
            show_help: false,
            show_find: false,
            find_tab: FindTab::Query,
            find_query: String::new(),
            find_replace: String::new(),
            find_left: String::new(),
            find_right: String::new(),
            find_insert: String::new(),
            find_result: String::new(),
            show_print: false,
        }
    }

    fn menu_bar(&mut self, ctx: &egui::Context) {
        egui::TopBottomPanel::top("menu_bar").show(ctx, |ui| {
            egui::menu::bar(ui, |ui| {
                // 文件菜单
                ui.menu_button("文件", |ui| {
                    if ui.button("新建").clicked() {
                        self.editor_text.clear();
                        self.file_path = None;
                        self.dirty = false;
                        self.status = "新建文档".into();
                        ui.close_menu();
                    }
                    if ui.button("打开...").clicked() {
                        if let Some(path) = rfd::FileDialog::new()
                            .add_filter("Markdown", &["md", "markdown"])
                            .add_filter("所有文件", &["*"])
                            .pick_file()
                        {
                            if let Ok(content) = std::fs::read_to_string(&path) {
                                self.editor_text = content;
                                self.file_path = Some(path);
                                self.dirty = false;
                                self.status = "已打开文件".into();
                            }
                        }
                        ui.close_menu();
                    }
                    if ui.button("保存").clicked() {
                        self.save_file();
                        ui.close_menu();
                    }
                    if ui.button("另存为...").clicked() {
                        self.save_as();
                        ui.close_menu();
                    }
                    ui.separator();
                    if ui.button("打印...").clicked() {
                        self.show_print = true;
                        ui.close_menu();
                    }
                    if ui.button("导出 HTML").clicked() {
                        self.export_html();
                        ui.close_menu();
                    }
                    ui.separator();
                    if ui.button("退出").clicked() {
                        ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                    }
                });

                // 编辑菜单
                ui.menu_button("编辑", |ui| {
                    if ui.button("全选").clicked() { ui.close_menu(); }
                    if ui.button("复制").clicked() { ui.close_menu(); }
                    if ui.button("粘贴").clicked() { ui.close_menu(); }
                    ui.separator();
                    if ui.button("查找 / 替换 / 插入...").clicked() {
                        self.show_find = true;
                        ui.close_menu();
                    }
                });

                // 语法菜单
                ui.menu_button("语法", |ui| {
                    ui.label("已加载语法：");
                    for name in self.syntax.names() {
                        ui.label(format!("  ✓ {}", name));
                    }
                    ui.separator();
                    ui.label("基础: # ## ### ** * ` > -");
                    ui.label("表情包: [文字]");
                    ui.label("对话框: :\"文字\"序号");
                    ui.label("超链接: ≈路径≈序号");
                    ui.label("彩蛋命令: \\\\>命令");
                });

                // 视图菜单
                ui.menu_button("视图", |ui| {
                    if ui.selectable_label(self.view_mode == ViewMode::EditOnly, "单编辑框").clicked() {
                        self.view_mode = ViewMode::EditOnly;
                        ui.close_menu();
                    }
                    if ui.selectable_label(self.view_mode == ViewMode::PreviewOnly, "单渲染框").clicked() {
                        self.view_mode = ViewMode::PreviewOnly;
                        ui.close_menu();
                    }
                    if ui.selectable_label(self.view_mode == ViewMode::Split, "双视图").clicked() {
                        self.view_mode = ViewMode::Split;
                        ui.close_menu();
                    }
                });

                // 设置菜单
                if ui.button("设置").clicked() {
                    self.settings.open = true;
                }

                // 帮助菜单
                ui.menu_button("帮助", |ui| {
                    if ui.button("使用说明").clicked() {
                        self.show_help = true;
                        ui.close_menu();
                    }
                    if ui.button("关于").clicked() {
                        self.show_about = true;
                        ui.close_menu();
                    }
                });
            });
        });
    }

    fn save_file(&mut self) {
        if let Some(path) = &self.file_path {
            // 保存时不包含彩蛋结果（保存原始文本）
            if std::fs::write(path, &self.editor_text).is_ok() {
                self.dirty = false;
                self.status = format!("已保存: {}", path.display());
            }
        } else {
            self.save_as();
        }
    }

    fn save_as(&mut self) {
        if let Some(path) = rfd::FileDialog::new()
            .add_filter("Markdown", &["md"])
            .set_file_name("untitled.md")
            .save_file()
        {
            if std::fs::write(&path, &self.editor_text).is_ok() {
                self.file_path = Some(path.clone());
                self.dirty = false;
                self.status = format!("已保存: {}", path.display());
            }
        }
    }

    fn export_html(&self) {
        let html = self.dll.render_html(&self.editor_text);
        if let Some(path) = rfd::FileDialog::new()
            .add_filter("HTML", &["html", "htm"])
            .set_file_name("output.html")
            .save_file()
        {
            let full = format!(
                "<!DOCTYPE html><html><head><meta charset='utf-8'><title>导出</title></head><body>{}</body></html>",
                html
            );
            std::fs::write(&path, &full).ok();
        }
    }

    /// 编辑器面板：split=true 用左分栏（双视图），false 用整面板（单编辑框）
    fn editor_panel(&mut self, ctx: &egui::Context, split: bool) {
        if split {
            egui::SidePanel::left("editor")
                .resizable(true)
                .default_width(ctx.screen_rect().width() * 0.5)
                .show(ctx, |ui| self.editor_contents(ui));
        } else {
            egui::CentralPanel::default().show(ctx, |ui| self.editor_contents(ui));
        }
    }

    fn editor_contents(&mut self, ui: &mut egui::Ui) {
        ui.horizontal(|ui| {
            ui.label(RichText::new("编辑器").strong());
            if self.dirty {
                ui.label(RichText::new("● 未保存").color(Color32::from_rgb(200, 100, 0)));
            }
            if let Some(p) = &self.file_path {
                ui.label(p.display().to_string());
            }
        });
        ui.separator();

        let before = self.editor_text.clone();
        ScrollArea::vertical().show(ui, |ui| {
            ui.add_sized(
                ui.available_size(),
                egui::TextEdit::multiline(&mut self.editor_text)
                    .code_editor()
                    .desired_rows(30)
                    .lock_focus(true),
            );
        });

        if before != self.editor_text {
            self.dirty = true;
            // 同步文档全文到后端文档模型（控制层由此生成渲染 IDP）
            self.dll.new_file();
            self.dll.insert_text(0, &self.editor_text);
        }
    }

    fn preview_panel(&mut self, ctx: &egui::Context) {
        egui::CentralPanel::default().show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.label(RichText::new("预览（分页）").strong());
                ui.separator();
                ui.label(format!("字符数: {}", self.editor_text.chars().count()));
            });
            ui.separator();

            ScrollArea::vertical().show(ui, |ui| {
                self.render_paged_preview(ui);
            });
        });
    }

    /// 按渲染端返回的分页 IDP 渲染每页（白纸 + 页码页脚）
    fn render_paged_preview(&mut self, ui: &mut egui::Ui) {
        let (w, h) = (self.settings.page_width as i32, self.settings.page_height as i32);
        let json = self.dll.render_paged(&self.editor_text, w, h);
        match serde_json::from_str::<serde_json::Value>(&json) {
            Ok(v) => {
                if let Some(pages) = v["pages"].as_array() {
                    if pages.is_empty() {
                        ui.label("（空文档，无分页）");
                        return;
                    }
                    for p in pages {
                        let page_no = p["page"].as_i64().unwrap_or(0);
                        if let Some(elems) = p["elements"].as_array() {
                            let elems_json = serde_json::to_string(elems).unwrap_or_else(|_| "[]".into());
                            if let Ok(list) = serde_json::from_str::<Vec<Element>>(&elems_json) {
                                egui::Frame::none()
                                    .fill(Color32::WHITE)
                                    .stroke(egui::Stroke::new(1.0, Color32::from_rgb(170, 170, 170)))
                                    .show(ui, |ui| {
                                        ui.set_min_width(200.0);
                                        ui.set_max_width(800.0);
                                        for (idx, el) in list.iter().enumerate() {
                                            self.render_element(ui, idx, el);
                                        }
                                    });
                                ui.add_space(4.0);
                                ui.centered_and_justified(|ui| {
                                    ui.label(RichText::new(format!("—— {} ——", page_no)).weak());
                                });
                                ui.add_space(14.0);
                            }
                        }
                    }
                } else {
                    ui.label("分页指令缺少 pages");
                }
            }
            Err(e) => {
                ui.label(format!("分页指令解析失败: {}", e));
            }
        }
    }

    fn render_packet(&mut self, ui: &mut egui::Ui, packet: &UiPacket) {
        for (idx, el) in packet.elements.iter().enumerate() {
            self.render_element(ui, idx, el);
        }
    }

    fn render_element(&mut self, ui: &mut egui::Ui, idx: usize, el: &Element) {
        match el {
            Element::Heading { level, text } => {
                let size = match level {
                    1 => 28.0,
                    2 => 24.0,
                    3 => 20.0,
                    _ => 17.0,
                };
                ui.heading(RichText::new(text).size(size).strong());
                ui.add_space(4.0);
            }
            Element::Paragraph { text, .. } => {
                // 彩蛋：\\> 命令 → 显示执行结果
                if text.starts_with("\\\\>") {
                    let result = self.dll.easter_exec(text);
                    ui.horizontal(|ui| {
                        ui.label(RichText::new("▶ ").color(Color32::from_rgb(0, 128, 0)));
                        ui.label(RichText::new(result).monospace().color(Color32::from_rgb(0, 100, 0)));
                    });
                } else {
                    ui.horizontal_wrapped(|ui| self.render_basic_inline(ui, text));
                }
                ui.add_space(2.0);
            }
            Element::Quote { text } => {
                ui.horizontal(|ui| {
                    ui.label(RichText::new("│").color(Color32::from_rgb(120, 160, 200)));
                    ui.label(text);
                });
                ui.add_space(2.0);
            }
            Element::List { ordered, items } => {
                for (i, it) in items.iter().enumerate() {
                    ui.horizontal(|ui| {
                        ui.label(if *ordered { format!("{}.", i + 1) } else { "•".to_string() });
                        ui.label(it);
                    });
                }
                ui.add_space(2.0);
            }
            Element::Code { text } => {
                egui::Frame::none()
                    .fill(Color32::from_rgb(245, 245, 245))
                    .rounding(4.0)
                    .inner_margin(egui::Margin::symmetric(8.0, 4.0))
                    .show(ui, |ui| {
                        ui.label(RichText::new(text).monospace().color(Color32::from_rgb(40, 40, 40)));
                    });
                ui.add_space(4.0);
            }
            Element::Separator => {
                ui.separator();
                ui.add_space(2.0);
            }
            Element::Image { text, src, width, height } => {
                self.render_image(ui, text, src, *width, *height);
            }
            Element::Button { text, action } => {
                if ui.button(text).clicked() {
                    self.status = format!("控件动作: {}", action);
                }
            }
            Element::Input { placeholder } => {
                ui.add(
                    egui::TextEdit::singleline(&mut self.input_text).hint_text(placeholder.as_str()),
                );
                ui.add_space(2.0);
            }
            Element::Menu { items } => {
                self.render_menu(ui, items);
                ui.add_space(2.0);
            }
            Element::Table { headers, rows } => {
                self.render_table(ui, idx, headers, rows);
                ui.add_space(4.0);
            }
            Element::Dialog { text, style_index } => {
                self.render_dialog(ui, text, *style_index);
                ui.add_space(4.0);
            }
            Element::Link { path, style_index } => {
                let rt = RichText::new(format!("🔗 {} [样式{}]", path, style_index))
                    .color(Color32::from_rgb(21, 101, 192))
                    .underline();
                if ui.link(rt).clicked() {
                    open_path(path);
                }
                ui.add_space(2.0);
            }
        }
    }

    fn render_image(&mut self, ui: &mut egui::Ui, text: &str, src: &str, width: f32, height: f32) {
        let w = if width > 0.0 { width } else { 48.0 };
        let h = if height > 0.0 { height } else { 48.0 };
        egui::Frame::none()
            .fill(Color32::from_rgb(200, 222, 242))
            .rounding(4.0)
            .inner_margin(egui::Margin::same(6.0))
            .show(ui, |ui| {
                ui.set_min_size(egui::vec2(w, h));
                ui.label(RichText::new(format!("[{}]", text)).small().color(Color32::from_rgb(40, 80, 130)));
                if !src.is_empty() {
                    ui.label(RichText::new(src).small().color(Color32::GRAY));
                }
            });
        ui.add_space(4.0);
    }

    fn render_menu(&mut self, ui: &mut egui::Ui, items: &[MenuItem]) {
        ui.horizontal(|ui| {
            for it in items {
                if it.sub.is_empty() {
                    if ui.button(&it.label).clicked() {
                        self.status = format!("菜单: {}", it.label);
                    }
                } else {
                    ui.menu_button(&it.label, |ui| {
                        for sub in &it.sub {
                            if ui.button(sub).clicked() {
                                self.status = format!("菜单 {}/{}", it.label, sub);
                                ui.close_menu();
                            }
                        }
                    });
                }
            }
        });
    }

    fn render_table(&mut self, ui: &mut egui::Ui, idx: usize, headers: &[String], rows: &[Vec<String>]) {
        egui::Grid::new(format!("idp_table_{}", idx)).striped(true).show(ui, |ui| {
            for h in headers {
                ui.label(RichText::new(h).strong());
            }
            ui.end_row();
            for row in rows {
                for cell in row {
                    ui.label(cell);
                }
                ui.end_row();
            }
        });
    }

    fn render_dialog(&mut self, ui: &mut egui::Ui, text: &str, style_index: i32) {
        let (bg, fg) = dialog_colors(style_index);
        egui::Frame::none()
            .fill(bg)
            .rounding(6.0)
            .inner_margin(egui::Margin::symmetric(10.0, 5.0))
            .show(ui, |ui| {
                ui.label(RichText::new(text).color(fg));
            });
    }

    fn render_basic_inline(&self, ui: &mut egui::Ui, text: &str) {
        // 简单处理 **粗体** 和 *斜体*
        let mut remaining = text;
        while !remaining.is_empty() {
            if let Some(start) = remaining.find("**") {
                if start > 0 {
                    ui.label(&remaining[..start]);
                }
                let after = &remaining[start + 2..];
                if let Some(end) = after.find("**") {
                    ui.label(RichText::new(&after[..end]).strong());
                    remaining = &after[end + 2..];
                } else {
                    ui.label("**");
                    remaining = after;
                }
            } else if let Some(start) = remaining.find('*') {
                if start > 0 {
                    ui.label(&remaining[..start]);
                }
                let after = &remaining[start + 1..];
                if let Some(end) = after.find('*') {
                    ui.label(RichText::new(&after[..end]).italics());
                    remaining = &after[end + 1..];
                } else {
                    ui.label("*");
                    remaining = after;
                }
            } else {
                ui.label(remaining);
                break;
            }
        }
    }

    fn status_bar(&mut self, ctx: &egui::Context) {
        egui::TopBottomPanel::bottom("status_bar").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.label(RichText::new(&self.status).small());
                ui.separator();
                ui.label(format!("行数: {}", self.editor_text.lines().count()));
                ui.separator();
                ui.label(format!("语法: {}", self.syntax.names().join(", ")));
                ui.separator();
                ui.label(format!("DLL: v{}", self.dll.version()));
            });
        });
    }

    fn about_window(&mut self, ctx: &egui::Context) {
        let mut open = self.show_about;
        if open {
            let mut close_clicked = false;
            egui::Window::new("关于 MDRenderer")
                .open(&mut open)
                .default_size([360.0, 240.0])
                .resizable(false)
                .show(ctx, |ui| {
                    ui.vertical_centered(|ui| {
                        ui.heading("MDRenderer");
                        ui.add_space(8.0);
                        ui.label(format!("版本 {}", self.dll.version()));
                        ui.add_space(8.0);
                        ui.label("Rust 原生前端 + GraalVM 原生 DLL 后端");
                        ui.label("19个模块化DLL · IDP内存双向栈通信");
                        ui.add_space(12.0);
                        ui.separator();
                        ui.add_space(8.0);
                        ui.label("扩展语法：");
                        ui.label("  [文字] 表情包");
                        ui.label("  :\"文字\"序号 对话框");
                        ui.label("  ≈路径≈序号 超链接");
                        ui.label("  \\\\>命令 彩蛋");
                        ui.add_space(12.0);
                        if ui.button("确定").clicked() {
                            close_clicked = true;
                        }
                    });
                });
            if close_clicked {
                open = false;
            }
            self.show_about = open;
        }
    }

    fn help_window(&mut self, ctx: &egui::Context) {
        let mut open = self.show_help;
        if open {
            egui::Window::new("使用说明")
                .open(&mut open)
                .default_size([420.0, 360.0])
                .resizable(true)
                .show(ctx, |ui| {
                    ScrollArea::vertical().show(ui, |ui| {
                        ui.heading("基础语法");
                        ui.label("# 标题  ## 二级  ### 三级");
                        ui.label("**粗体**  *斜体*  `代码`");
                        ui.label("> 引用  - 列表  --- 分隔线");
                        ui.add_space(8.0);
                        ui.separator();
                        ui.add_space(8.0);
                        ui.heading("扩展语法");
                        ui.label(RichText::new("[文字]").strong());
                        ui.label("  表情包：中括号包裹文字，在设置中关联图片");
                        ui.add_space(4.0);
                        ui.label(RichText::new(":\"文字\"序号").strong());
                        ui.label("  对话框：仿中文说话格式，序号选样式（1-5）");
                        ui.add_space(4.0);
                        ui.label(RichText::new("≈路径≈序号").strong());
                        ui.label("  超链接：约等号包裹路径，序号选样式（1-3）");
                        ui.label("  每个样式含图标区+名称/属性区");
                        ui.add_space(8.0);
                        ui.separator();
                        ui.add_space(8.0);
                        ui.heading("彩蛋命令");
                        ui.label(RichText::new("\\\\>命令").strong());
                        ui.label("  两个反斜杠+右尖括号，输入CMD命令");
                        ui.label("  结果替换显示，但不保存到文件");
                        ui.label("  二次打开可二次执行");
                    });
                });
            self.show_help = open;
        }
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.menu_bar(ctx);
        self.status_bar(ctx);
        match self.view_mode {
            ViewMode::EditOnly => self.editor_panel(ctx, false),
            ViewMode::PreviewOnly => self.preview_panel(ctx),
            ViewMode::Split => {
                self.editor_panel(ctx, true);
                self.preview_panel(ctx);
            }
        }
        self.settings.show(ctx, &self.dll, &self.syntax);
        self.about_window(ctx);
        self.help_window(ctx);
        self.find_window(ctx);
        self.print_dialog(ctx);
    }
}

impl App {
    /// 查找 / 替换 / 插入 对话框（业务逻辑全部在渲染端 DLL，前端只渲染 + 应用结果）
    fn find_window(&mut self, ctx: &egui::Context) {
        let mut open = self.show_find;
        egui::Window::new("查找 / 替换 / 插入")
            .open(&mut open)
            .resizable(true)
            .default_width(430.0)
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.selectable_value(&mut self.find_tab, FindTab::Query, "查询");
                    ui.selectable_value(&mut self.find_tab, FindTab::Replace, "替换");
                    ui.selectable_value(&mut self.find_tab, FindTab::Insert, "插入");
                });
                ui.separator();
                match self.find_tab {
                    FindTab::Query => {
                        ui.horizontal(|ui| {
                            ui.label("查找:");
                            ui.add(egui::TextEdit::singleline(&mut self.find_query).desired_width(280.0));
                        });
                        if ui.button("查询").clicked() {
                            let json = self.dll.render_find(&self.editor_text, &self.find_query);
                            self.find_result = Self::format_query_result(&json);
                        }
                    }
                    FindTab::Replace => {
                        ui.horizontal(|ui| {
                            ui.label("查找:");
                            ui.add(egui::TextEdit::singleline(&mut self.find_query).desired_width(280.0));
                        });
                        ui.horizontal(|ui| {
                            ui.label("替换为:");
                            ui.add(egui::TextEdit::singleline(&mut self.find_replace).desired_width(280.0));
                        });
                        if ui.button("全部替换").clicked() {
                            let json = self.dll.render_replace(
                                &self.editor_text, &self.find_query, &self.find_replace);
                            self.apply_text_result(&json);
                        }
                    }
                    FindTab::Insert => {
                        ui.horizontal(|ui| {
                            ui.label("左边:");
                            ui.add(egui::TextEdit::singleline(&mut self.find_left).desired_width(280.0));
                        });
                        ui.horizontal(|ui| {
                            ui.label("右边:");
                            ui.add(egui::TextEdit::singleline(&mut self.find_right).desired_width(280.0));
                        });
                        ui.horizontal(|ui| {
                            ui.label("插入:");
                            ui.add(egui::TextEdit::singleline(&mut self.find_insert).desired_width(280.0));
                        });
                        if ui.button("全部插入").clicked() {
                            let json = self.dll.render_insert(
                                &self.editor_text, &self.find_left, &self.find_right, &self.find_insert);
                            self.apply_text_result(&json);
                        }
                    }
                }
                ui.separator();
                ui.label(&self.find_result);
            });
        self.show_find = open;
    }

    /// 应用渲染端返回的替换 / 插入结果：写回编辑器 + 同步后端文档模型
    fn apply_text_result(&mut self, json: &str) {
        match serde_json::from_str::<serde_json::Value>(json) {
            Ok(v) => {
                let count = v["count"].as_i64().unwrap_or(0);
                if let Some(t) = v["text"].as_str() {
                    self.editor_text = t.to_string();
                    self.dirty = true;
                    self.dll.new_file();
                    self.dll.insert_text(0, &self.editor_text);
                }
                self.find_result = format!("已完成，共 {} 处。", count);
            }
            Err(e) => self.find_result = format!("结果解析失败: {}", e),
        }
    }

    /// 格式化查询结果：{"count":N,"positions":[...]}
    fn format_query_result(json: &str) -> String {
        match serde_json::from_str::<serde_json::Value>(json) {
            Ok(v) => {
                let count = v["count"].as_i64().unwrap_or(0);
                let pos = v["positions"].as_array().map(|a| a.len()).unwrap_or(0);
                format!("命中 {} 处，匹配位置 {} 个。", count, pos)
            }
            Err(e) => format!("查询结果解析失败: {}", e),
        }
    }

    /// 打印对话框：分页预览 + 直接打印（按渲染端分页 IDP，自动标页码）
    fn print_dialog(&mut self, ctx: &egui::Context) {
        let mut open = self.show_print;
        egui::Window::new("打印")
            .open(&mut open)
            .resizable(true)
            .default_size([640.0, 760.0])
            .show(ctx, |ui| {
                ui.label(format!(
                    "页面 {}px × {}px，共分页，超宽换行、超长换页并自动标页码。",
                    self.settings.page_width, self.settings.page_height
                ));
                ui.separator();
                ScrollArea::vertical().show(ui, |ui| {
                    self.render_paged_preview(ui);
                });
                ui.separator();
                ui.horizontal(|ui| {
                    if ui.button("直接打印（默认打印机）").clicked() {
                        if let Some(path) = self.write_print_html() {
                            self.print_html(&path);
                        }
                    }
                    if ui.button("打印预览（浏览器）").clicked() {
                        if let Some(path) = self.write_print_html() {
                            open_path(&path);
                        }
                    }
                });
            });
        self.show_print = open;
    }

    /// 生成分页 HTML 到临时文件，返回路径
    fn write_print_html(&self) -> Option<String> {
        let html = self.build_print_html();
        let dir = std::env::temp_dir();
        let path = dir.join("mdrenderer_print.html");
        std::fs::write(&path, html).ok()?;
        Some(path.display().to_string())
    }

    /// 生成含 @page 尺寸 + 分页 CSS + 每页内容 + 页码的 HTML
    fn build_print_html(&self) -> String {
        let (w, h) = (self.settings.page_width as i32, self.settings.page_height as i32);
        let json = self.dll.render_paged(&self.editor_text, w, h);
        let mut body = String::new();
        if let Ok(v) = serde_json::from_str::<serde_json::Value>(&json) {
            if let Some(pages) = v["pages"].as_array() {
                for p in pages {
                    let page_no = p["page"].as_i64().unwrap_or(0);
                    let mut content = String::new();
                    if let Some(elems) = p["elements"].as_array() {
                        let ej = serde_json::to_string(elems).unwrap_or_default();
                        if let Ok(list) = serde_json::from_str::<Vec<Element>>(&ej) {
                            for el in &list {
                                content.push_str(&Self::element_html(el));
                            }
                        }
                    }
                    body.push_str(&format!(
                        "<div class=\"page\">{}<div class=\"pagefooter\">— {} —</div></div>",
                        content, page_no
                    ));
                }
            }
        }
        format!(
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>MDRenderer 打印</title><style>\n\
             @page {{ size: {}px {}px; margin: 0; }}\n\
             body {{ font-family: \"Microsoft YaHei\",sans-serif; margin:0; color:#222; }}\n\
             .page {{ width: 100%; min-height: {}px; box-sizing: border-box; padding: 44px 48px 56px; position: relative; page-break-after: always; overflow: hidden; }}\n\
             .page:last-child {{ page-break-after: auto; }}\n\
             .pagefooter {{ position:absolute; left:0; right:0; bottom:12px; text-align:center; font-size:11px; color:#888; }}\n\
             h1,h2,h3,h4,h5,h6 {{ margin: 0 0 10px; }}\n\
             p {{ margin: 0 0 8px; line-height:1.6; }}\n\
             pre {{ background:#f5f5f5; padding:10px; border-radius:4px; white-space:pre-wrap; font-family:Consolas,monospace; font-size:12px; }}\n\
             blockquote {{ border-left:3px solid #999; padding-left:10px; color:#555; margin:0 0 8px; }}\n\
             li {{ line-height:1.6; }}\n\
             hr {{ border:none; border-top:1px solid #ccc; margin:10px 0; }}\n\
             img {{ max-width:100%; }}\n\
             </style></head><body>{}</body></html>",
            w, h, h, body
        )
    }

    /// 把单个渲染元素转为 HTML
    fn element_html(el: &Element) -> String {
        match el {
            Element::Heading { level, text } => {
                let lv = (*level).clamp(1u8, 6u8);
                format!("<h{}>{}</h{}>", lv, Self::esc_html(text), lv)
            }
            Element::Paragraph { text, .. } => format!("<p>{}</p>", Self::esc_html(text)),
            Element::Quote { text } => format!("<blockquote>{}</blockquote>", Self::esc_html(text)),
            Element::List { ordered, items } => {
                let tag = if *ordered { "ol" } else { "ul" };
                let lis: Vec<String> = items.iter().map(|s| format!("<li>{}</li>", Self::esc_html(s))).collect();
                format!("<{}>{}</{}>", tag, lis.join(""), tag)
            }
            Element::Code { text } => format!("<pre>{}</pre>", Self::esc_html(text)),
            Element::Separator => "<hr/>".into(),
            Element::Image { text, .. } => format!("<p><i>{}</i></p>", Self::esc_html(text)),
            Element::Dialog { text, .. } => format!("<p>💬 {}</p>", Self::esc_html(text)),
            Element::Link { path, .. } => format!("<p>🔗 {}</p>", Self::esc_html(path)),
            Element::Button { text, .. } => format!("<p>▣ {}</p>", Self::esc_html(text)),
            Element::Input { placeholder } => format!("<p>▢ {}</p>", Self::esc_html(placeholder)),
            Element::Table { headers, rows } => {
                let mut s = String::from("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">");
                if !headers.is_empty() {
                    s.push_str("<tr>");
                    for hd in headers { s.push_str(&format!("<th>{}</th>", Self::esc_html(hd))); }
                    s.push_str("</tr>");
                }
                for r in rows {
                    s.push_str("<tr>");
                    for c in r { s.push_str(&format!("<td>{}</td>", Self::esc_html(c))); }
                    s.push_str("</tr>");
                }
                s.push_str("</table>");
                s
            }
            Element::Menu { items } => {
                let names: Vec<String> = items.iter().map(|m| Self::esc_html(&m.label)).collect();
                format!("<p>菜单：{}</p>", names.join(" / "))
            }
        }
    }

    /// HTML 转义
    fn esc_html(s: &str) -> String {
        s.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;").replace('"', "&quot;")
    }

    /// Windows 直接打印 HTML 到默认打印机（rundll32 mshtml.dll,PrintHTML）
    fn print_html(&self, path: &str) {
        #[cfg(target_os = "windows")]
        {
            std::process::Command::new("rundll32")
                .args(["mshtml.dll,PrintHTML", path])
                .spawn()
                .ok();
        }
    }
}

fn hex_to_color(hex: &str) -> Option<Color32> {
    let hex = hex.trim_start_matches('#');
    if hex.len() == 6 {
        let r = u8::from_str_radix(&hex[0..2], 16).ok()?;
        let g = u8::from_str_radix(&hex[2..4], 16).ok()?;
        let b = u8::from_str_radix(&hex[4..6], 16).ok()?;
        Some(Color32::from_rgb(r, g, b))
    } else {
        None
    }
}

fn dialog_colors(style_index: i32) -> (Color32, Color32) {
    match style_index {
        1 => (Color32::from_rgb(227, 242, 253), Color32::from_rgb(21, 101, 192)),
        2 => (Color32::from_rgb(255, 235, 238), Color32::from_rgb(194, 24, 91)),
        3 => (Color32::from_rgb(232, 245, 233), Color32::from_rgb(27, 94, 32)),
        4 => (Color32::from_rgb(255, 248, 225), Color32::from_rgb(230, 81, 0)),
        5 => (Color32::from_rgb(243, 229, 245), Color32::from_rgb(123, 31, 162)),
        _ => (Color32::from_rgb(240, 240, 240), Color32::from_rgb(50, 50, 50)),
    }
}

fn open_path(path: &str) {
    #[cfg(target_os = "windows")]
    {
        std::process::Command::new("cmd")
            .args(["/C", "start", "", path])
            .spawn()
            .ok();
    }
}
