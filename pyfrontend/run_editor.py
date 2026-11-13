# -*- coding: utf-8 -*-
"""MDRenderer 启动器：加载 Cython 编译的 md_editor.pyd 并运行"""
import sys
import os

# 确保同目录的 .pyd 和 DLL 可被找到
os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import md_editor
md_editor.main()
