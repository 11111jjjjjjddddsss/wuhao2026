@echo off
echo =========================
echo Android APK Builder
echo =========================
cd /d %~dp0
echo 使用的模板路径=app/src/main/assets/gpt-demo.html
call gradlew clean
call gradlew assembleDebug
echo APK 输出路径=%cd%\app\build\outputs\apk\debug\app-debug.apk
pause
