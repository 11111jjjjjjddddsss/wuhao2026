@echo off
echo =========================
echo Android APK Builder
echo =========================
cd /d %~dp0
echo 使用的模板路径=app/src/main/assets/gpt-demo.html
call gradlew clean
call gradlew assembleDebug
if %errorlevel% neq 0 (
    echo 打包失败
    exit /b 1
)
for /f "delims=" %%i in ('dir /s /b app-debug.apk 2^>nul') do (
    echo APK 输出路径=%%i
    goto :done
)
:done
pause
