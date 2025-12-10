@echo off
REM 前端启动脚本 (Windows)

echo =========================================
echo 启动摄影展示平台 - 前端服务
echo =========================================

REM 检查Node.js
node -v >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Node.js，请先安装Node.js 18+
    pause
    exit /b 1
)
echo [OK] Node.js已安装

REM 检查npm
npm -v >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到npm
    pause
    exit /b 1
)
echo [OK] npm已安装

REM 进入前端目录
cd /d "%~dp0frontend"
if errorlevel 1 (
    echo [错误] 无法进入frontend目录
    pause
    exit /b 1
)

REM 检查node_modules
if not exist "node_modules" (
    echo.
    echo 首次运行，正在安装依赖...
    call npm install
    echo.
)

echo.
echo 开始启动前端开发服务器...
echo 访问地址: http://localhost:3000
echo 按 Ctrl+C 停止服务
echo.

REM 启动开发服务器
npm run dev

pause

