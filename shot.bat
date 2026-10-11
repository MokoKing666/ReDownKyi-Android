@echo off
REM 仅截图（不重新构建安装），用于观察界面状态变化
REM 用法：shot.bat [文件名]   默认 _shots/latest.png

setlocal
set "ROOT=%~dp0"
set "SHOT=%~1"
if "%SHOT%"=="" set "SHOT=latest.png"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%ROOT%_shots" mkdir "%ROOT%_shots"
"%ADB%" shell screencap -p /sdcard/shot.png
"%ADB%" pull /sdcard/shot.png "%ROOT%_shots\%SHOT%" >nul
"%ADB%" shell rm /sdcard/shot.png
echo 完成：_shots\%SHOT%
endlocal
