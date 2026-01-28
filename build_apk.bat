@echo off
echo =========================
echo Android APK Builder
echo =========================
cd /d %~dp0
call gradlew clean
call gradlew assembleDebug
echo.
echo APK generated at:
echo app\build\outputs\apk\debug\app-debug.apk
pause
