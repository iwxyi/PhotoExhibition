@echo off
REM 后端启动脚本 (Windows)

echo =========================================
echo 启动摄影展示平台 - 后端服务
echo =========================================

REM 检查Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Java，请先安装Java 11+
    pause
    exit /b 1
)
echo [OK] Java已安装

REM 检查Maven
mvn -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Maven，请先安装Maven
    pause
    exit /b 1
)
echo [OK] Maven已安装

REM 进入后端目录
cd /d "%~dp0backend"
if errorlevel 1 (
    echo [错误] 无法进入backend目录
    pause
    exit /b 1
)

echo.
echo 开始启动后端服务...
echo 访问地址: http://localhost:6060/api
echo 按 Ctrl+C 停止服务
echo.

REM 启动SpringBoot应用
mvn spring-boot:run

pause

