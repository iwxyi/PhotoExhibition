# 快速启动指南

> 💡 **快速开始**：如果已安装所有环境，可以直接使用启动脚本：
> - macOS/Linux: `./start-backend.sh` 和 `./start-frontend.sh`
> - Windows: `start-backend.bat` 和 `start-frontend.bat`
> 
> 详细步骤请继续阅读下文。

## 前置要求

### 必需环境
- **Java 11+** (推荐 Java 11 或 17)
- **Maven 3.6+**
- **Node.js 18+** 和 npm
- **MySQL 5.7+** 或 8.0+
- **Redis 6+**

### 检查环境

```bash
# 检查Java版本
java -version

# 检查Maven版本
mvn -version

# 检查Node.js版本
node -v
npm -v

# 检查MySQL版本
mysql --version

# 检查Redis版本
redis-cli --version
```

## 方式一：本地运行（推荐）

### 1. 准备图片目录

```bash
# 创建图片目录
mkdir -p data/photos

# 将你的照片文件夹放入 data/photos 目录
# 例如：
# data/photos/
#   ├── 旅行/
#   │   ├── photo1.jpg
#   │   └── photo2.jpg
#   └── 人像/
#       └── photo3.jpg
```

### 2. 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

### 3. 初始化数据库

数据库会在首次启动时自动初始化（通过schema.sql）。

### 4. 触发扫描

```bash
# 方式1: 通过API触发
curl -X POST http://localhost:6060/api/admin/scan

# 方式2: 等待自动扫描（默认1小时）
```

### 5. 访问应用

- 前端: http://localhost:3000
- 后端API: http://localhost:6060/api
- MySQL: localhost:3306
- Redis: localhost:6379

## 方式二：本地开发

### 后端开发

#### 1. 安装依赖

```bash
cd backend
mvn clean install
```

#### 2. 配置数据库

修改 `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/photo_exhibition?...
    username: root
    password: your_password
```

#### 3. 创建数据库

```bash
mysql -u root -p
CREATE DATABASE photo_exhibition CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
exit

# 导入表结构
mysql -u root -p photo_exhibition < database/schema.sql
```

#### 4. 启动Redis

```bash
# macOS
brew install redis
brew services start redis

# Linux
sudo systemctl start redis
`
# 或使用Docker
docker run -d -p 6379:6379 redis:7-alpine
```

#### 5. 配置图片路径

修改 `application.yml`:

```yaml
photo:
  scan:
    base-path: /path/to/your/photos
```

#### 6. 运行应用

```bash
mvn spring-boot:run
```

### 前端开发

#### 1. 安装依赖

```bash
cd frontend
npm install
```

#### 2. 启动开发服务器

```bash
npm run dev
```

访问: http://localhost:3000

#### 3. 构建生产版本

```bash
npm run build
```

## 配置说明

### 后端配置 (application.yml)

```yaml
photo:
  scan:
    base-path: /data/photos          # 图片根目录
    supported-formats: jpg,jpeg,png,heic,raw  # 支持的格式
    thumbnail-width: 400             # 缩略图宽度
    thumbnail-height: 400            # 缩略图高度
    webp-quality: 0.85               # WebP质量
    scan-interval: 3600              # 扫描间隔（秒）
```

### 环境变量（Docker）

可以通过环境变量覆盖配置：

```yaml
environment:
  PHOTO_SCAN_BASE_PATH: /data/photos
  SPRING_DATASOURCE_URL: jdbc:mysql://...
```

## 常见问题

### 1. 图片无法显示

**问题**: 图片路径404

**解决**: 
- **本地运行**：检查 `application.yml` 中的 `base-path` 配置是否正确
- 检查图片文件是否存在
- 检查文件权限（确保应用有读取权限）
- 查看后端日志确认路径解析是否正确
- 访问图片URL：`http://localhost:6060/files/图片路径`

### 2. 扫描不工作

**问题**: 图片没有被扫描到数据库

**解决**:
- 检查 `application.yml` 中的 `base-path` 路径是否正确
- 检查图片格式是否在支持列表中（jpg, jpeg, png, heic, raw等）
- 查看后端控制台日志，查找错误信息
- 确认图片文件有读取权限
- 手动触发扫描: `curl -X POST http://localhost:6060/api/admin/scan`
- 检查数据库连接是否正常

### 3. 数据库连接失败

**问题**: 后端无法连接MySQL

**解决**:
- **检查MySQL是否启动**:
  ```bash
  # macOS
  brew services list
  # Linux
  sudo systemctl status mysql
  # Windows: 检查服务管理器
  ```
- **检查数据库配置**：确认 `application.yml` 中的用户名、密码、数据库名正确
- **测试数据库连接**:
  ```bash
  mysql -u root -p -e "USE photo_exhibition; SHOW TABLES;"
  ```
- **检查数据库是否存在**:
  ```bash
  mysql -u root -p -e "SHOW DATABASES LIKE 'photo_exhibition';"
  ```
- **常见错误**:
  - `Access denied`: 用户名或密码错误
  - `Unknown database`: 数据库未创建，执行 `database/schema.sql`
  - `Connection refused`: MySQL服务未启动

### 4. Redis连接失败

**问题**: 缓存功能不工作

**解决**:
- **检查Redis是否启动**:
  ```bash
  # macOS
  brew services list | grep redis
  # Linux
  sudo systemctl status redis-server
  # 测试连接
  redis-cli ping
  ```
- **检查Redis配置**：确认 `application.yml` 中的Redis配置
- **手动启动Redis**:
  ```bash
  # macOS
  brew services start redis
  # Linux
  sudo systemctl start redis-server
  ```
- **注意**：Redis连接失败不影响主要功能，只是性能会下降（缓存不工作）

### 5. 前端API请求失败

**问题**: 前端无法获取数据

**解决**:
- **检查后端是否运行**: 
  - 访问 http://localhost:6060/api/albums 应该返回JSON
  - 查看后端控制台是否有错误日志
- **检查CORS配置**：后端已配置允许所有来源，一般不会有问题
- **检查代理配置**：确认 `vite.config.ts` 中的代理配置正确
- **浏览器控制台检查**：
  - 打开浏览器开发者工具（F12）
  - 查看Network标签，检查API请求状态
  - 查看Console标签，检查是否有错误信息
- **常见错误**:
  - `Network Error`: 后端未启动或端口不对
  - `404 Not Found`: API路径错误
  - `CORS error`: 后端CORS配置问题（已配置，一般不会出现）

## 开发建议

### 1. 图片组织建议

```
data/photos/
├── 旅行/
│   ├── IMG_001.jpg
│   └── IMG_002.jpg
├── 人像/
│   └── portrait_001.jpg
└── 风景/
    └── landscape_001.jpg
```

文件夹名会自动提取为标签。

### 2. 性能优化建议

- 首次扫描可能需要较长时间，建议分批添加图片
- 大量图片建议使用SSD存储
- 生产环境建议配置Redis持久化

### 3. 安全建议

- 修改默认管理员密码
- 配置HTTPS
- 限制API访问来源
- 定期备份数据库

## 下一步

1. 查看 [README.md](README.md) 了解完整功能
2. 查看 [API.md](API.md) 了解API接口
3. 查看 [ARCHITECTURE.md](ARCHITECTURE.md) 了解架构设计

### 6. Maven构建失败

**问题**: `mvn clean install` 或 `mvn spring-boot:run` 失败

**解决**:
- 检查Java版本：`java -version`（需要Java 11+）
- 检查Maven版本：`mvn -version`
- 清理并重新构建：`mvn clean install -U`
- 检查网络连接（需要下载依赖）
- 如果在中国，可能需要配置Maven镜像源

### 7. npm安装失败

**问题**: `npm install` 失败

**解决**:
- 检查Node.js版本：`node -v`（需要18+）
- 清理缓存：`npm cache clean --force`
- 删除 `node_modules` 和 `package-lock.json`，重新安装
- 如果在中国，使用淘宝镜像：
  ```bash
  npm config set registry https://registry.npmmirror.com
  ```

### 8. 端口被占用

**问题**: 端口6060或3000已被占用

**解决**:
- **查找占用端口的进程**:
  ```bash
  # macOS/Linux
  lsof -i :6060
  lsof -i :3000
  # Windows
  netstat -ano | findstr :6060
  ```
- **修改端口**:
  - 后端：修改 `application.yml` 中的 `server.port`
  - 前端：修改 `vite.config.ts` 中的 `server.port`

## 技术支持

如遇问题，请按以下顺序检查：

1. **后端日志**：查看控制台输出的错误信息
2. **数据库连接**：确认MySQL和Redis服务运行正常
3. **文件权限**：确认图片目录有读取权限
4. **配置文件**：检查 `application.yml` 中的配置是否正确
5. **端口占用**：确认6060和3000端口未被占用

## 开发模式 vs 生产模式

### 开发模式（当前配置）
- 前端：`npm run dev` - 热重载，开发服务器
- 后端：`mvn spring-boot:run` - 开发模式，自动重载

### 生产模式
- 前端：`npm run build` 然后使用Nginx等Web服务器
- 后端：`mvn clean package` 然后 `java -jar target/*.jar`

