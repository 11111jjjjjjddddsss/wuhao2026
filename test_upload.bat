@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
if "%~1"=="" (
  if defined UPLOAD_BASE_URL (set "BASE=!UPLOAD_BASE_URL!") else (
    echo 用法: test_upload.bat ^<UPLOAD_BASE_URL^>
    echo 示例: test_upload.bat https://api.example.com
    echo 或先 set UPLOAD_BASE_URL=https://api.example.com 再运行
    exit /b 1
  )
) else set "BASE=%~1"
if "%BASE:~-1%"=="/" set "BASE=%BASE:~0,-1%"
set "URL=%BASE%/upload"
set "TEST_IMAGE=%TEMP%\nongji-upload-test.jpg"
if not exist "%TEST_IMAGE%" (
  echo 正在生成测试图片...
  node scripts\create_test_image.js "%TEST_IMAGE%" || exit /b 1
)
echo 请求: POST %URL%
echo 文件: %TEST_IMAGE%
curl -s -w "\nHTTP_CODE:%%{http_code}" -X POST -F "file=@%TEST_IMAGE%" "%URL%"
echo.
endlocal
