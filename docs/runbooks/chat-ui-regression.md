# Chat UI 回归与接手 Runbook

最后更新：2026-06-13

## 目的

为新窗口、外部会诊前自查、以及每次聊天 UI 调整后的快速回归提供统一入口，避免每个人脑内的“复现步骤”和“修复标准”不一致。

本 runbook 只记录当前运行时真相。旧的反向列表、小分割、overlay / active-zone、`scrollBy` / raw delta 等历史路线只看 git 历史，不再作为当前回归入口。

## 当前接手建议

1. 先读根 `AGENTS.md`
2. 再读 `docs/project-state/current-status.md`
3. 需要看近期变更时读 `docs/project-state/recent-changes.md`
4. 只在要排风险 / 待决策时再读 `open-risks.md`、`pending-decisions.md`

如需外部会诊，当前默认整理成发给小米 / MiMo 的自包含短稿；对方通常看不到本地仓库，所以必须贴关键代码片段、状态名、调用顺序、已排除方案和限制条件。

## 当前稳定基线

- 消息列表是单一正向 `LazyColumn` 主人，`messages` oldest -> newest，视觉底部最新消息是 `lastIndex`
- 清数据 / 删除历史后的首次真实业务内容未触到工作线前，运行时是 `InitialWorklinePhase.TopUnreached`：同一个 `LazyColumn` 临时 `Arrangement.Top`，真实消息、图片 pending / 失败、assistant placeholder、失败 footer 和小球从顶部自然向下排；首屏文档流底边触到 96dp 工作线后，如果用户没在触碰 / 拖动 / 浏览，先进入极短 `TopAnchoring` 并继续保持 Top 布局，等列表可正向滚动且内容超过工作线约 56dp 时，同一执行点切回默认 `Arrangement.Bottom` 并复用现有强制底部锚点接一次
- 工作线 gap 是 `96.dp`，小球、streaming 正文、开机历史态、完成态尾部都围绕这条工作线；工作线以下空白必须完整露出来
- AutoFollow / 回到底部使用 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET`
- streaming 内容提交后由 `SideEffect` 在同帧 apply changes 后、layout 前请求底部锚定，压“下一行从工作线下方冒头闪”
- 用户进入 `UserBrowsing` 后让权；用户手动回到正向列表物理底部（`canScrollForward == false`、手指抬起、滚动停止）后先锚底再恢复 `AutoFollow`
- composer 是页面底部兄弟层，自己吃 `imePadding()`；列表不吃 IME 动画帧，不允许键盘抬升消息工作线
- active / settled 渲染共用 unified soft-wrap renderer；不再用小分割 item、物理行预切、fresh suffix 灰色高亮动画
- 流式运行态里的正文 / reveal buffer / message id / isStreaming 是运行时状态，不进入 Activity saved-state；切后台 / 重建恢复依赖已有流式草稿、远端快照和 pending 恢复链，避免大字符串进 saved-state 或出现“状态保存了但内容丢了”的半恢复状态
- 标准 Markdown 表格降级成普通项目行文本；emoji 偶发输出时按普通文本渲染

## 禁止回归的旧链

检查代码时发现以下符号或思路回潮，需要先停下复盘：

- `reverseLayout`
- `items.asReversed()`
- `StreamingBlockChatListItem`
- `StreamingTextBlock`
- `streaming_tail`
- `activeStreamingBlockIndex`
- `BottomActiveZone`
- `renderBottomActiveZone()`
- `requestSendStartBottomSnap()`
- streaming 高度 `scrollBy(...)` / `dispatchRawDelta(...)`
- `followStreamingByDelta(...)`
- 消息运行时 overlay / active-zone 作为第二消息主人
- `SparseBottomSpacer`
- `cleanStateSparseLayoutActive`
- 动态稀疏 bottom padding / spacer
- `ChatComposerCollapseOverlay`
- `composerCollapseOverlay*`

说明：输入框收口不再保留 `ChatComposerCollapseOverlay` / `composerCollapseOverlay*` 旧链；composer collapse 只走底部固定 composer 的单一实测 reserve。发现这些符号回潮，也按旧链恢复处理。

## 快速回归场景

### A. 首屏未触线与完成态贴底

步骤：
- 清数据后首次进入
- 删除所有历史对话后首次发 1 个字、发纯图、发图文
- 有本地 / 后端历史时进入聊天页
- 生成完成后停在完成态

预期：
- 清数据 / 删除历史后，首条真实业务内容未触到工作线前应从顶部自然向下排，不应吊在底部工作线；图片上传失败、用户失败 footer、assistant 失败 footer 都算真实内容高度
- 最新真实内容底边触到或超过 96dp 工作线后，应交回默认工作线主链；之后小球、streaming 正文和完成态尾部围绕工作线
- 最新消息尾部命中 96dp 工作线
- 工作线以下空白完整露出来
- 不应还能继续往上扒出额外底部空白
- 完成态不跳到长回复上方

### B. 发送起步与小球

步骤：
- 输入 1 个字发送
- 输入较长文字发送
- 纯图 / 图文发送

预期：
- 普通有历史 / 已触线场景：用户消息和 waiting 小球第一时间落在工作线附近
- 清数据 / 删除历史后的未触线场景：用户消息和 waiting 小球从顶部自然向下排，直到真实内容底边触到工作线后再恢复工作线锚点
- 发送瞬间历史文本不明显上下弹
- 输入框清空回缩不带旧高度残影

### C. Streaming 渲染

步骤：
- 触发长回复，包含段落、标题、列表、免责声明触发文本
- 观察换行、新段落、标题分割线、结束收口

预期：
- 中文吐字不应 3 到 4 个字一坨一坨跳出；长回复也不应因为吐字过细出现明显掉帧
- 工作线下方不应再冒出下一行黑点 / 黑字
- 分割线只跟随一级 / 二级标题规则，不随机丢
- streaming 期间免责声明只占位不显示，settled 后显示且尾部不跳

### D. 用户滚动与 AutoFollow

步骤：
- streaming 中上滑停在本条消息中间
- streaming 中上滑到历史消息
- 手动下滑回到底部，不点按钮
- 点击回到底部按钮

预期：
- 用户上滑 / 下滑时不抢手，想停哪里能停哪里
- 手动回到物理底部后应恢复 AutoFollow
- 回到底部按钮滑动中不显示，停止后离底足够才短暂显示

### E. 输入框 / IME

步骤：
- 冷启动首次点输入框
- 输入多行文本
- 键盘弹起 / 回缩
- streaming 中键盘弹起

预期：
- 键盘只移动输入框，不抬升消息工作线
- 历史区消息不随键盘整体联动
- 多行输入只影响 composer 内部，不进入列表 bottom reserve

### F. Markdown / 表格 / emoji / 链接

测试内容：

````markdown
| 项目 | 结论 |
| --- | --- |
| 番茄 | 疑似缺镁 |
| 辣椒 | 先观察 |

```text
a | b | c
```

偶发 emoji：🌱🙂
````

预期：
- 标准表格降级成可读项目行文本
- 代码块里的 `|` 不被当表格转换
- emoji 不崩、不撑乱布局
- 完成态链接仍按现有规则打开系统浏览器

### G. Clean-state 与恢复

步骤：
- 清数据后启动
- 切后台 / 杀进程后重进
- user 发送失败、assistant 中断失败、首 token 前失败

预期：
- 无账号 / 手机号时，清数据后应是 clean-state，不从本地备份回灌旧 UI
- 有稳定账号后，后端返回最近 30 轮业务记录属于账号级恢复，不是 UI 回退
- 失败 footer 与正文一起恢复，不只剩正文或只剩用户消息

### H. 弱网 / 后台 / 尾部动态

步骤：
- 纯文字无网发送，同文恢复网络后手动重输发送，再点旧失败消息重试
- 图片发送在 1/4 张上传失败、全部上传成功但首 token 前断网、SSE 中途断网三个阶段分别切后台 / 杀进程 / 重进
- assistant 失败态带远端图片 URL 时点“回复未完成 · 点击重试”，立刻切后台或杀进程
- 生成中打开“删除所有历史对话”，以及刚点发送后马上删除历史

预期：
- 同一条纯文字失败内容只复用同一个用户消息 ID，不产生第二个新 `client_msg_id`
- 带图 pending 使用 `chatScopeId + userMessageId` 唯一 work；前台已启动远端请求后写入保护窗，后台不抢跑
- 已有远端图片 URL 的 assistant 重试也重新登记 pending，App 被杀后仍有 WorkManager 兜底
- 后端同一用户同时只有一条活跃主流式请求；删除历史遇到活跃流返回 409，不允许旧快照在删除后重新写回
- 完成 / replay 以服务端归档为准；UI 不应出现“本地显示失败但后台偷偷继续扣次”的并存态

### I. 设置 / 反馈 / 更新动态

步骤：
- 帮助与反馈页加载历史后，让后台在加载和已读之间插入一条新消息
- 帮助与反馈里打开 `+` 面板时键盘已弹起，再用系统返回键 / 手势返回
- 检查更新分别验证无更新、有新版本、下载中断、SHA-256 / 包名 / `versionCode` 不匹配、用户取消安装
- 账号管理删除历史在无网、有活跃流、有 pending 图片任务、普通成功四种状态下执行

预期：
- 帮助与反馈已读只标记本次已经拉取到的后台 / 系统消息，新到但未展示的消息仍保留红点
- 打开反馈附件面板前应清输入焦点；系统返回键 / 手势优先收起附件面板，再返回上一页
- 自有 APK 更新必须经过 https、大小、SHA-256、包名和版本号校验，再交给系统安装页；不能静默安装
- 删除历史成功只清问诊聊天、本地草稿、streaming draft、pending 任务和私有 composer 图片，不删除会员 / 额度 / 帮助与反馈

## 跨机型 / 模拟器矩阵建议

优先覆盖以下组合，先找布局和滚动大问题，再考虑微调：

- 小屏：360dp 宽附近
- 常规手机：390dp 到 430dp 宽
- 大屏 / 折叠外屏：600dp 以上
- 平板 / 横屏：700dp / 900dp 分档
- 字体缩放：1.0、1.15、1.3
- 导航模式：手势导航、三键导航
- Android 版本：至少覆盖一个 Android 10/11 旧版本、一个 Android 13/14、一个 Android 15/16 新版本
- 输入法：系统默认输入法 + 用户常用输入法

每组最少跑：
- 首屏历史贴底
- 发送 1 字
- 长回复 streaming
- 上滑浏览 / 手动回底
- 点输入框弹起 / 回缩

## Baseline Profile 提醒

聊天页关键 UI 路径变更后，必须查看 `docs/runbooks/android-baseline-profile.md`。小样式、文案和小参数通常不用更新脚本；如果替换列表 / 输入框 / Selection / Markdown 主结构，发版前应重跑 `:app:generateReleaseBaselineProfile`。
