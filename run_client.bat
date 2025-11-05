@echo off
setlocal

pushd "%~dp0"

where java >nul 2>&1
if errorlevel 1 (
    echo Java runtime not found in PATH. Install JDK/JRE and try again.
    pause
    popd & endlocal & exit /b 1
)

set CP=
if exist out set CP=out
if "%CP%"=="" if exist src set CP=src
if "%CP%"=="" (
    echo Cannot locate classpath. Build first with build.bat
    pause
    popd & endlocal & exit /b 1
)

echo Using classpath: %CP%
java -cp "%CP%" main.TicTacToeClient
if errorlevel 1 (
    echo Client exited with error. See messages above.
    pause
)

popd
endlocal

