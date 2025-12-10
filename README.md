# 高端摄影作品展示平台

一个基于 SpringBoot + Vue3 的全栈摄影作品展示平台，支持自动扫描、多种展示模式、高级筛选等功能。

## 技术栈

### 后端
- SpringBoot 2.7+
- MySQL 5.7
- Redis
- Maven

### 前端
- Vue 3 + TypeScript
- Tailwind CSS
- Pinia
- Vite

## 核心功能

### 1. 文件夹自动扫描
- 监控指定目录，自动读取图片
- 提取EXIF信息和生成缩略图
- 从文件夹名自动生成标签

### 2. 三种展示模式
- **相册模式**：文件夹结构展示，每个相册封面为"左侧一张竖图+右侧上下两张横图"
- **图墙模式**：所有图片瀑布流展示，支持无限滚动
- **随机模式**：随机展示高质量图片组合

### 3. 高级筛选
- 多标签组合筛选
- EXIF条件筛选（相机型号、镜头、光圈等）
- 色彩筛选

### 4. 性能优化
- 图片懒加载和渐进式加载
- WebP格式自动转换
- Redis热点数据缓存

### 5. 管理员功能
- 手动调整标签
- 重新生成封面
- 批量操作

## 项目结构

```
PhotoExhibition/
├── backend/                 # SpringBoot后端
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/photoexhibition/
│   │       │       ├── controller/    # API控制器
│   │       │       ├── service/      # 业务逻辑
│   │       │       ├── repository/    # 数据访问
│   │       │       ├── entity/        # 实体类
│   │       │       ├── dto/           # 数据传输对象
│   │       │       └── config/        # 配置类
│   │       └── resources/
│   │           └── application.yml
│   └── pom.xml
├── frontend/                # Vue3前端
│   ├── src/
│   │   ├── views/          # 页面组件
│   │   ├── components/     # 通用组件
│   │   ├── stores/         # Pinia状态管理
│   │   ├── api/            # API接口
│   │   └── router/         # 路由配置
│   └── package.json
├── database/               # 数据库脚本
│   └── schema.sql
├── docker-compose.yml      # Docker编排
└── README.md
```

## 快速开始

### 方式一：Docker部署（推荐）

1. 准备图片目录
```bash
mkdir -p data/photos
# 将你的照片文件夹放入 data/photos 目录
```

2. 启动服务
```bash
docker-compose up -d
```

3. 访问应用
- 前端：http://localhost:3000
- 后端API：http://localhost:6060/api

### 方式二：本地开发

#### 后端开发

1. 安装依赖
```bash
cd backend
mvn install
```

2. 配置数据库
- 修改 `application.yml` 中的数据库连接信息
- 执行 `database/schema.sql` 创建表结构

3. 启动Redis
```bash
redis-server
```

4. 运行应用
```bash
mvn spring-boot:run
```

#### 前端开发

1. 安装依赖
```bash
cd frontend
npm install
```

2. 启动开发服务器
```bash
npm run dev
```

3. 构建生产版本
```bash
npm run build
```

## 配置说明

### 后端配置（application.yml）

```yaml
photo:
  scan:
    base-path: /data/photos          # 图片根目录
    supported-formats: jpg,jpeg,png  # 支持的格式
    thumbnail-width: 400             # 缩略图宽度
    thumbnail-height: 400            # 缩略图高度
    scan-interval: 3600              # 自动扫描间隔（秒）
```

### 数据库表结构

主要表：
- `album`: 相册表
- `photo`: 图片表
- `tag`: 标签表
- `album_tag`: 相册标签关联
- `photo_tag`: 图片标签关联
- `admin`: 管理员表

详细结构见 `database/schema.sql`

## API接口

### 相册相关
- `GET /api/albums` - 获取所有相册
- `GET /api/albums/{id}` - 获取相册详情
- `POST /api/albums/filter` - 筛选相册

### 图片相关
- `GET /api/photos/wall` - 图墙模式
- `GET /api/photos/random` - 随机模式
- `GET /api/photos/album/{albumId}` - 获取相册图片
- `GET /api/photos/{id}` - 获取图片详情
- `POST /api/photos/filter` - 高级筛选

### 管理员
- `POST /api/admin/scan` - 手动触发扫描
- `PUT /api/admin/albums/{id}/tags` - 更新相册标签`
- `POST /api/admin/albums/{id}/regenerate-cover` - 重新生成封面

## 功能特性

### 自动扫描
- 定时扫描指定目录（默认1小时）
- 自动提取EXIF信息
- 生成缩略图和WebP格式
- 分析图片色彩
- 计算质量评分

### 展示模式

#### 相册模式
- 网格布局展示相册
- 每个相册封面由3张图片组成（左竖图+右上下横图）
- 支持标签筛选

#### 图墙模式
- 瀑布流布局
- 无限滚动加载
- 响应式列数（1-4列）

#### 随机模式
- 随机展示高质量图片
- 可设置最小质量评分阈值

### 筛选功能
- 多标签组合筛选
- EXIF参数筛选（相机、镜头、光圈、ISO等）
- 色彩筛选
- 质量评分筛选

## 性能优化

1. **Redis缓存**
   - 相册列表缓存
   - 图片详情缓存
   - 热点数据缓存1小时

2. **图片优化**
   - 缩略图生成
   - WebP格式转换
   - 懒加载

3. **数据库优化**
   - 索引优化
   - 分页查询

## 开发计划

- [ ] 图片上传功能
- [ ] 用户系统
- [ ] 收藏功能
- [ ] 评论系统
- [ ] 分享功能
- [ ] 移动端适配优化

## 许可证

MIT License

