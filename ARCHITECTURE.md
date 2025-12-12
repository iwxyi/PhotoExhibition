# 项目架构设计文档

## 1. 整体架构

```
┌─────────────────┐
│   前端 (Vue3)    │
│  Port: 3000     │
└────────┬────────┘
         │ HTTP/REST
         │
┌────────▼────────┐
│ 后端 (SpringBoot)│
│  Port: 6060     │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
┌───▼───┐ ┌──▼────┐
│ MySQL │ │ Redis │
│ 3306  │ │ 6379  │
└───────┘ └───────┘
```

## 2. 后端架构

### 2.1 分层架构

```
Controller Layer (控制器层)
    ↓
Service Layer (业务逻辑层)
    ↓
Repository Layer (数据访问层)
    ↓
Entity Layer (实体层)
    ↓
Database (数据库)
```

### 2.2 核心模块

#### 2.2.1 实体模块 (Entity)
- `Album`: 相册实体
- `Photo`: 图片实体（含 contentHash/pathHash、EXIF、质量分）
- `Face`: 人脸实体（embedding、bbox、confidence、isConfirmed）
- `PersonProfile`: 人物档案（name/description，与 Face 关联）
- `Tag`: 标签实体

#### 2.2.2 服务模块 (Service)
- `PhotoScanService`
  - 定时/手动扫描；优先按 `contentHash` 复用已存在照片，再回退 `pathHash` / `originalPath`
  - EXIF 提取、缩略图/WebP 生成、色彩分析、质量评分
  - 关机/退出检测，避免关闭时的异常任务

- `FaceDetectionService`
  - ONNX Runtime 加载 RetinaFace（可配置开关、模型路径、置信度/NMS 阈值）
  - 置信度、面积、长宽比、边缘留白过滤，降低误检

- `FaceEmbeddingService`
  - ONNX Runtime 加载 R50/R100 识别模型，生成并归一化 embedding

- `FaceService`
  - 聚类：平均向量 + 代表向量，分层阈值，离群/合并保护，最小样本约束
  - 列表：已确认/自动分配/相似推荐/套图推荐/未分配；同/上级/再上级目录的“套图推荐”阈值放宽
  - 绑定：未命名聚类直接命名建人；区分人工确认与自动分配

- `AlbumService`：相册查询、封面生成、标签管理
- `PhotoService`：图片查询、筛选（标签/EXIF/色彩/评分）、统计
- `ColorAnalysisService`：主色调与调色板提取

#### 2.2.3 控制器模块 (Controller)
- `AlbumController`: 相册API
- `PhotoController`: 图片API
- `FaceController`: 人脸/人物管理API（聚类、推荐、分配/确认、套图推荐）
- `AdminController`: 管理员API

#### 2.2.4 配置模块 (Config)
- `RedisConfig`: Redis缓存配置
- `WebConfig`: Web配置（CORS、静态资源）

## 3. 前端架构

### 3.1 技术栈
- **框架**: Vue 3 (Composition API)
- **语言**: TypeScript
- **状态管理**: Pinia
- **路由**: Vue Router
- **样式**: Tailwind CSS
- **构建工具**: Vite

### 3.2 目录结构

```
src/
├── views/
│   ├── Home.vue             # 相册模式
│   ├── PhotoWall.vue        # 瀑布流
│   ├── RandomGallery.vue    # 随机模式
│   ├── AlbumDetail.vue
│   ├── PhotoDetail.vue
│   └── admin/Persons.vue    # 人物管理：左侧多列、自适应列数，右侧五级 Tab，面板可拖拽记忆
├── components/
│   ├── AlbumCard.vue
│   └── FilterPanel.vue
├── stores/
│   ├── photo.ts
│   └── theme.ts
├── api/
│   └── index.ts
└── router/
    └── index.ts
```

### 3.3 状态管理

#### Photo Store
- `albums`: 相册列表
- `photos`: 图片列表
- `currentAlbum`: 当前相册
- `currentPhoto`: 当前图片
- `loading`: 加载状态

#### Theme Store
- `isDark`: 深色模式状态
- `toggleTheme()`: 切换主题

## 4. 数据库设计

### 4.1 表关系图

```
album (相册)
  id (PK)
  name
  path
  photo_count
  └── album_tag (album_id, tag_id)

photo (图片)
  id (PK)
  album_id (FK)
  filename
  original_path / thumbnail_path / webp_path
  content_hash / path_hash
  exif_data (JSON), quality_score
  └── photo_tag (photo_id, tag_id)

face (人脸)
  id (PK)
  photo_id (FK)
  person_id (nullable)
  embedding (向量字符串)
  confidence, bbox
  is_confirmed (人工确认标记)

person_profile (人物)
  id (PK)
  name, description
  created_at, updated_at

tag (标签)
  id (PK)
  name (UNIQUE)
```

### 4.2 索引设计

- `album.path`: UNIQUE
- `photo.album_id`、`photo.taken_at`、`photo.camera_model`、`photo.quality_score`
- `photo.content_hash` / `photo.path_hash`
- `tag.name`: UNIQUE
- `face.photo_id`、`face.person_id`

## 5. 核心功能实现

### 5.1 文件夹自动扫描（hash 优先复用）

**流程**:
1. 定时/手动触发（默认1小时）
2. 递归扫描指定目录，识别图片
3. 按 `contentHash` 优先复用已有记录，再回退 `pathHash` / `originalPath`
4. 提取 EXIF；生成缩略图/WebP；色彩分析；质量评分
5. 保存数据库，避免重复写入；关闭期间任务检测

**技术实现**:
- `@Scheduled` 定时 + 管理接口手动触发
- `metadata-extractor` 提取 EXIF
- `thumbnailator` / `ImageIO` 生成缩略图与 WebP

### 5.2 三种展示模式

#### 相册模式
- 网格布局（响应式：1-3列）
- 封面布局：左侧竖图 + 右侧上下横图
- 支持标签筛选

#### 图墙模式
- 瀑布流布局（CSS Masonry）
- 无限滚动加载
- 响应式列数（1-4列）

#### 随机模式
- 网格布局
- 随机高质量图片
- 可设置质量阈值

### 5.3 高级筛选

**筛选维度**:
1. **标签筛选**: 多标签组合（AND/OR逻辑）
2. **EXIF筛选**: 
   - 相机型号
   - 镜头型号
   - 光圈范围
   - ISO范围
3. **色彩筛选**: 主色调匹配
4. **质量筛选**: 质量评分阈值

**实现方式**:
- 后端：JPA动态查询
- 前端：筛选面板组件

### 5.4 性能优化

#### 缓存策略
- **Redis缓存**:
  - 相册列表（1小时）
  - 图片详情（1小时）
  - 热点数据

#### 图片优化
- **缩略图**: 400x400像素
- **WebP格式**: 质量85%
- **懒加载**: 前端实现
- **渐进式加载**: 占位符

#### 数据库优化
- 分页查询
- 索引优化
- 查询优化

### 5.5 人脸识别与人物管理
- 检测：RetinaFace ONNX（可配置开关/模型路径/阈值），置信度、面积、长宽比过滤
- 识别：R50/R100 embedding，归一化向量
- 聚类：平均向量 + 多代表向量；分层阈值；离群/合并保护；最小样本约束
- 推荐分层：已确认、自动分配、相似推荐、套图推荐（同/上级/再上级目录阈值放宽）、未分配
- 交互：未命名聚类直接命名建人；就地编辑姓名/备注、删除；左右面板可拖拽并记忆宽度

## 6. 部署架构

### 6.1 Docker Compose

```
services:
  mysql:      # 数据库
  redis:      # 缓存
  backend:    # SpringBoot应用
  frontend:   # Nginx + Vue静态文件
```

### 6.2 数据卷

- `mysql_data`: MySQL数据持久化
- `redis_data`: Redis数据持久化
- `./data/photos`: 图片文件（只读）
- `./data/thumbnails`: 缩略图（读写）

## 7. 安全考虑

1. **文件访问**: 通过Nginx代理，限制访问路径
2. **API安全**: CORS配置，生产环境应限制来源
3. **管理员**: 需要实现认证授权（待完善）
4. **文件上传**: 需要验证文件类型和大小（待实现）

## 8. 扩展性设计

### 8.1 水平扩展
- 后端：多实例 + 负载均衡
- 数据库：主从复制
- Redis：集群模式

### 8.2 功能扩展
- 用户系统
- 收藏功能
- 评论系统
- 分享功能
- 图片上传
- 移动端APP

## 9. 监控与日志

### 9.1 日志
- Spring Boot Logging
- 文件日志 + 控制台日志
- 日志级别：INFO/WARN/ERROR

### 9.2 监控指标（建议）
- 扫描任务执行时间
- API响应时间
- 缓存命中率
- 数据库连接池状态

## 10. 开发规范

### 10.1 代码规范
- Java: Google Java Style Guide
- TypeScript: ESLint + Prettier
- 命名规范：驼峰命名

### 10.2 Git工作流
- 主分支：main
- 功能分支：feature/*
- 修复分支：fix/*

### 10.3 提交规范
- feat: 新功能
- fix: 修复bug
- docs: 文档更新
- style: 代码格式
- refactor: 重构
- test: 测试

