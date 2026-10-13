# PhotoExhibition

**建立自己的在线影展，让作品被看见，让回忆找得到。**

拍完一场活动，怎样让参与者找到自己的照片？积累了几年的作品，怎样回看某个人、某个主题，甚至比较不同年份的拍摄记录？想分享一个摄影系列，怎样同时呈现画面、拍摄信息和整个相册的氛围？

PhotoExhibition 把这些需求放进同一个可自部署的照片站点：用相册和照片墙展示作品，用人物归集与智能搜索查找照片，用目录管理和可配置存储维护照片库。你可以搭建个人摄影主页，也可以为团队中的不同用户提供独立的展示空间。

**值得先看的特点**：有氛围的相册展示 · 跨相册的人物照片 · 能查询和统计的 AI 搜索 · 摄影参数与颜色筛选 · 目录上传与扫描 · 多用户独立站点。

[为什么选择](#为什么选择-photoexhibition) · [特色功能](#特色功能) · [使用流程](#从照片目录到在线影展) · [技术架构](#技术架构) · [快速开始](#快速开始)

![相册首页：按分类呈现摄影系列与活动记录](./screenshots/首页-相册列表.png)

## 为什么选择 PhotoExhibition

照片的价值会随着整理、回看和分享被重新发现。PhotoExhibition 将作品展示与日常管理连接起来，让同一份照片库既能面向观众，也能服务创作者。

| 你的需求 | 可以怎样使用 | 带来的价值 |
| --- | --- | --- |
| 给作品一个完整的展示空间 | 设置相册封面、分类、排序与氛围，分享公开页面 | 按系列讲述一次拍摄，让观众从封面进入作品，再连续看图。 |
| 在活动照片中找到某个人 | 识别人脸、确认人物归属，再按人物浏览相关照片 | 跨相册回看同一个人，减少逐张翻找和重复整理。 |
| 忘了文件名，只记得时间和主题 | 用关键词、自然语言或组合筛选查找 | 从“记得拍过什么”出发寻找照片，也能进一步查看统计结果。 |
| 复盘自己的拍摄习惯 | 按相机、镜头、焦距、光圈、ISO、时间或颜色筛选 | 把拍摄参数和实际画面一起看，更方便挑选专题与回顾作品。 |
| 照片已经按文件夹存放 | 扫描目录，或上传文件夹并绑定相册 | 利用已有目录组织内容，后续可在后台调整封面、标签与展示方式。 |
| 想长期维护个人或团队站点 | 自行选择存储，配置独立用户站点与容量 | 让照片存放位置、站点名称和内容管理适应自己的使用方式。 |

适合摄影师建立作品集、活动团队分享现场照片，也适合家庭、学校和社团持续整理人物与活动记录。项目以 Web 站点运行，浏览者通过手机或电脑浏览器访问；站点运营者需要部署服务。人物识别和 AI 功能的启用条件见后文[能力与依赖](#能力与依赖)。

## 特色功能

### 1. 把一次拍摄呈现成一个摄影系列

相册封面和分类帮助观众选择想看的主题，照片墙适合自由浏览，随机发现则让旧作品有机会再次出现在眼前。你可以在连续浏览、按主题观看和随意探索之间切换。

- **相册展示**：支持封面、置顶、排序与子相册聚合，旅行、毕业、婚礼和个人专题都能按自己的结构组织。
- **相册氛围**：可使用樱花、雪花、烟花、流星等效果，并调整配色，让相册的呈现呼应拍摄主题。
- **专注看图**：支持原图查看、缩放与触控操作，展示 EXIF 拍摄信息；缩略图到大图的过渡与响应式布局兼顾手机和电脑。
- **分享与互动**：照片、相册和人物有独立页面入口，支持点赞与评论；相册可设置展示状态与下载权限。

![相册看图：连续浏览作品与查看照片细节](./screenshots/相册看图.png)

### 2. 从“哪次活动”切换到“照片里的谁”

同一个人可能出现在许多场活动和不同年份的相册中。人物归集提供另一种组织方式：识别人脸、确认人物姓名后，可以在人物页面集中回看相关照片。分享活动照片时，参与者也多了一个按人物查找的入口。

- **自动发现，人工确认**：人脸检测与特征匹配辅助归集，管理员可命名、合并、调整归属，逐步整理自己的图库。
- **辅助整理**：相似人物和同目录套图推荐，为未分配人脸提供可核对的候选。
- **人物展示**：人物详情页汇集照片；启用抠图模型后，还可用主体浮层呈现人物照片。

![人物详情：围绕同一个人回看照片](./screenshots/人物详情.png)

### 3. 用一句话查找照片，也能问问自己的图库

你可能记得“去年拍过樱花”，却忘了相册名称。AI 搜索支持将自然语言转为检索条件，并对命中的照片做统计分析。它既能帮助查找，也能让你从时间、主题和人物角度回看照片库。

以下是现有搜索逻辑覆盖的问题类型示例；结果取决于图库中的拍摄时间、标签、相册信息、人物归属和 AI 服务配置：

| 可以尝试的问题 | 对应能力 |
| --- | --- |
| 去年樱花有多少张？ | 在时间与主题范围内统计照片数量。 |
| 去年樱花主要在哪些相册、几月拍的？ | 按相册、月份或拍摄日期汇总结果。 |
| 去年和前年相比，樱花拍得更多还是更少？ | 对比不同年份的相关照片数量。 |
| 谁和谁最常同框？ | 根据人物归属与共同出现次数统计人物组合。 |
| 去年主要拍了什么主题？ | 根据相关照片和相册信息汇总主题。 |

搜索页可呈现照片、相册、人物以及分析结果。人物“同框”表达的是图库中的共同出现记录；主题和地点分析依赖已有信息，这些结果适合回顾记录，不应当作对现实人物关系或地点的确定判断。

### 4. 从色彩、器材和画面细节发现作品

挑选摄影专题时，除了时间和文件名，你还可能想找到同一支镜头拍的照片，或一组色调接近的作品。条件筛选与照片分析提供了更细的查找方式。

- **摄影参数筛选**：相机、镜头、焦距、快门、光圈、ISO 与拍摄时间可作为条件，便于回顾器材表现和拍摄习惯。
- **主题与色彩筛选**：结合分类、标签、颜色和最低质量评分，筛选想看的作品集合。
- **相似照片**：按颜色、标签、人脸和相册等特征寻找关联照片，帮助扩展同主题的浏览范围。
- **视觉分析**：配置支持图像理解的 AI 服务后，可生成画面描述、场景、氛围、视觉标签，以及构图、光线、色彩和拍摄建议。
- **辅助评分**：技术、构图与主题表现等分项提供选片参考；评分与建议由分析方法决定，审美判断仍由你掌握。

![条件筛选：结合摄影参数、主题与颜色挑选照片](./screenshots/条件筛选.png)

### 5. 把已有照片目录接入站点，持续整理也方便

照片已经按旅行、活动或年份分好文件夹时，可以利用现有目录进行扫描与相册组织。新增内容也可以在浏览器里上传文件或整个文件夹，再从后台整理。

- **目录与相册相连**：浏览目录、绑定相册、聚合子相册，调整封面与排序，兼顾文件组织和前台展示。
- **批量维护**：支持移动、删除、标签管理与人物整理，集中处理一批内容。
- **后台处理可查看**：扫描、EXIF、颜色、人脸和视觉分析等任务有进度与历史记录，便于确认处理状态。
- **存储可配置**：可接入本地目录、FTP、WebDAV、腾讯 COS、S3 兼容存储、MinIO、R2 等；上传、扫描和预览支持范围以对应提供者的能力提示为准。

### 6. 从个人作品站扩展到多个用户的展示空间

多用户模式为不同用户提供带 `/{slug}` 前缀的公开站点，可设置头像与中英文站点名称。创作者管理自己的内容，超级管理员集中维护用户、存储和系统配置，适合为多个摄影爱好者或团队成员提供展示空间。

- **账号与容量**：支持注册登录、个人资料、容量配额，以及可配置的邮件和短信验证。
- **分层管理**：内容后台负责相册、照片、标签与人物；超级管理员后台负责用户、存储、模型及第三方服务。
- **运营扩展**：包含会员套餐、容量与订单管理，以及支付渠道配置入口；真实收款需针对所选渠道完成联调。

## 从照片目录到在线影展

1. **接入照片**：配置照片目录或受支持的存储，也可在后台上传文件和文件夹。
2. **整理内容**：扫描生成照片记录和缩略图，绑定相册，设置封面、标签与展示顺序；需要时启用模型和视觉分析。
3. **发布展示**：选择对外展示的相册，设置下载权限，将站点、相册或照片页面链接分享出去。
4. **持续回看**：按人物、主题、参数或自然语言搜索照片，为下一次专题整理和作品复盘积累线索。

## 更多界面

<details>
<summary>照片墙、随机发现与移动端</summary>

![照片墙](./screenshots/首页-图墙列表.png)

![随机发现](./screenshots/首页-随机列表.png)

![人物列表](./screenshots/首页-人物列表.png)

![移动端相册首页](./screenshots/首页-相册列表-手机版.png)

</details>

<details>
<summary>内容管理与人物整理后台</summary>

![管理后台](./screenshots/后台-首页.png)

![相册管理](./screenshots/后台-相册管理.png)

![人物管理](./screenshots/后台-人物管理.png)

![人物套图推荐](./screenshots/后台-人物管理-套图推荐.png)

![图片管理](./screenshots/后台-图片管理.png)

![系统设置](./screenshots/后台-系统设置.png)

</details>

截图用于展示现有界面风格，具体布局和可用功能以当前版本及部署配置为准。

---

## 技术架构

### 技术栈与代码入口

| 层级 | 技术与职责 | 主要入口 |
| --- | --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Tailwind CSS；公开页面与管理后台 | `frontend/src/views`、`frontend/src/components` |
| API 与业务 | Java 11、Spring Boot 2.7、Spring Data JPA；照片管理、用户与任务服务 | `backend/src/main/java/com/photoexhibition/controller`、`service` |
| 数据与配置 | MySQL 存储元数据；Redis 或内存缓存；文件与远程存储保存照片资源 | `backend/src/main/resources/application.yml` |
| 本地模型 | ONNX Runtime；人脸、场景、情绪、显著性与抠图等分析 | `service` 中的模型相关服务、`backend/pom.xml` |
| AI 检索 | 自然语言解析、计划执行、统计聚合与结果组织 | `backend/src/main/java/com/photoexhibition/aisearch`、`service/AiSearchService.java` |
| 远程视觉分析 | 发送图片到配置的图像理解服务，保存结构化分析结果 | `service/PhotoVisualAnalysisService.java` |

前端通过 `/api` 访问后端。后端默认监听 `6060`，开发前端默认监听 `3030`，Vite 将 API 请求代理到后端；生产环境使用反向代理部署静态前端。

### 照片处理与查询

- **照片处理**：目录扫描与上传将内容纳入照片记录，后台生成缩略图、读取 EXIF 并执行所需分析；缩略图按展示场景分级，默认尺寸为 540、720 和 1920 像素。
- **可选视觉分析**：启用并配置远程 AI 后，扫描完成事件可将图片视觉分析加入后台任务，分析结果保存后供后续展示与处理使用。
- **搜索与分析**：AI 搜索将输入转换为结构化条件或执行计划，由后端对图库数据进行过滤、聚合、排序和分页，再组织照片、人物与统计结果。
- **管理与运维**：公开站点、用户后台和超级管理员后台承担不同职责；任务面板提供处理进度、历史及运行诊断，模型面板提供模型可用性和重建入口。

### 能力与依赖

| 能力 | 启用条件与范围 |
| --- | --- |
| 相册浏览、目录管理、搜索筛选 | 需要运行前后端、数据库和照片存储；EXIF 等条件依赖照片已有数据。 |
| 人脸识别、场景/情绪模型与抠图 | 需要配置并提供对应 ONNX 模型文件；仓库不包含 `.onnx` 模型。 |
| AI 搜索与视觉分析 | 需要配置服务地址、密钥和匹配的模型；视觉分析需使用支持图片输入的服务，会将用于分析的图片发送给该服务。 |
| 远程存储 | 需要对应账号、密钥和目录配置；不同提供者的管理、扫描与预览能力不同。 |
| 邮件、短信与支付 | 需要服务商配置；模拟模式与真实服务分开，支付需验证所选渠道的下单、回调、验签和退款流程。 |
| 缓存 | 可用 Redis；无 Redis 的本地运行需传入 `--spring.redis.host=disabled` 使用内存缓存回退。 |

下载权限控制站点提供的下载入口；对外公开的图片仍可被浏览器访问。分析结果、人物归属和展示状态建议由管理者按实际内容核对。

## 快速开始

### Docker 部署
```bash
# 1. 获取项目
git clone https://github.com/iwxyi/PhotoExhibition.git
cd PhotoExhibition

# 2. 组织您的照片文件
mkdir -p data/photos
# 建议的文件结构：
# data/photos/
# ├── 人像/
# │   ├── 2024.01.15 张三生日聚会/
# │   │   ├── IMG_0001.jpg
# │   │   └── IMG_0002.jpg
# │   └── 2024.02.20 李四毕业照/
# │       ├── IMG_0003.jpg
# │       └── IMG_0004.jpg
# ├── 风景/
# │   ├── 2024.03.10 樱花季/
# │   └── 2024.04.05 海边日出/
# └── 活动/
#     ├── 2024.05.01 五一劳动节/
#     └── 2024.06.15 端午节龙舟赛/

# 3. 一键启动
docker-compose up -d

# 4. 开始浏览
# 打开浏览器访问: http://localhost:3030
# 首次初始化的默认超级管理员账户: admin / admin
```

首次登录后请立即修改默认密码。`docker-compose.yml` 使用独立的 MySQL、Redis 容器和固定端口，适合单实例试用；生产部署应自行设置数据库密码、密钥、持久化目录和反向代理。

### 本地开发

准备 Java 11、Maven、Node.js、npm 与 MySQL，并创建 `photo_exhibition` 数据库。数据库连接默认值在 `backend/src/main/resources/application.yml` 中，可通过 `PHOTO_DB_HOST`、`PHOTO_DB_PORT`、`PHOTO_DB_NAME`、`PHOTO_DB_USER`、`PHOTO_DB_PASSWORD` 覆盖。

在两个终端分别运行：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.awt.headless=true" -Dspring-boot.run.arguments="--spring.redis.host=disabled"
```

```bash
cd frontend
npm install
npm run dev
```

前端默认访问 `http://localhost:3030`，Vite 将 `/api` 代理到默认的 `http://127.0.0.1:6060`。构建验证分别使用 `cd backend && mvn test` 和 `cd frontend && npx vite build`。

### 同一 MySQL 上部署两个实例

<details>
<summary>展开 Windows 启动、本机配置与多实例部署说明</summary>

Windows 可直接运行根目录的 `start.bat`，依次拉取代码、构建前后端并启动后端，需要已安装 Git、Node.js/npm、Java 11+、Maven，并已启动 MySQL。未配置时使用默认端口 `6060` 和数据库 `photo_exhibition`。需要自定义时，取消脚本顶部对应 `set` 行的 `REM` 注释并修改值；配置仅作用于当前脚本及其启动的程序，不影响其他实例。前端构建产物仍需由 Nginx 等 Web 服务部署，该脚本不会启动前端开发服务器。

更推荐将本机配置放在根目录的 `start.local.bat`，该文件已加入 `.gitignore`，不会被正常的 `git pull` 覆盖，也不会提交数据库密码。`start.bat` 会自动加载它。例如：

```bat
@echo off
set "SERVER_PORT=6061"
set "PHOTO_DB_NAME=photo2"
set "PHOTO_DB_USER=photo_user_2"
set "PHOTO_DB_PASSWORD=your-password"
set "PHOTO_SCAN_BASE_PATH=D:\PhotoExhibition2\data\photos"
set "APP_SECURITY_CONFIG_KEY_FILE=D:\PhotoExhibition2\secrets\config.key"
set "JWT_SECRET=replace-with-an-independent-long-random-secret"
exit /b 0
```

BAT 配置中的密码若含 `%`，应写为 `%%`；不要在配置文件中使用 `exit`，应使用 `exit /b`。直接修改受 Git 管理的 `start.bat` 可能在后续拉取时产生冲突，因此长期部署建议使用上述本机配置文件。脚本拉取失败时会提示警告并继续使用本地代码；构建或启动失败时则停止并保留错误输出。

两套工程可共用同一个 MySQL 服务，但必须使用不同数据库、后端端口、前端端口和数据目录。先创建两个库，并分别授予对应的数据库账号权限；不要让两个实例指向同一个库。

```sql
CREATE DATABASE photo1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE photo2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

在第一套工程的 `backend` 目录启动（保留原配置时无需设置这些变量）：

```bash
PHOTO_DB_NAME=photo1 SERVER_PORT=6060 PHOTO_DB_USER=photo_user_1 PHOTO_DB_PASSWORD='your-password' \
PHOTO_SCAN_BASE_PATH=/srv/photo1/photos APP_SECURITY_CONFIG_KEY_FILE=/srv/photo1/secrets/config.key \
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.redis.host=disabled"
```

在第二套工程的 `backend` 目录启动：

```bash
PHOTO_DB_NAME=photo2 SERVER_PORT=6061 PHOTO_DB_USER=photo_user_2 PHOTO_DB_PASSWORD='your-password' \
PHOTO_SCAN_BASE_PATH=/srv/photo2/photos APP_SECURITY_CONFIG_KEY_FILE=/srv/photo2/secrets/config.key \
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.redis.host=disabled"
```

生产环境使用打包后的 JAR 时，环境变量完全相同，只需将上面的 Maven 命令替换为：

```bash
java -Djava.awt.headless=true -jar target/photo-exhibition-backend-1.0.0.jar --spring.redis.host=disabled
```

也可将这些环境变量写入每套实例独立的 systemd `EnvironmentFile`，避免每次启动手动输入。Spring 启动参数仍可覆盖配置，例如 `--server.port=6061` 和 `--spring.datasource.url=...`。

分别在各自的 `frontend` 目录启动 Vite：

```bash
VITE_PORT=3030 VITE_API_PROXY_TARGET=http://127.0.0.1:6060 npm run dev
VITE_PORT=3031 VITE_API_PROXY_TARGET=http://127.0.0.1:6061 npm run dev
```

上面两条前端命令应在不同工程目录执行。访问 `http://localhost:3030` 和 `http://localhost:3031`。`PHOTO_DB_HOST`、`PHOTO_DB_PORT` 也可覆盖，默认是 `localhost:3306`；如需直接指定完整 JDBC URL，可使用 Spring 的 `SPRING_DATASOURCE_URL`。首次启动新库时 JPA 会建表。超级管理员设置中的本地存储根目录、用户资料根目录也要分别指向各自的目录，避免图片、缩略图及上传文件混用。已存在的加密配置迁移数据库时，必须同时迁移对应的 `config.key`；新库可使用独立密钥文件。生产环境还应为每套实例设置不同的 `JWT_SECRET`。

以上的 Vite 命令适合开发或直接运行。生产环境若部署静态前端，应让两个站点的反向代理分别把 `/api` 转发到各自的后端端口；仅修改前端监听端口不会改变 API 目标。现有 Docker Compose 文件会自行启动 MySQL/Redis，并使用固定容器名和端口，不适合原样启动两份；若使用容器部署，需要另配共享外部 MySQL、独立 Compose 项目名与宿主机端口。

</details>

### 照片组织建议

为了获得最佳的展示效果，请按以下结构组织您的照片：

```
data/photos/
├── [分类文件夹]/          # 如：人像、风景、活动、旅行等
│   ├── [时间] [相册名称]/  # 如：2024.01.15 张三生日聚会
│   │   ├── photo1.jpg     # 照片文件
│   │   ├── photo2.jpg
│   │   └── ...
│   └── [时间] [另一个相册]/
│       └── ...
└── ...
```

#### 命名规则

- **分类文件夹**：使用描述性的名称，如`人像`、`风景`、`活动`、`旅行`等
- **相册文件夹**：格式为`YYYY.MM.DD 相册描述`，日期前缀会被自动识别和剥离
- **照片文件**：默认扫描格式见 `backend/src/main/resources/application.yml` 中的 `photo.scan.supported-formats`；实际解码能力还取决于运行环境。

#### 使用建议

- 保持分类清晰，便于浏览和查找
- 使用有意义的相册名称，方便快速识别内容
- 同一场景的照片建议放在同一个相册中
- 系统会自动识别文件夹结构并生成相应的分类和标签

---

## 配置说明

### 核心配置

- **照片路径**: `photo.scan.base-path` - 照片存储目录
- **人脸识别**: `face.detection.confidence-threshold` - 检测置信度阈值
- **聚类阈值**: `face.clustering.min-threshold` - 人脸聚类相似度阈值
- **缩略图尺寸**: 由 `photo.scan.thumbnail.small/medium/large` 控制；当前默认分别为 540、720 和 1920 像素。

### AI 与模型配置

- `ai.scoring.enabled`、`ai.scene-recognition.enabled`、`ai.emotion-analysis.enabled` 等开关位于 `application.yml`；开关启用不代表模型已存在。
- ONNX Runtime 的 Maven 版本由 `backend/pom.xml` 中的平台 profile 选择。需要指定版本时可用 `ONNX_VERSION=1.16.3 mvn package -Pcustom-onnx`；构建后还需提供与服务配置匹配的模型文件。
- 模型可用性、缺失文件和重建任务可在超级管理员的“模型管理”与任务面板查看。
- AI 搜索和图片视觉分析需在后台配置服务地址、密钥及模型，并确认服务实际可用。图像评分和分析结果仅用于辅助整理，不代表客观摄影质量结论。

### 第三方服务

超级管理员后台可配置存储、短信、邮件和支付。各存储提供者的上传、浏览、预览、管理与扫描支持范围不同，以后台能力提示和实际联调结果为准。会员与订单模块包含模拟支付和真实渠道配置；上线收款前应完成所选渠道的回调、验签和退款联调。

---

## 故障排除

### 常见问题

#### 照片无法显示

- 检查 `photo.scan.base-path` 配置是否正确
- 确认文件权限设置
- 查看 Nginx 访问日志

#### 人脸识别不准确

- 调整检测置信度阈值
- 检查模型文件是否完整
- 验证 ONNX Runtime 版本兼容性

#### AI评分不工作

- 检查 `ai.scoring.enabled` 配置是否为 `true`
- 检查对应模型文件和超级管理员后台的模型健康状态
- 查看应用启动日志中的AI评分初始化信息
- 检查数据库中是否有评分结果表记录

#### 扫描性能问题

- 增加 JVM 内存分配
- 调整批处理大小
- 启用 Redis 缓存

#### ONNX Runtime 问题

ONNX Runtime 由 Maven 依赖引入；出现加载错误时，先核对平台 profile、原生库兼容性及模型路径。
```bash
# 运行完整的ONNX Runtime诊断
curl -X POST http://localhost:6060/api/admin/diagnostics/onnx \
  -H "Authorization: Bearer YOUR_TOKEN"

# 查看详细日志
tail -f logs/application.log
```

#### 数据库连接问题

```bash
# 测试 MySQL 连接
mysql -h localhost -P 3306 -u root -p photo_exhibition

# 使用本地内存缓存时不需要 Redis；Docker Compose 的 Redis 映射到宿主机 6380 端口
redis-cli -h localhost -p 6380 ping
```

### 日志分析

系统日志位置：

- 应用日志：`logs/application.log`
- 错误日志：`logs/error.log`
- Docker 日志：`docker-compose logs -f backend`

---


## 许可证

仓库当前未包含独立的许可证文件。使用、分发或二次开发前，请先确认项目授权条款。

---

## 致谢

感谢以下开源项目的支持：

- **Spring Boot**: 企业级应用框架
- **Vue.js**: 渐进式前端框架
- **InsightFace**: 人脸识别算法
- **ONNX Runtime**: 深度学习推理引擎，支持AI评分和图像分析
- **OpenCV**: 计算机视觉算法库
- **Tailwind CSS**: 实用优先的 CSS 框架

---

## 联系我们

- **项目主页**: https://github.com/iwxyi/PhotoExhibition.git
- **反馈建议**: 直接发Issues
- **邮箱**: wxy@iwxyi.com

---

<div align="center">
  <p><strong>🚀 让每一张照片都被珍视，每一个瞬间都被铭记</strong></p>
  <p>Made with ❤️ by iwxyi</p>
</div>

