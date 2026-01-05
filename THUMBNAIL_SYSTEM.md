# 三级缩略图系统

本文档描述了照片展览应用实现的三级缩略图系统。

## 缩略图等级

### 1. 小缩略图 (Small Thumbnail)
- **用途**: 压缩封面的缩略图、PhotoViewer下面的缩略图列表
- **尺寸**: 200x200px
- **质量**: 85%
- **文件名**: `basename_small.jpg`

### 2. 中缩略图 (Medium Thumbnail)
- **用途**: 各种瀑布流显示（图墙、随机、相册详情的图片）
- **尺寸**: 800x800px
- **质量**: 90%
- **文件名**: `basename_medium.jpg`

### 3. 大缩略图 (Large Thumbnail)
- **用途**: PhotoViewer中未打开原图时看的大图，清晰度高
- **尺寸**: 1920x1920px
- **质量**: 95%
- **特殊逻辑**: 如果无法进一步压缩（压缩后大小 > 80%原图），则不生成，使用原图节省空间
- **文件名**: `basename_large.jpg`

## 数据库字段

在`photo`表中添加了以下字段：

```sql
ALTER TABLE `photo`
ADD COLUMN `small_thumb_path` VARCHAR(1000) DEFAULT NULL COMMENT '小缩略图路径（用于封面和缩略图列表，尺寸较小）',
ADD COLUMN `medium_thumb_path` VARCHAR(1000) DEFAULT NULL COMMENT '中缩略图路径（用于瀑布流显示，尺寸中等）',
ADD COLUMN `large_thumb_path` VARCHAR(1000) DEFAULT NULL COMMENT '大缩略图路径（用于PhotoViewer大图显示，清晰度高）';
```

## 配置参数

在`application.yml`中添加了以下配置：

```yaml
photo:
  scan:
    thumbnail:
      small:
        width: 200
        height: 200
        quality: 0.85
      medium:
        width: 800
        height: 800
        quality: 0.90
      large:
        width: 1920
        height: 1920
        quality: 0.95
        skip-if-no-benefit: true  # 如果无法进一步压缩则不生成
```

## 查看原图功能

### 前端设置
在主页设置菜单中添加了"查看原图"开关：
- 默认关闭
- 存储在localStorage中：`pe-view-original-enabled`

### PhotoViewer逻辑
1. **默认显示**: 优先显示大缩略图（如果存在），否则显示webp或原图
2. **查看原图**: 当启用"查看原图"且当前正在查看大缩略图时，右上角显示"查看原图"按钮
3. **切换**: 点击"查看原图"按钮后显示原图，并显示"返回缩略图"按钮

## 组件使用

### 封面显示 (AlbumCard.vue)
使用小缩略图：
```javascript
const getImageUrl = (photo: any) => {
  if (photo.smallThumbPath) {
    return `/api/files${photo.smallThumbPath}`
  }
  // 回退逻辑...
}
```

### 瀑布流显示 (PhotoWall.vue, RandomGallery.vue, AlbumDetail.vue)
使用中缩略图：
```javascript
const getImageUrl = (photo: any) => {
  if (photo.mediumThumbPath) {
    return `/api/files${photo.mediumThumbPath}`
  }
  // 回退逻辑...
}
```

### PhotoViewer大图显示
智能选择显示内容：
```javascript
const getImageUrl = (photo: Photo) => {
  if (viewOriginalEnabled.value && viewingOriginal.value) {
    if (photo.originalPath) return `/api/files${photo.originalPath}`
  }

  if (photo.largeThumbPath) {
    return `/api/files${photo.largeThumbPath}`
  }

  if (photo.webpPath) return `/api/files${photo.webpPath}`
  if (photo.originalPath) return `/api/files${photo.originalPath}`
  return ''
}
```

## 缩略图生成逻辑

### 生成时机
- 新图片扫描时自动生成三级缩略图
- 扫描现有图片时检查并重新生成缺失的缩略图

### 大缩略图优化
大缩略图有特殊的生成逻辑：
1. 检查原图尺寸是否超过目标尺寸
2. 估算压缩后的文件大小
3. 如果预计压缩效果不佳（>80%原图大小），跳过生成

### 兼容性
- 原有的`thumbnailPath`字段继续使用小缩略图路径保持兼容性
- 所有新逻辑都有适当的回退机制

## 文件存储

缩略图存储在图片目录的`.thumbnails`子目录中：
```
photos/
├── album1/
│   ├── .thumbnails/
│   │   ├── photo1_small.jpg
│   │   ├── photo1_medium.jpg
│   │   ├── photo1_large.jpg
│   │   └── photo1_thumb.jpg  # 兼容性文件
│   └── photo1.jpg
```

## 性能优化

1. **按需生成**: 大缩略图只在真正需要时生成
2. **智能压缩**: 避免无意义的压缩操作
3. **缓存友好**: 客户端可以根据设置选择合适的缩略图
4. **存储效率**: 通过不生成低效的大缩略图来节约磁盘空间

## 迁移说明

运行数据库迁移脚本来添加新字段：
```bash
# 如果使用Docker
docker exec -i photo-exhibition-mysql mysql -u root -proot photo_exhibition < database/migration_add_thumbnail_levels.sql

# 或者直接执行（如果MySQL在本机）
mysql -u root -proot photo_exhibition < database/migration_add_thumbnail_levels.sql
```

重启后端服务后，Spring Boot的JPA会自动创建新字段（如果配置了`spring.jpa.hibernate.ddl-auto: update`）。

然后运行一次完整扫描来生成缺失的缩略图：
```bash
# 在管理界面中触发重新扫描，或等待定时扫描
```

## 清空缩略图功能

为了重新生成所有三级缩略图，系统提供了清空缩略图的功能。

### API端点
```
POST /api/admin/thumbnails/clear
```

### 功能说明
- 删除所有现有的缩略图文件（`_thumb.jpg`, `_small.jpg`, `_medium.jpg`, `_large.jpg`）
- 清空数据库中所有照片的缩略图路径字段
- 为下次扫描重新生成三级缩略图做准备

### 使用方法
1. 在管理后台的"API测试工具"中选择"清空缩略图（重新生成三级缩略图）"
2. 点击"发送请求"，确认操作
3. 执行完成后，再次触发扫描来重新生成所有缩略图

### 注意事项
- 此操作会删除所有现有缩略图文件（`_thumb.jpg`, `_small.jpg`, `_medium.jpg`, `_large.jpg`）
- 不会删除其他文件（如`_原图.jpg`等由其他工具生成的文件）
- 执行后需要重新扫描来生成新的三级缩略图
- 建议在系统负载较低时执行此操作
- 操作不可逆，请谨慎使用
