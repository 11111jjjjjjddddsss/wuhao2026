# P0 收口 — UI 分类 + 两条线分离 + 会员以后端为准 — 交付说明

## 一、UI 模块边界说明 + 入口函数 grep

### 1. 三层职责（gpt-demo.html 内分区）

| 层 | 职责 | 禁止 |
|----|------|------|
| **Data** | 存储（localStorage 读写的 key/方法）、后端 HTTP 封装（getEntitlementFromBackend、Android 调 getEntitlement） | 不包含业务裁决、不直接给 UI 暴露写订阅 |
| **Domain** | 会员裁决（computeEntitlement）、扣减口径（getQuota、checkQuotaBeforeSend、incrementUsage）、输入规则拼装、同档续费/升档/幂等（applyPurchase、setCurrentTier）、getCurrentTier、getMembership | 不直接改 DOM；只调 Data 读/写，只给 UI 提供“当前档位/额度/是否可发”等结果 |
| **UI** | 渲染/动画/灰块/按钮置灰/Toast（updateMembershipUI、updateTierUIEverywhere、setDisabled、showToast、openMembershipModal、createMessage、setInitialHistory、applyInterruptedUI、refreshQuotaUI 等） | 不直接改订阅、不直接改 A/B；只调 Domain 的 getMembership/getCurrentTier/getQuota/checkQuotaBeforeSend，不调 getSubscriptions/saveSubscriptions 写 |

### 2. 开关（仅切换 Data 层来源，UI 不改）

- **USE_BACKEND_AB**（默认 false）：实际生效在 Android `BuildConfig.USE_BACKEND_AB`；A/B 真相在后端时由 Android 拉 snapshot、append-a、update-b。
- **USE_BACKEND_ENTITLEMENT**（默认 false）：为 true 时额度/展示只读后端 GET /api/entitlement；前端 getCurrentTier/getMembership 读 `window._cachedEntitlement`（由 getEntitlementFromBackend 拉取并写入）。

### 3. 入口函数名 grep（便于 SAE+后端接入时定位）

```bash
# Data 层入口
grep -n "function getSubscriptions\|function saveSubscriptions\|function getUsage\|function setUsage\|function getEntitlementFromBackend\|function getTodayKey" app/src/main/assets/gpt-demo.html

# Domain 层入口
grep -n "function computeEntitlement\|function getCurrentTier\|function getMembership\|function getQuota\|function checkQuotaBeforeSend\|function incrementUsage\|function applyPurchase\|function setCurrentTier\|function migrateLegacyToSubscriptions\|function hasOrderId" app/src/main/assets/gpt-demo.html

# UI 层入口
grep -n "function updateMembershipUI\|function updateTierUIEverywhere\|function setDisabled\|function showToast\|function openMembershipModal\|function closeMembershipModal\|function createMessage\|function setInitialHistory\|function refreshQuotaUI\|function applyInterruptedUI\|function refreshEmptyState" app/src/main/assets/gpt-demo.html
```

---

## 二、两条线分离 — 一条真实日志链路

**场景**：启动拉 snapshot → 发起对话 done → append-a → A≥24 触发 B 提取 → update-b 成功清 A。

**前置**：后端已实现 GET /api/session/snapshot、POST /api/session/append-a、POST /api/session/update-b；Android 已打开 `USE_BACKEND_AB` 且配置 `UPLOAD_BASE_URL`。

**日志链路示例**（按时间序）：

```
1. 启动/切会话
   [SESSION] GET snapshot user_id=U1 session_id=S1 b_len=0 a_rounds=0 return_ui=0
   (Android) ABLayerManager loadSnapshot b_len=0 a_rounds=0

2. 用户发一条消息，模型流式返回，done=true
   (Android) dispatchComplete -> ABLayerManager.onRoundComplete
   [SESSION] POST append-a user_id=U1 session_id=S1 a_rounds=1
   (Android) appendA ok session=S1 a_rounds=1

3. 继续对话直至 A 达到 24 轮，再次 onRoundComplete
   [SESSION] POST append-a user_id=U1 session_id=S1 a_rounds=24
   (Android) appendA ok session=S1 a_rounds=24
   (Android) B 提取 -> updateB -> [SESSION] POST update-b user_id=U1 session_id=S1 b_len=xxx a_cleared
   (Android) updateB ok session=S1 b_len=xxx a_cleared
```

**验收**：done=true 才 append-a；B 成功写入后才 update-b 并清 A；中断/失败不写 A、不扣费。

---

## 三、会员以后端为准 — 4 条用例日志

**后端**：upload-server 已实现 GET /api/entitlement、POST /api/subscription/apply（no_downgrade、order_id 幂等、同档续费延 end_at）。

### 1) 升档冻结（Plus 剩 10 天 → 升 Pro，Plus 冻结 10 天）

- 操作：已有 Plus(active, 剩约 10 天) 时购买 Pro。
- 后端日志示例：`[ENT] apply user_id=U1 order_id=ord_pro_1 pro -> {"effective_tier":"pro","effective_end_at":...,"paused_list":[{"tier":"plus","remaining_days":10}]}`

### 2) 到期解冻（Pro 到期 → 自动恢复 Plus 剩 10 天）

- 操作：Pro 的 end_at 已过，下一次 GET /api/entitlement 或定时裁决。
- 后端：Pro 置 expired，最高 paused(Plus) 恢复 active，end_at = now + remaining_seconds*1000。
- 日志示例：GET /api/entitlement 返回 `effective_tier: plus`, `effective_end_at: <now+10天>`, `paused_list: []`。

### 3) 降档拒绝（已 Pro 时再购 Plus → 后端 no_downgrade）

- 操作：POST /api/subscription/apply body { user_id, order_id, tier: "plus" }。
- 响应：400 `{ "error": "no_downgrade" }`。
- 前端：按钮置灰、可点，toast「已开通更高档位，无需重复购买」（同日一次）。

### 4) 同档续费 + 幂等（同一 order_id 不重复生效）

- 操作：同一用户同档(Pro)续费，同一 order_id 调用两次 POST /api/subscription/apply。
- 第一次：200，延长该档 end_at。
- 第二次：200 `{ "ok": true, "idempotent": true }`，不重复延长、不重复扣减。

---

## 四、代码落点摘要

| 项 | 位置 |
|----|------|
| 会话两条线后端 | upload-server/server.js：SESSION_STORE、GET /api/session/snapshot、POST append-a、POST update-b |
| 会话两条线客户端 | SessionApi.kt（getSnapshot、appendA、updateB）；ABLayerManager（loadSnapshot、后端模式 onRoundComplete → appendA/updateB）；MainActivity onPageFinished 拉 snapshot 并 setInitialHistory |
| 会员后端 | upload-server/server.js：GET /api/entitlement、POST /api/subscription/apply（no_downgrade、幂等、同档续费） |
| 会员前端读后端 | gpt-demo：USE_BACKEND_ENTITLEMENT + getEntitlementFromBackend(AndroidInterface.getEntitlement)、_cachedEntitlement；MainActivity getEntitlement(callbackId) |
| UI 分层 | gpt-demo：Data（getSubscriptions/saveSubscriptions/getUsage/setUsage/getEntitlementFromBackend）、Domain（computeEntitlement/getCurrentTier/getMembership/getQuota/applyPurchase 等）、UI（updateMembershipUI/showToast/createMessage/setInitialHistory 等） |
