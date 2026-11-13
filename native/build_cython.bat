@echo off
REM ============================================================
REM  Cython compile md_editor.py -> md_editor.pyd (native C extension)
REM ============================================================
setlocal

call "C:\vs\vs\VC\Auxiliary\Build\vcvarsall.bat" amd64 >nul 2>&1
if errorlevel 1 ( echo [ERROR] vcvarsall failed & exit /b 1 )
echo [OK] MSVC %VCToolsVersion%

set PYTHON=C:\wpjc\py311\python.exe
cd /d "%~dp0"

echo [1/1] Cythonize + Compile md_editor.py -> md_editor.pyd ...
%PYTHON% setup.py build_ext --inplace 2>&1
if errorlevel 1 ( echo [ERROR] Compile failed & exit /b 1 )

echo.
if exist md_editor.pyd (
    echo [DONE] md_editor.pyd
    for %%F in (md_editor.pyd) do echo   Size: %%~zF bytes
) else (
    echo [ERROR] md_editor.pyd not found
)
endlocal
