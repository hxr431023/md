@echo off
setlocal
set GRAALVM_HOME=C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1
set PATH=%GRAALVM_HOME%\bin;%PATH%
set JAVA_HOME=%GRAALVM_HOME%
set OUT_DIR=%~dp0..\..\DLL\doc
cd /d "%~dp0src"
javac -encoding UTF-8 -cp ".;%~dp0..\mdidp\src;%~dp0..\mdmarkdown\src" *.java "%~dp0..\mdmarkdown\src\MDMarkdown.java"
if errorlevel 1 ( echo [ERROR] compile failed & exit /b 1 )
call native-image --shared -cp .;"%~dp0..\mdidp\src;%~dp0..\mdmarkdown\src" -H:+UnlockExperimentalVMOptions -H:Name=render -H:+ReportExceptionStackTraces
if errorlevel 1 ( echo [ERROR] native-image failed & exit /b 1 )
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
move /y render.dll "%OUT_DIR%\" >nul 2>&1
move /y render.h "%OUT_DIR%\" >nul 2>&1
del /q *.class >nul 2>&1
echo [OK] render.dll -> %OUT_DIR%
endlocal
