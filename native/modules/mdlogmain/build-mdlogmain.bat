@echo off
setlocal
set GRAALVM_HOME=C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1
set PATH=%GRAALVM_HOME%\bin;%PATH%
set JAVA_HOME=%GRAALVM_HOME%
set OUT_DIR=%~dp0..\..\DLL\core
set DEP=%~dp0..\mdlog\src;%~dp0..\mdcompress\src;%~dp0..\mdidp\src
cd /d "%~dp0src"

rem Compile main controller + MDLog/MDCompress/MDIdp into logmain.dll
javac -encoding UTF-8 -d . -cp ".;%DEP%" ^
  MDLogMain.java ^
  "%~dp0..\mdlog\src\MDLog.java" ^
  "%~dp0..\mdcompress\src\MDCompress.java" ^
  "%~dp0..\mdidp\src\MDIdp.java"
if errorlevel 1 ( echo [ERROR] compile failed & exit /b 1 )

call native-image --shared -cp .;%DEP% -H:+UnlockExperimentalVMOptions -H:Name=logmain -H:+ReportExceptionStackTraces
if errorlevel 1 ( echo [ERROR] native-image failed & exit /b 1 )

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
move /y logmain.dll "%OUT_DIR%\" >nul 2>&1
move /y logmain.h "%OUT_DIR%\" >nul 2>&1
del /q *.class >nul 2>&1
echo [OK] logmain.dll -^> %OUT_DIR%
endlocal
