@echo off
setlocal
set GRAALVM_HOME=C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1
set PATH=%GRAALVM_HOME%\bin;%PATH%
set JAVA_HOME=%GRAALVM_HOME%
set OUT_DIR=%~dp0..\..\DLL\core
cd /d "%~dp0src"
rem log.dll = self-compress log main: MDLog.java + MDCompress.java (+ MDIdp)
javac -encoding UTF-8 -cp ".;%~dp0..\mdidp\src;%~dp0..\mdcompress\src" *.java "%~dp0..\mdcompress\src\MDCompress.java"
if errorlevel 1 ( echo [ERROR] compile failed & exit /b 1 )
call native-image --shared -cp .;"%~dp0..\mdidp\src;%~dp0..\mdcompress\src" -H:+UnlockExperimentalVMOptions -H:Name=log -H:+ReportExceptionStackTraces
if errorlevel 1 ( echo [ERROR] native-image failed & exit /b 1 )
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
move /y log.dll "%OUT_DIR%\" >nul 2>&1
move /y log.h "%OUT_DIR%\" >nul 2>&1
del /q *.class >nul 2>&1
echo [OK] log.dll -> %OUT_DIR%
endlocal
