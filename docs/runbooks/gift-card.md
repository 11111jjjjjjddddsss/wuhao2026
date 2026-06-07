# 礼品卡 Runbook

最后更新：2026-06-07

## 当前状态

礼品卡首版后端、Android 用户兑换入口和管理后台已经接入。

当前真实能力：

- 后端新增礼品卡批次表、礼品卡表和兑换尝试表。
- 管理后台可创建 Plus / Pro 礼品卡批次，默认 30 天，可设置张数、天数和备注。
- 完整卡码只在创建批次成功的当次响应返回；数据库只保存 HMAC-SHA256 hash、掩码和尾号。
- 管理后台可查询批次、卡状态、兑换账号ID、脱敏手机号、兑换地区、兑换时间和会员到期时间。
- 管理后台可按批次、卡状态、账号ID和卡码尾号追溯礼品卡；可按账号ID、卡码尾号、成功 / 失败和失败原因筛选兑换尝试。
- 管理后台 `GET /admin-api/v1/gift-cards/summary` 已提供全局卡状态汇总、最近 24 小时失败尝试数和最近 7 天失败原因聚合。
- 管理后台可作废未兑换的 active 礼品卡，动作需要 `finance_ops` 或 `owner` 权限、二次确认、作废原因和审计。
- 用户侧接口 `POST /api/gift-cards/redeem` 已接入，必须登录后调用。
- Android 设置页“礼品卡”已经接到 `POST /api/gift-cards/redeem`，只在后端返回成功后展示“兑换成功”并刷新会员权益。
- 礼品卡兑换会事务内发会员权益，并写兑换尝试和审计。

当前仍未完成：

- 历史完整卡码不能导出，因为数据库不保存明文卡码；完整卡码只在创建成功当次展示。
- 批量发放名单、发放对象管理和更细风控统计尚未开放。
- 后台暂不展示完整卡码历史；这是刻意设计，避免卡码泄露。
- 礼品卡不是支付订单，不替代正式支付、退款或对账系统。

## 兑换规则

- 礼品卡类型只支持 `plus` 和 `pro`。
- 默认权益周期为 30 天，后台创建批次时可调整，最大 366 天。
- Free / 已到期用户兑换 Plus / Pro：从当前时间开始计算到期时间。
- 同档用户兑换同档卡：从当前有效期向后顺延。
- Plus 用户兑换 Pro：升级为 Pro，并把 Plus 剩余权益折算为升级补偿次数。
- Pro 用户兑换 Plus：拒绝兑换，不消耗礼品卡。
- 已兑换、作废、未到有效期或已过期的卡不会发权益。

## 数据真相

- `gift_card_batches`：批次名、档位、天数、数量、有效期、创建人、备注。
- `gift_cards`：卡 hash、掩码、尾号、档位、状态、兑换账号ID、兑换时间、地区、会员到期时间。
- `gift_card_redemption_attempts`：兑换尝试、成功 / 失败、失败原因、脱敏 IP、地区和时间。
- `user_entitlement`：会员当前档位和到期时间。
- `upgrade_credits`：Plus 升 Pro 时的补偿次数。
- `admin_audit_logs`：后台生成 / 查询和用户兑换相关审计。

不要明文保存可直接撞库的完整卡号。完整卡码只允许在创建成功响应中一次性显示。

## 接口

后台：

- `GET /admin-api/v1/gift-cards/batches`
- `POST /admin-api/v1/gift-cards/batches`
- `GET /admin-api/v1/gift-cards/summary`
- `GET /admin-api/v1/gift-cards/cards`
- `POST /admin-api/v1/gift-cards/void`
- `GET /admin-api/v1/gift-cards/attempts`

用户侧：

- `POST /api/gift-cards/redeem`

用户侧兑换接口必须鉴权，并按 `user_id + IP` 做短期限流。默认 1 小时 10 次，可用：

- `GIFT_CARD_REDEEM_RATE_LIMIT_WINDOW_SECONDS`
- `GIFT_CARD_REDEEM_RATE_LIMIT_MAX_HITS`
- `GIFT_CARD_REDEEM_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`

## 后续补齐

- 后台继续补批次详情钻取、批量发放对象、发放名单导入 / 导出和更细风控统计。
- 后台补批量发放名单和发放对象管理。
- 后台不要补“历史完整卡码导出”，除非以后改为创建当次短期下载且有更严格二次确认和审计；当前安全设计是不保存完整卡码。
- 若后续礼品卡用于商务发放，需要增加发放对象、发放渠道、外部备注和批次归属。

## 风险和监控

需要重点监控：

- 同一用户 / IP 连续兑换失败。
- 同一礼品卡重复兑换。
- 兑换成功但权益发放失败。
- 已作废 / 已过期礼品卡被大量尝试。
- 礼品卡生成、导出和作废操作。

礼品卡接入支付权益、商务发放或更高风险批量操作后，必须同步更新用户协议、隐私政策、风险提示、项目记忆和本 runbook。
