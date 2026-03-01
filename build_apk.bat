@echo off
echo =========================
echo Android APK Builder
echo =========================
cd /d %~dp0
echo 已切换为原生 Compose 入口（无 Web 模板依赖）
call gradlew clean
call gradlew assembleDebug
if %errorlevel% neq 0 (
    echo 打包失败
    exit /b 1
)
set "APK_PATH="
for /f "delims=" %%i in ('dir /s /b app-debug.apk 2^>nul') do (
    set "APK_PATH=%%i"
    goto :apk_found
)
:apk_found
if defined APK_PATH (
    echo APK 输出路径=%APK_PATH%
    dir "%APK_PATH%"
    echo APK_EXISTS=1
) else (
    echo APK_EXISTS=0
)
pause
