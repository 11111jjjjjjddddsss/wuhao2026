# 礼品卡 Runbook

最后更新：2026-06-07

## 当前状态

礼品卡首版后端和管理后台已经接入。

当前真实能力：

- 后端新增礼品卡批次表、礼品卡表和兑换尝试表。
- 管理后台可创建 Plus / Pro 礼品卡批次，默认 30 天，可设置张数、天数和备注。
- 完整卡码只在创建批次成功的当次响应返回；数据库只保存 HMAC-SHA256 hash、掩码和尾号。
- 管理后台可查询批次、卡状态、兑换用户、脱敏手机号、兑换地区、兑换时间和会员到期时间。
- 管理后台可查询成功 / 失败兑换尝试，包括失败原因、脱敏 IP、地区和时间。
- 用户侧接口 `POST /api/gift-cards/redeem` 已接入，必须登录后调用。
- 礼品卡兑换会事务内发会员权益，并写兑换尝试和审计。

当前仍未完成：

- Android 设置页“礼品卡”按钮仍需从占位提示接到 `POST /api/gift-cards/redeem`。
- 礼品卡作废、导出、批量发放和二次确认尚未开放。
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
- `gift_cards`：卡 hash、掩码、尾号、档位、状态、兑换用户、兑换时间、地区、会员到期时间。
- `gift_card_redemption_attempts`：兑换尝试、成功 / 失败、失败原因、脱敏 IP、地区和时间。
- `user_entitlement`：会员当前档位和到期时间。
- `upgrade_credits`：Plus 升 Pro 时的补偿次数。
- `admin_audit_logs`：后台生成 / 查询和用户兑换相关审计。

不要明文保存可直接撞库的完整卡号。完整卡码只允许在创建成功响应中一次性显示。

## 接口

后台：

- `GET /admin-api/v1/gift-cards/batches`
- `POST /admin-api/v1/gift-cards/batches`
- `GET /admin-api/v1/gift-cards/cards`
- `GET /admin-api/v1/gift-cards/attempts`

用户侧：

- `POST /api/gift-cards/redeem`

用户侧兑换接口必须鉴权，并按 `user_id + IP` 做短期限流。默认 1 小时 10 次，可用：

- `GIFT_CARD_REDEEM_RATE_LIMIT_WINDOW_SECONDS`
- `GIFT_CARD_REDEEM_RATE_LIMIT_MAX_HITS`
- `GIFT_CARD_REDEEM_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`

## 后续补齐

- Android 礼品卡页接入真实兑换接口和成功 / 失败文案。
- 后台补作废功能，必须 owner / finance_ops 权限、二次确认和审计。
- 后台补批量导出，但导出只能发生在创建成功当次，或走更严格的二次确认和短期下载。
- 后台补按批次详情钻取、按用户查询、按失败原因统计。
- 若后续礼品卡用于商务发放，需要增加发放对象、发放渠道、外部备注和批次归属。

## 风险和监控

需要重点监控：

- 同一用户 / IP 连续兑换失败。
- 同一礼品卡重复兑换。
- 兑换成功但权益发放失败。
- 已作废 / 已过期礼品卡被大量尝试。
- 礼品卡生成、导出和作废操作。

礼品卡接入 Android 真正入口、支付权益或商务发放后，必须同步更新用户协议、隐私政策、风险提示、项目记忆和本 runbook。
