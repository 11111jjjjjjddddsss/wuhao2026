@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
if "%~1"=="" (
  if defined UPLOAD_BASE_URL (set "BASE=!UPLOAD_BASE_URL!") else (
    echo 用法: test_upload.bat ^<UPLOAD_BASE_URL^>
    echo 示例: test_upload.bat https://xxx.up.railway.app
    echo 或先 set UPLOAD_BASE_URL=https://xxx 再运行 test_upload.bat
    exit /b 1
  )
) else set "BASE=%~1"
if "%BASE:~-1%"=="/" set "BASE=%BASE:~0,-1%"
set "URL=%BASE%/upload"
if not exist "upload-server\test-image.jpg" (
  echo 正在生成测试图片...
  node upload-server\create-test-image.js 2>nul || (echo 请先准备: 将任意 jpg 放到 upload-server\test-image.jpg & exit /b 1)
)
echo 请求: POST %URL%
echo 文件: upload-server\test-image.jpg
curl -s -w "\nHTTP_CODE:%%{http_code}" -X POST -F "file=@upload-server/test-image.jpg" "%URL%"
echo.
endlocal
