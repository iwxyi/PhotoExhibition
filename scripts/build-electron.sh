#!/bin/bash

echo "=========================================="
echo "PhotoExhibition Electron 打包脚本"
echo "=========================================="

# 检查操作系统
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS="mac"
elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    OS="win"
else
    OS="linux"
fi

echo "检测到操作系统: $OS"

# 构建后端
echo "构建后端..."
cd ../backend
if ! mvn clean package -DskipTests; then
    echo "后端构建失败！"
    exit 1
fi
cd ../scripts

# 构建前端
echo "构建前端..."
cd ../frontend
if ! npm install; then
    echo "npm install 失败！"
    exit 1
fi

if ! npm run build; then
    echo "前端构建失败！"
    exit 1
fi

# 打包应用
echo "开始打包..."

case $OS in
    "win")
        echo "打包 Windows 版本..."
        npm run build-electron-win
        ;;
    "mac")
        echo "打包 macOS 版本..."
        npm run build-electron-mac
        ;;
    "linux")
        echo "打包 Linux 版本..."
        npm run build-electron-linux
        ;;
    *)
        echo "未知操作系统，使用通用打包..."
        npm run build-electron
        ;;
esac

if [ $? -eq 0 ]; then
    echo "=========================================="
    echo "打包完成！"
    echo "安装文件位于: ../frontend/dist-electron/"
    echo "=========================================="
else
    echo "打包失败！"
    exit 1
fi
