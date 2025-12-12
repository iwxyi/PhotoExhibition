# PhotoExhibition

基于 Spring Boot + Vue 3 的摄影作品管理与展示平台，支持自动扫描、智能人像聚合、瀑布流展示、标签/EXIF/颜色筛选，以及管理员批量操作。

## 功能速览

### 1. 多维度图片浏览与管理
- 即刻看到所有作品：相册封面拼图、瀑布流、随机模式，支持无限滚动与懒加载，不用等待。
- 一键找图：按标签、EXIF（相机/镜头/光圈/ISO 等）、色彩、质量评分组合筛选，快速定位想要的照片。
- 自动扫描与去重：后台持续扫盘，识别已存在照片，移动/复制后数据可复用。
- 保持流畅：缩略图 + WebP、Redis 缓存、分批扫描、异步处理，浏览不卡顿。

### 2. 人脸识别与智能分类
- 看得准：RetinaFace 检测 + R50/R100 向量识别，精确提取人脸 embedding。
- 自动聚合：相似人脸自动成组，“已确认/自动分配/相似推荐/套图推荐/未分配”分层呈现，先批量建议，再由你一键确认。
- 直接命名：未命名聚类点击姓名即可建人；左侧卡片就地改名/备注，支持删除，面板宽度可拖拽记忆。
- 套图场景友好：同目录及上级目录的照片会适度放宽阈值做“套图推荐”，常见同一场景拍摄更易被找到。

## 技术栈
- 后端：Spring Boot 2.7+，MySQL 5.7/8.0，Redis 6+，Maven，ONNX Runtime
- 前端：Vue 3 + TypeScript，Vite，Tailwind CSS，Pinia

## 目录结构
```
PhotoExhibition/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/photoexhibition/
│   │   ├── controller/      # API 控制器
│   │   ├── service/         # 业务逻辑（扫描、嵌入、聚类、人脸分配）
│   │   ├── repository/      # 数据访问
│   │   ├── entity/          # 实体类
│   │   └── dto/             # 传输对象
│   └── src/main/resources/application.yml
├── frontend/                # Vue 3 前端
│   ├── src/views/           # 页面（含人物管理）
│   ├── src/components/      # 通用组件
│   ├── src/stores/          # Pinia 状态
│   └── src/api/             # 接口封装
├── database/schema.sql      # 数据库结构
├── docker-compose.yml
└── README.md
```

## 快速开始
### Docker（推荐）
```bash
mkdir -p data/photos
# 将照片放入 data/photos
docker-compose up -d
# 前端: http://localhost:3000
# 后端: http://localhost:6060/api
```

### 本地开发
后端
```bash
cd backend
mvn clean install
# 配置 application.yml 的数据库与 photo.scan.base-path
mvn spring-boot:run
```
前端
```bash
cd frontend
npm install
npm run dev  # http://localhost:3000
```

## 配置要点（application.yml）
```yaml
photo:
  scan:
    base-path: /data/photos          # 图片根目录
    supported-formats: jpg,jpeg,png,heic,raw
    thumbnail-width: 400
    thumbnail-height: 400
    webp-quality: 0.85
    scan-interval: 3600              # 秒
  detection:
    enabled: true                    # 开启专业检测模型（RetinaFace）
    model-path: ./models/face_detection.onnx
    confidence-threshold: 0.5
    nms-threshold: 0.4
  embedding:
    model-path: ./models/face_recognition.onnx # R50/R100
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/photo_exhibition?...
    username: root
    password: your_password
redis:
  host: localhost
  port: 6379
```

## 路径与标签约定
- `photo.scan.base-path` 的第一级目录仅作为“外层大分类”，用于首页分类 Tab，不参与标签提取。
- 标签从相对路径第二段开始提取，并去掉各级目录前的日期前缀（如 `2025.11.01`）。
- 例：`/data/photos/人像/2025.11.15 烟花-木木/833A5293.jpg`  
  - 分类：人像  
  - 相册显示标题：`2025.11.15 烟花-木木`（日期单独展示）

## 人脸与人物管理（新人须知）
- 检测：默认启用 RetinaFace ONNX，支持置信度、面积、长宽比过滤，降低误检。
- 识别：R50/R100 embedding，聚类使用平均向量 + 多代表向量，分层阈值、离群保护、最小样本约束。
- 界面：左侧人物（已确认 + 未命名聚类）多列自适应，点击整卡选择，名字可就地编辑；右侧多级 Tab（已确认、自动分配、相似推荐、套图推荐、未分配）便于批量确认。
- 自动建人：为未命名聚类输入名字即创建人物；删除人物、编辑备注均在左侧完成。
- 路径分层推荐：同目录/上级/再上级（不超过 base-path 下的第二层）会适度放宽相似度阈值，用于“套图推荐”。

## 常用命令
- 后端：`mvn spring-boot:run`
- 前端：`npm run dev` / `npm run build`
- 触发扫描：`curl -X POST http://localhost:6060/api/admin/scan`
- Docker 启停：`docker-compose up -d` / `docker-compose down`

## 常见问题
- 图片 404：检查 `photo.scan.base-path` 与文件权限；确认文件实际存在。
- 扫描无结果：确保格式在支持列表，查看后端日志；可手动触发扫描。
- 端口占用：检查 6060/3000 是否被占用，可在 `application.yml` 与 `vite.config.ts` 调整。
- MySQL/Redis 连接失败：确认服务已启动、账号密码正确，可用 CLI 测试。

## 许可证
MIT License

