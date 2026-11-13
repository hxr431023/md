//! 扩展语法加载器：读取 .tmmf 文件，解析为语法定义
//! TMMF 格式：每行 key=value，# 开头为注释

use std::collections::HashMap;
use std::path::{Path, PathBuf};

/// 一个语法定义（从 .tmmf 文件解析）
#[derive(Debug, Clone)]
pub struct SyntaxDef {
    pub name: String,
    pub version: String,
    pub description: String,
    pub trigger_start: Option<String>,
    pub trigger_end: Option<String>,
    pub trigger_pattern: Option<String>,
    pub syntax_type: String, // inline / block
    pub style_config_key: String,
    pub entries: HashMap<String, String>, // 所有 key=value
    pub file_path: PathBuf,
}

impl SyntaxDef {
    pub fn from_file(path: &Path) -> Result<Self, String> {
        let content = std::fs::read_to_string(path)
            .map_err(|e| format!("读取 {} 失败: {}", path.display(), e))?;
        Self::from_str(&content, path)
    }

    pub fn from_str(content: &str, path: &Path) -> Result<Self, String> {
        let mut entries = HashMap::new();
        for line in content.lines() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            if let Some(idx) = line.find('=') {
                let key = line[..idx].trim().to_string();
                let value = line[idx + 1..].trim().to_string();
                entries.insert(key, value);
            }
        }

        let name = entries.get("name").cloned().unwrap_or_else(|| {
            path.file_stem()
                .map(|s| s.to_string_lossy().to_string())
                .unwrap_or("unknown".to_string())
        });

        Ok(Self {
            name: name.clone(),
            version: entries.get("version").cloned().unwrap_or_default(),
            description: entries.get("description").cloned().unwrap_or_default(),
            trigger_start: entries.get("trigger_start").cloned(),
            trigger_end: entries.get("trigger_end").cloned(),
            trigger_pattern: entries.get("trigger_pattern").cloned(),
            syntax_type: entries.get("type").cloned().unwrap_or_else(|| "inline".to_string()),
            style_config_key: entries
                .get("style_config_key")
                .cloned()
                .unwrap_or_else(|| format!("{}_styles", name)),
            entries,
            file_path: path.to_path_buf(),
        })
    }

    /// 获取配置值
    pub fn get(&self, key: &str) -> Option<&str> {
        self.entries.get(key).map(|s| s.as_str())
    }

    /// 获取预设关联（preset_ 开头的键）
    pub fn presets(&self) -> Vec<(String, String)> {
        self.entries
            .iter()
            .filter(|(k, _)| k.starts_with("preset_"))
            .map(|(k, v)| (k["preset_".len()..].to_string(), v.clone()))
            .collect()
    }

    /// 获取样式定义（style_N_ 开头的键，按序号分组）
    pub fn styles(&self) -> HashMap<usize, HashMap<String, String>> {
        let mut styles: HashMap<usize, HashMap<String, String>> = HashMap::new();
        for (k, v) in &self.entries {
            if let Some(rest) = k.strip_prefix("style_") {
                if let Some(idx) = rest.find('_') {
                    if let Ok(num) = rest[..idx].parse::<usize>() {
                        let prop = rest[idx + 1..].to_string();
                        styles.entry(num).or_default().insert(prop, v.clone());
                    }
                }
            }
        }
        styles
    }
}

/// 语法管理器：加载所有 .tmmf 文件
pub struct SyntaxManager {
    pub base: Option<SyntaxDef>,
    pub extensions: Vec<SyntaxDef>,
    pub syntax_dir: PathBuf,
}

impl SyntaxManager {
    pub fn load(syntax_dir: &Path) -> Result<Self, String> {
        let mut base = None;
        let mut extensions = Vec::new();

        if syntax_dir.exists() {
            for entry in std::fs::read_dir(syntax_dir)
                .map_err(|e| format!("读取语法目录失败: {}", e))?
            {
                let entry = entry.map_err(|e| format!("读取目录项失败: {}", e))?;
                let path = entry.path();
                if path.extension().map(|e| e == "tmmf").unwrap_or(false) {
                    let def = SyntaxDef::from_file(&path)?;
                    if def.name == "base" {
                        base = Some(def);
                    } else {
                        extensions.push(def);
                    }
                }
            }
        }

        extensions.sort_by(|a, b| a.name.cmp(&b.name));

        Ok(Self {
            base,
            extensions,
            syntax_dir: syntax_dir.to_path_buf(),
        })
    }

    /// 按名称查找扩展语法
    pub fn find(&self, name: &str) -> Option<&SyntaxDef> {
        self.extensions.iter().find(|e| e.name == name)
    }

    /// 所有已加载的语法名称
    pub fn names(&self) -> Vec<String> {
        let mut names = Vec::new();
        if self.base.is_some() {
            names.push("base".to_string());
        }
        for e in &self.extensions {
            names.push(e.name.clone());
        }
        names
    }
}
