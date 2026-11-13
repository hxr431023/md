# -*- coding: utf-8 -*-
"""Cython 编译脚本：将 md_editor.py 编译为原生 C 扩展 (.pyd)"""
from setuptools import setup, Extension
from Cython.Build import cythonize

extensions = [
    Extension(
        "md_editor",
        ["md_editor.py"],
        extra_compile_args=["/O2"],  # MSVC 优化
    )
]

setup(
    name="MDRenderer",
    ext_modules=cythonize(
        extensions,
        language_level="3",
        compiler_directives={
            "embedsignature": True,
            "boundscheck": False,
            "wraparound": False,
        },
    ),
    zip_safe=False,
)
