@echo off
REM ============================================================
REM  Re：哔哩下载姬 —— 本地构建入口
REM
REM  用法：
REM    build.bat                        构建 Debug APK
REM    build.bat :app:assembleRelease   构建 Release APK
REM    build.bat :core:model:testDebugUnitTest   跑单元测试
REM    build.bat clean                  清理
REM ============================================================

setlocal

REM JDK 17+：优先使用已设置的 JAVA_HOME；
REM 未设置时回退到下面的默认路径 —— 换机器请按自己的 JDK 安装位置修改这一行
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Zulu\zulu-22"

REM Android SDK 位于用户目录；local.properties 里也指向同一位置
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"

cd /d "%~dp0"

if "%~1"=="" (
  call gradlew.bat :app:assembleDebug --console=plain
) else (
  call gradlew.bat %* --console=plain
)

endlocal
