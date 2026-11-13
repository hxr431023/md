# -*- coding: utf-8 -*-
"""按新架构移除模块的 IDP 包装导出，只保留直接 C 函数接口。
保留 IDP：mdrender（渲染）、mdmarkdown（HTML 渲染）、mdcmd（CMD）、mdidp、mdcontroller（前端栈）。
"""
import os, re, shutil

ROOT = r"C:\wpjc\md\native\modules"
BACKUP = r"C:\wpjc\md\native\archive\idp-removed-20261108"
os.makedirs(BACKUP, exist_ok=True)

FILES = [
    ("mdcompress", "MDCompress.java"),
    ("mdconfig", "MDConfig.java"),
    ("mdconvert-html", "MDConvertHtml.java"),
    ("mdconvert-text", "MDConvertText.java"),
    ("mdconvert-txt2md", "MDConvertTxt2Md.java"),
    ("mddoc", "MDDoc.java"),
    ("mdfile", "MDFile.java"),
    ("mdlerf", "MDLerf.java"),
    ("mdlog", "MDLog.java"),
    ("mdquicksort", "MDQuickSort.java"),
    ("mdsingle", "MDSingle.java"),
    ("mdthreadstat", "MDThreadStat.java"),
    ("mdtmmf", "MDTmmf.java"),
    ("mdvisualize", "MDVisualize.java"),
]

SUFFIXES = ["_write_idp", "_to_idp", "_read_idp"]


def remove_method(text, name):
    """删除从 @CEntryPoint(name = "name") 到方法结束的整段（含注解）。"""
    start = text.find('@CEntryPoint(name = "%s")' % name)
    if start < 0:
        return text, False
    # 方法结束：注解后第一个 4 空格缩进的独立右括号
    end_marker = "\n    }\n"
    end = text.find(end_marker, start)
    if end < 0:
        return text, False
    end += len(end_marker)
    return text[:start] + text[end:], True


removed = {}
for mod, fname in FILES:
    path = os.path.join(ROOT, mod, "src", fname)
    bak = os.path.join(BACKUP, os.path.basename(path).replace(".java", "") + "-" + mod + ".java.bak")
    shutil.copy2(path, bak)
    text = open(path, "r", encoding="utf-8").read()
    n = 0
    for suf in SUFFIXES:
        # 逐个尝试：名字后缀匹配（如 compress_write_idp）
        m = re.search(r'@CEntryPoint\(name = "([A-Za-z0-9_]+%s)"\)' % re.escape(suf), text)
        if m:
            text, ok = remove_method(text, m.group(1))
            n += 1 if ok else 0
    # 清理遗留的 IDP 注释块（连续 // 分隔线 + IDP 说明行）
    text = re.sub(
        r'\n    // ={10,}\n    //  IDP 输出[^\n]*\n    // ={10,}\n',
        "\n", text)
    open(path, "w", encoding="utf-8", newline="\n").write(text)
    removed[mod] = n
    print(f"{mod}: removed {n} IDP exports")

print("\n== 校验：仍残留的 IDP 导出 ==")
for mod, fname in FILES:
    path = os.path.join(ROOT, mod, "src", fname)
    left = re.findall(r'@CEntryPoint\(name = "([^"]*_write_idp|[^"]*_to_idp|[^"]*_read_idp)"\)',
                      open(path, "r", encoding="utf-8").read())
    print(f"  {mod}: {left if left else 'OK'}")
