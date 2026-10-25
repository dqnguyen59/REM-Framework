@echo off
setlocal
rem Run from the project folder so saved settings and assets are found.
pushd "%~dp0"
if errorlevel 1 exit /b 1

set "REM_JAVA=java"
if defined JAVA_HOME set "REM_JAVA=%JAVA_HOME%\bin\java.exe"
"%REM_JAVA%" -version >nul 2>&1
if errorlevel 1 (
    echo Java 21 or newer is required. Install Java or set JAVA_HOME.
    goto failed
)

if not exist "target\rem.jar" (
    echo Building REM Framework for the first run...
    call mvn -DskipTests package
    if errorlevel 1 goto failed
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
if errorlevel 1 goto failed
popd
exit /b 0

:failed
echo.
echo REM Framework could not start. See the message above.
pause
popd
exit /b 1
