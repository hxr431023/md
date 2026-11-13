//! 设置对话框：表情包关联、对话框样式、超链接样式、线程模式等

use crate::ffi::MDDll;
use crate::syntax::SyntaxManager;
use egui::{Color32, RichText, ScrollArea};
use std::collections::HashMap;

pub struct SettingsDialog {
    pub open: bool,
    pub tab: SettingsTab,
    // 表情包关联
    pub emoji_map: HashMap<String, String>,
    pub emoji_new_key: String,
    pub emoji_new_path: String,
    // 对话框样式
    pub dialog_styles: HashMap<usize, DialogStyle>,
    // 超链接样式
    pub link_styles: HashMap<usize, LinkStyle>,
    // 线程模式
    pub thread_mode: String,
    // 性能监控
    pub show_perf_monitor: bool,
    // 分页渲染页面尺寸（自定义宽高，逻辑像素）
    pub page_width: u32,
    pub page_height: u32,
}

#[derive(Debug, Clone)]
pub struct DialogStyle {
    pub name: String,
    pub bg: String,
    pub border: String,
    pub text_color: String,
    pub radius: String,
    pub align: String,
}

#[derive(Debug, Clone)]
pub struct LinkStyle {
    pub name: String,
    pub icon: String,
    pub text_color: String,
    pub underline: bool,
    pub show_url: bool,
}

impl Default for SettingsDialog {
    fn default() -> Self {
        let mut dialog_styles = HashMap::new();
        dialog_styles.insert(1, DialogStyle {
            name: "气泡-左".into(), bg: "#e3f2fd".into(), border: "#90caf9".into(),
            text_color: "#1565c0".into(), radius: "12px".into(), align: "left".into(),
        });
        dialog_styles.insert(2, DialogStyle {
            name: "气泡-右".into(), bg: "#c8e6c9".into(), border: "#81c784".into(),
            text_color: "#2e7d32".into(), radius: "12px".into(), align: "right".into(),
        });
        dialog_styles.insert(3, DialogStyle {
            name: "思考框".into(), bg: "#fff3e0".into(), border: "#ffb74d".into(),
            text_color: "#e65100".into(), radius: "4px".into(), align: "center".into(),
        });

        let mut link_styles = HashMap::new();
        link_styles.insert(1, LinkStyle {
            name: "网页链接".into(), icon: "🌐".into(), text_color: "#1565c0".into(),
            underline: true, show_url: true,
        });
        link_styles.insert(2, LinkStyle {
            name: "本地文件".into(), icon: "📄".into(), text_color: "#2e7d32".into(),
            underline: false, show_url: true,
        });
        link_styles.insert(3, LinkStyle {
            name: "程序启动".into(), icon: "⚡".into(), text_color: "#e65100".into(),
            underline: false, show_url: true,
        });

        Self {
            open: false,
            tab: SettingsTab::General,
            emoji_map: HashMap::new(),
            emoji_new_key: String::new(),
            emoji_new_path: String::new(),
            dialog_styles,
            link_styles,
            thread_mode: "folder".into(),
            show_perf_monitor: false,
            page_width: 794,
            page_height: 1123,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SettingsTab {
    General,
    Emoji,
    Dialog,
    Link,
    Performance,
}

impl SettingsDialog {
    pub fn show(&mut self, ctx: &egui::Context, dll: &MDDll, _syntax: &SyntaxManager) {
        let mut open = self.open;
        if !open {
            return;
        }

        egui::Window::new("设置")
            .open(&mut open)
            .default_size([520.0, 480.0])
            .resizable(true)
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.selectable_value(&mut self.tab, SettingsTab::General, "通用");
                    ui.selectable_value(&mut self.tab, SettingsTab::Emoji, "表情包");
                    ui.selectable_value(&mut self.tab, SettingsTab::Dialog, "对话框");
                    ui.selectable_value(&mut self.tab, SettingsTab::Link, "超链接");
                    ui.selectable_value(&mut self.tab, SettingsTab::Performance, "性能监控");
                });
                ui.separator();

                ScrollArea::vertical().show(ui, |ui| match self.tab {
                    SettingsTab::General => self.ui_general(ui, dll),
                    SettingsTab::Emoji => self.ui_emoji(ui),
                    SettingsTab::Dialog => self.ui_dialog(ui),
                    SettingsTab::Link => self.ui_link(ui),
                    SettingsTab::Performance => self.ui_performance(ui, dll),
                });
            });
        self.open = open;
    }

    fn ui_general(&mut self, ui: &mut egui::Ui, dll: &MDDll) {
        ui.heading("通用设置");
        ui.add_space(8.0);

        ui.label("线程模式：");
        egui::ComboBox::from_id_salt("thread_mode")
            .selected_text(match self.thread_mode.as_str() {
                "folder" => "每分类一个线程（默认）",
                "single" => "单线程",
                "per_dll" => "每个DLL一个线程",
                _ => &self.thread_mode,
            })
            .show_ui(ui, |ui| {
                ui.selectable_value(&mut self.thread_mode, "folder".into(), "每分类一个线程（默认）");
                ui.selectable_value(&mut self.thread_mode, "single".into(), "单线程");
                ui.selectable_value(&mut self.thread_mode, "per_dll".into(), "每个DLL一个线程");
            });
        if ui.button("应用线程模式").clicked() {
            dll.set_thread_mode(&self.thread_mode);
        }

        ui.add_space(16.0);
        ui.separator();
        ui.add_space(8.0);

        ui.heading("日志管理");
        if ui.button(RichText::new("清除所有日志").color(Color32::RED)).clicked() {
            let n = dll.clear_logs();
            println!("已清除 {} 个日志文件", n);
        }

        ui.add_space(16.0);
        ui.separator();
        ui.add_space(8.0);
        ui.heading("分页渲染（自定义页面尺寸）");
        ui.horizontal(|ui| {
            ui.label("页宽:");
            let mut w = self.page_width as u32;
            ui.add(egui::DragValue::new(&mut w).range(200..=4000));
            self.page_width = w;
            ui.label("px");
        });
        ui.horizontal(|ui| {
            ui.label("页高:");
            let mut h = self.page_height as u32;
            ui.add(egui::DragValue::new(&mut h).range(200..=6000));
            self.page_height = h;
            ui.label("px");
        });
        ui.label("预览与打印均按此宽高分页，超宽自动换行、超长自动换页并标页码。");

        ui.add_space(16.0);
        ui.separator();
        ui.add_space(8.0);
        ui.heading("关于");
        ui.label(format!("MDRenderer v{}", dll.version()));
        ui.label("Rust 前端 + GraalVM 原生 DLL 后端");
    }

    fn ui_emoji(&mut self, ui: &mut egui::Ui) {
        ui.heading("表情包关联");
        ui.label("语法：[文字]  →  渲染为表情包图片");
        ui.add_space(8.0);

        // 新增关联
        ui.horizontal(|ui| {
            ui.label("文字：");
            ui.text_edit_singleline(&mut self.emoji_new_key);
            ui.label("图片路径：");
            ui.text_edit_singleline(&mut self.emoji_new_path);
            if ui.button("添加").clicked() && !self.emoji_new_key.is_empty() {
                self.emoji_map.insert(
                    self.emoji_new_key.clone(),
                    self.emoji_new_path.clone(),
                );
                self.emoji_new_key.clear();
                self.emoji_new_path.clear();
            }
        });

        ui.add_space(8.0);
        ui.separator();
        ui.add_space(8.0);

        // 已有列表
        let mut to_remove = None;
        for (key, path) in &self.emoji_map {
            ui.horizontal(|ui| {
                ui.label(format!("[{}]", key));
                ui.label("→");
                ui.label(path);
                if ui.small_button("删除").clicked() {
                    to_remove = Some(key.clone());
                }
            });
        }
        if let Some(k) = to_remove {
            self.emoji_map.remove(&k);
        }

        if self.emoji_map.is_empty() {
            ui.label(RichText::new("（暂无关联，在上方添加）").color(Color32::GRAY));
        }
    }

    fn ui_dialog(&mut self, ui: &mut egui::Ui) {
        ui.heading("对话框样式");
        ui.label("语法：:\"文字\"序号  →  序号对应样式");
        ui.add_space(8.0);

        let mut indices: Vec<usize> = self.dialog_styles.keys().cloned().collect();
        indices.sort();

        for idx in indices {
            if let Some(style) = self.dialog_styles.get_mut(&idx) {
                ui.group(|ui| {
                    ui.horizontal(|ui| {
                        ui.label(format!("样式 {}：", idx));
                        ui.text_edit_singleline(&mut style.name);
                    });
                    ui.horizontal(|ui| {
                        ui.label("背景色：");
                        ui.text_edit_singleline(&mut style.bg);
                        ui.label("边框：");
                        ui.text_edit_singleline(&mut style.border);
                    });
                    ui.horizontal(|ui| {
                        ui.label("文字色：");
                        ui.text_edit_singleline(&mut style.text_color);
                        ui.label("圆角：");
                        ui.text_edit_singleline(&mut style.radius);
                        ui.label("对齐：");
                        ui.text_edit_singleline(&mut style.align);
                    });
                });
                ui.add_space(4.0);
            }
        }
    }

    fn ui_link(&mut self, ui: &mut egui::Ui) {
        ui.heading("超链接样式");
        ui.label("语法：≈路径≈序号  →  每个样式含图标区+名称区");
        ui.add_space(8.0);

        let mut indices: Vec<usize> = self.link_styles.keys().cloned().collect();
        indices.sort();

        for idx in indices {
            if let Some(style) = self.link_styles.get_mut(&idx) {
                ui.group(|ui| {
                    ui.horizontal(|ui| {
                        ui.label(format!("样式 {}：", idx));
                        ui.text_edit_singleline(&mut style.name);
                    });
                    ui.horizontal(|ui| {
                        ui.label("图标：");
                        ui.text_edit_singleline(&mut style.icon);
                        ui.label("文字色：");
                        ui.text_edit_singleline(&mut style.text_color);
                    });
                    ui.horizontal(|ui| {
                        ui.checkbox(&mut style.underline, "下划线");
                        ui.checkbox(&mut style.show_url, "显示路径");
                    });
                });
                ui.add_space(4.0);
            }
        }
    }

    fn ui_performance(&mut self, ui: &mut egui::Ui, dll: &MDDll) {
        ui.heading("性能监控");
        ui.checkbox(&mut self.show_perf_monitor, "启用实时性能监控");
        ui.add_space(8.0);

        if ui.button("刷新统计").clicked() {
            dll.threadstat_sample();
        }

        ui.add_space(8.0);
        let stats = dll.threadstat_json();
        ui.label("当前线程统计：");
        ui.add_space(4.0);
        egui::Frame::group(ui.style()).show(ui, |ui| {
            ui.monospace(&stats);
        });

        ui.add_space(8.0);
        if ui.button("导出可视化HTML").clicked() {
            let html = dll.visualize_html(&stats);
            let path = std::env::temp_dir().join("md_perf_report.html");
            std::fs::write(&path, &html).ok();
            println!("已导出: {}", path.display());
        }
    }
}
