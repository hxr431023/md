@echo off
setlocal
set GRAALVM_HOME=C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1
set PATH=%GRAALVM_HOME%\bin;%PATH%
set JAVA_HOME=%GRAALVM_HOME%
set OUT_DIR=%~dp0..\..\DLL\doc
cd /d "%~dp0src"
javac -encoding UTF-8 -cp "%~dp0..\mdidp\src" *.java "%~dp0..\mdidp\src\MDIdp.java"
if errorlevel 1 ( echo [ERROR] Compile failed & exit /b 1 )
call native-image --shared -cp .;"%~dp0..\mdidp\src" -H:+UnlockExperimentalVMOptions -H:Name=quicksort -H:+ReportExceptionStackTraces
if errorlevel 1 ( echo [ERROR] native-image failed & exit /b 1 )
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
move /y quicksort.dll "%OUT_DIR%\" >nul 2>&1
move /y quicksort.h "%OUT_DIR%\" >nul 2>&1
del /q *.class >nul 2>&1
echo [DONE] quicksort.dll -> DLL\doc\
endlocal
