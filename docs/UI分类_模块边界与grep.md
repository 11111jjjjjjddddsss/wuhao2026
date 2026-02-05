# UI 分类/分层 · 模块边界与 grep 可验

## 边界约定

- **Data 层**：只做存储、后端 HTTP；不写业务规则（如单一生效档、冻结/解冻规则）。
- **Domain 层**：会员裁决、扣减口径、输入规则；只调 Data 层读写，**不直接改 DOM**（改展示通过调用 UI 层函数）。
- **UI 层**：只负责渲染、动画、灰块、置灰、Toast；**不直接改订阅、不直接改 A/B、不写扣费/状态**（扣费与状态由 Domain/后端决定后，UI 只展示）。

## 入口函数列表（gpt-demo.html）

| 层 | 入口函数（示例） | 说明 |
|----|------------------|------|
| Data | getSubscriptions, saveSubscriptions, getEntitlementFromBackend, getUsage, getTodayKey | 存储与后端拉取 |
| Domain | getCurrentTier, getMembership, computeEntitlement, applyPurchase, getQuota, getTierLabel, updateTierUIEverywhere, updateDrawerFromTierAndUsage | 裁决与口径，调 Data；更新展示调 UI |
| UI | refreshQuotaUI, updateMembershipUI, updateInputMode, showToast, createMessage, sendMessage, sendToAndroid, applyInterruptedUI, refreshEmptyState, applyMessageWindow | 渲染与用户操作入口，不写订阅/扣费 |

## Grep 命令（证明分层存在、非仅文档）

```bash
# Data 层：存储与后端
grep -n "getSubscriptions\|saveSubscriptions\|getEntitlementFromBackend\|getUsage\|getTodayKey" app/src/main/assets/gpt-demo.html

# Domain 层：不直接改 DOM（应无 document\.getElementById|\.innerHTML|\.textContent 在 Domain 注释块内；若 Domain 内调 updateTierUI 等则视为调 UI 层）
grep -n "getCurrentTier\|getMembership\|computeEntitlement\|applyPurchase\|getQuota\|getTierLabel\|updateTierUIEverywhere\|updateDrawerFromTierAndUsage" app/src/main/assets/gpt-demo.html

# UI 层：不直接写订阅/扣费（应无 saveSubscriptions|localStorage\.setItem.*membership|deduct 等在 UI 注释块内由 UI 函数直接写）
grep -n "refreshQuotaUI\|updateMembershipUI\|updateInputMode\|showToast\|createMessage\|sendMessage\|sendToAndroid\|applyInterruptedUI\|refreshEmptyState" app/src/main/assets/gpt-demo.html

# 分区标记（注释）
grep -n "Data 层\|Domain 层\|UI 层" app/src/main/assets/gpt-demo.html
```

## 文件内分区

- 单文件内用注释块 `// ==================== Data 层：...`、`// ==================== Domain 层：...`、`// ==================== UI 层：...` 分区。
- 开关 `USE_BACKEND_AB`（实际生效在 Android BuildConfig）、`USE_BACKEND_ENTITLEMENT` 仅切换 Data 层来源，UI 不改。
