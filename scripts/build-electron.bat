@echo off
echo ==========================================
echo PhotoExhibition Electron 打包脚本
echo ==========================================

echo 构建后端...
cd ..\backend
call mvn clean package -DskipTests
if errorlevel 1 (
    echo 后端构建失败！
    pause
    exit /b 1
)
cd ..\scripts

echo 构建前端...
cd ..\frontend
call npm install
if errorlevel 1 (
    echo npm install 失败！
    pause
    exit /b 1
)

call npm run build
if errorlevel 1 (
    echo 前端构建失败！
    pause
    exit /b 1
)

echo 开始打包...
call npm run build-electron-win
if errorlevel 1 (
    echo 打包失败！
    pause
    exit /b 1
)

echo ==========================================
echo 打包完成！
echo 安装文件位于: ..\frontend\dist-electron\
echo ==========================================
pause
