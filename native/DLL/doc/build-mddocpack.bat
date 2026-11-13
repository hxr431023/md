@echo off
setlocal
set GRAALVM_HOME=C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1
set PATH=%GRAALVM_HOME%\bin;%PATH%
set JAVA_HOME=%GRAALVM_HOME%
set OUT_DIR=%~dp0
set M=%~dp0..\..\modules
set CP=%M%\mdidp\src;%M%\mdmarkdown\src;%M%\mddoc\src;%M%\mdrender\src;%M%\mdconvert-html\src;%M%\mdconvert-text\src;%M%\mdconvert-txt2md\src;%M%\mdquicksort\src
cd /d "%~dp0"
echo [1/2] Compiling mddocpack (8 modules: mdidp mdmarkdown mddoc mdrender mdconvert-html/text/txt2md mdquicksort)...
javac -encoding UTF-8 -cp "%CP%" ^
    "%M%\mdidp\src\MDIdp.java" ^
    "%M%\mdmarkdown\src\MDMarkdown.java" ^
    "%M%\mddoc\src\MDDoc.java" ^
    "%M%\mdrender\src\MDRender.java" ^
    "%M%\mdconvert-html\src\MDConvertHtml.java" ^
    "%M%\mdconvert-text\src\MDConvertText.java" ^
    "%M%\mdconvert-txt2md\src\MDConvertTxt2Md.java" ^
    "%M%\mdquicksort\src\MDQuickSort.java"
if errorlevel 1 ( echo [ERROR] compile failed & exit /b 1 )
echo [2/2] native-image --shared...
call native-image --shared ^
    -cp "%CP%;." ^
    -H:+UnlockExperimentalVMOptions ^
    -H:Name=docpack ^
    -H:+ReportExceptionStackTraces
if errorlevel 1 ( echo [ERROR] native-image failed & exit /b 1 )
move /y docpack.dll "%OUT_DIR%\" >nul 2>&1
move /y docpack.h "%OUT_DIR%\" >nul 2>&1
del /q *.class >nul 2>&1
echo [OK] docpack.dll -> %OUT_DIR%
endlocal
