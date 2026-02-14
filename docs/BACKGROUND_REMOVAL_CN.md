# 人物抠图与立体封面效果

本功能使用本地 AI 模型进行背景移除，实现"小红书风格"的立体拼图效果。

## 特点

- **纯本地运行**：无需联网，使用本地 CPU 推理
- **边缘细腻**：使用 BriaAI RMBG 模型，抠图效果优秀
- **异步处理**：后台处理大量图片，不阻塞前端
- **立体效果**：CSS 3D 变换，悬浮时人物"浮出"封面

## 快速开始

### 1. 下载 AI 模型

```bash
# 进入项目根目录
cd PhotoExhibition

# 下载 BriaAI RMBG 模型（~100MB）
./scripts/download_rmbg_model.sh
```

或者手动下载：
- 模型地址：https://huggingface.co/briaai/RMBG-1.4/resolve/main/model.onnx
- 保存到：`backend/models/briaai_rmbg.onnx`

### 2. 启用功能

编辑 `backend/src/main/resources/application.yml`：

```yaml
background:
  removal:
    enabled: true  # 设为 true 启用
    model-path: ./models/briaai_rmbg.onnx
    cpu-threads: 4   # 根据 CPU 核心数调整
    keep-size: 1024
```

### 3. 重启后端服务

```bash
# 方式一：使用现有脚本
./scripts/start-backend.sh

# 方式二：手动启动
cd backend
mvn spring-boot:run
```

### 4. 测试效果

访问任意相册封面，如果该相册的照片已经进行过背景移除处理，将看到立体拼图效果。

## 工作原理

```
原图 → BriaAI RMBG 模型推理 → 透明背景 PNG → 前端 3D 效果展示
```

### 后端流程

1. 接收前端请求（`GET /photos/{id}/remove-background`）
2. 加载 ONNX 模型（仅首次加载）
3. 图片预处理（缩放、标准化）
4. 模型推理（生成 Alpha 掩码）
5. 后处理（边缘平滑、生成透明 PNG）
6. 返回图片给前端

### 前端效果

1. **阴影层**：原图模糊版本作为阴影
2. **主体层**：透明背景的人物图
3. **3D 变换**：`translateZ()` 创造深度感
4. **悬浮交互**：鼠标悬停时人物"浮起"

## 性能优化

### CPU 线程数

```yaml
background:
  removal:
    cpu-threads: 8  # 8核 CPU 可设为 8
```

### 批量处理

对于大量图片，建议使用异步批量处理：

```java
@Autowired
private BackgroundRemovalService backgroundRemovalService;

public void batchProcessPhotos(List<Long> photoIds) {
    for (Long photoId : photoIds) {
        Photo photo = photoRepository.findById(photoId).orElse(null);
        if (photo != null) {
            File source = new File(photo.getOriginalPath());
            File output = new File(photo.getPath() + "_no_bg.png");
            backgroundRemovalService.removeBackground(source, output);
        }
    }
}
```

## 效果预览

### 普通封面
```
┌─────────────────────┐
│                     │
│     [  图片  ]       │
│                     │
└─────────────────────┘
```

### 立体封面（开启抠图后）
```
┌─────────────────────┐
│                     │
│   [人物]  ← 浮起     │
│                     │
│   ← 阴影层          │
└─────────────────────┘
```

## 已知问题

1. **首次加载较慢**：模型首次加载约 3-5 秒，后续请求秒级响应
2. **复杂背景**：对于非常复杂的背景（如发丝细节），可能需要后处理
3. **内存占用**：处理大图时需要较多内存，建议 4GB+ 可用内存

## 故障排除

### 模型文件不存在

```
 WARN: 背景移除模型文件不存在: ./models/briaai_rmbg.onnx
```

解决：
1. 确认模型文件已下载
2. 检查文件路径配置

### ONNX Runtime 错误

```
 WARN: ONNX Runtime 本地库缺失
```

解决：
- Windows: 安装 Visual C++ Redistributable
- Linux: 安装 `libgomp1`, `libatlas-base-dev`
- macOS: 通常无需额外依赖

### 内存不足

降低处理图片的最大尺寸：
```yaml
background:
  removal:
    keep-size: 512  # 从 1024 降到 512
```

## API 接口

### GET /api/photos/{id}/remove-background

获取移除背景后的图片（透明 PNG）

**响应**：PNG 图片流

**请求头**：
- `Cache-Control: public, max-age=31536000`（建议缓存）

### GET /api/photos/{id}/background-removal/available

检查功能是否可用

**响应**：
```json
{
  "enabled": true,
  "photoExists": true
}
```

## 技术细节

### 模型信息

| 属性 | 值 |
|------|-----|
| 模型 | BriaAI RMBG 1.4 |
| 大小 | ~100 MB |
| 输入尺寸 | 1024 × 1024 |
| 输出 | Alpha 掩码 (H × W) |
| 推理时间 | 1-3 秒（CPU） |

### ONNX Runtime 配置

```java
// 启用 CPU 优化
opts.setIntraOpNumThreads(cpuThreads);
opts.setInterOpNumThreads(cpuThreads);
opts.setOptimizationLevel(OptLevel.ALL_OPT);
```

## 扩展用途

1. **人物拼贴**：将多个人的透明图叠加
2. **背景替换**：将抠出的人物放到新背景上
3. **动态贴纸**：生成的表情包透明图
4. **证件照**：简单场景的证件照背景替换
