//! 通用 IDP（接口数据包）数据结构 —— 由 Java 渲染端 render_ui 生成
//! 契约：{"view":"ui","layout":"flow","elements":[{"kind":...,"text":...,...}]}
//! 前端渲染器按 kind 渲染内容块（heading/paragraph/quote/list/code/separator）
//! 与交互控件（button/input/menu/image/table/dialog/link）。

use serde::Deserialize;

#[derive(Debug, Deserialize)]
pub struct UiPacket {
    #[serde(default)]
    pub view: String,
    #[serde(default)]
    pub layout: String,
    #[serde(default)]
    pub elements: Vec<Element>,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "kind")]
pub enum Element {
    #[serde(rename = "heading")]
    Heading { #[serde(default)] level: u8, #[serde(default)] text: String },
    #[serde(rename = "paragraph")]
    Paragraph {
        #[serde(default)] text: String,
        #[serde(default)] inline: Vec<InlineSpan>,
    },
    #[serde(rename = "quote")]
    Quote { #[serde(default)] text: String },
    #[serde(rename = "list")]
    List {
        #[serde(default)] ordered: bool,
        #[serde(default)] items: Vec<String>,
    },
    #[serde(rename = "code")]
    Code { #[serde(default)] text: String },
    #[serde(rename = "separator")]
    Separator,
    #[serde(rename = "image")]
    Image {
        #[serde(default)] text: String,
        #[serde(default)] src: String,
        #[serde(default)] width: f32,
        #[serde(default)] height: f32,
    },
    #[serde(rename = "button")]
    Button { #[serde(default)] text: String, #[serde(default)] action: String },
    #[serde(rename = "input")]
    Input { #[serde(default)] placeholder: String },
    #[serde(rename = "menu")]
    Menu { #[serde(default)] items: Vec<MenuItem> },
    #[serde(rename = "table")]
    Table {
        #[serde(default)] headers: Vec<String>,
        #[serde(default)] rows: Vec<Vec<String>>,
    },
    #[serde(rename = "dialog")]
    Dialog { #[serde(default)] text: String, #[serde(default)] style_index: i32 },
    #[serde(rename = "link")]
    Link { #[serde(default)] path: String, #[serde(default)] style_index: i32 },
}

#[derive(Debug, Clone, Deserialize)]
pub struct InlineSpan {
    pub s: usize,
    pub e: usize,
    #[serde(default)]
    pub st: String,
}

#[derive(Debug, Deserialize)]
pub struct MenuItem {
    #[serde(default)]
    pub label: String,
    #[serde(default)]
    pub sub: Vec<String>,
}
