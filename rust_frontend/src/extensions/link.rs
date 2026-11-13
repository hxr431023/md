//! 超链接扩展：≈路径≈序号 → 渲染为超链接（图标区+名称区）

use super::ParsedSegment;
use crate::syntax::SyntaxDef;
use regex::Regex;

pub fn parse(segments: &[ParsedSegment], syntax: &SyntaxDef) -> Vec<ParsedSegment> {
    let re = Regex::new(r"≈([^≈]+)≈(\d+)").unwrap();
    let styles = syntax.styles();
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
                    let link_path = cap[1].to_string();
                    let style_index = cap[2].parse::<usize>().unwrap_or(1);
                    let style = styles.get(&style_index).cloned().unwrap_or_default();
                    result.push(ParsedSegment::Link {
                        path: link_path,
                        style_index,
                        style,
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
