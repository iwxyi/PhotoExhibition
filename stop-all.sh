#!/bin/bash

# 停止所有服务脚本

echo "========================================="
echo "停止摄影展示平台服务"
echo "========================================="

# 停止后端服务
echo "正在停止后端服务..."
BACKEND_PID=$(lsof -ti:6060 2>/dev/null)
if [ -n "$BACKEND_PID" ]; then
    echo "  找到后端进程: $BACKEND_PID"
    kill -9 $BACKEND_PID 2>/dev/null
    echo "  ✅ 后端服务已停止"
else
    echo "  ℹ️  后端服务未运行"
fi

# 停止Maven进程
pkill -f "spring-boot:run" 2>/dev/null
pkill -f "photo-exhibition" 2>/dev/null

# 停止前端服务
echo "正在停止前端服务..."
FRONTEND_PID=$(lsof -ti:3000 2>/dev/null)
if [ -n "$FRONTEND_PID" ]; then
    echo "  找到前端进程: $FRONTEND_PID"
    kill -9 $FRONTEND_PID 2>/dev/null
    echo "  ✅ 前端服务已停止"
else
    echo "  ℹ️  前端服务未运行"
fi

# 停止Node进程
pkill -f "vite" 2>/dev/null

# 检查是否还有相关进程
echo ""
echo "检查残留进程..."
REMAINING=$(ps aux | grep -E "spring-boot|vite|photo-exhibition" | grep -v grep | grep -v stop-all)
if [ -n "$REMAINING" ]; then
    echo "  发现残留进程:"
    echo "$REMAINING"
    echo ""
    read -p "是否强制终止这些进程? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        pkill -9 -f "spring-boot" 2>/dev/null
        pkill -9 -f "vite" 2>/dev/null
        echo "  ✅ 已强制终止"
    fi
else
    echo "  ✅ 无残留进程"
fi

echo ""
echo "========================================="
echo "停止完成"
echo "========================================="

