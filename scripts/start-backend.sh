#!/bin/bash

# 后端启动脚本

echo "========================================="
echo "启动摄影展示平台 - 后端服务"
echo "========================================="

# 设置JAVA_HOME（macOS）
if [[ "$OSTYPE" == "darwin"* ]]; then
    if [ -z "$JAVA_HOME" ]; then
        # 尝试使用 /usr/libexec/java_home
        if [ -x /usr/libexec/java_home ]; then
            export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
        fi
        # 如果还是为空，尝试Homebrew安装的Java
        if [ -z "$JAVA_HOME" ]; then
            if [ -d "/opt/homebrew/opt/openjdk" ]; then
                export JAVA_HOME="/opt/homebrew/opt/openjdk"
            elif [ -d "/opt/homebrew/opt/openjdk@11" ]; then
                export JAVA_HOME="/opt/homebrew/opt/openjdk@11"
            elif [ -d "/opt/homebrew/opt/openjdk@17" ]; then
                export JAVA_HOME="/opt/homebrew/opt/openjdk@17"``
            fi
        fi
    fi
fi

# 检查Java
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到Java，请先安装Java 11+"
    echo "   安装方法: brew install openjdk@11 (macOS)"
    exit 1
fi
echo "✅ Java版本: $(java -version 2>&1 | head -n 1)"

# 显示JAVA_HOME
if [ -n "$JAVA_HOME" ]; then
    echo "✅ JAVA_HOME: $JAVA_HOME"
else
    echo "⚠️  警告: JAVA_HOME未设置，Maven可能无法正常工作"
    echo "   设置方法: export JAVA_HOME=\$(/usr/libexec/java_home)"
fi

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: 未找到Maven，请先安装Maven"
    exit 1
fi
echo "✅ Maven版本: $(mvn -version | head -n 1)"

# 检查MySQL
if ! command -v mysql &> /dev/null; then
    echo "⚠️  警告: 未找到MySQL命令，请确保MySQL已安装并运行"
else
    echo "✅ MySQL已安装"
fi

# 检查Redis
if command -v redis-cli &> /dev/null; then
    if redis-cli ping &> /dev/null; then
        echo "✅ Redis运行中"
    else
        echo "⚠️  警告: Redis未运行，请启动Redis服务"
    fi
else
    echo "⚠️  警告: 未找到Redis，缓存功能将不可用"
fi

# 保存项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT/backend" || exit 1

echo ""
echo "开始启动后端服务..."
echo "项目根目录: $PROJECT_ROOT"
echo "访问地址: http://localhost:6060/api"
echo "按 Ctrl+C 停止服务"
echo ""

# 设置工作目录为项目根目录（这样相对路径才能正确解析）
export SPRING_BOOT_WORK_DIR="$PROJECT_ROOT"

# 启动SpringBoot应用
mvn spring-boot:run

