# 支付与会员订单 Runbook

最后更新：2026-06-25

## 目的

记录农技千查支付、会员订单、权益发放、回调验签、对账和退款边界，避免把开发期接口或未验收代码误当正式收费系统。

当前状态：支付宝 APP 支付代码已接入仓库，Android 已接支付宝 SDK，后端已接服务端创建支付宝 APP 支付订单、异步通知验签、`payment_orders` / `payment_notifications`、幂等发放会员 / 加油包权益和后台只读订单核查。2026-06-25 起，后端订单创建额外受生产支付门禁控制：默认关闭；内测联调必须同时配置测试包构建类型和账号白名单才允许创建支付宝订单，单独只配其中一个仍保持关闭；正式放量必须另开 `ALIPAY_PAYMENT_PUBLIC_ENABLED=true`。2026-06-25 晚已把生产门禁开到 `limited`，仅允许指定账号白名单 + 内部 debug 测试包 + 0.01 元测试金额联调；这不是正式收费开放。生产正式收费仍不得直接放量，必须完成小额真机支付、异步通知公网验签、重复通知幂等、权益发放、异常补偿、对账、人工补权益和退款流程验收后，再按正式发版和运营口径打开。

## 当前代码真相

- Android 会员中心的 Plus、Pro 和加油包按钮已改为真实支付入口：先请求后端 `POST /api/payments/alipay/orders` 创建订单，再把后端返回的 `order_string` 交给支付宝 SDK `PayTask.payV2` 调起支付。
- Android 创建支付宝订单时会带 `client_build_type` 和 `client_version_code`；后端用它配合账号白名单控制“内部测试包可测、正式包继续不可用”。旧正式包不带构建类型时不会通过测试门禁；测试期只配 `ALIPAY_PAYMENT_ALLOWED_BUILD_TYPES=debug` 或只配账号白名单也不会放行。
- Android 不保存、不生成、不读取支付宝应用私钥、支付宝公钥、商户密钥或任何支付渠道密钥；客户端同步结果只用于提示和轮询订单状态，不直接发放权益。
- Android 会把 `9000` 视为同步成功、`8000 / 6004 / 6006` 视为处理中、`6002` 视为网络异常并继续轮询后端订单；只有后端订单达到 `paid + grant_status=success` 才提示权益已生效。
- 后端新增正式支付订单表 `payment_orders` 和通知表 `payment_notifications`。支付宝通知会验签并校验 `app_id / seller_id / out_trade_no / trade_no / total_amount / trade_status`，成功处理后才返回支付宝要求的 `success`。
- 0.01 元联调订单支付成功后会按商品类型发放完整测试权益，因此只允许白名单账号 + debug 测试包使用。测试订单不删除，`payment_orders` 会记录 `is_test_order / original_amount_cents / client_build_type / client_version_code`；后台汇总不把测试订单计入正式支付金额，便于后续和支付宝账单、回调日志、权益流水对账。
- 后端发权益使用 `pay_<out_trade_no>` 作为幂等订单号，复用现有会员 / 加油包账本；重复通知不会重复发权益。`grant_status=processing` 未完成时不返回成功，保留支付宝重试机会；晚到的 `TRADE_CLOSED` 不应覆盖已支付订单。
- Plus / Pro 会员仍按 30 天周期处理；加油包次数和 Plus 升 Pro 生成的升级补偿次数都按永久有效处理，不随会员到期清零；每日额度是自然日额度，不跨天结转。后续扣次顺序仍是每日额度 -> 升级补偿 -> 加油包。
- 后端开发期直改接口 `/api/tier/renew_plus`、`/api/tier/renew_pro`、`/api/tier/upgrade_plus_to_pro`、`/api/topup/buy` 仍保留给本地 / 内测调试，默认返回 `PAYMENT_NOT_CONFIGURED`；生产环境必须继续保持 `dev_order_endpoints=false`，不能把开发期接口当正式收费入口。
- 管理后台订单页已接只读核查：可查看支付宝订单、渠道交易号、订单状态、金额、权益发放状态和会员变更记录。当前不提供手动补发、退款、对账自动化或手动改权益按钮；正式收费前必须补受控的人工补权益入口或同等 SOP。
- 只读支付门禁脚本：[check-payment-readiness.ps1](D:/wuhao/scripts/check-payment-readiness.ps1)。它会检查 Android SDK / manifest / 调起支付 / 未知结果轮询、后端支付宝订单 / 通知 / 验签 / seller_id / 幂等重试、数据库迁移、后台只读订单页、App / 官网隐私和第三方共享清单。脚本只输出配置是否缺失，不打印任何密钥值；当前输出 `payment_readiness_status=attention` 表示代码已对齐但正式收费开放前仍要做生产配置、回调、对账、退款和人工验收。

## 0.01 元内测联调流程

目标：只让指定账号的内部 debug 测试包跑通真实支付宝 0.01 元支付，不开放正式收费，不让正式包或非白名单账号创建订单。

联调前置：

1. 支付宝开放平台正式应用已上线或具备可生产调用的 APP 支付配置，准备好 `ALIPAY_APP_ID`、`ALIPAY_SELLER_ID`、应用私钥和支付宝公钥。
2. 生产 ECS 配置支付宝正式变量和 `ALIPAY_NOTIFY_URL=https://api.nongjiqiancha.cn/api/payments/alipay/notify`，密钥只进服务器环境或本机安全配置，不进仓库、聊天、日志或 APK。
3. 生产 ECS 只开受控测试门禁：`ALIPAY_PAYMENT_PUBLIC_ENABLED=false`、`ALIPAY_PAYMENT_ALLOWED_BUILD_TYPES=debug`、`ALIPAY_PAYMENT_ALLOWED_USER_IDS=acct_...`、`ALIPAY_PAYMENT_TEST_AMOUNT_CENTS=1`。
4. 重新跑 `check-payment-readiness.ps1`，要求生产 `/healthz` 里 `alipay=ok` 且 `alipay_payment_gate=limited`；若仍是 `missing_config` 或 `closed`，手机端不能做实付。
5. 生成并安装最新内部 debug 测试包；正式 release 包、旧包或未带 `client_build_type=debug` 的包不能通过测试门禁。

手机端操作：

1. 登录白名单里的测试账号。
2. 打开左上角三横线，进入“会员中心”。
3. 点“开通 Plus / 开通 Pro / 续费 Pro / 升级 Pro”中的一个。Free 用户点“加油包”会是灰色或“会员可购买”，这是正常限制，不是支付故障。
4. App 提示“正在创建支付订单”后应拉起支付宝，支付金额应为 0.01 元。
5. 支付后回到 App，等待订单轮询或刷新会员中心；权益只能以后端验签通知或订单状态核验后发放，客户端同步成功不直接发权益。

验收记录：

- 0.01 测试单不能删除，后台应标记为测试订单并排除正式收入统计。
- 每次联调都要记录测试包对象 / commit / 账号白名单 / 商品类型 / 支付宝支付结果 / 后端订单状态 / 权益是否到账 / 是否触发回调 / 是否需要人工补权益。
- App 自动日志应能串起完整链路：`payment.button_tapped`、`payment.button_blocked`、`payment.start`、`payment.order_create_started`、`payment.order_create_success`、`payment.order_create_failed`、`payment.alipay_launch_started`、`payment.alipay_sync_result`、`payment.order_poll_started`、`payment.order_poll_tick`、`payment.order_poll_timeout`、`payment.order_status_failed`、`payment.grant_success`、`payment.grant_needs_ops`。日志只保留商品类型、当前会员档、构建类型、版本号、订单尾号、状态和安全错误码，不能记录完整订单号、`order_string`、渠道交易号、密钥、公钥、手机号或支付参数全文。
- 若“点不动”，先确认点的是 Plus/Pro 购买按钮而不是 Free 状态下的加油包；再确认会员中心权益已加载、测试包确实是 debug、测试账号在白名单内、生产门禁为 `limited`。
- 若能点但提示“支付暂时不可用 / 下单失败”，优先看 `check-payment-readiness.ps1`、`/healthz` 的 `alipay` 和 `alipay_payment_gate`、App 日志 `payment.order_create_failed`、后端支付日志。
- 测试完成后必须移除 `ALIPAY_PAYMENT_TEST_AMOUNT_CENTS`，正式收费前不得打开 `ALIPAY_PAYMENT_PUBLIC_ENABLED=true`。

## 已支付但权益未到账处理口径

- 用户侧：如果用户反馈已付款但会员或加油包没到账，先引导用户走 App 内“帮助与反馈”联系客服，并补充账号、订单号尾号、支付截图或必要说明；不要让用户把密钥、后台截图或敏感材料发到聊天里。
- 运营侧：客服先核对后台订单、支付宝账单 / 回调记录和服务端日志。确认订单已支付、账号归属正确且权益确实未发放时，优先人工补权益并记录处理备注 / 审计，退款不是第一处理动作。
- 退款适用：无法核实付款、重复扣款、用户明确不接受补权益、平台规则或法律要求退款时，再进入退款流程。
- 当前缺口：后台订单页仍是只读核查，没有一键补权益按钮；正式收费前应补 `owner / finance_ops` 二次确认、订单校验和审计齐全的人工补权益入口。短期如需人工补偿，可用受控礼品卡或后台 SOP 处理，但礼品卡本身不是支付订单或退款凭证。
- 测试单处理：内部 0.01 元订单不得从数据库删除；如需排查或对账，应在后台按“测试订单”识别并排除正式收入统计。删除订单会造成支付宝账单、异步回调和本地权益发放记录断链。

## 申请前准备

支付申请通常会用到这些信息，实际以微信支付 / 支付宝平台当时页面为准：

- 公司主体：北京农技千问科技有限公司。
- App 名称：农技千查。
- Android 包名：`com.nongjiqiancha`。
- 固定正式签名：备案 / 上架用 MD5 / SHA1 / SHA256 / RSA 公钥信息保存在本机 `%USERPROFILE%\.nongjiqiancha\android-release-public-info.txt`，不要把私钥或签名密码上传到仓库或聊天。
- 官网：`https://nongjiqiancha.cn/`。
- API 域名：`https://api.nongjiqiancha.cn/`。
- 后台域名：`https://admin.nongjiqiancha.cn/`。
- 网站 ICP：`京ICP备2026031728号-1`；网站公安联网备案：`京公网安备11010602202723号`。
- App 备案：已通过，App 备案号 `京ICP备2026031728号-2A`，可用于支付平台和应用商店材料；App 公安备案仍待补。
- 商品 / 服务描述建议按“农业 AI 问诊会员服务、问诊次数权益、加油包次数权益”准备，不写成农资交易、处方药、金融投资、包治包赔或线下代收款。

## 2026-06-13 官方校准结论

本节按微信支付和支付宝官方文档重新校准，后续接入代码必须服从这些边界。

微信 App 支付：

- 申请 / 配置侧需要准备微信开放平台移动应用 AppID、微信支付商户号 `mchid`、商户 API 证书、商户 API 证书序列号、APIv3 密钥、微信支付公钥或平台证书，以及 Android 包名 / 正式签名信息。
- 微信支付产品开放状态必须以微信支付商户后台当时实际可开通项为准；2026-06-15 复核到的微信 APIv3 App 下单文档页带有灰度 / 开放状态提示，后续申请时不要只凭文档 URL 判断已经可直接生产开通。
- 微信官方文档说明，APIv3 请求、应答、回调和 App 调起支付都涉及签名 / 验签；回调报文还会用 APIv3 密钥加密，服务端必须验签并解密后再处理。
- 后端负责创建商户订单和预支付单，Android 只拿后端返回的 `appId / partnerId / prepayId / packageValue / nonceStr / timeStamp / sign` 等参数调起 SDK。
- 微信支付回调建议先预留 `https://api.nongjiqiancha.cn/api/payments/wechat/notify`，该接口不依赖 App 登录态，但必须验签、解密、校验订单金额 / 商品 / 账号ID，并幂等发权益。
- APIv3 密钥设置后平台不支持查看，只能重设；商户 API 证书下载后也必须妥善保管，不能进 APK、仓库、日志、聊天或后台页面。

支付宝 App 支付：

- 申请 / 配置侧需要准备支付宝开放平台应用、APP 支付产品、AppID、RSA2 应用私钥 / 应用公钥或证书模式材料、支付宝公钥 / 支付宝证书，以及 Android SDK 接入权限。
- 生产环境正式调用前，支付宝开放平台应用需要完成创建、配置、审核上线，并开通 APP 支付产品；应用未上线、未开通 APP 支付或 AppID 配置不对时，生产链路可能报“APPID 应用未上线 / 未开通 App 支付”等错误。未上线、开通审核或应用未上线阶段可按支付宝沙箱环境做联调；沙箱成功后仍必须以生产环境审核和正式配置为准，不作为真实扣费入口。
- 支付宝官方 App 支付链路是商家 App 先请求商家服务端，由服务端调用 `alipay.trade.app.pay` 生成可信 `orderStr`，Android 只把 `orderStr` 交给支付宝 SDK。
- 支付宝 Android SDK 当前官方文档建议通过 Maven 依赖接入，旧 AAR 打包方式不作为新接入默认方案。
- Android 同步返回只能当“支付流程结束 / 需要刷新订单状态”的 UI 信号，不作为发放会员权益的依据；真实支付结果必须以后端收到并验签通过的异步通知或主动查单结果为准。
- 支付宝回调建议先预留 `https://api.nongjiqiancha.cn/api/payments/alipay/notify`，服务端验签、校验 `out_trade_no / trade_no / total_amount / app_id / seller_id / trade_status` 后，再幂等发权益；处理成功后按平台要求返回 `success`。

当前联调和上线前检查：

- `check-payment-readiness.ps1` 会检查本机是否具备支付宝正式联调所需环境变量：`ALIPAY_APP_ID / ALIPAY_SELLER_ID / ALIPAY_APP_PRIVATE_KEY(_FILE) / ALIPAY_PUBLIC_KEY(_FILE)`，只输出 ready / missing，不打印任何密钥。
- 生产 ECS 若要启用支付宝，必须配置 `ALIPAY_APP_ID`、`ALIPAY_SELLER_ID`、应用私钥、支付宝公钥和 `ALIPAY_NOTIFY_URL=https://api.nongjiqiancha.cn/api/payments/alipay/notify`；缺任一项都不能写成支付 ready。配置这些密钥只代表支付宝客户端可用，不代表正式包已开放收费。
- 支付订单创建门禁：
  - `ALIPAY_PAYMENT_PUBLIC_ENABLED=true`：正式放量开关，只有真实上线验收完成后才能开。
  - `ALIPAY_PAYMENT_ALLOWED_BUILD_TYPES=debug`：测试期必须配置，只允许内部 debug 测试包创建订单；正式包或旧包缺少构建类型时会继续收到“支付暂时不可用”。
  - `ALIPAY_PAYMENT_ALLOWED_USER_IDS=acct_...`：测试期必须配置，只允许指定账号创建订单。测试模式下账号白名单和构建类型白名单必须同时存在并同时命中，单独只配一项不会放行。
  - `ALIPAY_PAYMENT_TEST_AMOUNT_CENTS=1`：仅用于白名单账号 + debug 测试包的小额联调，表示 0.01 元；该开关在 `ALIPAY_PAYMENT_PUBLIC_ENABLED=true` 时不会生效，没有账号白名单或没有构建类型白名单时也不会生效，即使误把 `release` 加进构建类型白名单也不会对 release 包生效。测试完成后必须从生产环境移除，不能把它当正式定价；测试单仍会发放完整权益，但会标记 `is_test_order=true`。
- 生产 `/healthz` 会额外返回 `alipay_payment_gate=closed / limited / public`。`alipay=ok` 只说明支付宝密钥配置完整；是否能创建订单还要看支付门禁状态。
- 当前已能自动测试的内容：Android 是否集成支付宝 SDK、是否把未知同步结果转为轮询、后端是否注册订单 / 通知路由、是否强制 seller 校验、是否避免完整支付号进日志、支付表迁移是否存在、后台和协议页面是否同步支付宝口径。
- 当前仍必须人工或平台实测的内容：生产或沙箱小额下单、支付宝 App 拉起、用户取消、网络中断、`6004 / 6006` 未知结果、异步通知公网验签、重复通知、金额不一致拒绝、订单关闭、已支付权益发放、支付成功但权益未生效的客服人工补权益处理、对账和退款流程。
- 微信 App 支付仍未接入；如后续接微信，必须另行准备微信开放平台移动应用 AppID、微信支付商户号、API 证书 / APIv3 Key / 平台证书，并复用同一套服务端订单和权益发放边界。
- 以上前置项只允许放本机安全配置、服务器环境变量或云端密钥管理，不能进入 APK、仓库、日志、后台页面或聊天记录。

共同边界：

- Android 永远不保存微信商户私钥、APIv3 密钥、支付宝应用私钥、支付宝公钥证书私钥或任何支付渠道密钥。
- 会员、加油包和升级补偿只能由服务端正式订单状态 + 验签后的回调 / 查单结果驱动；客户端“支付成功”只能触发刷新，不允许直接发权益。
- 首版不接自动续费。自动续费以后需要单独的签约、解约、续扣失败、协议文案和后台状态，不和普通会员购买混做。
- 支付渠道接入前不要把个人收款码、H5 临时代收、客服私下收款或线下转账混进正式购买链路。

## 申请页面填写建议

给用户去平台申请时参考，实际页面字段以平台为准：

- 主体：北京农技千问科技有限公司。
- 应用 / App 名称：农技千查。
- Android 包名：`com.nongjiqiancha`。
- Android 正式签名：从本机 `%USERPROFILE%\.nongjiqiancha\android-release-public-info.txt` 读取 MD5 / SHA1 / SHA256 / 公钥信息；只填公钥 / 指纹，不上传私钥和签名密码。
- 官网：`https://nongjiqiancha.cn/`。
- 回调 / API 域名：`https://api.nongjiqiancha.cn/`。
- 管理后台域名：`https://admin.nongjiqiancha.cn/`，一般不作为用户支付回调域名填写。
- ICP / 公安备案：网站 ICP `京ICP备2026031728号-1`，App 备案 `京ICP备2026031728号-2A`，网站公安联网备案 `京公网安备11010602202723号`；App 公安备案仍需按平台要求补齐。
- 服务类目建议选择软件服务、工具服务、AI 咨询、农业技术服务等相近类目；避免写成农资销售、处方药、病害绝对诊断、金融投资、公益募捐、虚拟币或包治包赔。
- 商品名建议：`农技千查 Plus 会员`、`农技千查 Pro 会员`、`农技千查问诊加油包`。商品说明写“农业 AI 问诊参考、问答次数权益、图文问诊辅助分析”，不要承诺诊断准确率或收益。

## 支付渠道选择

首版建议按“先接一个主渠道，跑通创建订单 -> 调起支付 -> 异步回调 -> 发权益 -> 后台查账”的顺序推进；微信和支付宝都要接时，也应复用同一套服务端正式订单表和权益发放函数。

选择时注意：

- 微信 App 支付通常需要微信开放平台 AppID、微信支付商户号、商户 API 证书 / APIv3 密钥、Android 包名和签名等配置。
- 支付宝 App 支付通常需要支付宝开放平台应用、APP 支付产品、应用公钥 / 私钥或证书模式、Android SDK，以及服务端调用 `alipay.trade.app.pay` 生成订单参数。
- 不管哪家，Android 不能保存商户私钥、APIv3 密钥、应用私钥或平台证书私钥。
- 不要为了快，把 H5 支付、个人收款码、线下转账或客服代收款混进正式会员购买主链。

## 现有开发期接口

- `POST /api/tier/renew_plus`：开发期续 Plus。
- `POST /api/tier/renew_pro`：开发期续 Pro。
- `POST /api/tier/upgrade_plus_to_pro`：开发期 Plus 升 Pro，并计算永久升级补偿次数。
- `POST /api/topup/buy`：开发期购买加油包，仍要求当前有效档位为 Plus / Pro；当前加油包次数不设到期时间，未用完次数长期保留，也允许按需继续购买叠加。

这些接口只允许本地 / 内测调试，不允许公开生产使用。真实支付接入后，应移除、继续硬隔离，或只保留受控测试环境。

## 正式支付主链

支付宝 APP 支付代码已接入后，会员权益发放必须收敛到服务端支付回调 / 对账，不由 Android 直接改权益；生产放量前仍需完成真实小额支付、异步通知、对账、退款和异常补偿验收。

推荐主链：

1. Android 发起购买请求，请求后端创建待支付订单。
2. 后端校验账号ID、商品、金额、当前会员状态、升级补偿规则和幂等键。
3. 后端生成商户订单号，写正式订单表 `pending`。
4. 后端调用支付渠道下单接口，生成 Android 调起支付所需参数。
5. Android 只拿后端返回的支付参数调起微信 / 支付宝 SDK。
6. 支付渠道异步通知后端 HTTPS 回调地址。
7. 后端验签 / 解密回调，校验订单号、金额、商品、用户、支付状态和幂等键。
8. 后端在同一个事务内发放会员 / 加油包 / 升级补偿，并写权益流水和正式订单流水。
9. Android 支付返回后只展示“处理中 / 刷新权益”，并轮询订单状态或刷新 `/api/me`；不信任客户端“支付成功”作为发权益依据。
10. 后台订单页展示正式订单、回调状态、权益发放结果、退款 / 对账状态和审计。

## 正式订单表至少需要

- 商户订单号。
- 支付渠道订单号。
- 用户账号ID。
- 商品类型：Plus、Pro、Plus 升 Pro、加油包。
- 应付金额、实付金额、币种。
- 订单状态：pending、paid、failed、closed、refunded。
- 支付渠道：wechat、alipay 或后续渠道。
- 创建时间、支付时间、回调时间、关闭时间、退款时间、更新时间。
- 回调通知原文的受控脱敏摘要，不能把密钥、完整证书、手机号或敏感明文直接铺到后台。
- 幂等发放状态、权益发放结果和失败原因。
- 审计字段：创建来源、App 版本、账号ID、脱敏 IP、request id。

## 回调地址建议

- 微信 / 支付宝回调统一走后端公网 HTTPS，例如：
  - `https://api.nongjiqiancha.cn/api/payments/wechat/notify`
  - `https://api.nongjiqiancha.cn/api/payments/alipay/notify`
- 回调接口不依赖 App 登录态，但必须做渠道验签 / 解密 / 订单校验 / 幂等。
- 回调接口不能把完整回调 body、证书、签名、手机号或密钥打到日志。
- 回调失败时要返回支付平台要求的失败响应，让平台按规则重试；成功发权益后再返回成功。

## 必须防住的坑

- 不能信任 Android 上报的“支付成功”。
- 不能只凭前端订单号发权益。
- 不能金额不校验、商品不校验、用户不校验。
- 支付回调可能重复到达，必须幂等。
- 支付回调和用户刷新 `/api/me` 是异步关系，Android 要允许短暂“支付处理中”。
- 退款、撤销、失败和超时关闭不能和成功订单混在一起。
- Plus 升 Pro 仍要保留当前“不坑用户”的补偿口径：Plus 剩余每日权益折成永久升级补偿次数，不能被 Pro 覆盖后静默丢失；当前不做现金折扣抵扣。
- 加油包仍只允许 Plus / Pro 有效会员购买；当前未用完次数长期保留，允许按需继续购买叠加，后端仍按账本顺序逐包扣减。
- 自动续费如果未来接入，必须单独做签约、解约、续费通知、扣款失败、到期和协议文案；当前产品没有自动续费。
- 支付密钥、商户私钥、平台证书、APIv3 密钥等只能放服务端密钥管理或环境变量，不能进 APK，不能写仓库。

## 接入后要补的后台能力

- 正式订单查询：按账号ID、手机号精确匹配、商户订单号、渠道订单号、状态、渠道、时间筛选。
- 支付回调查询：回调时间、验签结果、幂等命中、权益发放结果。
- 异常队列：支付成功但权益未发放、重复回调、金额不匹配、商品不匹配、订单关闭后回调成功。
- 退款 / 对账状态：首版至少只读展示，不先开放随手退款。
- 人工补权益：必须 owner / finance_ops 二次确认、核对支付订单和账号归属并写审计；支付首版不开放大范围批量改权益，但“用户已付款且权益未到账”应优先走人工补权益，不默认退款。
- 支付观测指标接入监控面板：创建订单失败率、回调失败率、权益发放失败、退款异常、订单超时关闭。

## 上线观察指标

- 创建订单失败率。
- 支付 SDK 调起失败率。
- 支付回调验签失败率。
- 支付成功但权益未发放。
- 重复回调命中幂等。
- 用户支付后 `/api/me` 未刷新到新权益。
- 退款 / 关闭订单仍显示有效权益。
- 订单量、实收金额、退款金额和异常金额。

## 文案和合规同步

支付接入前后必须同步复核：

- App 内用户协议、隐私政策、第三方信息共享清单、个人信息收集清单、应用权限和风险提示。
- 官网和应用商店隐私政策 URL。
- 应用市场商品 / 会员截图、价格说明、退款说明。
- 客服话术：不允许 App 外私下收款、代充、代兑换或非官方客服收款。

## 官方文档入口

- [微信支付 APIv3 签名和验签](https://pay.wechatpay.cn/doc/v3/merchant/4012365342)：微信支付请求、应答、回调和调起支付都涉及签名 / 验签。
- [微信支付开发必要参数说明](https://pay.wechatpay.cn/doc/v3/merchant/4013070756)：普通商户模式开发前需要准备 `mchid`、`appid`、商户 API 证书、证书序列号、微信支付公钥 / 平台证书和 APIv3 密钥等。
- [微信支付 App 下单接口](https://pay.wechatpay.cn/doc/v3/merchant/4012525136)：服务端先创建预支付交易单，`notify_url` 要用公网可访问的 HTTPS 地址。
- [支付宝 APP 支付接入准备](https://opendocs.alipay.com/open/204/105297/)：正式调用前需要完成创建应用、配置应用、上线应用和开通产品。
- [支付宝 APP 支付快速接入](https://opendocs.alipay.com/open/204/01dcc0)：App 端购买请求应先到商家服务端，服务端生成支付订单参数。
- [支付宝 App 支付沙箱联调](https://opendocs.alipay.com/support/01rftu)：应用未上线或产品未开通阶段可用沙箱测试，生产仍以正式环境审核和开通结果为准。
- [支付宝 Android 集成流程](https://opendocs.alipay.com/open/204/105296/)：Android 侧集成支付宝 SDK 调起支付；密钥和签名仍由服务端负责。
- [支付宝同步通知说明](https://opendocs.alipay.com/open/204/105302)：同步结果可只作为支付结束通知，实际支付成功应以后端异步通知为准。
- [支付宝异步通知说明](https://opendocs.alipay.com/open/204/105301)：支付宝按 `notify_url` 通过 POST 发送支付结果，服务端验签处理成功后再返回成功响应。
