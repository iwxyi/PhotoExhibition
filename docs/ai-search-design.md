# AI 搜索设计文档

## 1. 产品定位

AI 搜索当前的定位不是多轮聊天，而是“单轮自然语言检索 + 必要时一句检索结论”。

目标有两类：

1. 把用户的一句话转换成可执行的结构化检索条件。
2. 在需要时补一句简短判断，例如“杭州去年的樱花开得怎么样”。

当前前端仍以搜索框为入口，没有上下文会话，因此回答能力要服务于检索，而不是扩展成完整聊天。

## 2. 当前已实现的能力

### 2.1 结果展示

- 搜索结果按类型分区展示，而不是把人物、相册、照片混在一起。
- 顶部可选显示“检索结论”卡片；只有确实需要一句总结时才返回。
- 人物显示在人物列表中。
- 相册显示在相册列表中。
- 照片显示在照片列表中。
- 搜索页不再重复展示“搜索结果”“关键词: xxx”“搜索 xxx 的照片”这类冗余标题。
- 搜索框可直接编辑并重新搜索。

### 2.2 交互体验

- 搜索弹层按 `ESC` 时会同时取消焦点并关闭面板。
- AI 搜索建议已升级为结构化建议动作，不再只是纯文本建议词。
- 无结果时支持自动放宽部分条件。
- 放宽条件和收窄条件在前端有不同视觉样式。

### 2.3 检索语义

- 支持 `must / should / mustNot` 三类条件。
- 支持“并集”“交集”“排除”等更自然的布尔表达。
- 支持可选的一句式回答，但不是多轮聊天。
- 支持时间、人物、标签、相册、文件名、相机、镜头、焦距、光圈、快门、ISO、色彩、质量等条件。

### 2.4 已收紧的边界

- AI 搜索已禁止前台检索隐藏内容。
- 不再展示“包括隐藏的”相关提示、能力或建议。
- “去年 / 今年 / 前年”按全年理解：
  - 去年 = `01-01` 到 `12-31`
  - 今年 = `01-01` 到 `12-31`
  - 前年 = `01-01` 到 `12-31`
- 若用户明确提到月份或具体日期，才缩小为更小时间范围。
- 相册结果会做时间冲突过滤；如果相册和时间条件明显不相交，则不再显示为命中相册。

## 3. 当前总体架构

当前后端实际是“预检索 + LLM 结构化解析 + 条件执行 + 可选回答 + 建议动作”的流程。

```text
用户输入
  -> Phase 0: 分词与候选预检索
  -> Phase 1: LLM 生成结构化 intent
  -> Phase 2: 布尔条件执行
  -> Phase 3: 结果分区组装
  -> Phase 4: 可选生成一句 answer
  -> Phase 5: 自动放宽与 suggestionActions
```

### 3.1 Phase 0：分词与候选预检索

后端先对查询做轻量清洗与切词：

- 保留字母、数字、中文
- 去除停用词
- 生成 2 字和 3 字滑窗 token

再基于 token 预检索候选数据：

- 人物候选
- 标签候选
- 相册候选
- 可用相机型号
- 可用镜头型号

目的不是直接返回结果，而是把“可能相关的候选集”交给 LLM，避免把全库数据直接塞进模型。

### 3.2 Phase 1：LLM 结构化意图解析

LLM 不直接生成最终文案，而是先输出结构化 `AiSearchIntent`。

核心规则：

- `must` 表示必须满足
- `should` 表示满足其一
- `mustNot` 表示必须排除
- 无明显布尔关系时默认放入 `must`
- 需要一句判断时才设置 `needAnswer = true`
- `includeHidden` 固定为 `false`
- `resultTypes` 决定返回哪些分区

### 3.3 Phase 2：条件执行

后端把结构化条件转成可执行检索：

- `must` 逐个求交集
- `should` 先求并集，再与当前候选集收敛
- `mustNot` 做排除

照片结果最终按拍摄时间倒序展示。

### 3.4 Phase 3：按类型组装响应

当前响应不是单一混合流，而是分别返回：

- `photos`
- `albums`
- `persons`

前端据此分区展示，保持信息清晰。

### 3.5 Phase 4：可选一句式回答

只有当用户确实在发问时，后端才会生成 `answer`。

例如：

- “杭州去年的樱花开得怎么样”
- “小明和小红经常一起出现吗”

如果没有明显提问语气，通常只返回结构化结果，不额外回答。

### 3.6 Phase 5：自动放宽与建议动作

若照片结果为空，系统会按顺序尝试放宽：

1. 放宽关键词条件
2. 放宽时间条件
3. 同时放宽关键词和时间条件
4. 放宽相册条件，仅保留核心主题

同时返回可执行的 `suggestionActions`，例如：

- 去掉时间限制再搜
- 只搜某些关键词
- 只按文件名再搜

用户点击后，前端调用执行接口，不需要重新让 LLM 重解整句。

## 4. 核心数据模型

### 4.1 AiSearchIntent

当前 `AiSearchIntent` 同时保留了：

- 旧版平铺字段：`personIds`、`tagIds`、`albumIds`、`startDate`、`endDate` 等
- 新版结构化字段：`must`、`should`、`mustNot`

这样做的目的：

1. 兼容现有前端展示逻辑
2. 便于后端逐步演进到更复杂的检索计划

示例：

```json
{
  "must": [
    { "type": "keyword", "values": ["樱花", "杭州"] },
    { "type": "date_range", "startDate": "2025-01-01", "endDate": "2025-12-31" }
  ],
  "should": [],
  "mustNot": [],
  "resultTypes": ["albums", "photos"],
  "needAnswer": true,
  "answerPrompt": "用一句话概括花况",
  "includeHidden": false,
  "explanation": "搜索去年杭州樱花相关内容，并尝试给出简短判断"
}
```

### 4.2 AiSearchCondition

`AiSearchCondition.type` 当前允许的主要类型：

- `person`
- `tag`
- `album`
- `keyword`
- `filename_keyword`
- `camera_model`
- `lens_model`
- `focal_length`
- `aperture`
- `shutter_speed`
- `iso`
- `color`
- `quality`
- `date_range`

字段约定：

- `ids`：人物、标签、相册
- `value / values`：关键词、文件名、型号、色彩
- `minValue / maxValue`：数值区间
- `startDate / endDate`：时间区间

### 4.3 AiSearchResponse

当前响应关键字段：

- `answer`：顶部一句式检索结论
- `needAnswer`：本次是否需要回答
- `relaxed` / `relaxedReason`：是否触发自动放宽
- `suggestions`：兼容旧版文本建议
- `suggestionActions`：结构化建议动作
- `parsedIntent`：最终实际执行的结构化意图
- `photos` / `albums` / `persons`：分区结果
- `matchedPersonName` / `matchedTagNames` / `matchedAlbumNames`：用于前端条件摘要展示

### 4.4 AiSearchSuggestionAction

当前结构：

```json
{
  "label": "去掉时间限制再搜",
  "actionType": "remove_condition_types",
  "conditionTypes": ["date_range"]
}
```

已支持的动作：

- `remove_condition_types`
- `keep_only_condition_types`

### 4.5 AiSearchExecuteRequest

前端点击建议动作后发送：

```json
{
  "query": "杭州去年的樱花开得怎么样",
  "intent": { "...": "上一次解析出的 intent" },
  "suggestionAction": {
    "label": "去掉时间限制再搜",
    "actionType": "remove_condition_types",
    "conditionTypes": ["date_range"]
  },
  "page": 0,
  "size": 30
}
```

## 5. API 设计

### 5.1 搜索接口

`GET /api/photos/ai-search?q={query}&page=0&size=20`

用途：

- 单轮自然语言搜索
- 首次解析 intent
- 返回分区结果、建议动作和可选回答

### 5.2 建议动作执行接口

`POST /api/photos/ai-search/execute`

用途：

- 基于已有 `intent` 执行建议动作
- 避免每次点击建议都重新完整走一遍自然语言解析
- 更适合“去掉时间限制再搜”“只保留关键词再搜”这类二次检索

### 5.3 状态接口

`GET /api/photos/ai-search/status`

用途：

- 前端检查 AI 搜索是否启用

## 6. 关键业务规则

### 6.1 时间语义

- “去年 / 今年 / 前年”默认是整年范围，不是默认某个月。
- 只有出现明确日期信息时，才缩小范围。
- 前端时间标签会友好显示为：
  - `去年全年`
  - `今年全年`
  - `前年全年`
  - `2025 全年`

### 6.2 隐藏内容

- 前台 AI 搜索只面向公开内容。
- 即便后端保留了部分包含隐藏内容的底层查询能力，当前 AI 搜索流程也不会开放给前台。
- 文案、建议、标签展示均不再暴露“包括隐藏的”语义。

### 6.3 相册与时间冲突过滤

有些查询会命中相册名，但相册本身与时间条件并不相交。

当前处理方式：

1. 先检查该相册在时间范围内是否有可见照片
2. 如果没有，再尝试从相册名中提取日期前缀判断
3. 若仍不相交，则不把该相册展示为命中相册

这样可以避免“明明搜的是去年，却把 2024.03.10 樱花季相册仍展示出来”这类误导。

### 6.4 一句式回答边界

当前 `answer` 只适合：

- 结果概括
- 简短判断
- 基于已有结果的一句话总结

不适合：

- 多轮追问
- 长篇解释
- 复杂分析链路

## 7. 前端页面设计

### 7.1 搜索入口

`frontend/src/components/SearchSpotlight.vue`

- Spotlight 弹层输入
- `ESC` 关闭面板并失焦
- 回车后打开搜索页

### 7.2 搜索结果页

`frontend/src/views/Search.vue`

当前页面行为：

- 顶部保留一个可编辑搜索框
- 不再重复大标题和冗余说明
- 顶部可选显示“检索结论”
- 下方显示条件摘要
- 再下方显示可执行建议动作
- 最后按“人物 / 相册 / 照片”分区展示

### 7.3 结构化建议动作

前端根据 `actionType` 区分视觉样式：

- 放宽条件：绿色倾向
- 收窄条件：蓝色倾向

这比简单文本建议更清晰，也便于后续继续扩展更多动作类型。

## 8. 当前局限

当前能力已经能较好覆盖“单轮检索型问题”，但对于更复杂的分析问题还不够：

- “去年在哪里拍过樱花”
- “谁最常和谁同框”
- “小明和小红经常一起出现吗”
- “小丽最喜欢穿什么衣服”

这类问题往往不只是检索单张照片，而是需要：

- 聚合统计
- 对比
- 排名
- 关系推断
- 证据归纳

单次 LLM -> 单个 intent 的模式，表达能力已经接近上限。

## 9. 下一阶段方案：多阶段 AI 分析搜索

推荐把下一阶段能力定义为“分析型问答搜索”，而不是直接做聊天。

建议流程：

```text
用户问题
  -> Planner: 判断问题类型
  -> Query Planner: 生成查询计划 / 指标计划
  -> Executor: 执行只读查询或聚合
  -> Reasoner: 基于证据做判断
  -> Renderer: 返回结论 + 证据 + 可跳转结果
```

### 9.1 问题类型分层

可以先把问题分成四类：

1. 直接检索型
   - 例：“杭州去年的樱花”
   - 直接复用现有 intent 检索

2. 检索 + 一句总结型
   - 例：“杭州去年的樱花开得怎么样”
   - 检索后加一句 answer

3. 聚合统计型
   - 例：“去年在哪里拍过樱花”
   - 需要按地点、相册、月份做统计

4. 关系推断型
   - 例：“小明和小红经常一起出现吗”
   - 需要多指标统计后再做推断

### 9.2 为什么要多阶段

因为复杂问题通常不能靠“一次 LLM 输出一个最终答案”稳定完成。

例如“经常一起出现吗”至少需要这些证据：

- 小明照片总量
- 小红照片总量
- 二人同框照片量
- 二人同框相册量
- 同框分布是否集中在某一时期
- 是否集中出现在婚纱照、合照等关系强标签下

只有把这些中间指标算出来，再让模型做判断，结果才更可信。

## 10. 查询计划优先于自由 SQL

虽然可以适度开放只读 SQL，但默认更推荐先做“查询计划 DSL”，而不是一上来完全自由 SQL。

推荐的中间层：

```json
{
  "questionType": "relationship_frequency",
  "metrics": [
    "person_a_photo_count",
    "person_b_photo_count",
    "cooccurrence_photo_count",
    "cooccurrence_album_count"
  ],
  "filters": {
    "persons": ["小明", "小红"]
  }
}
```

这样有几个好处：

- 更安全
- 更容易审计
- 更容易缓存
- 更容易做前端可解释展示
- 更容易逐步映射到底层 SQL

## 11. 只读 SQL 开放方案

如果后续确实要开放更复杂自由度，可以在受控前提下支持“只读 SQL”。

建议限制：

- 只允许 `SELECT`
- 禁止 `INSERT / UPDATE / DELETE / ALTER / DROP / TRUNCATE`
- 禁止多语句
- 禁止注释穿透
- 限制可访问表
- 限制结果行数
- 限制执行时长
- 强制参数化或模板化
- 所有执行结果都写审计日志

推荐不是让模型直接任意写 SQL，而是：

1. 先让 Planner 生成查询计划
2. 再由受控 SQL Builder 生成只读 SQL
3. 最后由 Reasoner 基于结果做结论

## 12. 关系分析的初步方向

“人物关系”不应直接硬编码成单一结论，而应走“证据评分”。

可以考虑的证据来源：

- 同框照片数
- 同框相册数
- 同框占各自总照片比例
- 是否长期稳定共同出现
- 是否带有婚纱照、合照、情侣、旅行等标签
- 是否在同一时间段频繁共同出现

然后输出：

- 关系猜测
- 置信度
- 关键证据
- 相关照片 / 相册入口

例如：

- 夫妻：高同框率 + 婚纱/情侣标签 + 长期共同出现
- 朋友：有合照但同框密度和专属标签较弱
- 仅偶尔同框：同框数存在，但相对总量占比很低

## 13. 自动语义标注路线图

后续复杂搜索想做得更好，离不开自动语义标注。

推荐逐步补充的语义维度：

- 服饰：汉服、西装、校服、婚纱、运动装
- 场景：樱花、海边、雪景、夜景、草地、室内、舞台
- 活动：婚礼、毕业、聚餐、旅行、亲子、约会
- 关系线索：合照、情侣、婚纱照、多人聚会
- 姿态与构图：自拍、半身、全身、双人合照、多人合照
- 情绪与氛围：开心、正式、温柔、热闹
- 地点语义：城市、景点、室内场馆
- 时间语义：季节、花期、节日、年份事件

这些标注可以来自：

- 相册名
- 相册路径
- 现有标签
- 图片多模态识别
- EXIF 时间与地点
- 人物共现关系

## 14. 推荐的数据输出形态

未来复杂问题建议统一返回四层信息：

1. `answer`
   - 一句结论

2. `evidence`
   - 关键统计指标

3. `insights`
   - 模型对结果的简短解释

4. `resultGroups`
   - 可点击的照片 / 相册 / 人物分区

这样既能保持“可搜索”，也能逐步支持“可分析”。

## 15. 当前涉及的主要文件

后端：

- `backend/src/main/java/com/photoexhibition/controller/AiSearchController.java`
- `backend/src/main/java/com/photoexhibition/service/AiSearchService.java`
- `backend/src/main/java/com/photoexhibition/dto/AiSearchIntent.java`
- `backend/src/main/java/com/photoexhibition/dto/AiSearchResponse.java`
- `backend/src/main/java/com/photoexhibition/dto/AiSearchCondition.java`
- `backend/src/main/java/com/photoexhibition/dto/AiSearchSuggestionAction.java`
- `backend/src/main/java/com/photoexhibition/dto/AiSearchExecuteRequest.java`
- `backend/src/main/java/com/photoexhibition/repository/PhotoRepository.java`

前端：

- `frontend/src/api/index.ts`
- `frontend/src/components/SearchSpotlight.vue`
- `frontend/src/views/Search.vue`

## 16. 结论

当前 AI 搜索已经从“单纯自然语言转筛选条件”升级为“可分区展示、可一句式回答、可结构化二次检索”的版本。

下一阶段不建议直接做完整聊天，而建议继续沿着“分析型问答搜索”推进：

- 前端仍保持搜索入口
- 后端升级为多阶段推理
- 中间增加查询计划层
- 必要时受控开放只读 SQL
- 持续补齐自动语义标注

这样能在不把产品做成重聊天系统的前提下，支持更复杂、更有价值的问题。
