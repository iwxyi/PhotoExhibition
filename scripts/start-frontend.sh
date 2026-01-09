#!/bin/bash

# 前端启动脚本

echo "========================================="
echo "启动摄影展示平台 - 前端服务"
echo "========================================="

# 检查Node.js
if ! command -v node &> /dev/null; then
    echo "❌ 错误: 未找到Node.js，请先安装Node.js 18+"
    exit 1
fi
echo "✅ Node.js版本: $(node -v)"

# 检查npm
if ! command -v npm &> /dev/null; then
    echo "❌ 错误: 未找到npm"
    exit 1
fi
echo "✅ npm版本: $(npm -v)"

# 进入前端目录
cd "$(dirname "$0")/../frontend" || exit 1

# 检查node_modules
if [ ! -d "node_modules" ]; then
    echo ""
    echo "首次运行，正在安装依赖..."
    npm install
    echo ""
fi

echo ""
echo "开始启动前端开发服务器..."
echo "访问地址: http://localhost:3030"
echo "按 Ctrl+C 停止服务"
echo ""

# 启动开发服务器
npm run dev

