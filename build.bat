@echo off
setlocal

pushd "%~dp0"

if not exist out mkdir out

rem Do NOT quote the wildcard on Windows
javac -d out src\main\*.java
if errorlevel 1 (
    echo Build failed. Please check errors above.
    pause
    popd & endlocal & exit /b 1
)

echo Build succeeded. Classes are in: out
pause
popd
endlocal
@echo off
setlocal

rem Build all Java sources into the out folder
pushd "%~dp0"
if not exist out (
    mkdir out
)

rem IMPORTANT: do NOT quote the wildcard path on Windows
javac -d out src\main\*.java
if errorlevel 1 (
    echo Build failed.
    popd
    exit /b 1
)

echo Build succeeded. Classes are in: out
popd
endlocal
