# OSS 图片存储 Runbook

最后更新：2026-06-13

## 目的

记录农技千查图片 / APK 等对象存储的当前资源状态、后续 Bucket 配置原则和接入边界。

## 当前现状

- 已购买阿里云 OSS 标准-本地冗余存储包（华北2 / 北京）100GB
- 资源包实例：`OSSBAG-cn-mqq4sqfvr001`
- 生效时间：`2026-05-24T15:00:00Z`
- 到期时间：`2027-05-24T15:00:00Z`
- 当前剩余额度：100GB
- 已创建 Bucket：`nongjiqiancha-prod`
- Region：`oss-cn-beijing`
- ACL：private
- 存储类型：Standard
- 冗余类型：LRS / 本地冗余
- 默认服务端加密：2026-06-12 已开启，`SSEAlgorithm=AES256`
- 2026-05-31 已配置生命周期：
  - `uploads/`：问诊上传图 3 天自动删除
  - `support/`：帮助与反馈图片 30 天自动删除
  - 未完成分片上传：1 天自动清理
- `server-go` 已新增 OSS 上传存储后端。2026-05-31 已创建最小权限 RAM 子账号 / 策略、完成上传 / 下载 / 删除冒烟测试，并把生产 ECS 配置为 `UPLOAD_STORAGE_BACKEND=oss`、`OSS_BUCKET=nongjiqiancha-prod`、`OSS_ENDPOINT=https://oss-cn-beijing-internal.aliyuncs.com` 和 OSS 凭证；2026-06-13 已把该 RAM 策略默认版本更新到 v3，仅放行 Bucket 本体、`uploads/*` 和 `support/*` 所需对象操作，避免帮助与反馈图片切到 `support/` 后生产上传失败。当前 `/healthz` 返回 `upload_storage=oss`，`/upload` 写私有 OSS，且默认按 `user_id + IP` 做 10 分钟 120 次短期限流，防异常客户端循环刷上传成本。App、模型和历史 URL 仍走本后端 `https://api.nongjiqiancha.cn/uploads/<file>.jpg` 或 `https://api.nongjiqiancha.cn/uploads/support/<file>.jpg`，不把 OSS AK/SK 下发 Android。未配置 OSS 的其他环境仍可回退 ECS 本机 `/var/lib/nongjiqiancha/uploads`
- 2026-06-13 起，帮助与反馈图片仍复用后端 `/upload` 接口，但 Android 会额外传 `purpose=support`；后端写入 OSS `support/` 前缀并返回 `/uploads/support/<file>.jpg`，按 30 天生命周期删除。普通问诊图片不传 purpose，继续写入 `uploads/` 并按 3 天删除。主聊天接口只接受普通 `/uploads/<file>.jpg`，不接受 `/uploads/support/<file>.jpg`，避免客服截图误进主模型图片链

## 存储包口径

- 该 100GB 是标准存储容量抵扣包，不是下行流量包
- 存储包按实际存储占用抵扣，类似“当前放了多少文件就占多少容量”，不是一年累计上传量
- 超出 100GB 后，超出部分按量计费；服务不会因为超出存储包而自动停止
- 上传流量通常不是当前主要成本；用户从 OSS 下载 / 浏览图片会产生下行流量或请求费用，当前未购买下行流量包 / CDN

## 后续 Bucket 建议

- Region：`cn-beijing` / 华北2（北京）
- 存储类型：标准存储
- 冗余类型：本地冗余
- 读写权限：私有
- 阻止公共访问：开启
- 版本控制：首版关闭
- 命名建议：`nongjiqiancha-prod`；若被占用，可使用带年份或随机后缀的全局唯一名称

## 图片生命周期原则

- 模型上下文只保留当前轮和上一轮图片；更早图片不进入模型上下文，只保留文字结论 / 摘要
- OSS 文件保存周期与模型上下文无关；即使文件仍在 OSS，也不会自动进入模型上下文
- 问诊上传图当前按 3 天删除；模型上下文只需要当前轮和上一轮图片，3 天足够短期追问和后台重试，也更省钱、少留隐私数据
- 帮助与反馈聊天记录正文、发送人、时间和已读状态保存在 MySQL `support_messages` / `support_conversations`，不随 OSS 图片生命周期自动删除。客服图片附件保存在 OSS `support/`，30 天后可能不可见；旧客服记录应以文字沟通为主，图片过期时 App 显示过期占位。客服记录最终保存 / 删除 / 注销处理规则仍需按合规口径单独收口
- `session_round_archive` 的文字问答和图片 URL 仍可保留 30 天；3 天后问诊图片对象可能不可见，旧聊天应以文字结论为主。Android 主聊天已补“图片已过期”缩略图占位，全屏预览显示“图片已过期，仅保留文字记录”；帮助与反馈图片也会在远端图不可用时显示过期占位
- 用户同一台手机翻旧聊天时优先依赖本地缓存；换机、清数据或生命周期删除后，旧图片可能不可见，这属于可接受的首版取舍

## 接入前置

- 不把 OSS AccessKey / Secret 写入仓库、文档或聊天
- Android 不直连 OSS，不打入 OSS 密钥
- 后端是上传和访问控制唯一入口
- 多台 ECS 或回到 SAE 多实例前，生产环境已满足共享对象存储前置条件，且 `api.nongjiqiancha.cn` HTTPS 已就绪；仍需在模型 Key 配好后用真实 App 链路验证 `/upload`、`/uploads/` 和模型拉图链路
- 若 APK 分发也放 OSS，应单独配置下载域名、HTTPS、文件大小和 SHA-256，不建议让 Go 后端动态服务大 APK

## 当前阻塞

- OSS 生产环境变量已写入 ECS，健康检查已显示 `upload_storage=oss`
- OSS 凭证已使用最小权限 RAM 子账号 / 策略，只允许访问 `nongjiqiancha-prod` Bucket 本体、`uploads/*` 和 `support/*` 所需对象操作；后续仍需定期轮换，不使用主账号长期 AccessKey 作为应用凭证
- `api.nongjiqiancha.cn` HTTPS 已完成，`BASE_PUBLIC_URL / UPLOAD_BASE_URL=https://api.nongjiqiancha.cn` 可作为图片公网基地址；DashScope 模型 Key 已配置，仍需用真实 App 链路验证 `/upload`、`/uploads/` 和模型拉图链路，验证前不能把图片问诊当完整生产可用
