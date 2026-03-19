# AI 自然语言搜索设计文档

## 概述

AI 搜索功能允许用户使用自然语言查询照片、相册和人物。系统采用**"预检索 + GPT 精选"**三阶段架构，将用户的自然语言查询转换为结构化筛选条件，返回混合类型的搜索结果。

## 架构设计

### 三阶段流程

```
用户查询 → Phase 0: 分词+预检索 → Phase 1: GPT解析 → Phase 2: 组合查询 → 混合结果
```

#### Phase 0: 分词 + 预检索

1. **分词**：对用户查询去除停用词和标点，生成 2-char 和 3-char 滑动窗口子串
2. **预检索**：用每个 token 在数据库中模糊搜索：
   - 人物表 (`PersonProfile`) → 候选人物列表
   - 标签表 (`Tag`) → 候选标签列表
   - 相册表 (`Album`) → 候选相册列表
   - 全量获取相机型号和镜头型号（数量少）

**目的**：只给 GPT 发送相关的少量候选项（~1000 tokens），而非全库数据（可能几千条），大幅降低 token 消耗。

#### Phase 1: GPT 意图解析

将候选列表和用户查询发送给 GPT，GPT 返回结构化 JSON：

```json
{
  "personIds": [1, 2],
  "tagIds": [5, 8],
  "albumIds": [3],
  "startDate": "2025-01-01",
  "endDate": "2025-12-31",
  "cameraModel": "Canon EOS R5",
  "lensModel": null,
  "minFocalLength": null,
  "maxFocalLength": null,
  "minAperture": null,
  "maxAperture": null,
  "colorCategory": null,
  "minQualityScore": null,
  "keywords": [],
  "filenameKeywords": ["IMG_1234"],
  "resultTypes": ["albums", "persons", "photos"],
  "includeHidden": false,
  "explanation": "搜索某某和某某在某相册中的照片"
}
```

#### Phase 2: 组合查询

使用集合交集逻辑组合多个筛选条件：

1. 按 `personIds` 查照片（多人物做 **union**）
2. 按 `tagIds` 查照片 → 与上一步 **intersect**
3. 按 `albumIds` 查照片 → **intersect**
4. 按 `filenameKeywords` 搜索文件名 → **intersect**
5. 按 `keywords` 模糊搜索（文件名+相册名）→ **intersect**
6. 应用 EXIF/日期/色彩条件（内存过滤或数据库查询）
7. 根据 `resultTypes` 获取相册和人物 DTO

## 数据模型

### AiSearchIntent（GPT 返回的意图）

| 字段 | 类型 | 说明 |
|------|------|------|
| personId | Long | 单人物ID（兼容旧版） |
| personIds | List\<Long\> | 多人物ID列表 |
| tagIds | List\<Long\> | 标签ID列表 |
| albumIds | List\<Long\> | 相册ID列表 |
| startDate / endDate | String | 日期范围 (yyyy-MM-dd) |
| cameraModel | String | 相机型号 |
| lensModel | String | 镜头型号 |
| minFocalLength / maxFocalLength | Double | 焦距范围 (mm) |
| minAperture / maxAperture | Double | 光圈范围 |
| colorCategory | String | 色彩分类 |
| keywords | List\<String\> | 未匹配的关键词 |
| filenameKeywords | List\<String\> | 文件名搜索词 |
| resultTypes | List\<String\> | 结果类型: "albums", "persons", "photos" |
| includeHidden | boolean | 是否包含隐藏内容 |
| explanation | String | GPT 对搜索条件的中文描述 |

### AiSearchResponse（返回给前端的响应）

| 字段 | 类型 | 说明 |
|------|------|------|
| explanation | String | AI 对查询的理解说明 |
| parsedIntent | AiSearchIntent | GPT 解析的结构化意图 |
| photos | List\<PhotoDTO\> | 匹配的照片列表 |
| albums | List\<AlbumDTO\> | 匹配的相册列表 |
| persons | List\<PersonSummaryDTO\> | 匹配的人物列表 |
| totalElements | long | 照片总数 |
| matchedPersonName | String | 匹配的人物名称 |
| matchedTagNames | List\<String\> | 匹配的标签名称 |
| matchedAlbumNames | List\<String\> | 匹配的相册名称 |
| aiSearchEnabled | boolean | AI 搜索是否启用 |

## GPT Prompt 设计

### 系统 Prompt 结构

```
你是照片搜索助手。根据用户的自然语言查询和数据库中的候选项，生成精确的搜索条件JSON。

## 匹配到的人物
- id:1 "某某"
- id:2 "某某某"

## 匹配到的标签
- id:5 "蓝天"
- id:8 "樱花"

## 匹配到的相册
- id:3 "某某出游" (/photos/某某出游)

## 可用相机型号
Canon EOS R5, Sony A7M4, ...

## 可用镜头型号
RF 50mm F1.2L, FE 35mm F1.4 GM, ...

## 色彩分类
RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, PINK, BROWN, GRAY, BLACK, WHITE

## 输出JSON格式
{...}

## 规则
1. 日期映射
2. 人物名语义匹配
3. 标签语义关联
4. resultTypes 决定返回类型
5. includeHidden 隐藏内容控制
6. filenameKeywords 文件名搜索
...
```

### 语义匹配规则

- **人物名**：用户说"某某"，候选有"王某某"，应匹配
- **标签语义关联**：用户说"白天"，候选中"蓝天""晴天"都是白天场景
- **日期映射**：去年/前年/今年 → 自动计算年份
- **摄影术语**：长焦→minFocalLength:85, 广角→maxFocalLength:35, 大光圈→maxAperture:2.8
- **色彩映射**：暖色→ORANGE, 冷色→BLUE

## API 端点

### 搜索接口

```
GET /api/photos/ai-search?q={query}&page=0&size=30
```

- 无需认证（在 `/photos/` 路径下，JwtInterceptor 自动放行）
- 返回 `AiSearchResponse`

### 状态检查

```
GET /api/photos/ai-search/status
```

- 返回 `{ "enabled": true/false }`

## 配置管理

AI 搜索配置存储在 `SystemConfig` 表中：

| 配置键 | 说明 | 默认值 |
|--------|------|--------|
| ai_search_enabled | 是否启用 | false |
| ai_search_api_url | API 地址 | "" |
| ai_search_api_key | API 密钥 | "" |
| ai_search_model | 模型名称 | gpt-4o |

管理端点在 `/api/admin/config/ai-search-*` 下，需要管理员认证。

## 前端集成

### 搜索流程

1. 页面加载时检查 AI 搜索是否启用
2. 用户输入关键词，AI 启用时调用 AI 搜索接口
3. AI 搜索失败时自动 fallback 到普通关键词搜索
4. 显示 AI 理解说明（蓝色提示框）
5. 显示匹配条件标签（人物/标签/相册/日期/色彩/相机/文件名/隐藏状态）
6. 混合显示搜索结果：照片网格 + 相册卡片 + 人物卡片

### 搜索示例

| 用户查询 | AI 理解 | 返回结果 |
|----------|---------|----------|
| "某某或者蓝天" | 搜索人物某某和蓝天相关照片 | 人物卡片 + 蓝天标签照片 |
| "佳能拍的照片" | 按 Canon 相机筛选 | 照片列表 |
| "索尼35mm" | Sony相机 + 35mm焦距 | 照片列表 |
| "IMG_1234" | 搜索文件名 | 匹配的照片 |
| "某某出游时的照片" | 在某某出游相册中搜索 | 相册卡片 + 相册中照片 |
| "前女友的照片，包括隐藏的" | 搜索并包含隐藏内容 | 含隐藏照片的结果 |

## 文件清单

### 后端

| 文件 | 说明 |
|------|------|
| `service/AiSearchService.java` | 核心搜索服务：分词、预检索、GPT 调用、组合查询 |
| `controller/AiSearchController.java` | REST 控制器 |
| `dto/AiSearchIntent.java` | GPT 返回的意图 DTO |
| `dto/AiSearchResponse.java` | API 响应 DTO |
| `service/SystemConfigService.java` | AI 配置存储 |
| `controller/SystemConfigController.java` | AI 配置管理端点 |
| `repository/PhotoRepository.java` | 照片查询（含隐藏照片变体） |
| `repository/TagRepository.java` | 标签模糊搜索 |
| `config/RestTemplateConfig.java` | HTTP 客户端配置 |

### 前端

| 文件 | 说明 |
|------|------|
| `api/index.ts` | API 类型定义和接口 |
| `views/Search.vue` | 搜索页面，AI 搜索模式和结果展示 |
| `views/admin/Settings.vue` | 管理后台 AI 配置卡片 |
