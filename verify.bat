@echo off
REM ============================================================
REM  Re：哔哩下载姬 —— 一键真机验证
REM
REM  用法：verify.bat [截图文件名]
REM    verify.bat                  -> _shots/latest.png
REM    verify.bat dark.png         -> _shots/dark.png
REM
REM  流程：构建 Debug APK -> 安装到已连接设备 -> 冷启动 -> 截图
REM ============================================================

setlocal
set "ROOT=%~dp0"
set "SHOT=%~1"
if "%SHOT%"=="" set "SHOT=latest.png"

REM JDK 17+：优先 JAVA_HOME，未设置时回退到默认路径（换机器请自行修改）
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Zulu\zulu-22"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "APPID=com.moko.downkyi.re"

cd /d "%ROOT%"

echo [1/5] 构建...
call gradlew.bat :app:assembleDebug --console=plain -q
if errorlevel 1 (
  echo BUILD FAILED
  exit /b 1
)
echo     ok

echo [2/5] 安装...
REM 用 push + pm install，而不是 adb install。
REM
REM vivo 的 OriginOS 会拦截 adb install，静默返回
REM   INSTALL_FAILED_ABORTED: User rejected permissions
REM 而手机上根本不弹确认框 —— 看起来像授权出了问题，实际是安装通道被换掉了。
REM pm install 走同一套 PackageManager，可以正常通过。
"%ADB%" push "%APK%" /data/local/tmp/redownkyi.apk >nul
"%ADB%" shell pm install -r /data/local/tmp/redownkyi.apk > "%TEMP%\redownkyi_install.log" 2>&1
findstr /C:"Success" "%TEMP%\redownkyi_install.log" >nul
if errorlevel 1 (
  echo 安装失败，设备返回：
  type "%TEMP%\redownkyi_install.log"
  exit /b 1
)

echo [3/5] 启动...
"%ADB%" shell am force-stop %APPID%
"%ADB%" shell am start -n %APPID%/.MainActivity >nul

echo [4/5] 等待渲染...
ping -n 6 127.0.0.1 >nul

echo [5/5] 截图...
if not exist "%ROOT%_shots" mkdir "%ROOT%_shots"
"%ADB%" shell screencap -p /sdcard/shot.png
"%ADB%" pull /sdcard/shot.png "%ROOT%_shots\%SHOT%" >nul
"%ADB%" shell rm /sdcard/shot.png

echo.
echo 完成：_shots\%SHOT%

endlocal
