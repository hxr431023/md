//! 表情包扩展：[文字] → 渲染为表情包图片

use super::ParsedSegment;
use crate::syntax::SyntaxDef;
use regex::Regex;

pub fn parse(segments: &[ParsedSegment], syntax: &SyntaxDef) -> Vec<ParsedSegment> {
    let re = Regex::new(r"\[([^\[\]]+)\]").unwrap();
    let mut result = Vec::new();

    for seg in segments {
        match seg {
            ParsedSegment::Text(text) => {
                let mut last = 0;
                for cap in re.captures_iter(text) {
                    let m = cap.get(0).unwrap();
                    if m.start() > last {
                        result.push(ParsedSegment::Text(text[last..m.start()].to_string()));
                    }
                    let emoji_text = cap[1].to_string();
                    let image_path = lookup_image(&emoji_text, syntax);
                    result.push(ParsedSegment::Emoji {
                        text: emoji_text,
                        image_path,
                    });
                    last = m.end();
                }
                if last < text.len() {
                    result.push(ParsedSegment::Text(text[last..].to_string()));
                }
            }
            other => result.push(other.clone()),
        }
    }

    result
}

fn lookup_image(text: &str, syntax: &SyntaxDef) -> Option<String> {
    // 先查预设
    let preset_key = format!("preset_{}", text);
    if let Some(path) = syntax.get(&preset_key) {
        return Some(path.to_string());
    }
    None
}
