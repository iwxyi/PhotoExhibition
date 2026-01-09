#!/bin/bash

# PhotoExhibition 部署包创建脚本
# 将所有必要文件整理到deploy文件夹中

echo "=========================================="
echo "PhotoExhibition 部署包创建脚本"
echo "=========================================="

DEPLOY_DIR="./deploy"
APP_NAME="PhotoExhibition"

# 创建目录结构
echo "创建目录结构..."
mkdir -p "$DEPLOY_DIR/$APP_NAME"
mkdir -p "$DEPLOY_DIR/$APP_NAME/backend"
mkdir -p "$DEPLOY_DIR/$APP_NAME/models"
mkdir -p "$DEPLOY_DIR/$APP_NAME/data/photos"

# 复制应用文件
echo "复制应用文件..."

# 复制Electron应用（macOS版本）
if [ -d "frontend/dist-electron/mac/$APP_NAME.app" ]; then
    echo "复制macOS应用..."
    cp -r "frontend/dist-electron/mac/$APP_NAME.app" "$DEPLOY_DIR/$APP_NAME/"
elif [ -d "frontend/dist-electron/win-unpacked" ]; then
    echo "复制Windows应用..."
    cp -r "frontend/dist-electron/win-unpacked" "$DEPLOY_DIR/$APP_NAME/"
fi

# 复制后端JAR文件（作为备用）
if [ -f "backend/target/photo-exhibition-backend-1.0.0.jar" ]; then
    echo "复制后端JAR文件..."
    cp "backend/target/photo-exhibition-backend-1.0.0.jar" "$DEPLOY_DIR/$APP_NAME/backend/"
fi

# 复制AI模型文件
if [ -d "backend/models" ]; then
    echo "复制AI模型文件..."
    cp -r backend/models/* "$DEPLOY_DIR/$APP_NAME/models/" 2>/dev/null || true
fi

# 创建启动脚本
echo "创建启动脚本..."
cat > "$DEPLOY_DIR/$APP_NAME/start.sh" << 'EOF'
#!/bin/bash

# PhotoExhibition 启动脚本

echo "=========================================="
echo "PhotoExhibition 启动脚本"
echo "=========================================="

# 检查操作系统
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "检测到macOS"
    APP_PATH="./PhotoExhibition.app/Contents/MacOS/PhotoExhibition"
elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    echo "检测到Windows"
    APP_PATH="./PhotoExhibition.exe"
else
    echo "检测到Linux"
    APP_PATH="./PhotoExhibition"
fi

# 检查应用是否存在
if [ ! -f "$APP_PATH" ]; then
    echo "❌ 找不到应用文件: $APP_PATH"
    echo "请确保所有文件都在同一目录中"
    exit 1
fi

echo "✅ 找到应用文件: $APP_PATH"
echo "启动应用..."
echo ""
echo "应用启动后，打开浏览器访问: http://localhost:3030"
echo "如遇问题，请检查终端输出信息"
echo ""

# 启动应用
"$APP_PATH"

echo "应用已关闭"
EOF

chmod +x "$DEPLOY_DIR/$APP_NAME/start.sh"

# 创建README文件
echo "创建使用说明..."
cat > "$DEPLOY_DIR/$APP_NAME/README.md" << 'EOF'
# PhotoExhibition 照片展览平台

## 快速开始

### 方法1：双击启动
- macOS: 双击 `PhotoExhibition.app`
- Windows: 双击 `PhotoExhibition.exe`
- Linux: 运行 `./PhotoExhibition`

### 方法2：使用脚本启动
```bash
./start.sh
```

## 首次使用

1. **启动应用** - 应用会自动启动后端服务
2. **等待初始化** - 首次运行需要加载AI模型，可能需要1-2分钟
3. **访问界面** - 浏览器会自动打开 `http://localhost:3030`
4. **初始化管理员** - 使用默认账号登录：`admin / admin123`
5. **设置图片目录** - 选择您的照片文件夹

## 功能特性

- 🖼️ 智能照片管理
- 🤖 AI图像分析（人脸识别、场景分类等）
- 🎨 美观的瀑布流展示
- 📱 响应式设计
- 🔍 高级搜索和筛选

## 系统要求

- **操作系统**: macOS 10.15+, Windows 10+, Ubuntu 18.04+
- **内存**: 至少4GB RAM
- **存储**: 至少2GB可用空间

## 故障排除

### 白屏问题
1. 等待更长时间（首次启动需要1-2分钟）
2. 检查浏览器是否阻止了弹出窗口
3. 查看终端是否有错误信息

### 启动失败
1. 确保所有文件都在同一目录中
2. 检查是否有足够的磁盘空间
3. 在终端中运行 `./start.sh` 查看详细错误信息

### 性能问题
- 关闭其他占用内存的应用程序
- 确保有足够的可用内存（推荐8GB+）
- 首次使用后性能会更好（缓存已建立）

## 文件结构

```
PhotoExhibition/
├── PhotoExhibition.app      # macOS应用
├── PhotoExhibition.exe      # Windows应用
├── PhotoExhibition          # Linux应用
├── backend/                 # 后端文件（备用）
├── models/                  # AI模型文件
├── start.sh                 # 启动脚本
└── README.md               # 本文件
```

## 技术支持

如遇到问题，请检查：
1. 终端错误信息
2. 系统日志
3. 确保网络连接正常（用于加载某些资源）

---
*PhotoExhibition v1.0.0 - 智能照片管理平台*
EOF

# 创建文件列表
echo "生成文件列表..."
find "$DEPLOY_DIR/$APP_NAME" -type f | sort > "$DEPLOY_DIR/filelist.txt"

echo ""
echo "=========================================="
echo "✅ 部署包创建完成！"
echo "=========================================="
echo ""
echo "部署文件夹: $DEPLOY_DIR/$APP_NAME"
echo "文件列表: $DEPLOY_DIR/filelist.txt"
echo ""
echo "使用方法:"
echo "1. 复制整个 $APP_NAME 文件夹到目标机器"
echo "2. 进入文件夹，双击应用启动或运行 ./start.sh"
echo ""
echo "文件大小统计:"
du -sh "$DEPLOY_DIR/$APP_NAME"
echo ""
echo "文件数量: $(find "$DEPLOY_DIR/$APP_NAME" -type f | wc -l) 个"
