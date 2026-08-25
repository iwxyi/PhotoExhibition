# 多账号 / 多租户改造设计

## 1. 目标

本次改造的核心目标，是把当前“单管理员、单数据空间”的项目，演进为“可选单用户 / 多用户共存”的多租户摄影平台。

本阶段优先解决以下问题：

- 支持多个用户注册、登录、独立管理自己的图片空间
- 引入超级管理员与普通用户后台的分层管理
- 为手机号登录、短信验证码、VIP 配额、多存储后端打基础
- 把文件路径、数据库数据、公开 URL 全部纳入用户维度
- 为弱性能服务器场景设计低并发、排队、断点续扫的扫描体系

## 2. 当前现状

当前代码仍是典型的单租户结构：

- 登录用户只有 `admin_user`
- 前端登录态只保存 `admin_token` / `admin_username`
- 公开页面路由没有用户前缀
- 相册、照片、人脸、人物、标签等核心数据表均没有 `user_id`
- 本地存储根路径为全局单一 `photo.scan.base-path`
- 上传后的后台处理与扫描状态也是全局共享

这意味着“多账号支持”不是局部功能新增，而是体系级重构。

## 3. 核心架构决策

### 3.1 用户与租户模型

采用“单库多租户（共享库、共享表、显式 user_id 隔离）”方案。

- 每个用户拥有独立的数据空间
- 所有核心业务表最终都应增加 `user_id`
- 用户对外访问建议使用 `slug`
- 用户内部文件目录仍使用数值 `userId`

推荐区分：

- `id`：数据库主键，内部关联与文件目录使用
- `slug`：公开 URL 使用，例如 `domain/alice`
- `username`：登录用户名

### 3.2 角色模型

至少需要以下角色：

- `SUPER_ADMIN`：系统级管理员，可管理用户、配额、存储、系统开关
- `USER_ADMIN`：普通用户后台管理员，只能管理自己的数据

后续如需游客评论、协作成员、家庭共享等能力，再扩展更细粒度角色。

### 3.3 URL 方案

公开前台统一采用用户前缀，避免后续权限、缓存、SEO 与跨用户数据混淆。

推荐主方案：

- 首页：`/{userSlug}`
- 搜索：`/{userSlug}/search?q=xxx`
- 图墙：`/{userSlug}/wall`
- 随机：`/{userSlug}/random`
- 人物：`/{userSlug}/persons`
- 相册详情：`/{userSlug}/album/{albumId}`
- 图片详情：`/{userSlug}/photo/{photoId}`

不建议长期保留 `domain/album/{albumId}` 作为主路径，因为它天然削弱用户隔离边界。若后续为了兼容旧链接，可保留短链跳转，但服务端仍必须校验 `albumId -> userId` 归属。

### 3.4 存储路径方案

本地存储默认改为：

- 图片主空间：`data/photos/{userId}/{分类}/{相册名}`
- 用户资料目录：`data/users/{userId}/`

其中：

- 原图属于用户配额统计范围
- 缩略图、WebP、大图缓存、抠图、人脸裁剪等派生文件不计入用户配额
- 派生文件建议继续跟随原图旁路生成，或集中放入派生目录，但逻辑上必须标记为“非配额文件”

### 3.5 配额与 VIP 设计

默认策略：

- 新用户默认空间限额 `3GB`
- 实际使用量统计仅基于原图对象大小

后续演进：

- 引入 `storage_plan` 或 `vip_plan`
- 用户持有当前生效方案
- 用户限额由“基础限额 + 方案增量 + 超级管理员额外调整”共同决定

### 3.6 多存储后端

存储层必须抽象出统一接口，至少覆盖：

- `LOCAL`
- `FTP`
- `WEBDAV`
- `COS`

当前演进状态：

- 已接通上传 / 浏览 / 扫描能力：`LOCAL`、`FTP`、`WEBDAV`、`COS`、`S3_COMPATIBLE`、`MINIO`、`OSS`、`R2`、`SFTP(挂载目录模式)`、`SMB(挂载目录模式)`、`NFS(挂载目录模式)`、`GCS(XML API / HMAC 模式)`、`OBS`、`TOS`、`BOS`、`UCLOUD_US3`、`JD_JSS`、`WASABI`、`QINIU_KODO`、`B2`、`UPYUN`、`DROPBOX`、`ONEDRIVE`
- `Azure Blob` 已补第一版 REST 接入：
  - 支持 `accountName + accountKey` 方式
  - 也支持直接配置 `SAS Token`
  - 已接通上传 / 浏览 / 下载 / 批量管理 / 基础扫描 / 预览能力
- `Dropbox` 已补第一版官方 HTTP API 接入：
  - 使用 `accessToken` + `contentEndpoint`
  - 已接通上传 / 浏览 / 下载 / 批量管理 / 临时预览链接
- `OneDrive` 已补第一版 Microsoft Graph 接入：
  - 优先支持直接配置 `accessToken`
  - 也支持 `tenantId + clientId + clientSecret` 换取应用令牌
  - 默认 `drivePrefix=/me/drive`，可改为 `/users/{id}/drive` 或 `/drives/{driveId}`
  - 已接通上传 / 浏览 / 下载 / 批量管理 / 临时预览链接
- `又拍云` 已补第一版 REST 接入：
  - 使用 `服务名(bucketName) + operator + password`
  - 已接通上传 / 浏览 / 下载 / 批量管理
  - 预览需额外配置 `publicBaseUrl` 或 `cdnDomain`
- 超级管理员存储配置页现已补充“推荐配置提示”：
  - 新建存储提供者时会按类型展示推荐 `endpoint / bucket / baseDirectory / configJson` 示例
  - 前端会即时提示当前类型缺少的核心字段，减少创建后才发现无法使用的情况
  - 后端 `SuperAdminService` 也已补充基础校验，避免明显缺失 `endpoint / bucketName / baseDirectory` 的存储配置被保存
  - 现有存储提供者编辑区也可直接看到推荐配置、缺失字段提示，并支持一键“套用推荐”回填骨架
  - 创建与保存存储前，前端现在也会先做提交前校验，优先给出直接可读的字段缺失提示
- S3 兼容对象存储家族本轮已补“真实传输”链路：
  - 统一按 SigV4 方式支持 `上传 / 下载 / 列目录 / 创建目录占位 / 删除 / 移动 / 预签名预览`
  - 当前先按路径风格（`endpoint/bucket/key`）落地，优先覆盖 `S3_COMPATIBLE / MINIO / OSS / R2 / OBS / TOS / BOS / US3 / JSS / WASABI / KODO / B2`
  - `OSS` 现也已并入这一链路，超管能力识别、推荐配置与浏览/上传/扫描支持状态已与实际实现对齐
- 普通用户不暴露存储系统概念，上传位置仅由超级管理员统一分配
- 文件路径逐步从“绝对路径”迁移为“存储位置 + 相对路径 / `storage://providerId/...`”模式，便于后续迁移、去重与跨存储复制
  - `AlbumService` 的相册 DTO 生成已继续去掉对本地文件系统目录遍历的依赖：
    - `hasSubAlbums` 改为按逻辑相对路径和数据库中的直接子相册判断
    - 聚合相册的最早拍摄时间回填也已支持 `storage://providerId/...` 路径，不再因为远端存储目录无法 `Files.list(...)` 而回退为空
  - `PhotoService` 的照片 DTO 路径展示也继续收口：
    - 优先返回标准相对路径
    - 若旧数据暂时还不能直接推导标准相对路径，则退回 `UserPathService.toDisplayPath(...)` 保留目录层级信息
    - 只有在两者都失败时，才继续退化成叶子文件名，尽量减少前台只看到“文件名而无层级”的情况
  - `FaceService` 的人物样本、人脸图片、人脸封面等路径展示也已跟随同一规则，避免人物页/人脸页仍保留单独的旧绝对路径兜底逻辑
  - 文件浏览器的服务层与控制器层也已跟进同一策略：
    - 浏览器列表返回路径会优先使用标准相对路径
    - 若旧路径无法直接转相对路径，则回退到 `toDisplayPath(...)`
    - 错误信息里的嵌入路径也会优先做同样的展示路径脱敏，避免前端仍直接看到旧绝对路径
- 本地存储提供者不再强依赖“必须位于扫描根目录内”才能被文件浏览器使用；若配置在扫描根目录外，仍允许浏览 / 上传 / 预览，但会明确标记“暂不支持自动扫描”
- 文件浏览器上传接口现会返回 `scanQueued / scanMessage`，前端可明确提示“已入扫描队列”还是“上传成功但当前存储未自动扫描”
- 扫描任务入口现会在建单前校验存储能力；若所选存储当前不支持扫描（例如扫描根目录外的本地存储），会直接拒绝创建扫描任务，而不是排入一个注定失败的队列项
- 后台首页的“立即触发扫描”与“扫描任务队列”也已同步展示存储扫描能力：不可扫描的存储会在下拉中直接禁用，并在任务列表中展示限制原因，避免前后端状态不一致
- 超级管理员的“存储配置”表单已改为按存储类型动态显示必需字段（如本地仅显示根目录，对象存储显示 endpoint / bucket / 前缀 等），并在保存时自动清理当前类型不需要的字段，避免本地存储残留旧的 bucket / endpoint 配置
- 扫描续扫游标本轮继续细化：
  - 目录断点改为在“目录处理完成后”再写入 checkpoint，不再刚进入目录就把整棵目录标成已完成
  - 恢复时若命中的是“已完成目录”断点，会跳过该目录整棵子树，再从后续兄弟项继续
  - 这样既避免了“刚进入目录就暂停，恢复后整目录被误跳过”，也避免了“命中目录断点后仍重复扫描已完成子树”
- `SMB / NFS` 已补第一版“挂载目录模式”：
  - 不直接在 Java 进程里执行挂载，而是要求运维先把共享挂载到宿主机
  - `baseDirectory` 填服务器上的挂载目录，如 `/mnt/photo-smb`
  - `endpoint` 可作为共享来源备注保留，但不再是必填项
  - 这样可以直接复用本地文件链路，获得浏览 / 上传 / 批量管理 / 预览 / 基础扫描能力
- 超级管理员存储能力说明已与实际能力对齐：`FTP / WebDAV` 现统一展示为“浏览 / 管理 / 上传 / 扫描 / 预览已接通”，不再误报“扫描链路待接入”
- 后端 `SuperAdminService` 保存存储提供者时也会同步做字段归一化：若当前类型不需要 `endpoint / bucketName / baseDirectory` 中的某项，会直接清空该字段，避免历史脏配置残留在数据库里
- 对外 API 返回图片路径时，逐步统一经由 `UserPathService` 转成“用户域相对路径”，避免控制器里继续手工裁剪 `/data/photos/...` 这类旧逻辑
- 依赖本地文件句柄的离线任务（例如部分批量抠图/图像处理入口）现在会先解析 `storage://...` 引用，仅对可映射到本地磁盘的资源执行，避免远端存储路径直接被误当作本地绝对路径
- 公开侧/后台侧的背景移除接口也已统一按此规则执行：远端存储照片不会再被误当作本地文件直接处理，而是明确返回“不支持本地抠图”的结果
- 服务层中的 AI 评分、EXIF 时间回填等依赖本地文件句柄的批处理也开始统一走相同判定：若图片路径无法映射到本地磁盘，则记录跳过，而不是继续按绝对路径硬读
- 后台照片批量移动/目标目录探测也开始优先兼容存储引用路径，避免多账号迁移后仍把路径字符串一律当作旧本地绝对路径处理
- 后台照片批量移动、删除、目标目录探测现在也开始按当前登录用户做所有权校验：普通用户只能操作自己的照片与相册，超级管理员例外

原则：

- 超级管理员配置全局可用存储提供者
- 后端必须能解析“当前生效写入存储位置”，普通用户无需感知底层存储系统
- 用户表可保存“由超级管理员分配的上传存储”
- 扫描任务应记录对应的存储提供者与根路径

### 3.7 扫描与任务系统

当前全局扫描机制不适合多用户并发场景，建议改为任务队列模式：

- 每个扫描动作入库成为 `scan_task`
- 默认低并发，建议单机最多同时运行 `1~2` 个扫描任务
- 当前已落地为“默认 1 个扫描工作线程”，并支持超级管理员在后台调到 `2`
- 多用户扫描按队列排队
- 上传触发扫描时创建增量扫描任务
- 任务必须保存断点信息，支持暂停 / 恢复 / 重试
- 定时扫描默认关闭，由超级管理员在后台显式开启

### 3.8 配置项存储

现有大量系统配置已经存于数据库 `system_config`，因此多用户相关系统开关也应继续放入数据库，而不是依赖 `application.yml`。

推荐新增的系统级配置项：

- `multi_user_enabled`
- `scan_scheduler_enabled`
- `default_user_quota_bytes`
- `local_storage_root`
- `user_data_root`

### 3.9 数据迁移策略

迁移必须分两部分进行：

1. 数据库迁移  
   - 为旧数据补充 `user_id`
   - 创建默认超级管理员
   - 创建“默认站点用户”或“迁移接收用户”
   - 把旧相册、照片、人脸、人物等归档到该用户名下

2. 文件系统迁移  
   - 把旧目录 `data/photos/...` 搬迁到 `data/photos/{userId}/...`
   - 建立迁移日志与可恢复检查点
   - 迁移完成后触发增量校验与补扫

## 4. 推荐表结构

本阶段先引入以下基础表：

- `user_account`
- `storage_provider`
- `login_record`
- `operation_log`
- `scan_task`

后续阶段继续扩展：

- `sms_verification_code`
- `vip_plan`
- `user_plan_order`
- 各业务表补充 `user_id`

## 5. 实施阶段

### P0：设计与基础设施

- 落地多账号设计文档
- 新增用户、存储、审计、扫描任务实体
- 在系统配置中加入多用户与扫描开关
- 定时扫描默认走数据库配置开关

### P1：身份体系重构

- 新增用户注册 / 登录接口
- 保留旧 `admin_user` 仅作过渡兼容
- 新前端登录页支持账号密码
- 后台鉴权从“是否管理员登录”演进为“用户身份 + 角色”

### P2：手机号与短信验证码

- 接入阿里云短信
- 支持手机号 + 密码登录
- 支持手机号 + 验证码登录
- 增加发送频率限制、验证码有效期、防刷策略

当前补充：

- 已扩展短信平台抽象，当前可接入 `ALIYUN`、`TENCENT_CLOUD`、`TWILIO`、`HUAWEI_CLOUD`、`VOLCENGINE`、`CLOOPEN`、`AWS_SNS`、`YUNPIAN`、`SUBMAIL`、`MESSAGEBIRD`、`VONAGE`、`INFOBIP`、`PLIVO`、`SINCH`、`TELNYX`、`SMSAERO`、`HTTP_WEBHOOK`
- 其中 `ALIYUN / TENCENT_CLOUD / TWILIO` 已有专用适配；其余平台当前可通过通用 HTTP / Webhook 骨架先完成配置联调
- 超级管理员可配置“强制绑定手机号”
- 当前仍以账号密码注册 / 登录为主线，短信验证码登录作为补充能力持续完善

### P2.5：邮件能力预埋

- 增加超级管理员 SMTP 配置
- 支持发送测试邮件
- 为后续邮件注册、邮箱验证码、密码找回预留基础设施
- 当前已补齐“找回密码”第一版：
  - `手机号 + 验证码 + 新密码`
  - `邮箱 + 验证码 + 新密码`
  - 前端提供独立“找回密码”页面，支持 Mock 场景联调

### P2.6：支付能力预埋

- 增加超级管理员支付配置
- 兼容多支付平台抽象，优先覆盖支付宝、微信支付
- 为后续 VIP 购买、订单支付回调、自动续费预留基础设施

当前补充：

- 超级管理员后台已落地统一支付配置，当前支持：
  - `ALIPAY`
  - `WECHAT_PAY`
  - `STRIPE`
  - `PAYPAL`
  - `UNIONPAY`
  - `PADDLE`
  - `LEMON_SQUEEZY`
  - `ADYEN`
  - `CUSTOM_WEBHOOK`
- 新增平台当前以“统一配置骨架 + 订单预览 + Mock 联调 + 回调结构兼容”为主，后续再逐个平台接 SDK / 官方签名逻辑
- 回调解析现已额外兼容：
  - `UNIONPAY`：`orderNo / queryId / respCode`
  - `PADDLE`：`event_type=transaction.paid` + `data.custom_data.orderNo`
  - `LEMON_SQUEEZY`：`meta.custom_data.orderNo` + `data.attributes.status`
  - `ADYEN`：`merchantReference / pspReference / eventCode`
- 已支持按平台生成“推荐接口地址 + 配置缺失字段 + 请求载荷预览 + 回调样例”
- 已增加“真实支付适配入口骨架”：
  - 公共入口：`POST /api/payments/orders/{orderId}/initiate`
  - 用户侧入口：`POST /api/auth/vip/orders/{orderId}/checkout/initiate`
  - 超级管理员入口：`POST /api/admin/super-admin/vip-orders/{orderId}/payment-initiate`
  - 当前会返回统一的 `launchUrl / httpMethod / redirect / actionType / headers / formFields / qrCodeText / payload / preview`
  - 这样前端已能区分“API 请求 / 表单跳转 / 二维码拉起”等模式，而不只是展示一份原始 JSON
  - 用户会员中心与超级管理员 `VIP 订单` 面板现也已补“一键拉起支付”能力：
    - `REDIRECT_FORM`：直接按返回表单字段提交到真实网关
    - `REDIRECT_GET`：自动拼接参数并跳转
    - `QR_CODE`：若返回 `weixin://` 或 `http(s)` 拉起链接，可直接在浏览器中打开
  - 后续各平台 SDK、签名、页面跳转、二维码生成、收银台拉起等逻辑统一收敛到 `PaymentProviderAdapter`
- 已支持针对 `VIP 订单` 做后台支付预览，便于先联调订单与支付参数
- 已支持 `Mock 支付`，用于在未接真实支付网关前验证“订单已支付 -> 套餐生效 -> 配额变化”链路
- 用户侧“个人资料 / 会员中心”已可查看当前配额、当前套餐、可购买套餐、最近订单，并可自助创建购买订单
- 已新增独立会员页 `/vip`，避免后续会员能力都堆叠在个人资料页
- 已补 `POST /api/payments/notify/{providerType}` 与 `GET /api/payments/return/{providerType}` 回调骨架，后续真实网关可直接往该统一入口接
- 支付发起入口现会把订单同步标记为 `gatewayStatus=PAYMENT_INITIATED`（Mock 模式为 `PAYMENT_INITIATED_MOCK`），并把拉起元数据落回订单，便于后台追踪“订单是否真的已经发起过支付”
- 已补超级管理员安全预演入口 `POST /api/admin/super-admin/payments/notify-preview/{providerType}`，用于不落库地检查：
  - 平台识别
  - 订单号提取
  - 订单号来源字段识别
  - 验签骨架结果
  - 支付/退款/取消状态识别
  - 外部交易号与退款金额提取
- 预演返回结果现也会补：
  - `providerLabel`
  - `resolvedOrderNoSource`
  - `predictedLifecycleAction`
  - `wouldUpdateOrder`
  - `predictedFinalStatus`
  - `recommendedActions`
  - 便于超级管理员直接判断“当前缺的是订单号、验签、状态识别还是本地订单”
- 已开始补齐 `PaymentCallbackAdapter` 注册层，当前先拆出：
  - `ALIPAY`
  - `WECHAT_PAY`
  - `STRIPE`
  - `PAYPAL`
  - `LEMON_SQUEEZY`
  - `MOLLIE`
  - `UNIONPAY`
  - `PADDLE`
  - `ADYEN`
  - `XENDIT`
  - `MIDTRANS`
  - `DefaultPaymentCallbackAdapter`
- 后续其余平台可继续按同一模式把“订单号提取 / 交易号提取 / 状态识别 / 返回页识别”从 `PaymentCallbackService` 迁出
- 退款预览也开始接入 `PaymentRefundAdapter` 注册层，当前已拆出：
  - `ALIPAY`
  - `WECHAT_PAY`
  - `STRIPE`
  - `PAYPAL`
  - `UNIONPAY`
  - `PADDLE`
  - `LEMON_SQUEEZY`
  - `ADYEN`
  - `MOLLIE`
  - `XENDIT`
  - `MIDTRANS`
  - `DefaultPaymentRefundAdapter`
- 退款预览主流平台骨架已基本全部迁入独立适配器，后续重点转向真实 SDK、验签、交易号回写与退款状态同步
- Stripe / PayPal 的退款预览也已进一步贴近真实网关请求：
  - Stripe 退款预览会额外展示 `Idempotency-Key`、`application/x-www-form-urlencoded` 请求体对象与编码后表单串
  - PayPal 退款预览会额外展示 `PayPal-Request-Id`、Basic 认证预览与 JSON 请求体草案
  - 超级管理员 `VIP 订单` 的退款骨架面板现可直接查看 `requestBodyJson / requestBodyEncoded`，便于人工联调真实退款 API
- 超级管理员“执行退款”链路已收紧为两段式：
  - `Mock` 模式下仍可直接落本地退款，便于联调会员额度回收
  - 非 `Mock` 模式下若退款配置已就绪，只会把订单打到 `refundStatus=REQUESTED / gatewayStatus=REFUND_REQUESTED`
  - 此时前后端统一显示“退款处理中”，等待真实支付平台回调或后续人工确认，不再直接伪装成“已退款”
  - 超级管理员现在也可在 `退款处理中` 阶段做两种人工收口：
    - `确认退款`：直接把订单收口为 `REFUNDED`
    - `退款失败`：把订单回退为 `refundStatus=FAILED / gatewayStatus=REFUND_FAILED`
  - 当退款失败但订单主状态仍为 `PAID / ACTIVE` 时，后台列表、会员中心与支付结果页会统一展示“退款失败”，提示重新发起退款或核对配置后重试
- 自动续费建单骨架也继续往前推进：
  - 预演结果现在会同时给出支付平台名称、`Mock/Live` 就绪度、缺失字段和告警，方便直接判断子单能否继续发起支付
  - 执行自动续费时，若支付已启用且处于 `Mock` 或 `Live Ready`，会在创建续费子单后同步写入支付发起参数骨架
  - 若支付未启用或配置未就绪，则仍保留“只创建续费待支付订单”的回退路径，并在执行结果里返回明确原因
- 当前回调解析已开始兼容常见的嵌套事件结构，例如 Stripe `data.object.client_reference_id`、PayPal `resource.invoice_id`
- 超级管理员支付设置已新增验签相关配置骨架：
  - `paymentVerificationMode`
  - `paymentApiSecret`
  - `paymentCertificateSerialNo`
  - `paymentPlatformCertificate`
  - 支付回调现已补齐“验签骨架”：
    - `AUTO`：根据支付平台与已配置密钥自动推断 `HMAC / RSA / CERTIFICATE`
    - `HMAC`：可用 `paymentWebhookSecret` / `paymentApiSecret`
    - `RSA`：为支付宝等公钥验签预留
    - `CERTIFICATE`：为微信支付平台证书验签预留
    - `CUSTOM`：用于自定义回调共享密钥
  - 当前阶段重点是“配置校验 + 验签结果结构化返回 + Mock 联调”，真实 SDK 验签由后续各支付平台适配器接入
- 已增加支付结果页 `/vip/result`，便于联调回跳页与订单状态查询
- 支付结果页返回结构已补充诊断字段：
  - `resolvedOrderNoSource`
  - `terminal`
  - `suggestedPollIntervalSeconds`
  - `recommendedActions`
- 当订单处于 `refundStatus=REQUESTED` 且主状态仍为 `PAID / ACTIVE` 时，支付结果页与后台列表会统一展示“退款处理中”，并提示继续等待退款回调或人工确认
- 前端结果页会直接展示建议动作，并按后端建议的轮询间隔做自动刷新
- 返回页订单号识别继续细化到更多支付平台：
  - `UNIONPAY`：`orderId / merOrderId`
  - `XENDIT`：`reference_id`
  - `MIDTRANS`：`order_id`
  - `ADYEN`：`merchantReference`
  - `STRIPE`：`client_reference_id`
- 因此 `resolvedOrderNoSource` 不再只依赖通用兜底，能更准确地告诉前端“本次到底是从哪个回跳参数识别出订单号的”
- 超级管理员 `API测试工具` 已新增“预演支付回调”，可先验证回调结构，再决定是否命中真实回调入口更新订单状态
- 超级管理员 `API测试工具` 现已支持按支付平台一键填充“回调负载 / Header / 返回页参数”示例，降低多平台联调成本
- 同一面板现已补上“原始回调体”输入：
  - 预演接口会把 `rawBody` 一并带入，便于检查微信/Stripe/自定义 Webhook 的签名原文识别
  - 超管预演入口 `POST /api/admin/super-admin/payments/notify-preview/{providerType}` 现也已对齐真实回调入口，支持 `application/json / form / text/plain / 无明确 Content-Type`
  - 真实回调测试若填写了原始回调体，会按 `Content-Type` 自动选择发送 JSON 包裹体或直接发送 `text/plain` 报文
  - 复制出的回调 `cURL` 也会同步切换成对应的原始报文，便于直接在终端复现第三方网关回调
- 同一面板现也会把“预演结果”摘要化展示，并提供：
  - 打开支付结果页
  - 按订单号定位到超级管理员 `VIP 订单`
  - 让支付联调从“构造 payload”到“定位订单排查”形成闭环
- 同一面板中的“返回页测试”现也会摘要展示：
  - 平台
  - 订单号来源
  - 当前状态 / 是否终态
  - 建议轮询间隔
  - 建议动作
  - 方便直接判断是继续等回调，还是转去超管订单页继续排查
- 同一面板现也支持一键复制：
  - 支付回调预演 `cURL`
  - 支付回调 `cURL`
  - 返回页查询 `cURL`
  - 便于把浏览器里的联调参数直接搬到终端、脚本或运维环境中复现
- 面板中也会直接展示三类命令预览，超级管理员可以先目视确认参数，再复制出去执行
- 面板中的支付调试 JSON 现已按支付平台持久化到本地：
  - 切换平台后会恢复上次编辑内容
  - 刷新页面后仍可继续上次联调
  - 避免在多平台轮流接入时反复丢失样例参数
- 同时支持：
  - 导出当前平台调试预设
  - 一键重置当前平台预设为系统模板
  - 便于在测试 / 预发 / 正式等多环境之间共享联调参数
- 下一阶段再继续接：
  - 真实下单接口
  - 异步回调验签
  - 支付网关交易号回写
  - 退款 / 取消 / 自动续费

### P3：前端路由与页面改造

- 公开路由改为 `/{userSlug}/...`
- 增加注册页、登录页、个人资料页
- 首页、相册、图墙、随机、人物页标题栏右上角展示当前账号
- 用户后台与超级管理员后台拆分 Tab
- 超级管理员页继续拆分为多个内部页签：概览、全局设置、短信/邮件/迁移/API 工具、用户管理、登录记录、操作记录、VIP、VIP 订单、存储配置
- 用户管理、登录记录、操作记录等长列表统一使用表格工作区，并支持：
  - 服务端分页与页大小切换
  - 列点击排序
  - 列显示/隐藏
  - 列拖动排序
  - 偏好持久化保存到超级管理员配置（后端 `system_config`）
  - 用户管理中的就地编辑与单行保存
- 同一套表格能力继续复用于 VIP 套餐、VIP 订单、存储提供者等长列表，保证后续扩展更多超管页面时交互一致
- 概览页已补充二级明细块，用于集中查看目录位置、通知/支付开关和基础风险指标
- 管理端表单逐步统一为“固定标题 + 输入框 + 辅助说明”结构，避免仅依赖 placeholder 导致录入后看不清字段含义；存储配置字段也按存储类型动态展示
- 超级管理员的存储配置表单继续补齐可见标题与辅助文案：如“优先级”“服务器地址 / WebDAV 地址 / 桶名 / 基础目录”等字段不再只靠 placeholder 区分，输入后仍能明确知道当前值的含义
- 超级管理员的短信 / 邮件 / 支付配置页也已继续补齐固定说明文案，尤其是 `Endpoint / Region / Secret / 回调地址 / 证书序列号 / 端口` 等容易在输入后失去语义的字段，后续新增系统级表单应保持同样结构
  - 支付配置表单现也已改成按平台动态显示字段：
    - 不同平台只展示自身需要关注的字段
    - 每个字段都会保留标题、简短标签、是否必填和用途说明
    - 避免输入后再看不出“100”“密钥文本”“地址”分别代表什么
  - 支付配置中的 `paymentVerificationMode` 也已改成按平台动态约束：
    - 支付宝默认只允许 `AUTO / RSA`
    - 微信支付默认只允许 `AUTO / HMAC / CERTIFICATE`
    - Stripe、Adyen、Paddle、Lemon Squeezy 等 Webhook 型平台默认只允许 `AUTO / HMAC`
    - 切换支付平台或套用推荐配置时，前端会自动把旧的无效验签模式归一化到当前平台允许值，避免出现“平台不支持但仍被保存”的配置组合
    - 后端 `PaymentConfigService` 也会在“切换平台 / 保存验签模式 / 读取配置”三个入口再次归一化，避免绕过前端直接调接口时写入无效模式

### P4：业务数据加 user_id

- `album`
- `photo`
- `face`
- `person_profile`
- `tag`
- `comment`
- `filter_option`
- 其他所有与图片数据相关的表

### P5：文件系统迁移

- 旧文件迁移到 `data/photos/{userId}/...`
- 新增 `data/users/{userId}/...`
- 迁移完成后刷新数据库路径

### P6：配额、VIP、多存储

- 原图空间统计
- 超额上传拦截
- VIP 套餐扩容
- 存储提供者切换与默认上传位置

### P7：扫描任务系统重构

- 全局扫描逻辑改为任务队列
- 低并发执行
- 断点续扫
- 排队可视化
- 定时扫描由超级管理员控制

## 6. 风险与注意事项

- 当前项目使用 `ddl-auto: update`，不适合长期承担大规模结构迁移
- 旧公开链接兼容需要单独设计跳转与归属校验
- 内容去重与秒传不能只依赖当前 `content_hash unique`
- 后续若要支持跨用户全局搜索 / 人脸联动，需要在“租户隔离”和“全局索引”之间做双层设计

## 7. 本次已开始落地的内容

本次提交优先落地：

- 设计文档
- 多账号基础实体骨架
- 审计与扫描任务骨架
- 系统配置中的多用户 / 扫描开关
- 定时扫描默认关闭的后端逻辑基础

截至当前阶段，已经继续落地：

- 新登录 / 注册 / 当前用户接口
- 旧 `admin_user` 向 `user_account` 的兼容迁移
- 前端登录页、注册页、个人资料页
- 公共页面右上角当前账号菜单
- 后台 Tab 拆分为“后台管理 / 超级管理员”
- 超级管理员后端接口第一版：
  - 全局设置（多用户开关、扫描开关、默认配额、存储根目录）
  - 用户列表与用户配额 / 角色 / 状态调整
  - 存储提供者列表、新增、编辑、默认存储切换
- 超级管理员前端控制台第一版
- `album` / `photo` / `person_profile` / `photo_face` / `tag` 已增加 `user_id`
- 启动时自动把旧相册、旧照片、旧标签等迁移归属到默认超级管理员账号
- 开发环境已关闭 Spring Devtools 热重载，避免大改时容器内类文件损坏
- 公共前端路由已支持 `/{userSlug}/...` 别名访问
- 公共接口已支持按 `userSlug` 做相册 / 照片 / 人物 / 搜索 / 筛选 / 筛选项隔离
- 评论接口已支持按 `userSlug` 做读取 / 创建 / 删除 / 限流隔离，评论表已补 `user_id`
- `filter_option` 已按用户维度重建缓存，公开筛选项与标签统计不再串用户
- 后台标签管理已按当前登录账号隔离：
  - `/tags` 仅返回当前账号标签
  - 新建 / 修改 / 删除标签仅作用于当前账号
  - 相册“添加标签 / 移除标签”已校验相册与标签归属一致
  - AI 智能标签会按 `photo.user_id` 写入对应用户标签集合
- `tag` 表已切换为 `(user_id, name)` 复合唯一约束，并在启动时尝试迁移旧的全局唯一索引
- 公共页面中的首页、图墙、随机、人物、搜索、相册详情、人物详情、图片详情已开始保留并传递 `userSlug`
- 登录/注册成功后，若开启多用户，会默认跳到当前账号的 `/{userSlug}` 公共首页，避免根路径看不到私有数据
- `VIP / 支付结果 / 个人资料` 等登录后私有页面不再参与 `userSlug` 公开站点重写，避免被错误拼成 `/{userSlug}/vip/...`
- 后台中的“打开公开图片详情”也已改为优先带上当前登录账号的 `slug`，避免从后台跳出时落到错误站点
- 后台中的“按标签打开图墙”也已统一保留当前登录账号的 `slug`，确保图墙筛选结果属于当前用户站点
- 登录页已支持两种模式：
  - 用户名/手机号 + 密码
  - 手机号 + 短信验证码
- 后端已新增短信验证码基础设施：
  - `sms_verification_code` 表
  - `/auth/send-code` 发送登录验证码
  - `/auth/login` 支持 `loginType=smsCode`
  - 阿里云短信直连签名请求（无 SDK），未配置时默认走 mock 模式，便于本地联调
- 超级管理员后台现已支持短信参数可视化配置：
  - 支持配置真实短信开关、mock 开关、Endpoint、RegionId、AccessKey、签名、模板编号、模板参数名、验证码有效期
  - 运行时优先读取数据库中的系统配置；若未配置则回退到 `application.yml`
  - 因此当前可以先保持账号密码注册/登录为主流程，仅在需要时逐步打开短信能力
- 超级管理员设置已新增“强制绑定手机号”：
  - 配置项为 `force_bind_phone`
  - 开启后注册接口与注册页都会要求填写手机号
  - 仍保留账号密码注册/登录，不强制切到短信登录
  - 成功使用短信验证码登录后，会自动将该手机号标记为已验证
- 公开认证配置已新增 `/auth/public-settings`，前端登录/注册页可读取多用户与手机号策略
- 用户资料页已支持编辑昵称、手机号、项目中英文名称：
  - `PUT /auth/profile` 可更新当前登录用户资料
  - `GET /auth/me` 与登录响应已返回 `projectNameZh`、`projectNameEn`、`phoneVerified`
  - 手机号修改后会自动重置为“未验证”状态，等待后续验证码验证
  - 当前登录用户也可自定义公开 `slug`，用于 `/{slug}` 访问路径
- 当前登录用户已支持手机号验证闭环：
  - `POST /auth/phone/send-code` 发送当前账号手机号绑定验证码
  - `POST /auth/phone/verify` 校验验证码并把 `phoneVerified` 置为 `true`
  - 个人资料页已提供发送验证码与确认绑定入口，便于后续启用“强制绑定手机号”
- 用户头像已开始落地到 `data/users/{userId}`：
  - `POST /auth/avatar` 支持上传当前登录用户头像
  - 头像文件通过 `/api/user-files/**` 对外访问
  - 个人资料页与右上角账号菜单已展示头像
- 公开站点项目名已开始按 `userSlug` 联动：
  - `GET /auth/public-user` 可返回公开用户的项目名与基础资料
  - 前端公共页标题、头部品牌文案、账号菜单会优先展示该用户自定义项目名
- 当前上传存储规则已调整为“普通用户对存储系统无感”：
  - 资料页不再暴露默认上传存储设置
  - 上传位置与存储提供者仅允许超级管理员在后台统一配置
  - 普通用户文件浏览器会自动落到系统默认或管理员分配的存储，不允许自行切换 `providerId`
- 后台文件浏览器已按当前登录用户隔离到独立根目录；多用户开启时默认落到 `data/photos/{userId}/...`
- 文件浏览器已开始接入“当前存储提供者”选择：
  - `GET /admin/folders/base-path`、`/browser/list`、`/browser/upload` 等接口已支持携带 `providerId`
  - 后端会优先按“超级管理员为该用户分配的上传存储 -> 系统默认存储 -> 第一个可用本地存储”解析当前上传位置
  - 当前文件浏览器已放开 `LOCAL / WEBDAV / FTP / COS / S3 兼容家族` 等可浏览存储；普通用户仍不暴露选择器，只有超级管理员可以切换查看不同存储
  - 实际写盘路径已不再强绑全局唯一根目录，而是落到 `provider.baseDirectory/{userId}/...`
  - 前端文件浏览器已新增管理员写入位置下拉、当前根目录展示；写入位置下拉仅超级管理员可见，普通用户仅使用管理员分配结果
- 文件浏览器下载链路已拆出专用接口：
  - 新增 `GET /admin/folders/browser/download`
  - 下载接口统一返回 `attachment` 响应头、安全文件名与 UTF-8 文件名编码
  - 前端下载动作会优先读取响应头中的真实文件名，不再复用预览接口模拟下载
- 批量移动目标目录已改为“相对当前存储根目录”输入，不再要求输入绝对路径，进一步降低跨用户目录误操作风险
- 旧的目录迁移管理页也已切到“相对当前根目录”输入模式，前端不再鼓励管理员直接手填绝对路径
- 相册管理页中涉及父子层级、聚合到上一级等交互，也开始优先基于后端返回的 `relativePath` 判断，不再把绝对路径直接暴露给管理员作为主要操作语义
- 后台设置页关于“最大相册层级”的说明文案也已同步改成“当前用户图片根目录 / 分类 / 顶级相册 / 层级...”视角，避免继续向管理员暴露单租户 `base-path` 语义
- 相册管理页里“移动到上级 / 打开目录选择器 / 聚合到上一级 / 关闭聚合后恢复子相册”这几段前端逻辑已进一步统一到相对路径分段处理，顺手修复了一个聚合时引用未定义 `pathParts` 的前端运行时问题
- 相册移动、合并、目录选择器、同级/下级目录探测等后台接口也开始统一要求登录态，并按当前用户或超级管理员作用域限制可见目录：
  - 普通用户只能在自己的 `data/photos/{userId}`（或当前分配存储的对应用户根目录）内选择目标位置
  - 超级管理员仍可查看全局根目录并执行跨用户治理类操作
  - 路径预检、冲突检查、覆盖清理、数据库路径重写均会带上当前操作人的作用域，避免误扫/误改其他账号的数据
- 照片删除接口也已切到“必须登录 + 按照片所属用户校验”模式，避免匿名或跨账号直接删除图片；后续其余照片级写操作也继续沿同一规则收口
- 文件上传已接入用户空间配额校验，按原始上传文件大小统计
- 存储提供者能力模型已细化，不再把非本地存储一刀切拒绝：
  - `LOCAL` 已标记为浏览 / 上传 / 扫描 / 预览全链路可用
  - 已配置账号密码的 `WEBDAV` 现可作为“默认上传存储”或“用户分配上传存储”被超级管理员保存
  - 已配置账号密码的 `FTP` 与已配置 `bucketName + region + 密钥` 的 `COS` 现已支持真实上传，可作为系统默认上传位置或用户分配上传位置
- 文件浏览器中的存储切换下拉现仅允许选择“可浏览”的存储，避免误选到暂不支持浏览的远端存储
- 文件浏览器的“新建文件夹 / 开启多选 / 批量移动 / 批量删除 / 拖拽上传”现已统一按当前存储能力矩阵直接禁用，不再等点击后才弹错误；页面会同步展示“上传 / 管理 / 扫描 / 预览”的即时状态与受限原因
- 上传链路已开始接入统一存储写入服务：
  - `FolderService.uploadFiles` 不再写死 `MultipartFile.transferTo(localPath)`，而是按当前生效存储提供者路由
  - `LOCAL` 仍写入 `provider.baseDirectory/{userId}/...` 并自动触发后续本地扫描任务
  - `WEBDAV` 已支持递归创建目录、覆盖上传与覆盖前大小探测（HEAD + MKCOL + PUT）
  - `FTP` 已支持按用户目录前缀自动建目录、覆盖上传与覆盖前大小探测
  - `COS` 已支持按 `bucketName/baseDirectory/{userId}/...` 上传对象与覆盖前大小探测
  - 远端存储上传完成后会自动创建对应 `UPLOAD_SCAN` 任务，不再静默跳过入库
- 远端文件浏览器已继续往前推进：
  - `WEBDAV` 当前已支持浏览、建目录、删除、重命名、移动
  - `FTP` 当前已支持浏览、建目录、删除、重命名、移动
  - `COS` 当前已支持浏览、建目录、删除、重命名、移动
  - `COS` 当前已支持在后台文件浏览器中生成临时预览地址打开原文件
  - 文件浏览器打开远端文件时，前端现会优先尝试“直开远端预览 URL”，若当前存储类型不支持再自动回退为后台代理流式预览，减少大文件一律先走后端中转的压力
  - 文件浏览器现在会直接展示当前存储的“浏览 / 上传 / 预览”能力状态，并同步显示当前存储的限制说明
  - 当某个远端存储尚未接通预览或批量管理能力时，前端会优先按当前存储能力给出更直观的提示，而不是只报通用失败
  - 文件右键菜单已新增“下载”动作，可直接复用当前受控预览接口把远端文件下载到本地
- `WEBDAV / FTP / COS / S3 兼容家族` 当前已接通“远端扫描入库”链路：上传后可自动把远端目录结构、相册、照片基础记录写入数据库
- 远端扫描本轮已继续增强一层：
  - 会把远端源图按用户范围下载到本地受控缓存目录 `data/photos/{userId}/.remote-cache/{providerId}/...`
  - 在本地缓存上继续执行 `EXIF 提取 / 缩略图生成 / 基础色彩分析`
  - 原图 `originalPath` 仍保持远端 `storage://远端provider/...` 引用，不会被本地缓存路径覆盖
  - 缩略图等派生资源落本地默认存储，便于现有前端预览链路直接复用
- 当前远端扫描仍未完全补齐人脸、主体、智能标签、AI评分、抠图等完整重处理链路
  - 仍未支持 `WEBDAV / FTP` 远端文件预览，以及远端图片派生资源生成
- 照片文件路径已开始从“绝对路径”渐进迁移到“存储位置 + 相对路径引用”：
  - `Photo.originalPath`、缩略图、WebP、抠图等路径字段当前允许同时存在旧绝对路径和 `storage://{providerId}/{userId}/{relativePath}` 两种格式
  - 启动迁移会尝试把本地存储中的旧照片路径改写为 `storage://...` 引用，并同步刷新 `pathHash`
  - 新扫描写入本地存储时会优先保存为 `storage://...`，停止继续扩大绝对路径债务
  - 文件读取、AI 打分、抠图等直接读原图的链路已开始统一通过兼容解析层把 `storage://...` 还原到真实本地路径
  - 扫描查重、文件浏览器缩略图查询、覆盖移动清理等高频入口正在持续补齐“绝对路径 / 存储引用”双格式兼容
  - `AlbumService`、`PhotoScanService` 中旧的 `data/photos` / `base-path` 字符串截取逻辑已开始继续收敛，优先统一复用 `UserPathService.extractTenantRelativePhotoPath(...)`
- 上传后的扫描链路已开始把新建相册 / 照片 / 人脸自动写入对应 `user_id`
- 前台搜索弹窗与后台“打开公开相册”已统一按当前 `userSlug` 生成链接，避免多站点模式下跳错到默认站点
- 背景抠图派生文件路径已补齐为“存储位置 + 相对路径引用”写法，继续收敛绝对路径遗留
- 超级管理员配置文案也继续去单租户化：
  - “最大相册层级”改为按“当前用户图片根目录 / 分类 / 顶级相册 / 层级...”理解
  - 默认用户配额 / 默认 VIP 额外配额文案改为建议按 GB 输入与展示，底层仍按字节存储
  - 本地存储根目录明确为“总根目录”，多用户模式下实际落到 `{root}/{userId}/...`
- 公开端 `POST /photos/{id}/remove-background/async` 已从占位接口补成可用提交入口：
  - 现在会校验源图路径、复用现有并发抠图线程池并返回 `queued / processing / cached / status`
  - 抠图任务完成后会自动把 `backgroundRemovedPath` 回写到数据库，并优先写成 `storage://...` 引用
- 抠图缓存读取与清理链路已补齐 `storage://...` 解析兼容，避免路径引用化后缓存命中失败
- 前端拼贴封面、搜索结果、人脸批量分配等图片访问入口已继续统一到 `buildPhotoAssetUrl(...)`，减少旧式 `/api/files` / `/api/photos/{path}` 直链残留
- 旧数据启动迁移后会顺带回填每个用户的 `storage_used_bytes`
- 旧数据启动迁移现在会继续补齐评论 `user_id`，并重建 `filter_option` 缓存
- 超级管理员后台已新增“执行旧数据迁移”入口，可手动重跑历史数据归属与目录迁移，方便排查登录后历史照片为空的问题
- 旧文件批量迁移已补齐到按用户目录执行：
  - 启动迁移与手动迁移都会把旧的 `data/photos/分类/相册` 重写到 `data/photos/{userId}/分类/相册`
  - 会按相册 / 照片记录逐条重写路径，并尽量搬迁磁盘上的原图、缩略图、WebP、抠图等派生文件
  - 路径解析、相册层级计算、人物目录相似度前缀匹配已统一兼容可选的 `userId` 前缀，避免迁移后出现分类错位或深度误判
  - 超级管理员迁移结果面板现会额外展示：
    - 开始时间 / 完成时间
    - 归属迁移总计
    - 路径改写总计
    - `rewrittenPhotoStorageRefCount` 等“绝对路径 -> 存储引用”细项
- 人物后台接口已按当前登录账号隔离：
  - `FaceController` 下的人物 / 人脸 / 聚类 / 照片指派接口已统一注入当前登录用户作用域
  - 普通用户后台不再读取或修改其他账号的人物、人脸、人物相册推荐与图片指派数据
  - 超级管理员仍保留全局视角
- 人物公开页导航已调整为站内跳转：
  - 人物列表进入人物详情不再强制新开标签页
  - 多用户模式下会继续保留并传递当前 `userSlug`
- 操作日志已开始落地：
  - 文件浏览器的上传、删除、移动、重命名、建目录会写入 `operation_log`
  - 扫描任务的创建、恢复、暂停、取消会写入 `operation_log`
  - 后台首页已展示最近操作记录
- 登录记录已开始落地并支持超级管理员查看：
  - 登录成功 / 失败都会写入 `login_record`
  - 记录包含登录方式、IP、UA、失败原因、用户名/手机号快照
- 超级管理员后台可按用户筛选最近登录记录
- 普通用户后台首页也可查看自己的最近登录记录
- 超级管理员前端已进一步拆成多页签工作区，用户管理与登录记录已改为分页交互，可分别独立浏览和维护
- 超级管理员现已新增“操作记录”独立页签，支持按用户筛选、分页查看上传/删除/移动/扫描等审计事件
- 用户侧关键动作也已开始进入统一审计：
  - 注册成功后会记录 `USER_ACCOUNT` 注册事件
  - 资料修改、手机号验证码发送/确认、头像上传会记录到 `USER_PROFILE / PHONE_VERIFY / USER_AVATAR`
  - 会员下单、发起支付、Mock 支付、自动续费开关更新会记录到 `VIP_ORDER`
- 扫描任务已落地数据库队列执行：
  - `/admin/scan`、`/admin/scan/force` 改为入队 `scan_task`，并支持携带 `storageProviderId` 指定扫描哪个存储
  - 后端默认以 `1` 个工作线程串行执行，避免多人并发扫描压垮服务器
  - 超级管理员现可在全局设置中把扫描工作线程数调到 `2`
  - 多工作线程场景下，运行中任务现按集合而不是单个 `activeTaskId` 跟踪；服务关闭时会把所有正在执行的任务统一落回 `PAUSED`，避免只暂停最后一个任务
  - 同一用户、同一路径的待执行扫描任务会自动合并，减少重复排队与重复扫盘
  - 同优先级任务已开始按用户轮转派发，降低单个用户连续占满队列导致的饥饿问题
  - 文件浏览器上传完成后不再立即重扫单文件，而是自动合并为 `UPLOAD_SCAN` 队列任务
  - 任务持久化保存 `processed_items`、`skipped_items`、`failed_items`、`last_processed_path`
  - `checkpoint_json` 现已开始显式保存恢复游标，暂停 / 异常 / 服务重启后会优先按检查点恢复
  - 断点信息现已额外记录 `rootPath`、`lastProcessedType`、`resumeFromType`，可区分当前恢复锚点是文件还是目录，便于继续往目录栈级续扫演进
  - 续扫进度不再在预统计阶段被清零，恢复时会保留已处理计数，并从断点文件的下一项继续
  - 当断点锚点命中“目录”时，现在会直接跳过该目录整棵子树，从它后面的兄弟项继续；不会再把刚完成的目录重新整棵重扫
  - 当断点锚点命中“文件”时，则只跳过该文件本身，继续处理下一个文件，目录与文件两类锚点的恢复语义已拆开
  - 服务重启后会把进行中的任务恢复为待续扫状态，并继续排队执行
  - 管理后台扫描状态接口现已返回当前任务、运行中任务摘要、排队用户分布与队列数量
  - 管理后台首页已展示最近扫描任务，并直接显示任务归属、存储位置、失败信息，支持对失败/暂停/已取消任务重新入队
  - 运行中任务已支持发送“暂停 / 取消”请求，在当前文件处理完成后安全生效；暂停后的任务不会自动恢复，需手动继续

仍未完成但已进入下一阶段的内容：

- 超级管理员用户管理继续增强中：
  - 已支持修改昵称、手机号、角色、状态、基础配额、VIP 额外配额、项目名、公开状态、上传存储分配
  - 已支持由超级管理员直接重置用户密码
- VIP 当前先采用“基础配额 + 额外配额”模型落地，套餐、订单、购买流程仍待后续完善
- 超级管理员后台已新增 VIP 套餐管理骨架：
  - 可维护套餐编码、名称、扩容空间、时长、价格、启停状态
  - 可将套餐直接分配到用户，并设置到期时间
  - 当前“购买下单 / 支付回调 / 自动续费”流程仍待后续完善
- `user_plan_order` 骨架已开始落地：
  - 超级管理员可手工创建 / 维护 VIP 订单记录
  - 订单状态切到 `PAID / ACTIVE` 后会自动把套餐生效到用户并计算到期时间
  - 超级管理员可对订单生成支付预览，并在 Mock 模式下一键模拟支付成功
  - 超级管理员现在也可直接对订单执行“标记取消 / 标记退款”，用于人工客服处理、联调和异常补单场景
  - VIP 订单列表现已支持“仅自动续费 / 仅看待续费”筛选，便于从长列表中快速定位需要续期处理的订单
  - 普通用户已可在个人资料页创建自助购买订单、查看最近订单、生成结算预览
  - 订单模型现已额外记录 `gatewayStatus / cancelledAt / refundStatus / refundAmountFen / refundedAt`
  - 自动续费骨架字段也已进入订单模型：`autoRenewEnabled / nextRenewalAt`，便于后续补续费任务与提醒计划
  - 超级管理员后台已新增“自动续费预演”面板：
    - 仅挑出 `autoRenewEnabled = true`
    - `nextRenewalAt <= now`
    - `status in (PAID, ACTIVE)` 的候选订单
    - 当前支持先做干跑（dry-run）预演，再手动执行“续费建单”
  - 自动续费建单骨架已开始落地：
    - 超级管理员可手动执行“续费建单”，对到期订单批量生成新的 `AUTO_RENEW` 待支付订单
    - 原始订单会关闭自身 `autoRenewEnabled`，并通过 `renewalSourceOrderId` 把续费链路串到新订单上，避免重复建单
    - 新续费订单支付成功后会自动补齐新的 `expireAt / nextRenewalAt`
    - 同时来源主单会补记 `gatewayStatus=RENEWAL_COMPLETED` 与续费子单编号备注，便于后台顺着订单链排查
    - 若续费子单被取消，来源主单会自动恢复 `autoRenewEnabled` 与 `nextRenewalAt`，并改记 `gatewayStatus=RENEWAL_CANCELLED`
    - 续费预演与执行结果会直接返回“阻塞原因 / 已存在续费子单编号与状态”，便于超管快速判断某条订单为何被跳过
    - 当前仍不会自动真实扣款，只负责续费订单派生，便于后续继续接支付宝 / 微信 / Stripe 等自动代扣能力
    - 续费阻塞条件已继续补齐：
      - 若用户状态不是 `ACTIVE`，会直接阻止派生续费订单
      - 若套餐已停用，也会直接阻止派生续费订单
      - 预演与执行结果都会回显明确的 `renewalBlockedReason / reason`
  - 普通用户现在也可在个人资料页 / 会员中心对自己的订单自助开启或关闭自动续费：
    - 当前仅允许 `CREATED / PAID / ACTIVE` 状态修改
    - 开启后优先以订单 `expireAt` 作为下一次续费时间
    - 续费派生出的新订单也会显示 `renewalSourceOrderId`，便于用户理解续费链路
  - 普通用户的 `Profile` 页现在也已补齐与 `会员中心` 基本一致的支付发起链路：
    - 最近订单可直接执行“发起支付”
    - 对 `REDIRECT_FORM / REDIRECT_GET / QR_CODE` 类型的发起结果，可直接在浏览器中拉起支付页
  - 订单交互约束已进一步收紧：
    - 仅 `CREATED` 状态允许发起支付 / Mock 支付
    - 已取消、已退款、已支付订单不会再暴露支付发起入口
    - 后端已额外返回 `canInitiatePayment / canToggleAutoRenew / orderStageLabel / renewalChainType`，前端不再硬编码状态判断
  - 超级管理员全局设置已新增“自动续费建单任务”开关：
    - 定时任务每 5 分钟检查一次到期待续费订单
    - 逻辑与手动执行相同，只创建续费待支付订单，不直接扣款
  - 支付回调已开始识别 `PAID / CANCELLED / REFUNDED` 三类结果并写回订单，便于后续继续接退款、撤销、自动续费补偿等链路
  - TODO：真实支付、回调验签、自动续费真实扣款、退款网关联动仍待后续完善
  - TODO：当前优先保证 `ALIPAY / WECHAT_PAY` 两条主链，其余支付平台统一按“骨架 / 预览 / 提示”管理，避免误判为已完工
- 超级管理员后台已新增支付配置骨架：
  - 已支持 `ALIPAY`、`WECHAT_PAY`、`STRIPE`、`PAYPAL`、`UNIONPAY`、`PADDLE`、`LEMON_SQUEEZY`、`ADYEN`、`MOLLIE`、`XENDIT`、`MIDTRANS`、`CUSTOM_WEBHOOK`
  - 可配置应用ID、商户号、商户名、密钥、回调地址、返回地址、Webhook Secret、币种
  - 已可按订单生成支付请求预览与回调样例，辅助后续接入真实网关
  - 支付预览里的“缺失字段”判断已进一步按平台细化：
    - 微信支付现会额外校验 `paymentApiSecret`，若验签模式为 `CERTIFICATE` 还会要求 `paymentCertificateSerialNo / paymentPlatformCertificate`
    - Stripe / PayPal / Paddle / Lemon Squeezy / Adyen / Xendit 等也会分别校验各自更接近真实网关的关键字段
  - 支付预览已进一步拆分“字段完整”和“真实接入就绪”：
    - 仅当必填字段齐全且没有明显签名/证书格式风险时，`liveModeReady=true`
    - 现会额外返回 `readinessWarnings / signatureReady / callbackVerificationReady / refundReady`
    - 可更早识别“字段填了，但私钥/公钥/证书明显不像真实可用内容”的场景
    - 现还会补 `stageReadiness` 分阶段清单：
      - `下单`：检查商户标识、签名私钥/API 凭证、回跳地址
      - `回调`：检查通知地址以及 RSA/HMAC/证书等对应验签材料
      - `退款`：检查退款 API 地址与退款凭证/私钥
    - 超级管理员和用户侧支付预览都会直接展示该阶段检查结果，便于明确卡在支付链路哪一段
    - 现还会补 `recommendedConfigFields / nextActionHints`：
      - 前者按平台列出当前最关键的配置项
      - 后者给出下一步联调建议，减少超管还要自己从长文案里提炼动作
  - 发起支付入口现在会在“关闭 Mock 且 liveModeReady=false”时直接拒绝，避免进入只会报错的半成品下单流程
  - 支付发起入口已进一步拆成独立平台适配器：
    - 现已为 `ALIPAY`、`WECHAT_PAY`、`STRIPE`、`PAYPAL`、`UNIONPAY`、`PADDLE`、`LEMON_SQUEEZY`、`ADYEN`、`MOLLIE`、`XENDIT`、`MIDTRANS` 提供单独的发起骨架
    - 每个平台会返回更贴近真实网关的 `launchUrl / headers / formFields / payload / actionType`
    - 其余未单独细化的平台仍由默认适配器兜底，避免新增平台时阻塞整条订单链路
    - 本轮继续把四个主干平台的发起骨架收紧到更接近真实请求：
      - 支付宝：补 `charset / sign_type / version / timestamp / biz_content`
      - 支付宝发起结果现会直接回显待签名串 `signingContent`、签名字段名 `sign`，便于后续直接接 RSA2 真签名
      - 若已配置商户私钥，支付宝发起结果现会直接给出 `sign` 预签名结果，超管可先用于联调比对
      - 支付宝 `REDIRECT_FORM` 现也已改成更接近真实网关表单：`biz_content` 会序列化为 JSON 字符串参与签名与提交，并额外回显 `requestBodyForm / requestBodyEncoded`
      - 微信支付：补 `appid / mchid / amount / attach / payer`
      - 微信支付发起结果现会直接回显 `Authorization` 签名字段、签名算法名与 Header 模板，便于继续接商户私钥签名
      - 若已配置商户私钥，微信支付发起结果现会直接给出 `signingMessage / requestBodyJson / authorizationPreview`
      - Stripe：补 `client_reference_id / metadata / line_items / allow_promotion_codes`
      - Stripe 发起结果现也会直接给出更接近真实请求的 `application/x-www-form-urlencoded` Body 预览、`Idempotency-Key` 与认证头骨架，便于直接对照官方 API 联调
      - PayPal：补 `purchase_units / application_context / payer`
      - PayPal 发起结果现也会直接给出 `Basic Authorization` 预览、`PayPal-Request-Id` 与 JSON Body 字符串，便于直接对照官方下单请求排查
      - 用户侧会员中心与超级管理员支付发起面板现也会直接展示这些 JSON / 表单编码请求体预览，减少联调时还要手工翻嵌套字段
  - 已补统一支付回调 / 返回页骨架，但验签、证书校验、真实网关 SDK 仍待继续完成
  - 支付回调识别继续细化中：
    - `PaymentCallbackService` 现已支持从数组 / 嵌套对象结构里取值，兼容类似 Adyen `notificationItems[0].NotificationRequestItem` 这类真实 webhook 结构
    - 回调时间提取已额外兼容 `transaction_time / settlement_time / created_at / updated_at / eventDate`
    - Midtrans / Adyen / Xendit / Paddle / Stripe / 微信 / 支付宝等主干平台已补针对性的回调单测，后续继续接真实验签时可直接在现有识别层上迭代
    - HMAC 验签已从“占位放行”升级为真正可工作的统一校验：
      - 会对回调负载做稳定化 canonical 文本展开，再计算 `HmacSHA256`
      - 同时兼容 `hex / base64` 签名值，以及 Stripe 风格 `t=...,v1=...` Header
      - 候选签名提取已额外规避把 Base64 末尾 `=` 误判成 `key=value` 的问题，避免真实签名被截断
      - 仍保留对旧 Mock / 简化签名值的兼容，避免已存在的联调脚本全部失效
    - RSA 验签已从骨架升级为统一可工作的基础实现：
      - 支持读取 PEM/X509 公钥配置并执行 `SHA256withRSA` 验签
      - 默认会对回调负载生成稳定 canonical 文本作为签名原文
      - 若平台需要自定义签名原文，也可在 payload 中透传 `signaturePayload / signingContent / canonicalPayload / rawBody`
      - 同时兼容 `base64 / hex` 两类签名值，便于对接不同网关或中间层
    - 证书验签也已从骨架推进到统一基础实现：
      - 支持从平台证书 PEM / Base64 证书内容中提取公钥
      - 也兼容直接填入 PEM 公钥，便于联调环境先跑通
      - 微信支付场景下会优先使用 `timestamp + nonce + body + \\n` 规则拼接签名原文
      - 同时保留显式透传 `signaturePayload / signingContent / canonicalPayload` 的覆写能力
    - 支付宝 / 微信 / Stripe / PayPal 的主干适配器又补了一轮常见真实字段兼容：
      - 支付宝兼容 `biz_content / notify_data`
      - 微信兼容 `resource.original / resource.plaintext` 结构和 `TRANSACTION.SUCCESS / REFUND.SUCCESS` 事件
      - Stripe 兼容更多 `metadata.orderNo / order_no`、`checkout.session.expired`
      - PayPal 兼容 `purchase_units[0].invoice_id / custom_id` 与补充的 `resource.status`
  - 前端支付结果页与 API 类型定义现已补齐 `MOLLIE / XENDIT / MIDTRANS` 等新增平台枚举，避免超管已切换新平台后，回跳页仍被前端错误降级为旧平台
  - 支付结果接口现在也会补充 `providerLabel / orderStageLabel / renewalChainType / canInitiatePayment / canToggleAutoRenew / renewalChildOrderNo` 等订单链路字段，前端回跳页可直接展示“主单/续费子单/是否还能支付或改续费”，不用再硬编码状态推断
  - 结果接口与订单列表也已继续补齐续费链路摘要：
    - 若当前订单是续费子单，会额外返回来源单 `renewalSourceOrderNo / renewalSourceOrderStatus`
    - 若当前订单是主单，也会返回最近一个续费子单的 `renewalChildOrderNo / renewalChildOrderStatus`
    - 支付结果页因此可以直接展示“从哪张单续来 / 已派生出哪张续费单”，便于人工核对订单链路
  - 前端支付结果页现在也补了操作闭环：
    - 可一键跳到会员中心并自动定位当前订单
    - 若存在来源单 / 续费子单，也可直接跳过去定位对应订单
    - 若当前登录者是超级管理员，也可直接回到 `VIP 订单` 页按订单号定位排查
    - 若当前未登录，则直接给出登录入口，避免回跳页只能停留在只读状态
    - 会员中心已支持通过 `focusOrderNo` 查询参数高亮并滚动到指定订单卡片
    - 超级管理员的 `VIP 订单` 也已支持通过 `focusOrderNo` 做本页定位：来源单 / 子单 / 当前单可直接回到超管页内部继续编辑、发起、Mock、取消、退款
  - 个人资料页里的“最近订单”区域也已同步补齐相同的链路跳转：
    - 可直接打开支付结果页
    - 可直接跳到会员中心定位同一张订单
    - 避免用户需要先切到会员中心再二次查找
  - 会员中心 / 个人资料页中的“续费来源单 / 续费子单”现也已改成可点击跳转：
    - 点击后会直接跳到会员中心并定位目标订单
  - 超级管理员支付设置与订单支付预览现在会额外结构化展示：
    - 建议拉起方式（表单跳转 / API / 二维码）
    - 建议验签模式（HMAC / RSA / CERTIFICATE / CUSTOM）
    - 建议退款模式（API_REFUND / DASHBOARD_OR_API / CUSTOM_CALLBACK）
    - 平台能力标签与真实接入步骤列表
    - 便于后续把“真实支付接入”按平台逐项落地，而不是只看一段通用提示文案
    - 超级管理员 `VIP 订单` 现已进一步补上“退款骨架预览”：
      - 可按自定义退款金额生成各支付平台的退款请求地址、请求头、请求体草案
      - 会同步展示建议退款模式、建议验签模式与真实退款接入步骤
      - 当前仍不会真实调用第三方退款网关，只用于联调和后续正式接入的参数确认
    - 主干退款骨架也继续细化到更接近真实网关：
      - 支付宝：补 `out_request_no / biz_content / Content-Type`
      - 微信支付：补 `notify_url / amount / Content-Type`
      - 若已配置商户私钥，微信退款预览也会直接回显 `signingMessage / requestBodyJson / authorizationPreview`
      - Stripe：补 `metadata / instructions_email`
      - PayPal：补 `invoice_id / note_to_payer / custom_id`
      - 银联：补 `merId / backUrl / signatureHint`
      - Adyen：补 `reference / metadata`
      - Mollie：补 `description / metadata`
      - Xendit：补 `reference_id / metadata`
      - Midtrans：补 `refund_key / additionalInfo`
      - 若已配置商户私钥，支付宝退款预览也会直接回显 `signingContent / signaturePreview`
    - 退款预览现在也已补齐“退款链路就绪度”视角：
      - `PaymentRefundService` 返回值不再沿用“下单 liveModeReady”，而是单独按 `refundReady` 判断当前是否可进入真实退款联调
      - 超级管理员退款预览面板会同步展示缺失字段、接入风险、退款阶段检查和下一步建议，方便区分“下单已就绪但退款仍未就绪”的情况
  - 超级管理员 `API测试工具` 现已补上支付回调调试区：
    - 可直接指定 `providerType`、回调 JSON、Header JSON 与返回页查询参数
    - 可直接命中统一入口 `/api/payments/notify/{providerType}` 与 `/api/payments/return/{providerType}`
    - 便于联调“订单号识别 / 验签骨架 / 状态回写 / 返回页展示”，减少手工拼 curl
    - 这样用户可以沿着续费链路连续追踪多张关联订单

- 更细粒度的断点续扫（基于文件游标而不是仅依赖已入库内容跳过）
- 内容去重方向已继续往前铺底：
  - 扫描时若仅命中“内容哈希相同”但路径/相册/用户不相同，不再直接复用并覆盖原 `photo` 记录
  - 现在会为该副本创建新的照片记录，并通过 `canonicalPhotoId` 指向命中的规范源照片
  - 规范源照片继续保留 `contentHash`；重复副本不再重复写入 `contentHash`，避免全局唯一索引冲突
  - 这样先保证多用户 / 多相册复制同一张图时不会互相覆盖数据，同时也为后续“秒传 / 去重对象存储 / 派生资源复用”预留规范源链路
  - 已新增上传前预检查接口 `GET /api/admin/folders/upload-precheck?contentHash=...`
    - 可在正式上传前判断是否命中已有规范源内容
    - 会返回“是否同用户 / 是否可见 / 是否已有可复用元数据 / 是否已有可复用派生资源”
    - 普通用户命中其他用户内容时不会泄露对方真实路径，仅返回布尔状态与可复用能力摘要
  - 重复副本现在也会优先复用规范源已有的尺寸、EXIF、缩略图/WebP、色彩分析和质量评分，减少重复生成派生资源与重复跑基础分析
  - 当前仍保留人脸、主体、标签等后半段链路的独立处理，避免在人物/标签归属语义尚未完全抽象前过早共享这部分数据
  - `PhotoDTO` 现已补 `canonicalPhotoId / canonicalSource / duplicateContent`，前端和管理端后续可以直接区分“规范源照片”和“重复内容副本”
  - 后台图片管理页也已开始直接展示“规范源 / 重复副本 / 规范源ID / 内容哈希摘要”，并支持按“全部 / 仅规范源 / 仅重复副本”过滤，便于超管排查扫描去重效果与异常链路
- WebDAV / FTP / COS / 对象存储类远端扫描已升级为“远端文件落本地缓存后复用完整图片处理链路”：
  - 已接通 EXIF 提取、三级缩略图、基础色彩分析、人脸检测、主体检测、智能标签、AI 评分、自动背景移除
  - 当前原图路径仍保留为 `storage://providerId/...`，派生资源默认落本地可映射存储，便于复用现有预览与缓存体系
  - 远端缓存目录为 `data/photos/{userId}/.remote-cache/{providerId}/...`
  - 后续若要继续演进为“远端派生资源也写回对象存储”或“去重内容存储”，可在这一层继续替换缓存与派生落点
- WebDAV / FTP 的远端文件预览已接通，当前通过后台受控代理流式打开远端文件
- 邮件发送当前仍以 SMTP 兼容方式为底层，但超级管理员已可直接选择常见平台预设：
  - `阿里云邮件推送`
  - `腾讯企业邮 / QQ 企业邮箱`
  - `AWS SES`
  - `SendGrid`
  - `Mailgun`
  - `Resend`
  - `Postmark`
  - `Brevo`
  - `MailerSend`
  - `ZeptoMail`
  - `Mailjet`
  - `SparkPost`
  - `Elastic Email`
  - `SMTP2GO`
  - `SendLayer`
  - `网易企业邮箱`
  - `自定义 SMTP`
- 超级管理员后台邮件能力已从“测试邮件”扩展到“自定义收件人 / 主题 / 正文发送”，便于后续复用到邮箱注册、验证码通知、找回密码等场景
- 现已继续补上邮箱验证码基础链路：
  - 超级管理员可配置 `邮件发送启用 / 邮件验证码 Mock / 邮箱验证码登录开关 / 验证码有效期`
  - 公共登录页支持 `邮箱 + 验证码` 登录
  - 个人资料页支持发送邮箱绑定验证码并完成邮箱验证
  - 当未接通真实 SMTP 时，可先通过 Mock 模式联调整条链路
- 认证页现已补上“找回密码”入口：
  - 登录页可直接跳到 `/forgot-password`
  - 支持按系统当前开关显示“手机号找回 / 邮箱找回”
  - 密码重置成功后会清空锁定状态与失败次数，便于用户立即重新登录
- 超级管理员用户管理现已可直接维护用户 `邮箱 / 邮箱验证状态`，并支持通过邮箱关键字搜索账号
- 前端展示层已补统一“平台 / 存储类型”友好名称：
  - 超级管理员、文件浏览器、个人资料、会员中心等页面不再直接暴露 `ALIPAY / WEBDAV / COS` 这类原始枚举
  - 同一类平台标签统一复用公共映射，便于后续继续扩展短信、邮件、支付、存储提供者时保持界面一致
  - TODO：未完成的支付平台与未接通的存储类型，会继续在超管提示与后端能力文案中显式带 `TODO`
- 支付适配入口已进一步拆分为独立平台实现：
  - 已新增 `ALIPAY`、`WECHAT_PAY`、`STRIPE`、`PAYPAL` 的专属 `PaymentProviderAdapter`
  - 默认适配器继续兜底 `UNIONPAY / PADDLE / LEMON_SQUEEZY / ADYEN / MOLLIE / XENDIT / MIDTRANS / CUSTOM_WEBHOOK`
  - TODO：后续接真实签名、证书、SDK 与跳转链路时，可直接在对应平台适配器中演进，避免所有支付逻辑继续堆在一个默认实现里
- 支付平台兼容面已进一步扩大：
  - 新增 `Mollie / Xendit / Midtrans` 的超管配置入口、友好名称、预览骨架与回调字段识别
  - 便于后续按区域逐步接欧洲、东南亚、印尼等市场的真实支付能力
- 支付结果页已补充更多回写信息：
  - 返回页查询结果现在会展示平台友好名称、网关状态、外部交易号、回调通知时间、退款状态、退款金额、续费来源单
  - 便于联调支付回跳、回调验签、订单状态流转与续费链路排查
- 统一支付返回入口已增强：
  - `GET /api/payments/return/{providerType}` 不再只依赖显式 `orderNo`
  - 现可直接从支付宝 `out_trade_no`、PayPal `invoice_id`、微信/Stripe/银联等常见返回字段中自动提取订单号
  - 更接近真实第三方回跳场景，便于后续把支付平台返回地址直接指向统一入口
- 统一支付返回入口现已区分“浏览器跳转”和“接口查询”：
  - 浏览器以 `Accept: text/html` 打开 `/api/payments/return/{providerType}` 时，会自动 `302` 跳到前端 `/vip/result`
  - XHR / API 查询仍返回 JSON，供前端轮询订单状态
  - 这样第三方网关可直接把返回地址配置到统一后端入口，无需手动拼前端回跳链接
- 超级管理员支付设置页已新增“一键填充统一回调地址”能力：
  - 可直接把 `paymentNotifyUrl` 填为 `/api/payments/notify/{providerType}`
  - 可直接把 `paymentReturnUrl` 填为 `/api/payments/return/{providerType}`
  - 进一步降低不同支付平台接入时手工复制地址造成的配置错误
- 超级管理员支付设置页现已增加“配置体检”区块：
  - 前端会按当前支付平台即时提示必填缺失字段，并尽量对齐后端 `PaymentGatewayService` 的缺失字段判定逻辑
  - 会额外根据 `HMAC / RSA / CERTIFICATE / CUSTOM` 不同验签模式提示需要补充的密钥、公钥或平台证书
  - 会直接展示推荐统一回调地址，并提示当前表单里的回调/返回地址是否已经对齐统一入口
  - 便于在真实 SDK 接入前先把超管配置准备完整，降低回调与回跳地址配置错误
- 前端支付结果页现会把第三方回跳的原始查询参数完整透传给后端返回接口：
  - 不再只发送 `orderNo`
  - 因此使用 `out_trade_no`、`invoice_id`、`merchantReference` 等字段回跳时，也能正确命中订单回查与自动轮询
  - 若第三方把参数展开成 `data[custom_data][orderNo]`、`metadata.orderNo` 这类扁平键，后端也会继续尝试识别并回填订单号来源
  - 支付结果页查询成功后会把后端解析出的 `orderNo / providerType` 回写到地址栏，便于刷新、复制链接与后续登录回带
  - 未登录用户从支付结果页跳去登录时，也会把 `focusOrderNo` 一并带回会员中心，避免登录后丢失当前订单定位
- 统一支付回调入口已增强：
  - `POST /api/payments/notify/{providerType}` 现会合并 `JSON body + form/query 参数 + headers`
  - 更适配支付宝/银联这类表单回调、Stripe/PayPal 这类 JSON Webhook，以及后续自定义聚合支付网关
  - 对 `data[custom_data][orderNo]`、`data.object.metadata.orderNo` 这类扁平化嵌套键会先恢复成层级结构，再交给各平台适配器识别
  - 重复字段会保留成列表，订单号、状态与 Header 读取时优先使用首个有效值，降低部分网关重复传参导致的识别失败
  - 其中支付宝已额外兼容 `passback_params` 携带订单号，微信支付已额外兼容 `attach` 字符串携带订单号，优先保证这两条主链路回跳/回调可继续定位本地订单
- 统一支付验签入口已进一步兼容 Header 场景：
  - HMAC / 自定义验签现在会额外读取 `Stripe-Signature`、`PayPal-Transmission-Sig`、`X-Signature` 等常见 Header
  - 证书验签骨架现在也会识别 `Wechatpay-Serial` 等证书序列号 Header
  - 这样第三方网关无需把所有签名字段强行改写进 JSON body，也能先走统一入口联调
- 支付回调验签本轮继续往“更接近真实网关报文”推进：
  - `支付宝` 的 RSA 验签现在会优先按表单回调的排序参数串规则构造签名原文，而不是只走通用 canonical payload
  - `Stripe` 的 HMAC 验签现在会优先按官方 `timestamp.rawBody` 规则计算签名，同时仍保留对通用 raw body / canonical payload 的兼容兜底
  - `微信支付` 的证书验签已可直接使用 `Wechatpay-Timestamp / Wechatpay-Nonce / Wechatpay-Signature + rawBody` 组合验证
  - `PaymentController` 已收敛为统一的 `POST /api/payments/notify/{providerType}` 入口：无论 `application/json / form / text/plain / 无明确 Content-Type`，都会把原始 `rawBody` 与 Header 一并透传给服务层
  - 这两条主干回调都已补对应单测，便于后续继续接正式网关时回归验证
- 统一支付通知控制器现在显式兼容多种 Content-Type：
  - `application/json`
  - `application/x-www-form-urlencoded`
  - `multipart/form-data`
  - 以及无明确 `Content-Type` 的兜底 POST
  - 可减少支付宝/银联/部分聚合支付网关回调时出现 `415 Unsupported Media Type` 的概率
- 前端支付结果页已改为可未登录访问：
  - 第三方支付完成后可直接回跳 `/vip/result?...`，不再因为前端登录守卫被强制拦到登录页
  - 已登录用户返回会员中心，未登录用户则可继续手动去登录查看账号侧更多信息
- 支付结果页交互已增强：
  - 若订单仍处于待确认状态，前端会在短时间内自动轮询订单结果，并显示剩余秒数与轮询次数
  - 一旦订单变为已支付、已取消或已退款，会自动停止轮询，减少用户手动刷新成本
- 最近继续收口了几处多用户边界与旧单租户查询：
  - `PhotoService` 的用户筛选继续前移到仓库查询，减少 `findAll() + 内存过滤`
  - 照片分类筛选不再依赖旧的 `base-path + 绝对路径` 推导一级目录，改为基于 `UserPathService` 的租户相对路径识别分类
  - 人物分页 / 搜索 / 人物列表继续切到 `userId` 作用域查询，避免 `PersonProfile` 全表扫描后再手动过滤
  - 这也为后续“绝对路径 -> 存储位置 + 相对路径”的彻底迁移继续铺路，减少服务层继续依赖本地物理路径结构
- 文件浏览器与目录服务继续补齐本地存储作用域：
  - 本地文件浏览器列表、目录浏览、重命名、批量移动、批量删除等入口继续按 `scopedRoot` 校验
  - 顶层目录的 `parent` 已裁剪为空，普通用户在文件浏览器中不会再看到跳出自己根目录的返回路径
  - 本地目录重命名 / 移动时，数据库里的相册与图片路径更新也会优先按当前路径解析出的 `userId` 范围过滤，减少跨租户全表扫描
- 人脸后台调试与统计接口继续清理：
  - 人物数量统计已直接使用 `countByUserId`，不再通过 `findAll().stream()` 手动统计
  - 调试接口去掉了 `findByPersonId(null)` 与基于全表流式扫描的兜底逻辑
  - 最近指派记录改为有限条数查询，避免调试页继续无边界读取整张 `photo_assignment` 表
- 扫描任务队列继续按多用户场景收口：
  - 超管查看任务列表与状态概览时，后台改为优先走按时间排序的仓库查询，不再到处直接 `findAll()` 再手动筛
  - 任务合并候选会优先按“请求人 / 任务归属用户”范围查找，减少多人上传、断点续扫时的无关任务扫描
  - 定时扫描是否已有运行中任务也改成专门查询，避免定时器每轮都读取整张扫描任务表
  - 队列调度前现会额外回收“数据库状态仍是 RUNNING、但当前进程已不再持有”的僵尸任务：
    - 这类任务会自动改回 `QUEUED / RESUME_SCAN` 或 `PENDING`
    - 可减少线程异常退出后队列一直卡住、任务却看起来仍在运行的情况
  - 扫描列表、单任务详情、状态概览现在在读取前也会先执行同一轮僵尸任务回收，减少“调度线程还没下一轮、页面却一直显示 RUNNING”的假状态
- `PhotoScanService` 内部批处理继续改成“有扫描用户上下文就只处理该用户数据”：
  - 上传后扫描、断点续扫进入用户上下文时，初始化处理状态、哈希回填、抠图缓存清理、EXIF修复、颜色重算等批量任务不再默认扫全站
  - 相册氛围更新也会优先限制在当前扫描用户的相册集合中，减少多人并发场景下无关数据被反复重算
  - “未扫描文件分析 / 文件系统计数 / 孤儿数据清理 / 批量更新时间”这批维护入口也已继续收口到当前扫描用户范围，避免进入某个用户上下文后仍误扫全站
  - 若当前扫描上下文指向远端非本地存储，则依赖本地文件系统遍历的分析入口会直接跳过，而不是错误回落到全局本地 `base-path`
  - 扫描状态里的“总照片数 / 失败数 / 已完成数”、失败照片重试、批量背景移除总量、清空缩略图等辅助入口，也开始优先按当前用户或当前本地存储根目录执行
  - 相册氛围 / 特效的“全量重建”入口也已支持按当前扫描用户作用域执行，避免某个用户触发重建时误扫全站相册
- 后台批处理控制器继续按用户作用域收口：
  - `AdminController` 中的 AI 全量重评分、AI 评分统计、同步批量抠图、异步批量抠图，现已统一要求登录态，并按当前用户或超级管理员视角执行
  - 普通用户触发上述批处理时，仅会处理自己的照片；超级管理员仍可处理全站数据
  - 异步批量抠图已把 `scopedUserId` 显式传入扫描线程上下文，避免异步线程丢失当前租户后回退成全站任务
  - 旧 `/api/admin/photos/batch-remove-background` 同步接口也已补上 `Authorization` 校验，并在传入 `albumId` 时先验证相册归属
  - 相册级 AI 重评分、单图 AI 测试、调试用照片元数据/数值字段统计接口，也已改为按当前登录用户作用域查询，不再默认读取全站样本
- 人物后台剩余调试接口继续收口：
  - `/api/admin/photos/{photoId}/assignment-status` 与 `/api/admin/debug/database-status` 现已改为按当前登录用户作用域返回
  - `PhotoAssignment` 调试统计不再直接读整张表，而是通过 `photo -> userId` 关联限定当前用户或超级管理员视角
  - 这样在多人场景下，即使打开调试页，也不会再直接暴露其他用户的图片认领关系与样本记录
- 前台图片服务与筛选项继续去除多租户场景下的全表回退：
  - `FilterOptionService` 更新筛选项时，已进一步改为直接使用仓库聚合查询：
    - 相机 / 镜头 / 色彩分类改为数据库分组统计
    - 焦距 / 快门 / 光圈 / ISO 改为数据库 `MIN/MAX` 聚合
  - 多用户模式下刷新全站筛选项时，也改成先只读取用户 ID 列表，不再把整张 `user_account` 表完整加载到内存
  - `PhotoService` 的随机高分图、标签筛选、分类筛选、随机图墙等用户态入口，继续收敛到按 `userId` 或按用户相册集合查询，减少分页失真与跨租户混入
- 超管概览与长列表接口继续做轻量化查询：
  - `SuperAdminService.getOverview()` 现在优先使用用户概览投影与状态计数查询，不再为概览页整表加载完整 `UserAccount` 实体
  - 登录记录 / 操作记录分页列表改成“先查当前页记录，再按当前页涉及的用户 ID 批量回填昵称 / slug”
  - 这样可避免超管打开长列表时，每次都额外读取整张 `user_account` 表做用户名映射
- 相册批量重建与旧迁移刷新也继续轻量化：
  - `AlbumAtmosphereAnalysisService` 与 `AtmosphereEffectsService` 的全量重建，改成只读取 `photoCount > 0` 的相册
  - 多用户重建时则改为只读取“当前用户且有照片”的相册，避免空相册也参与整批重算
  - `LegacyDataMigrationService` 在迁移后回刷空间占用时，改成只遍历用户 ID 列表，不再整表加载所有账号实体
- 相似照片与高级筛选继续按用户作用域收口：
  - `/api/admin/photos/{photoId}/similar` 现已要求登录态，并按当前普通用户或超级管理员视角执行
  - `SimilarPhotoSearchService` 增加了 `userId` 作用域重载，普通用户无法再用“相似照片”接口扫到其他用户图片
  - `PhotoService.filterPhotos(...)` 中原先仍会对当前用户图片做内存全量过滤的颜色筛选 / EXIF 筛选，现已改成直接走按 `userId` 的仓库查询
  - 分类筛选也开始优先走 `AlbumRepository.findByUserIdAndTopLevelCategory(...)`，避免普通用户筛选分类时先把自己的全部相册加载到内存再逐条解析一级目录
  - 全站 / 超管视角的分类筛选现也改为直接走 `AlbumRepository.findByTopLevelCategory(...)`，不再回退成 `albumRepository.findAll()` 后再逐条解析一级分类
  - 当前用户的“随机高质量图片”也已改为数据库随机查询 + 计数，不再把该用户全部可见照片先拉到内存再洗牌
  - 用户态“随机排序图墙”也已改成 `PhotoRepository.findVisibleRandomByUserId(...)`，避免随机浏览时全量读取当前用户所有可见照片再 `shuffle`
- 文件目录操作与后台 AI 批处理继续降全表扫描：
  - `FolderService` 在本地目录移动 / 重命名 / 删除时，更新数据库引用已优先改用“路径前缀查询”而不是同作用域整表加载后再逐个判断
  - 相册路径改为优先走 `AlbumRepository.findByPathStartingWithNormalized(...)`
  - 图片路径改为优先走 `PhotoRepository.findByOriginalPathStartingWith(...)`，再同步修正缩略图、WebP、抠图等派生路径
  - 普通用户作用域下也已补齐 `AlbumRepository.findByUserIdAndPathStartingWithNormalized(...)` 与 `PhotoRepository.findByUserIdAndOriginalPathStartingWith(...)`，不再先把该用户全部相册/照片拉出后再做内存前缀过滤
  - `AdminController` 的批量 AI 评分、相册 AI 评分从一次性 `findAll()` / `PageRequest.of(0, Integer.MAX_VALUE)` 改成分页流式处理，减少大库场景下的内存峰值与停顿
- 相册管理侧继续收口：
  - `AlbumService` 的分类提取、相册重命名后的子相册路径修正、相册时间字段批处理，已尽量避免多用户场景下先全表加载再过滤
  - `AlbumService` 的相册计数、相册时间字段回刷、相册重命名后的照片路径修正，已开始统一改成分页遍历 `Album` / `Photo`，不再依赖 `PageRequest.of(0, Integer.MAX_VALUE)` 或单次全量相册列表
  - `AlbumMoveService` 的合并相册、整批路径更新、覆盖移动清理等流程，开始优先使用“当前可访问相册/照片集合”，减少跨租户全表扫描
  - `AlbumMoveService` 中“合并相册 / 覆盖移动清理”涉及的按相册读取全部照片，已改成分页加载；目标相册照片数回刷也改成直接 `countByAlbumId(...)`，避免 `PageRequest.of(0, Integer.MAX_VALUE)` 这类一次性大查询
- 扫描与人物侧继续去除大范围实体加载：
  - `PhotoScanService` 的处理状态初始化、哈希回填、抠图缓存清理、EXIF 数值回填、颜色重算、颜色分类回填、孤儿数据清理、时间字段回填，均已改成按当前扫描作用域分页遍历 `Photo` / `Album`
  - 扫描差异分析里“数据库照片路径集合”已改成分页只取 `originalPath`，避免大库下把整批 `Photo` 实体全量装载进内存
  - `FaceService` 的人物列表、人物 DTO 输出、相似人物计算改为分页加载 `PersonProfile`
  - 人物列表页的人脸数量聚合也已改为按 `userId` 作用域统计，避免多用户模式下把其它账号的人脸计数混入当前人物结果
- 旧数据迁移链路继续去掉部分无必要全表查找：
  - 评论补归属改成直接查询 `userId is null` 的旧评论
  - 默认归属账号选择改成优先走“首个超管 / 首个用户”的仓库方法，而不是把所有用户加载到内存里筛选
- 启动阶段的旧数据迁移现已补“完成标记”：
  - 首次成功迁移后会写入系统配置 `legacy_migration_completed=true`
  - 后续应用重启默认跳过自动全量迁移，避免每次启动都重复跑完整个迁移流程
  - 超级管理员重复点击迁移入口时，也会先检查该标记并直接跳过，避免重复搬目录、重复改写历史路径
  - 顶层目录搬迁也已补边界保护，默认不会再误把当前用户根目录自己搬进自己目录里
- 旧路径迁移的绝对路径规范化继续统一到 `UserPathService`：
  - 迁移服务中的“绝对路径规范化 / 本地存储根目录解析”开始复用统一路径服务
  - 这样后续继续把旧绝对路径改写为“存储位置 + 相对路径”时，可减少多处重复实现导致的不一致
  - `AlbumService` 的相册相对路径、分类前缀等逻辑现在也开始优先走 `UserPathService.extractTenantRelativePhotoPath(...)`
  - 对 `storage://...`、旧绝对路径、带用户目录前缀的本地路径，都会先统一抽成租户相对路径，再参与标题、分类和展示相对路径计算
  - `UserPathService.tryBuildStoragePathReference(...)` 现在也不再先把全部存储提供者加载后再筛本地类型，而是直接走 `StorageProviderRepository.findByTypeOrderByPriorityAscIdAsc(LOCAL)`，减少存储配置增多后的无谓扫描
  - `PhotoManageService` 在照片移动 / 覆盖清理 / 目标相册创建时，现已优先把目标目录和覆盖文件改写为 `storage://...` 存储引用，避免新的移动操作又把相册路径写回旧绝对路径
  - 同时，删除派生文件、移动派生文件、目录变更等本地文件操作已开始显式跳过“不可映射到本地磁盘的远端存储引用”，避免把 `storage://...` 当成本地路径误删误改
  - `AlbumService.resolveBasePath()` 也已改为直接复用 `UserPathService.resolvePhotoBasePath()`，继续去掉服务内各自拼接 `photo.scan.base-path` 的重复实现
  - `AlbumMoveService` 与 `FolderService` 的 `resolveBasePath()` 也已统一复用 `UserPathService.resolvePhotoBasePath()`，减少目录移动、目录浏览场景下的路径解析分叉
  - `AlbumMoveService.resolveAlbumPath(...)` 现也会优先解析 `storage://...` 存储引用，不再默认把相册路径一律当成本地绝对路径
  - `PhotoScanService.resolveBasePath()` 也已统一走 `UserPathService.resolvePhotoBasePath()`，继续收敛扫描链路中旧的项目根目录/相对路径手工推断逻辑
  - `FaceService` 的目录前缀推断与相册显示路径也开始统一优先走 `UserPathService.extractTenantRelativePhotoPath(...)`，减少人物推荐链路继续混用“租户相对路径 / 原始绝对路径”的判断分叉
  - `FolderService.moveFolder(...)` / `deleteFolder(...)` 入口现在也可直接解析 `storage://...` 目录路径，避免文件浏览器或迁移入口继续假设目录参数一定是本地绝对路径
  - `AlbumService.renameAlbum(...)` 在重命名后回写相册路径时，也会优先保留 `storage://...` 存储引用格式，不再把已引用化的相册路径重新写回裸绝对路径
  - `PhotoScanService` 的“扫描根目录解析”与“本地存储提供者根目录解析”现在也统一走 `UserPathService`，继续减少扫描入口里各自推断项目根目录的重复逻辑
  - `PhotoScanService.init()` 的初始化扫描入口现已改为直接触发“当前扫描作用域根目录”的默认扫描，不再把旧配置字符串 `photo.scan.base-path` 原样下传
  - `PhotoScanService.resolveRequestedFilesystemPath(...)` 现已把“空路径 / 旧 `base-path` 字符串 / 当前作用域绝对路径”统一识别为默认扫描根目录，并在用户作用域下把相对路径统一解析到当前用户目录，避免再错误回退到全局图片根目录
  - `UserPathService.resolveScopedPath(...)` 也已开始统一剥离重复的用户目录前缀，避免文件浏览器、扫描任务、后台目录操作在收到 `7/旅行` 这类相对路径时被错误解析成 `/data/photos/7/7/旅行`
  - `ScanTaskService.resolveRequestedRoot(...)` 的远端存储分支也同步补上了相同的用户前缀去重逻辑，保证本地 / 远端扫描任务在路径解析语义上保持一致
  - `FolderService.resolveScopedLocalPath(...)` 与 `resolveUploadTargetRelativeDirectory(...)` 也已同步收口，文件浏览器列表/上传在用户目录下收到带用户前缀的相对路径时，不会再重复拼出 `/.../{userId}/{userId}/...`
  - `FolderController.resolveScopedPath(...)` 也已与服务层保持一致，前端即使传入 `/{userId}/...` 风格的相对路径，请求入口也不会先行重复拼接用户目录
  - `AlbumService` 顶级相册判断现在也统一复用逻辑相对路径计算，不再继续依赖 `basePath` 字符串截取和分隔符兼容分支来推断相册层级
  - `AlbumMoveService.resolveRequestedPathWithinScope(...)` 也已补齐用户前缀去重，避免相册移动/合并链路收到 `7/旅行` 这类请求时落成 `/data/photos/7/7/旅行`
  - `UserPathService.extractUserIdFromPath(...)` 现已兼容 `storage://...` 存储引用，后续相册创建、照片查找、目录归属判断在引用化路径下不再丢失 `userId`
  - `FolderService.findOrCreateAlbum(...)` 现在也会优先把目录路径写成 `storage://...` 存储引用，并兼容命中历史绝对路径记录，减少目录同步链路继续扩大绝对路径债务
  - `AdminController`、`PhotoController`、`PhotoManageService`、`PhotoScanService`、`FolderController` 等对外展示或错误返回路径的入口，现已统一优先走 `UserPathService.toDisplayPath(...)` / `sanitizeVisibleText(...)`，避免一部分接口仍只认识旧绝对路径到相对路径转换、导致远端存储或混合迁移数据继续原样泄露
  - 同类的错误脱敏逻辑也继续扩展到了 `SuperAdminController`、`AuthController`、`SystemConfigController`、`AiSearchController`、`FaceController`、`PaymentController` 以及后台任务服务日志，减少同一份路径在不同入口下展示规则不一致
  - `AlbumService`、`FaceService`、`FolderService`、`AlbumMoveService` 内部仍遗留的相对路径 helper 也继续收口到同一规则：先尝试统一 display path，再兜底叶子文件名，避免人物页、相册页、目录移动预览等 DTO 在混合路径数据下各走各的老逻辑
  - `PhotoScanService.resolveRemoteScanRoot(...)` 也已补齐用户前缀去重，远端存储扫描收到 `7/旅行` 这类相对路径时不再错误拼成 `/remote-root/7/7/旅行`
  - `FolderService.isUnderBase(...)` 现已不再只认默认 `photo.scan.base-path`，对落在本地存储 provider 根目录下、且可转换为 `storage://...` 的路径也会视为受管目录，避免文件浏览器同步上传/移动后漏掉数据库更新
  - `PhotoScanService` 本地扫描创建/查找相册时，现也开始统一按“绝对路径 + `storage://...` 引用”双候选查找，并优先把新相册路径写成存储引用，兼容继续命中历史绝对路径 `pathHash/path`
  - `PhotoScanService.buildPhotoPathLookupCandidates(...)` 也会优先结合当前扫描上下文里的 `userId` 推断存储引用，不再只依赖从旧绝对路径反推归属；这让本地 provider 扫描下的照片补录、重复路径查找对用户目录的识别更稳定
  - `FolderService.buildLocalDirectoryListing(...)` 中目录封面/照片数量的相册匹配，现已与其它目录链路统一复用“绝对路径 + 存储引用 + 路径哈希”候选查找，文件浏览器浏览本地 provider 目录时也能命中已引用化相册
  - `PhotoManageService.findOrCreateAlbumForPath(...)` 现在也会优先按“存储引用 / 绝对路径 / 原始参数”三套候选路径与 `pathHash` 复用历史相册，减少照片移动/批量管理继续因为路径引用化而重复建相册
  - `AlbumMoveService.updateAllPaths(...)`、目标相册查找与前缀扫描，现在也开始统一按“绝对路径 + \`storage://...\` 引用”双候选处理；相册移动、合并、覆盖时，已引用化的相册/照片路径也会一起被正确改写，不再只更新旧绝对路径记录
  - `FolderService.moveFolder(...)`、`renameItem(...)`、`deleteFolder(...)` 的目录批量改写与前缀匹配，也已统一按“绝对路径 + \`storage://...\` 引用”双候选执行；文件浏览器里的目录移动、重命名、删除不再漏改已引用化数据
  - `ScanTaskService.resolveRequestedRoot(...)` 的“无当前用户”本地分支现在也支持识别 `7/旅行/...` 这类带用户前缀的相对路径，并在解析时只保留一层用户目录，避免超级管理员创建扫描任务时把根路径错误落成 `/data/photos/7/7/...`
  - `AlbumService.createAlbumIfNotExists(...)` 也已开始在创建前统一把目录路径解析为“本地绝对路径 + `storage://...` 候选”，并复用历史 `path/pathHash` 记录，避免后台手工建相册入口继续扩大绝对路径债务
  - `StorageUploadService.resolveLocalDirectoryPath(...)` 现在也会在多用户本地存储下剥离重复的用户目录前缀，上传/移动链路即使收到 `7/旅行/...` 也不会再解析成 `/storage/7/7/旅行/...`
  - `FolderController.resolveScopedPath(...)` 现已直接复用 `UserPathService.stripLeadingUserSegment(...)`，控制器入口与服务层的用户前缀去重规则保持一致，减少后续再出现“接口层和服务层行为不一致”的问题
  - `PhotoScanService.findOrCreateRemoteAlbum(...)` 现在也会把远端存储扫描得到的相册路径优先写成 `storage://{providerId}/{userId}/{relativePath}`，避免 COS / WebDAV / FTP 扫描继续把远端目录路径裸写回数据库
  - `LegacyDataMigrationService.rewriteLegacyPath(...)` 现在也会在“旧绝对路径迁入用户目录”后，立即尝试转成 `storage://...` 存储引用；这样历史数据迁移不再只是“换一个新的绝对路径”，而是直接进入新路径语义
  - `AlbumService.convertToDTO(...)` 与相册封面保存返回值中的 `AlbumDTO.path`，现在也统一转成公开可用的相对客户端路径，不再把数据库里的原始 `storage://...` / 绝对路径直接暴露给前端
  - `AlbumMoveService.getCategories(...)`、`getSiblingDirectories(...)`、`getChildDirectories(...)`、`listDirectories(...)` 现在也统一返回客户端可继续提交的相对路径，不再把后台目录选择器里的本地绝对路径直接暴露给前端
  - `PhotoManageService.getMoveTargets(...)` 里的上级目录 / 同级目录 / 子目录路径，也已统一收口为客户端相对路径，避免照片批量移动入口继续把服务器物理路径透出到页面
  - `FolderService.listDirectories(...)` 与 `buildLocalDirectoryListing(...)` 的本地浏览器返回值也已按当前作用域统一转成浏览器路径；默认图库根目录下返回 `/{userId}/...`，本地 provider 作用域下返回相对 `scopedRoot` 的浏览路径，减少后端继续向文件浏览器返回宿主机绝对路径
  - `StorageProviderService.BrowserStorageContext.toResponse()` 与 `FolderController /admin/folders/list` 的 `base/basePath` 现在也统一改成浏览器根路径语义（`/` 起步的作用域内路径），不再把本地 provider 的宿主机绝对目录直接返回给前端
  - 同时，存储能力描述中的 `resolvedBaseDirectory` 与文件浏览器上下文中的 `storageProviderBaseDirectory` 已改为返回存储配置本身的目录值，而不是服务端解析后的绝对磁盘路径，减少超管页面继续暴露部署机目录结构
  - `ScanTaskService.toTaskMap(...)` 与检查点返回中的 `rootPath` / `lastProcessedPath` / `resumeFromPath` 也已统一改成展示路径语义；前端即使回退读取这些原字段，也不会再看到服务器绝对目录
  - `OperationLogService.toMap(...)` 现也会把操作日志里的 `targetPath` 优先转成租户相对路径；即便历史日志里残留旧绝对路径，超管/用户页面也不再直接看到宿主机目录
  - `SuperAdminService.listOperationLogs(...)` 的超管操作日志表格输出也已同步走相同收口规则，避免普通日志页和超管日志页出现不同的路径展示语义
  - `PhotoScanService.rebuildFacesForPhoto(...)` / `rebuildFaceEmbeddingsForPhoto(...)` 的“文件不存在”错误，现已改为返回客户端相对路径，不再把照片原始存储路径直接拼进错误消息
  - `AlbumMoveService.mergeAlbum(...)` 与 `AlbumService.renameAlbum(...)` 的目录不存在提示，也已改为返回客户端可理解的相对路径/相册路径，避免相册管理接口继续把服务器物理目录暴露给前端
  - `FaceService.getAlbumDisplayPath(...)` 在无法直接抽出租户相对路径时，也会优先降级为公开相对路径；若仍无法安全映射，则返回空值而不是把原始绝对路径透传到人物/人脸相关 DTO
  - `PhotoService` / `AlbumService` 的照片与相册 DTO 路径转换、以及 `PhotoScanService` 的日志展示路径，在无法从旧值安全映射为租户相对路径时，现会退化为叶子名而不是直接返回原始绝对路径，继续收敛残余泄漏面
  - `OperationLogService` 与 `SuperAdminService` 的操作日志 `detailJson` 现也会在 JSON 结构内递归收口路径字段；即便历史明细里保存了旧绝对路径，表格详情中也会优先显示租户相对路径
  - `AdminController.updateFilterOptions()` 的异常返回不再直接回传 `stackTrace`，改为精简的 `errorType`，避免后台页面继续收到无意义且可能包含部署细节的异常堆栈字符串
  - `PhotoScanService.resolveOriginalFile(...)`、`resolveStoredPathSafely(...)` 与 `calculateSha256(File)` 抛出的校验错误，现统一改为相对路径展示；文件浏览器重命名与照片批量移动中的“源路径/源文件不存在”错误也已同步收口
  - `PhotoController` / `AdminController` 的背景移除入口，以及 `PhotoAIScoringService` 的本地原图解析异常，也已统一改为记录/返回租户相对路径，避免用户在手动触发抠图或 AI 评分时再看到服务器磁盘路径
  - `BackgroundRemovalService` 的模型路径日志、抠图结果回写失败日志，以及 `PhotoScanService` 的目录读取/图片处理失败日志，也开始统一走安全路径展示，减少后台日志继续暴露宿主机绝对目录
  - `FaceDetectionService`、`EmotionAnalysisService`、`SceneRecognitionService` 的模型缺失/加载日志，以及 `ONNXDiagnosticUtil` 的临时目录、工作目录、用户主目录诊断输出，也已降级为安全路径/叶子名展示，避免诊断日志继续暴露部署机目录结构
  - `ImageClassificationService`、`SaliencyDetectionService`、`FaceEmbeddingService` 的模型加载/失败日志，以及 `PhotoScanService` 的扫描中断和“相册目录已不存在”日志，也已同步改成安全路径展示
  - `AlbumService` 中涉及子相册递归、聚合相册、聚合过滤等 debug 日志的相册路径输出，也已统一改为公开相对路径，避免调试日志继续混出数据库原始路径
  - `AiSearchService` 中用于构造候选提示文本的相册路径，也已改为租户相对路径，避免 AI 搜索链路继续把数据库里的原始相册路径拼回提示内容
  - 本轮继续补齐 `PhotoManageService`、`PhotoManageController`、`AlbumController`、`AdminController`、`PhotoScanService`、`BackgroundRemovalService` 的残余日志收口：批量移动/删除、孤儿记录清理、哈希回填、重试处理、AI 评分失败明细、空目录批处理等链路，现在统一优先输出租户相对路径；`AiSearchService.normalizeTenantRelativePath(...)` 在无法安全映射时也会退化为叶子名，而不再把原始绝对路径作为兜底文本继续带入搜索解释或提示上下文
  - 新增一轮控制器侧兜底：`FolderController`、`SuperAdminController`、`AdminController` 的异常返回现在会扫描错误文案中的嵌入式路径片段（含 `storage://...`、Unix 绝对路径、Windows 绝对路径）并逐段转为公开相对路径，减少文件浏览器、超管接口、后台全局异常在 catch 分支里再次把宿主机目录原样回传给前端
  - 继续扩展到认证/支付/配置/人物后台：`SystemConfigController`、`PaymentController`、`AuthController`、`FaceController` 的运行时异常返回也开始统一走相同的嵌入式路径脱敏规则；这样即使会员订单、头像上传、短信/支付回调、系统配置、调试接口内部抛出了带文件路径的异常，前端收到的也只会是公开相对路径或通用失败文案
  - 进一步补齐 `AiSearchController` 与 `AdminController` 内部的诊断/任务日志链路：AI 搜索失败说明、ONNX 诊断提示、批量 AI 重评分任务失败明细等文本，现在也统一在拼接前先做路径脱敏，避免后台任务日志或诊断字符串再次把历史绝对路径带回页面
  - 同时把几处服务层直返文案也补齐：`AlbumMoveService` 的合并/移动失败消息、`AlbumService` 的相册重命名失败消息、`PhotoManageService` 的目标目录创建失败消息、`PhotoScanService` 的单图人脸重建/embedding 重建路径解析失败消息，现在也统一先做公开相对路径转换，减少“控制器已脱敏但服务层返回值仍夹带原始路径”的尾巴
  - `PhotoScanService` 的任务日志与处理状态文本也开始统一收口：`appendTaskLog(...)`、`completeTask(...)`、背景移除批处理失败、未扫描文件分析错误、以及 `markProcessingFailed(...)` 写入数据库的失败原因，现在都会先对异常文案里的路径做公开相对化，避免任务状态面板、照片处理失败详情继续混出宿主机路径
  - 继续补齐扫描管理收尾场景：筛选项更新失败、相册氛围更新失败、特效配置失败、缩略图/人脸/智能标签/残留数据清理失败等后台结果文案，现在也统一复用同一套可见消息脱敏逻辑，减少超级管理员面板和任务状态抽屉里的残余绝对路径
  - `UserPathService` 已新增统一的可见文本脱敏入口，邮件发送、短信发送（阿里云 / 腾讯云 / Twilio / Webhook）、头像上传、远端图片读取、COS 元数据探测、AI 回答解析等链路也开始统一复用；即便底层异常带出宿主机路径、网关原文或临时文件名，返回前端时也会优先折叠为租户相对路径或叶子名
- 本轮继续补了几项“先能用起来”的关键闭环：
  - 上传前去重预检：文件浏览器上传前会先计算 SHA-256，并调用 `/api/admin/folders/upload-precheck` 做重复内容探测；当前已可在前端明确提示“是否为同用户重复 / 是否可复用派生资源”，先减少重复上传误操作
  - 支付回跳参数跟踪：支付宝、微信、Stripe、PayPal、银联、Paddle、Adyen、Mollie、Lemon Squeezy、Xendit、Midtrans 等适配器现在都会把 `orderNo / providerType / redirect=true` 自动拼到返回地址，第三方跳回 `/vip/result` 或统一后端返回入口时更容易直接定位订单
  - 远端缓存刷新判断：远端扫描缓存不再只按文件大小判断是否复用；若存储列表返回了 `lastModified`，现在也会同步比较时间戳并在重下载后回写本地缓存时间，避免“同尺寸替换图片后仍沿用旧缓存”的问题
