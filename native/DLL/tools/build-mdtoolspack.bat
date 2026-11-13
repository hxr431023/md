@echo off
setlocal
set GRAALVM_HOME=C:\wpjc\graalvm\graalvm-community-25.3.4.1+1.1
set PATH=%GRAALVM_HOME%\bin;%PATH%
set JAVA_HOME=%GRAALVM_HOME%
set OUT_DIR=%~dp0
set M=%~dp0..\..\modules
set CP=%M%\mdidp\src;%M%\mdtmmf\src;%M%\mdlerf\src
cd /d "%~dp0"
echo [1/2] Compiling mdtoolspack (2 modules: mdtmmf mdlerf)...
javac -encoding UTF-8 -cp "%CP%;." ^
    "%M%\mdtmmf\src\MDTmmf.java" ^
    "%M%\mdlerf\src\MDLerf.java"
if errorlevel 1 ( echo [ERROR] compile failed & exit /b 1 )
echo [2/2] native-image --shared...
call native-image --shared ^
    -cp "%CP%;." ^
    -H:+UnlockExperimentalVMOptions ^
    -H:Name=toolspack ^
    -H:+ReportExceptionStackTraces
if errorlevel 1 ( echo [ERROR] native-image failed & exit /b 1 )
move /y toolspack.dll "%OUT_DIR%\" >nul 2>&1
move /y toolspack.h "%OUT_DIR%\" >nul 2>&1
del /q *.class >nul 2>&1
echo [OK] toolspack.dll -> %OUT_DIR%
endlocal
