# 人脸识别模型配置说明

## 配置概览

人脸识别功能包含两个模型：
1. **人脸检测模型**（FaceDetectionService）：检测照片中的人脸位置
2. **人脸特征提取模型**（FaceEmbeddingService）：提取人脸特征向量用于识别

## 必须配置的参数

### 1. 人脸检测模型（FaceDetectionService）

在 `application.yml` 中配置：

```yaml
face:
  detection:
    enabled: true                    # ⚠️ 必须：设置为 true 启用专业检测
    model-path: ./models/face_detection.onnx  # ⚠️ 必须：模型文件路径
    confidence-threshold: 0.5        # 可选：检测置信度阈值（0-1）
    nms-threshold: 0.4               # 可选：非极大值抑制阈值（0-1）
```

**需要用户更改的参数：**

| 参数 | 说明 | 默认值 | 是否必须 | 建议值 |
|------|------|--------|----------|--------|
| `enabled` | 是否启用专业检测模型 | `false` | ✅ **必须** | `true`（推荐） |
| `model-path` | 检测模型文件路径 | `./models/face_detection.onnx` | ✅ **必须** | 根据实际路径调整 |
| `confidence-threshold` | 置信度阈值，低于此值的人脸会被过滤 | `0.5` | 可选 | `0.5-0.7`（值越高越严格） |
| `nms-threshold` | NMS阈值，用于去除重叠的检测框 | `0.4` | 可选 | `0.3-0.5`（值越高保留越多） |

**参数调优建议：**
- **误检较多**：提高 `confidence-threshold` 到 `0.6-0.7`
- **漏检较多**：降低 `confidence-threshold` 到 `0.3-0.4`
- **重复检测**：降低 `nms-threshold` 到 `0.3`

### 2. 人脸特征提取模型（FaceEmbeddingService）

在 `application.yml` 中配置：

```yaml
face:
  embedding:
    model-path: ./models/face_recognition.onnx  # ⚠️ 必须：特征提取模型路径
```

**需要用户更改的参数：**

| 参数 | 说明 | 默认值 | 是否必须 | 建议值 |
|------|------|--------|----------|--------|
| `model-path` | 特征提取模型文件路径 | `./models/face_recognition.onnx` | ✅ **必须** | 根据实际路径调整 |

**注意：** 当前版本特征提取模型没有 `enabled` 开关，如果模型文件不存在，会自动跳过特征提取（但检测仍会进行）。

## 模型下载

### 人脸检测模型（RetinaFace）

**推荐下载地址：**
- ONNX Model Zoo: https://github.com/onnx/models
- 搜索 "RetinaFace" 或 "face detection"
- 或使用以下链接：
  - RetinaFace (640x640): https://github.com/deepinsight/insightface/releases
  - 转换为 ONNX 格式

**模型要求：**
- 输入尺寸：640x640（代码中已固定）
- 输出格式：boxes, scores, landmarks

### 人脸特征提取模型（ArcFace/R50/R100）

**推荐下载地址：**
- InsightFace 官方：https://github.com/deepinsight/insightface
- 模型类型：ArcFace R50 或 R100
- 转换为 ONNX 格式

**模型要求：**
- 输入尺寸：112x112（代码中已固定）
- 输出：512维或1024维特征向量

## 快速配置检查清单

- [ ] 下载人脸检测模型到 `./models/face_detection.onnx`
- [ ] 下载人脸特征提取模型到 `./models/face_recognition.onnx`
- [ ] 在 `application.yml` 中设置 `face.detection.enabled: true`
- [ ] 检查模型路径是否正确（支持相对路径和绝对路径）
- [ ] （可选）根据实际效果调整 `confidence-threshold` 和 `nms-threshold`

## 配置示例

### 最小配置（使用默认值）

```yaml
face:
  detection:
    enabled: true
    model-path: ./models/face_detection.onnx
  embedding:
    model-path: ./models/face_recognition.onnx
```

### 完整配置（自定义参数）

```yaml
face:
  detection:
    enabled: true
    model-path: ./models/face_detection.onnx
    confidence-threshold: 0.6      # 提高阈值，减少误检
    nms-threshold: 0.3              # 降低阈值，减少重复检测
  embedding:
    model-path: ./models/face_recognition.onnx
```

### 使用绝对路径

```yaml
face:
  detection:
    enabled: true
    model-path: /Users/yourname/models/face_detection.onnx
  embedding:
    model-path: /Users/yourname/models/face_recognition.onnx
```

## 故障排除

### 模型未加载

1. **检查模型文件是否存在**
   ```bash
   ls -lh backend/models/face_*.onnx
   ```

2. **检查文件权限**
   ```bash
   chmod 644 backend/models/*.onnx
   ```

3. **查看日志**
   - 成功：`人脸检测模型已加载: ./models/face_detection.onnx`
   - 失败：`加载人脸检测模型失败: ...`

### 检测效果不佳

1. **调整置信度阈值**
   - 误检多 → 提高 `confidence-threshold`
   - 漏检多 → 降低 `confidence-threshold`

2. **检查模型是否匹配**
   - 确保使用的是 RetinaFace 检测模型
   - 确保输入输出格式匹配

3. **尝试其他模型**
   - 可以尝试 MTCNN、YOLOv5-Face 等其他检测模型
   - 需要相应调整代码中的输入输出解析逻辑

## 性能优化

1. **模型选择**
   - 检测模型：RetinaFace（平衡速度和准确率）
   - 特征模型：R50（512维）或 R100（512维）

2. **硬件加速**
   - ONNX Runtime 支持 GPU 加速（需要 CUDA 版本）
   - 可以在 `OrtSession.SessionOptions` 中配置

3. **批量处理**
   - 人脸检测和特征提取已集成到异步扫描流程
   - 不会阻塞主线程

