@echo off
echo =========================
echo Android APK Builder
echo =========================
cd /d %~dp0
echo 当前默认产出正式签名 release 包（业务链路与正式上架一致）
call gradlew clean
call gradlew assembleRelease
if %errorlevel% neq 0 (
    echo 打包失败
    exit /b 1
)
set "APK_PATH="
for /f "delims=" %%i in ('dir /s /b app-release.apk 2^>nul') do (
    set "APK_PATH=%%i"
    goto :apk_found
)
:apk_found
if defined APK_PATH (
    echo APK 输出路径=%APK_PATH%
    dir "%APK_PATH%"
    echo 正在校验 release APK 物料...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\check-android-release-artifact.ps1" -ApkPath "%APK_PATH%"
    if errorlevel 1 (
        echo APK 校验失败
        exit /b 1
    )
    echo APK_EXISTS=1
) else (
    echo APK_EXISTS=0
    echo 未找到 release APK
    exit /b 1
)
pause
