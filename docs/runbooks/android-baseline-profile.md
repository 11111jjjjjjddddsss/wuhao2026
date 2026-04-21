# Android Baseline Profile Runbook

本 runbook 记录农技千问 Android 客户端的 Baseline Profile / Macrobenchmark 入口。它服务的是“刚启动 App、首次进入聊天页、首次滑长文本、首次点输入框时有轻微帧感”的系统性预热问题，不替代业务 UI 修 bug。

## 目标

- 让聊天页关键路径在正式包安装 / 更新后尽量提前由 ART 编译
- 减少 Compose 首次组合、Markdown 长文布局、Selection、输入框弹起 / 收起、LazyColumn 滑动等路径的冷启动 JIT 成本
- 保持业务 UI 代码不变：不动工作线、AutoFollow、sendStart、finalize、streaming wrap guard

## 当前模块

- App 模块：[app](D:/wuhao/app)
- Baseline Profile / Macrobenchmark 模块：[baselineprofile](D:/wuhao/baselineprofile)

关键测试：

- `BaselineProfileGenerator.generateChatBaselineProfile`
- `ChatMacrobenchmark.startup`
- `ChatMacrobenchmark.chatScrollAndComposer`

当前关键路径只做 UI 预热，不点击发送，不触发后端 / 模型调用。

## Codex 修改检查清单

以后只要改到聊天页关键 UI 路径，Codex 需要主动检查本 runbook 和 `:baselineprofile` 脚本是否要跟着更新，不要让用户自己记。

必须检查的常见改动范围：

- `ChatScreen.kt` 的首屏、hydrate、聊天列表、输入框接线、IME、Selection、发送起步、streaming / finalize 主链
- `ChatStreamingRenderer.kt` 的 Markdown、SelectionContainer 包裹范围、TextLayout / TextMeasurer、streaming / settled 渲染结构
- `ChatComposerPanel.kt` / `ChatComposerCoordinator.kt` 的输入框聚焦、输入、回缩、长文本编辑、键盘交互
- `ChatRecyclerViewHost.kt` 的 LazyColumn item 结构、key、contentType、padding、滚动容器
- 新增会影响“冷启动首次进入、首次滑长文、首次点输入框”的关键交互

处理规则：

- 如果只是颜色、文案、很小的视觉参数，通常不需要更新 baseline profile 脚本
- 如果关键路径或可点击 / 可滑动 / 可聚焦节点发生变化，需要同步检查 `BaselineProfileGenerator.exerciseChatCriticalPath(...)` 和 `ChatMacrobenchmark.chatScrollAndComposer`
- 如果新增了新的首屏关键路径或替换了输入框 / 列表主结构，应把新路径加入 baseline profile 采集
- 正式发版前，如果这些关键 UI 路径近期变过，应重新运行 `.\gradlew.bat :app:generateReleaseBaselineProfile`

## 本地编译验证

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileReleaseKotlin :baselineprofile:compileNonMinifiedReleaseKotlin :baselineprofile:compileBenchmarkReleaseKotlin
```

## 生成 Baseline Profile

前提：

- 使用真机或可运行 Macrobenchmark 的模拟器
- 设备屏幕保持点亮
- 推荐 Android 13 / API 33 以上
- 无线调试可以使用；无线只是 ADB 连接方式，不影响 profile 是否生效

命令：

```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile
```

生成后检查：

- `app/src/release/generated/baselineProfiles/`
- 或 Gradle 输出里提示的 baseline profile 目标路径

如果后续 Gradle / Android Gradle Plugin 版本改变，输出路径可能略有变化，以 Gradle 输出为准。

## 什么时候需要重跑

- 聊天页主结构明显变化
- 输入框交互 / IME 链路明显变化
- Markdown / Selection / LazyColumn 渲染路径明显变化
- 新增关键首屏或关键交互路径
- 发布正式版本前希望刷新预编译覆盖面

小样式改动、文案改动、后端接口改动通常不需要重跑。

## 验证收益

可运行：

```powershell
.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

看 `ChatMacrobenchmark.startup` 和 `ChatMacrobenchmark.chatScrollAndComposer` 的结果。若只想做人工体感，尽量用 release / profileable 非 debug 包，不要用 debug 包判断最终丝滑度。

## 注意事项

- Baseline Profile 不是运行时“偷偷先滑一遍页面”，而是把关键代码路径交给系统提前编译
- 它不能修复真实布局 bug，只能减少冷启动 / 首次使用路径的 JIT 和初始化帧感
- 如果真机上仍有持续性卡顿，应继续用 JankStats / Perfetto / Android Studio Profiler 定位，不要继续凭感觉改业务链路
