@echo off
setlocal

pushd "%~dp0"
if not exist out (
    mkdir out
)

rem IMPORTANT: do NOT quote the wildcard path on Windows
javac -d out src\main\*.java
if errorlevel 1 (
    echo Build failed. Please check errors above.
    popd
    exit /b 1
)

echo Build succeeded. Classes are in: out
popd
endlocal
