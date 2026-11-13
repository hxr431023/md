@echo off
REM ============================================================
REM  Unified build entry -> forwards to the AUTHORITATIVE script:
REM    modules\mdcontroller\build-mdcontroller.bat  (19 modules)
REM  Then syncs artifacts from DLL\app to dist\
REM  (Single source of truth lives in build-mdcontroller.bat)
REM ============================================================
setlocal
call "%~dp0modules\mdcontroller\build-mdcontroller.bat"
if errorlevel 1 ( echo [ERROR] build-mdcontroller.bat failed & exit /b 1 )
set OUT=%~dp0DLL\app
set DST=%~dp0dist
if not exist "%DST%" mkdir "%DST%"
copy /y "%OUT%\controller.dll" "%DST%\" >nul
copy /y "%OUT%\controller.h" "%DST%\" >nul
if exist "%OUT%\controller.lib" copy /y "%OUT%\controller.lib" "%DST%\" >nul
echo [DONE] controller.dll -^> dist\  (authoritative: modules\mdcontroller\build-mdcontroller.bat)
endlocal
