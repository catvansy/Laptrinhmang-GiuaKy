@echo off
setlocal

pushd "%~dp0"

rem Remove compiled output directory
if exist out (
    echo Removing out\ ...
    rmdir /s /q out
)

rem Remove any stray .class files recursively in src
if exist src (
    echo Removing stray .class files under src\ ...
    for /r src %%F in (*.class) do (
        del /q "%%F" 2>nul
    )
)

echo Clean done.
pause

popd
endlocal

