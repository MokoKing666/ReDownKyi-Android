@echo off
REM 便捷调用本机 platform-tools 里的 adb（该目录未加入系统 PATH）
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" %*
