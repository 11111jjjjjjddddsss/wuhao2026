# B 层稳定性收尾报告

## 一、原子提交点

| 项目 | 位置 | 说明 |
|------|------|------|
| 持久化方式 | `ABLayerManager.kt` 第 89-90 行 | `prefs.edit().putString(...).commit()` |
| 成功条件 | 第 90 行 | `val committed = prefs?.edit()?.putString(KEY_B_SUMMARY, newSummary)?.commit() ?: false` |
| 清空 A 与更新内存 | 第 91-96 行 | 仅当 `committed == true` 时执行 `synchronized(aLock){ aRounds.clear(); bSummary = newSummary }` |
| 失败分支 | 第 97-99 行 | `commit() == false` 时仅打日志，不写 B、不清 A |

## 二、参数单源位置

| 项目 | 文件 | 说明 |
|------|------|------|
| 唯一定义 | `ModelParams.kt` | `TEMPERATURE=0.85`, `TOP_P=0.9`, `MAX_TOKENS=4000`, `FREQUENCY_PENALTY=0`, `PRESENCE_PENALTY=0` |
| A 层引用 | `QwenClient.kt` 第 94-98 行 | `callApi` 请求体使用 `ModelParams.*` |
| B 层引用 | `QwenClient.kt` 第 349-353 行 | `extractBSummary` 请求体使用 `ModelParams.*` |

## 三、B 摘要校验规则

| 规则 | 实现位置 | 说明 |
|------|----------|------|
| 非空 | `ABLayerManager.validateBSummary` 第 112-113 行 | `trim()` 为空则返回 false |
| 长度 200~1200 字 | 第 114-117 行 | `t.length !in B_MIN_LEN..B_MAX_LEN` 则拒绝 |
| 禁止结构化开头 | 第 22 行 + 第 118-121 行 | `FORBIDDEN_START` 匹配 `^[#*\-]`、`^[一二三四五六七八九十百]+、`、`^\d+\.` |
| 校验失败处理 | 第 80-83 行 | 不写入 B、不清空 A，直接 return |

## 四、A 快照位置

| 项目 | 位置 | 说明 |
|------|------|------|
| 锁内复制 | `ABLayerManager.onRoundComplete` 第 47-55 行 | `synchronized(aLock){ ... val snap = aRounds.map { it }; doExtract to snap }` |
| 锁外使用 | `tryExtractAndUpdateB` 第 69、77 行 | 接收 `aRoundsSnapshot`，在 Thread 内调用 `buildDialogueText(aRoundsSnapshot)`，不访问 `aRounds` |

## 五、防并发机制

| 项目 | 位置 | 说明 |
|------|------|------|
| extracting 标志 | `ABLayerManager` 第 61-64 行 | `extracting.compareAndSet(false, true)` 失败则本轮跳过，不阻塞、不排队 |
| 释放 | 第 102 行 | `finally { extracting.set(false) }` |
