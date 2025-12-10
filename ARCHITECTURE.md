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
- `Photo`: 图片实体
- `Tag`: 标签实体

#### 2.2.2 服务模块 (Service)
- `PhotoScanService`: 图片扫描服务
  - 文件夹监控
  - EXIF提取
  - 缩略图生成
  - WebP转换
  - 色彩分析
  
- `AlbumService`: 相册服务
  - 相册查询
  - 封面生成
  - 标签管理
  
- `PhotoService`: 图片服务
  - 图片查询
  - 筛选功能
  - 统计功能

- `ColorAnalysisService`: 色彩分析服务
  - 主色调提取
  - 调色板生成

#### 2.2.3 控制器模块 (Controller)
- `AlbumController`: 相册API
- `PhotoController`: 图片API
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
├── views/          # 页面组件
│   ├── Home.vue           # 首页（相册模式）
│   ├── PhotoWall.vue      # 图墙模式
│   ├── RandomGallery.vue  # 随机模式
│   ├── AlbumDetail.vue    # 相册详情
│   └── PhotoDetail.vue    # 图片详情
├── components/     # 通用组件
│   ├── AlbumCard.vue      # 相册卡片
│   └── FilterPanel.vue    # 筛选面板
├── stores/        # Pinia状态管理
│   ├── photo.ts           # 图片相关状态
│   └── theme.ts           # 主题状态
├── api/           # API接口
│   └── index.ts           # Axios配置
└── router/        # 路由配置
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
  ├── id (PK)
  ├── name
  ├── path
  └── photo_count
      │
      ├── album_tag (相册标签关联)
      │   ├── album_id (FK)
      │   └── tag_id (FK)
      │
      └── photo (图片)
          ├── id (PK)
          ├── album_id (FK)
          ├── filename
          ├── exif_data (JSON)
          └── ...
              │
              └── photo_tag (图片标签关联)
                  ├── photo_id (FK)
                  └── tag_id (FK)

tag (标签)
  ├── id (PK)
  └── name
```

### 4.2 索引设计

- `album.path`: UNIQUE索引
- `photo.album_id`: 索引
- `photo.taken_at`: 索引
- `photo.camera_model`: 索引
- `photo.quality_score`: 索引
- `tag.name`: UNIQUE索引

## 5. 核心功能实现

### 5.1 文件夹自动扫描

**流程**:
1. 定时任务触发（默认1小时）
2. 递归扫描指定目录
3. 识别图片文件（根据扩展名）
4. 提取EXIF信息
5. 生成缩略图
6. 转换WebP格式
7. 分析色彩
8. 计算质量评分
9. 保存到数据库

**技术实现**:
- `@Scheduled`: Spring定时任务
- `metadata-extractor`: EXIF提取
- `thumbnailator`: 缩略图生成
- `ImageIO`: 图片处理

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

