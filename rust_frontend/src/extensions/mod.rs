//! 扩展语法解析模块
//! 每个扩展语法负责解析自己的触发模式，返回渲染指令

pub mod emoji;
pub mod dialog;
pub mod link;

use crate::syntax::SyntaxDef;
use std::collections::HashMap;

/// 解析结果：一段文本中的一个语法片段
#[derive(Debug, Clone)]
pub enum ParsedSegment {
    /// 普通文本
    Text(String),
    /// 表情包 [文字]
    Emoji { text: String, image_path: Option<String> },
    /// 对话框 :"文字"序号
    Dialog { text: String, style_index: usize, style: std::collections::HashMap<String, String> },
    /// 超链接 ≈路径≈序号
    Link { path: String, style_index: usize, style: std::collections::HashMap<String, String> },
}

/// 解析一行文本，应用所有扩展语法
pub fn parse_line(line: &str, syntaxes: &[&SyntaxDef]) -> Vec<ParsedSegment> {
    let mut segments = vec![ParsedSegment::Text(line.to_string())];

    for syntax in syntaxes {
        match syntax.name.as_str() {
            "emoji" => segments = emoji::parse(&segments, syntax),
            "dialog" => segments = dialog::parse(&segments, syntax),
            "link" => segments = link::parse(&segments, syntax),
            _ => {}
        }
    }

    segments
}
