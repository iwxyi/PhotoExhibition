# AI 搜索架构设计 V2

## 1. 目标

V2 的目标不是继续给 `AiSearchService` 叠加更多特殊分支，而是把 AI 搜索改造成一个可长期维护的受控检索系统：

1. AI 负责理解问题、规划步骤、处理歧义。
2. 本地负责执行过滤、聚合、集合运算、排序、截断。
3. AI 只看压缩后的证据，不直接消费全量原始结果。
4. 所有执行步骤都必须落在白名单算子内，不能变成任意 SQL 或任意脚本执行。

这套架构需要同时覆盖三类问题：

1. 普通检索
   例如：`佳能`、`去年樱花`
2. 复合条件检索
   例如：`佳能R62或佳能R8`
3. 计算链路型问题
   例如：`去年新认识的人物有谁`
   例如：`X 今年比去年胖了吗`

## 2. 为什么要从 Intent 迁移

当前 `AiSearchIntent` 模型适合表达“单次过滤条件”，但它不适合表达多阶段计算问题：

1. 它天然偏向一次性过滤，不擅长表达中间结果。
2. 它没有清晰的执行 DAG，复杂问题只能继续堆在 `AiSearchService`。
3. 它无法自然表达集合差集、聚合、排行、证据裁剪等步骤。
4. 它会诱导继续添加 query-specific 规则，维护成本越来越高。

因此，V2 不再把“AI 搜索”视为一次 intent 解析，而是分成：

1. `Planner`
2. `Validator`
3. `Executor`
4. `Reducer`
5. `Resolver`

`AiSearchIntent` 仍然保留一段时间，用作兼容层输入和旧接口输出。

## 3. 核心分层

### 3.1 Planner

输入：

1. 用户原始查询
2. 查询模式
3. 候选摘要
4. 可用数据源与算子白名单

输出：

1. `AiSearchPlan`

Planner 只负责“决定怎么查”，不直接执行查询。

规划输出必须是受控 DSL，而不是自由文本，也不是任意 SQL。

### 3.2 Validator

职责：

1. 校验步骤引用是否合法
2. 校验算子是否在白名单内
3. 校验参数类型是否可执行
4. 拦截危险或不完整的计划

只允许执行“平台认识的计划”，不允许执行 Planner 随意发明的步骤。

### 3.3 Executor

职责：

1. 执行 `filter / aggregate / set / sort / limit / summarize`
2. 管理中间结果
3. 控制结果量和成本
4. 统一走受控数据源适配器

Executor 不关心用户措辞，它只执行计划。

### 3.4 Reducer

职责：

1. 把执行结果压缩成 token 可控的证据包
2. 限制传给 AI 的实体数、样本数、聚合桶数量
3. 保留足够证据，避免 AI 凭空总结

Reducer 是避免 token 失控的关键层，必须优先本地计算，再把摘要交给 AI。

### 3.5 Resolver

职责：

1. 根据证据包生成最终一句结论
2. 输出保守回答，而不是自由发挥
3. 在证据不足时明确返回 `limited` 或 `none`

Resolver 只处理“解释结果”，不再直接负责数据库检索。

## 4. V2 执行流

```text
用户查询
  -> Query Router
  -> Candidate Builder
  -> Planner
  -> Validator
  -> Executor
  -> Reducer
  -> Resolver
  -> Response Assembler
```

更具体的阶段如下：

1. `Query Router`
   判断是 `simple_search / simple_answer / analysis / hybrid_plan`
2. `Candidate Builder`
   构造人物、标签、相册、器材、时间等候选摘要
3. `Planner`
   生成受控执行计划
4. `Validator`
   拒绝非法算子、非法引用、参数缺失
5. `Executor`
   执行本地计划并输出中间结果
6. `Reducer`
   压缩成证据包
7. `Resolver`
   按证据输出一句结论或保守说明
8. `Response Assembler`
   组装成现有 `AiSearchResponse`

## 5. 计划 DSL

### 5.1 基本结构

```json
{
  "version": "v2",
  "query": "去年新认识的人物有谁",
  "queryMode": "analysis",
  "planType": "llm_plan",
  "resultTypes": ["persons", "photos"],
  "maxEvidenceItems": 40,
  "steps": [
    {
      "id": "s1",
      "operator": "filter_photos",
      "args": {
        "dateRange": {
          "start": "2025-01-01",
          "end": "2025-12-31"
        }
      },
      "outputKey": "photos_last_year"
    },
    {
      "id": "s2",
      "operator": "aggregate_persons",
      "dependsOn": ["s1"],
      "inputRef": "photos_last_year",
      "outputKey": "persons_last_year"
    }
  ]
}
```

### 5.2 首批白名单算子

V2 第一阶段只允许这些高价值、低风险算子：

1. `filter_photos`
2. `aggregate_persons`
3. `aggregate_albums`
4. `set_union`
5. `set_intersection`
6. `set_difference`
7. `sort`
8. `limit`
9. `summarize`

后续可以新增：

1. `aggregate_tags`
2. `aggregate_locations`
3. `aggregate_camera_models`
4. `aggregate_lens_models`
5. `compare_periods`
6. `derive_person_cooccurrence`
7. `derive_person_growth_signals`

新增算子时必须同步补：

1. 参数 schema
2. 执行器实现
3. 单测
4. 文档

## 6. 数据源适配层

V2 不允许 Planner 直接选择任意数据库连接。

统一通过 `AiSearchDataSource` 适配：

1. `primary_photo_store`
   主业务库，覆盖照片、相册、人物、标签、器材
2. `analytics_store`
   可选的分析库或 wrapper 数据库
3. `materialized_stats_store`
   预聚合统计视图

注意：

1. Planner 只能引用被注册的数据源 ID。
2. 每个数据源只暴露有限操作，不暴露裸 SQL。
3. 如果未来接入 wrapper 数据库，也必须走白名单适配器。

## 7. Token 与成本控制

V2 明确采用“本地算、AI 解释”的原则。

### 7.1 不允许的做法

1. 把数百条照片原始记录直接塞给模型
2. 把全量人物明细直接塞给模型
3. 让模型直接写 SQL 再执行

### 7.2 推荐做法

1. 先本地过滤出候选集
2. 本地做聚合、对比、集合运算
3. 只把 Top N 摘要和关键统计送给 AI
4. 对超大结果集优先返回结构化结果，不强制生成回答

### 7.3 证据包策略

证据包建议包含：

1. 查询摘要
2. 执行计划摘要
3. Top N 结果
4. 聚合统计
5. 时间分布或对比指标
6. 证据完整度标记

## 7.4 响应可观测性

为了让迁移过程可排查、可回归，V2 约定在响应中输出 `executionPlan` 摘要。

当前至少包含这些字段：

1. `planType`
   当前回答或执行属于哪类计划，例如 `relative_new_persons`、`technical_disjunction`、`count_overview`、`year_compare`
2. `evidenceStatus`
   证据强度，取值为 `sufficient / limited / none`
3. `resolverUsed`
   本次回答是否经过 Resolver
4. `finalOutputKeys`
   当前计划或 synthetic plan 产出的最终输出键
5. `operators`
   当前计划实际使用的白名单算子列表
6. `metadata`
   当前计划的关键参数摘要，例如年份、主题关键词、器材候选

说明：

1. 对真实 `Planner -> Executor` 路径，`executionPlan` 应尽量包含真实步骤摘要。
2. 对尚未完全迁移执行器、但已经迁移回答链的路径，允许先输出 synthetic `executionPlan`。
3. 前端不应依赖 `executionPlan` 做强业务逻辑，但可用于调试面板、日志采样、灰度观察。
4. 搜索页可以把 `executionPlan` 作为默认折叠的只读调试面板展示，方便灰度观察和问题回溯。

## 8. 对复杂问题的支持方式

### 8.0 当前已迁移到真实受控执行的路径

当前已经不再只是文档设计，而是有一部分查询真正落到了 `Planner -> Validator -> Executor -> Reducer -> Resolver` 或其等价受控执行链上：

1. `relative_new_persons`
   真实执行 `filter_photos -> aggregate_persons -> set_difference -> sort -> limit`
2. `technical_disjunction`
   真实执行器材布尔检索与分页截断
3. `count_overview`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `aggregate_albums` 计划负责相册级统计，最终通过 reducer / resolver 生成回答
4. `month_overview`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `aggregate_months` 计划负责月份聚合，最终通过 reducer / resolver 生成回答
5. `album_overview`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `aggregate_albums` 计划负责相册级排行，最终通过 reducer / resolver 生成回答
6. `tag_overview`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `aggregate_tags` 计划负责标签聚合，最终通过 reducer / resolver 生成回答
7. `location_overview`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `aggregate_locations` 计划负责地点信号聚合；地点抽取规则独立收敛到 helper，避免继续散落在 `AiSearchService`
8. `theme_overview`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `aggregate_themes` 统一消费标签主题和相册主题信号，最终通过 reducer / resolver 生成回答
9. `day_overview`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `aggregate_days` 计划负责日期聚合，最终通过 reducer / resolver 生成回答
10. `person_overview`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `aggregate_persons -> sort -> limit` 计划负责人物概览与排序，最终通过 reducer / resolver 生成回答
11. `person_cooccurrence`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `derive_person_cooccurrence -> sort -> limit` 计划负责“与指定人物共同出现”的人物排行，最终通过 reducer / resolver 生成回答；当前优先支持有明确锚点人物的问题
12. `person_pair_cooccurrence`
   先复用现有本地照片过滤结果，再把 `matched_photo_ids` 注入执行上下文，由 `derive_person_pair_cooccurrence -> sort -> limit` 计划负责“全局人物对共同出现”的排行，最终通过 reducer / resolver 生成回答，覆盖“谁和谁最常同框”这类无锚点关系问题
13. `year_compare`
   先复用现有本地过滤得到左右年份命中集，再把左右时期结果注入执行上下文，由 `compare_periods` 算子负责真实对比计算，最终通过 reducer / resolver 生成回答
14. `body_change`
   先定位人物与比较时期，再由 `derive_person_growth_signals` 输出体型变化指标，最终通过 reducer / resolver 生成保守结论，并把结构化变化摘要返回给前端

这意味着后续迁移不应再向 `AiSearchService` 追加 query-specific 的统计分支，而应沿着“过滤候选集 -> 注入上下文 -> 受控聚合 -> 证据归约 -> 结论解析”的方式扩展。

### 8.1 多器材 / 布尔条件

示例：`佳能R62或佳能R8`

优先本地执行：

1. 品牌归一
2. 型号模糊匹配
3. `should` / `union` 组合

这类问题通常不需要 Resolver 参与。

更具体的当前链路：

1. Query Router 识别为 `simple_search`
2. 候选构建阶段从本地器材词表中识别 `Canon EOS R6m2 / Canon EOS R8`
3. Planner 输出 `technical_disjunction`
4. Executor 执行“器材条件并集 + 排序 + 分页”
5. Response Assembler 输出照片结果与 `executionPlan`

这类查询的关键点是：

1. 不把“或”问题强行交给大模型自由推断
2. 先做品牌、型号、别名归一，再进入白名单执行
3. 前端可用 `executionPlan.metadata` 直接看到命中的器材候选

### 8.1.1 典型示例矩阵

下面这些例子建议作为后续扩展和回归测试的固定样本：

| 用户问题 | 计划类型 | 本地执行核心链路 | 当前状态 |
|---|---|---|---|
| `佳能R62或佳能R8` | `technical_disjunction` | 器材候选识别 -> 并集过滤 -> 分页 | 已支持 |
| `去年有谁` | `person_overview` | 先筛照片 -> `aggregate_persons -> sort -> limit` | 已支持 |
| `小明经常一起出现的是谁` | `person_cooccurrence` | 先筛照片 -> `derive_person_cooccurrence -> sort -> limit` | 已支持 |
| `谁和谁最常同框` | `person_pair_cooccurrence` | 全局可见照片 -> `derive_person_pair_cooccurrence -> sort -> limit` | 已支持 |
| `去年在杭州谁和谁最常同框` | `person_pair_cooccurrence` | 时间/关键词预筛 -> `derive_person_pair_cooccurrence -> sort -> limit` | 已支持 |
| `去年新认识的人物有谁` | `relative_new_persons` | 去年人物集 - 去年以前人物集 -> 排序 -> 截断 | 已支持 |
| `去年拍樱花主要集中在哪几天` | `day_overview` | 先筛照片 -> `aggregate_days` | 已支持 |
| `去年和前年相比樱花拍得更多还是更少` | `year_compare` | 左右时期筛选 -> `compare_periods` | 已支持 |
| `X 今年比去年胖了吗` | `body_change` | 人物时序统计 -> 变化信号归约 -> 保守回答 | 已支持 |
| `去年新认识的人里，谁后来又经常和小明同框` | `multi_stage_relation` | 需要多阶段中间结果复用 | 规划中 |
| `去年新认识的人物里，有哪些是用佳能拍到的` | `multi_stage_filtered_overview` | 集合差 -> 器材过滤 -> 人物聚合 | 规划中 |
| `前年不存在但去年存在，今年又没再出现的人有谁` | `multi_stage_temporal_set` | 多时期集合差与交集 | 规划中 |

“规划中”这几类问题不应通过继续向 `AiSearchService` 叠加 if/else 解决，而应通过可组合算子实现。

### 8.2 计算链路问题

示例：`去年新认识的人物有谁`

不再把“新认识”写死成单个字段判断，而是由计划表达：

1. 取去年有人脸的人物集合
2. 取去年以前的人物集合
3. 做差集
4. 排序和裁剪
5. 输出人物结果和必要说明

对应的受控计划样式可以表达为：

```text
filter_photos(去年)
  -> aggregate_persons
  -> set_difference(去年以前的人物集合)
  -> sort
  -> limit
  -> reducer
  -> resolver
```

这类问题的关键不是“让 AI 直接回答新认识的是谁”，而是：

1. AI 只负责识别“这是一个跨时期集合差问题”
2. 本地负责做集合运算
3. Resolver 只读取压缩后的结果摘要，不直接读取全量照片

这也是未来支持更自由时间逻辑的基础，例如：

1. `前年不存在但去年存在的人物`
2. `去年第一次出现、今年又继续出现的人物`
3. `去年出现过但今年没再出现的人物`

### 8.3 高自由度分析问题

示例：`X 今年比去年胖了吗`

正确做法不是继续在 Service 里叠条件，而是：

1. Planner 识别为体型变化分析
2. Executor 走受控的人物时序统计算子
3. Reducer 输出期间对比摘要
4. Resolver 仅基于摘要给出保守结论

这类问题的重要边界是：

1. 不把单张照片或全量人脸框直接交给模型
2. 先在本地把时序信号压成有限指标
3. 允许回答 `limited`，而不是逼模型给强结论

### 8.4 关系分析问题

关系分析已经明确拆成两条路径，不能继续混在一个意图里：

1. `person_cooccurrence`
   有明确锚点人物，例如 `小明经常一起出现的是谁`
2. `person_pair_cooccurrence`
   没有锚点人物，例如 `谁和谁最常同框`

这样拆分的原因：

1. 两类问题的输入约束不同
2. Reducer / Resolver 的结论模板不同
3. 前端 `analysisData` 结构不同
4. 后续如果支持多锚点关系，也能继续扩展而不破坏现有 plan type

当前实现里，这两类关系分析都采用“先做本地照片范围筛选，再把 `matched_photo_ids` 注入执行上下文”的方式，避免关系算子直接扫描不受控的全库。

### 8.5 当前还不建议直接支持的问题

下面这些问法最终要支持，但当前不适合用临时规则硬补：

1. `去年新认识的人物里，谁和谁后来最常同框`
2. `佳能拍到的新认识人物里，后续出现次数最多的是谁`
3. `去年新认识的人中，哪些人今年比去年更胖`

原因不是“问题太难”，而是它们都需要真正的多阶段中间结果复用：

1. 先计算 A 集合
2. 再把 A 当成下游过滤条件
3. 再进入关系聚合或变化分析
4. 最后再做排序和解释

如果现在用 Service 分支硬堆，短期能跑，长期会直接毁掉架构边界。

## 8.6 文档化的 token 控制原则

复杂问题的示例越多，越要明确 token 控制边界。推荐固定采用下面的局部原则：

1. Planner 不接触全量实体明细，只接候选摘要
2. Executor 在本地完成尽可能多的过滤、集合运算、聚合、排序
3. Reducer 只保留 Top N 实体、必要统计量、时间范围、结论所需的少量证据
4. Resolver 不再请求“请阅读全部结果后总结”，而是只读取证据包

以 `去年在杭州谁和谁最常同框` 为例：

1. 本地先筛出“去年 + 杭州”的照片范围
2. 只把 `matched_photo_ids` 交给关系算子
3. 关系算子本地输出 Top N 人物对
4. Reducer 只保留人物对名称、出现次数、时期标签
5. Resolver 基于这些压缩信息输出一句话

这样即使底层匹配到了几千张照片，传给最终回答链的 token 仍然可控。

## 9. 与当前接口的兼容策略

现阶段前端仍依赖：

1. `AiSearchResponse.parsedIntent`
2. `AiSearchResponse.answer`
3. `AiSearchResponse.photos / albums / persons`

因此迁移采用分阶段兼容：

### 阶段 A

1. 保留现有控制器与响应结构
2. 引入 V2 文档与模块骨架
3. 增加 `LegacyIntent -> AiSearchPlan` 兼容桥

### 阶段 B

1. 新查询先经过 Planner/Validator
2. 旧 `AiSearchIntent` 查询通过兼容桥转成计划
3. `AiSearchService` 逐步退化为编排层

### 阶段 C

1. 核心执行迁移到 `Executor`
2. `AiSearchIntent` 降级为兼容 DTO
3. `AiSearchService` 不再承载大量 query-specific 分支

## 10. 目录建议

后端建议目录如下：

```text
backend/src/main/java/com/photoexhibition/aisearch/
  datasource/
  executor/
  operator/
  plan/
  planner/
  reducer/
  resolver/
  validation/
  compatibility/
```

各层职责边界：

1. `plan`
   计划数据结构
2. `planner`
   计划生成
3. `validation`
   计划校验
4. `operator`
   算子接口与注册
5. `datasource`
   受控数据源适配器
6. `executor`
   步骤执行与上下文管理
7. `reducer`
   证据归约
8. `resolver`
   最终回答
9. `compatibility`
   旧意图兼容桥

## 11. 当前仓库落地约定

本仓库从本次开始采用以下约定：

1. 不再继续新增面向单一问句的硬编码分支，除非是临时兼容修复。
2. 新增复杂查询能力时，优先新增算子与计划映射，而不是继续扩充 `AiSearchIntent`。
3. 所有新能力都必须说明：
   - 使用哪些算子
   - 使用哪些数据源
   - 如何控制 token
   - 如何做降级
4. 文档先行，代码与文档保持同步。

## 12. 下一步实施顺序

建议按下面顺序推进：

1. 抽离 `LegacyIntentAiSearchPlanner`
2. 增加 `AiSearchPlanValidator`
3. 引入首批执行器与算子注册表
4. 让一部分简单查询先走 V2 计划执行
5. 增加证据归约与受控 Resolver
6. 把体型变化、人物新增识别等复杂问题迁移到计划式执行

## 13. 与 V1 文档的关系

`docs/ai-search-design.md` 继续记录当前线上兼容实现与历史设计。

本文档用于定义新的长期架构边界、迁移原则和模块分层。后续如两者冲突，以本文档的目标架构为准，再通过分阶段迁移逐步落地。
