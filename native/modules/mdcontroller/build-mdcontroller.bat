@echo off
REM ============================================================
REM  controller.dll master build (all 17 dependency modules)
REM  categories: core(6) + doc(6) + tools(2) + app(3=cmd,threadstat,visualize)
REM  output: DLL\app\controller.dll (only DLL Python frontend needs)
REM ============================================================

setlocal
set GRAALVM_HOME=C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1
set PATH=%GRAALVM_HOME%\bin;%PATH%
set JAVA_HOME=%GRAALVM_HOME%
set OUT_DIR=%~dp0..\..\DLL\app

set M=%~dp0..
set CP="%M%\mdidp\src;%M%\mdlog\src;%M%\mdcompress\src;%M%\mdconfig\src;%M%\mdfile\src;%M%\mdsingle\src;%M%\mdmarkdown\src;%M%\mddoc\src;%M%\mdrender\src;%M%\mdconvert-html\src;%M%\mdconvert-text\src;%M%\mdconvert-txt2md\src;%M%\mdtmmf\src;%M%\mdlerf\src;%M%\mdcmd\src;%M%\mdthreadstat\src;%M%\mdvisualize\src;%M%\mdquicksort\src;%~dp0src"

cd /d "%~dp0src"

echo [1/2] Compiling mdcontroller (18 modules)...
javac -encoding UTF-8 -cp %CP% ^
    MDController.java ^
    "%M%\mdidp\src\MDIdp.java" ^
    "%M%\mdlog\src\MDLog.java" ^
    "%M%\mdcompress\src\MDCompress.java" ^
    "%M%\mdconfig\src\MDConfig.java" ^
    "%M%\mdfile\src\MDFile.java" ^
    "%M%\mdsingle\src\MDSingle.java" ^
    "%M%\mdmarkdown\src\MDMarkdown.java" ^
    "%M%\mddoc\src\MDDoc.java" ^
    "%M%\mdrender\src\MDRender.java" ^
    "%M%\mdconvert-html\src\MDConvertHtml.java" ^
    "%M%\mdconvert-text\src\MDConvertText.java" ^
    "%M%\mdconvert-txt2md\src\MDConvertTxt2Md.java" ^
    "%M%\mdtmmf\src\MDTmmf.java" ^
    "%M%\mdlerf\src\MDLerf.java" ^
    "%M%\mdcmd\src\MDCmd.java" ^
    "%M%\mdthreadstat\src\MDThreadStat.java" ^
    "%M%\mdvisualize\src\MDVisualize.java" ^
    "%M%\mdquicksort\src\MDQuickSort.java"
if errorlevel 1 ( echo [ERROR] Compile failed & exit /b 1 )

echo [2/2] native-image --shared (19 modules combined)...
call native-image --shared ^
    -cp %CP% ^
    -H:+UnlockExperimentalVMOptions ^
    -H:Name=controller ^
    -H:+ReportExceptionStackTraces ^
    --initialize-at-build-time=MDController
if errorlevel 1 ( echo [ERROR] native-image failed & exit /b 1 )

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
move /y controller.dll "%OUT_DIR%\" >nul 2>&1
move /y controller.h "%OUT_DIR%\" >nul 2>&1
del /q *.class >nul 2>&1

echo.
echo [DONE] controller.dll -> DLL\app\ (19 modules combined)
echo   Python only loads this one DLL, calls md_app_* functions.
endlocal
