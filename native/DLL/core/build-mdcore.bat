@echo off
setlocal
set GRAALVM_HOME=C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1
set PATH=%GRAALVM_HOME%\bin;%PATH%
set JAVA_HOME=%GRAALVM_HOME%
set OUT_DIR=%~dp0
set M=%~dp0..\..\modules
set CP=%M%\mdidp\src;%M%\mdlog\src;%M%\mdcompress\src;%M%\mdconfig\src;%M%\mdfile\src;%M%\mdsingle\src
cd /d "%~dp0"
echo [1/2] Compiling mdcore (6 modules: mdidp mdlog mdcompress mdconfig mdfile mdsingle)...
javac -encoding UTF-8 -cp "%CP%;." ^
    "%M%\mdidp\src\MDIdp.java" ^
    "%M%\mdlog\src\MDLog.java" ^
    "%M%\mdcompress\src\MDCompress.java" ^
    "%M%\mdconfig\src\MDConfig.java" ^
    "%M%\mdfile\src\MDFile.java" ^
    "%M%\mdsingle\src\MDSingle.java"
if errorlevel 1 ( echo [ERROR] compile failed & exit /b 1 )
echo [2/2] native-image --shared...
call native-image --shared ^
    -cp "%CP%;." ^
    -H:+UnlockExperimentalVMOptions ^
    -H:Name=core ^
    -H:+ReportExceptionStackTraces
if errorlevel 1 ( echo [ERROR] native-image failed & exit /b 1 )
move /y core.dll "%OUT_DIR%\" >nul 2>&1
move /y core.h "%OUT_DIR%\" >nul 2>&1
del /q *.class >nul 2>&1
echo [OK] core.dll -> %OUT_DIR%
endlocal
