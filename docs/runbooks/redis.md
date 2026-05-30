# Redis Runbook

最后更新：2026-05-30

## 当前现状

- 阿里云 Redis 开源版实例已购买：`nongjiqiancha-prod-redis` / `r-2zet46zvmoo9wu3bic`
- 地域 / 可用区：`cn-beijing` / `cn-beijing-l`
- 规格：Redis 7.0、标准高可用主备、`256MB`、包年包月，到期时间 `2027-05-30T16:00:00Z`
- 网络：生产 VPC `vpc-2zeax2zowza2398b9dzot`，交换机 `vsw-2zemsq82lj2kp8za90aky`
- 内网地址：`r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com:6379`
- 白名单：`127.0.0.1`、ECS 私网 IP `192.168.1.237`
- ECS 已验证内网 DNS 和 TCP 6379 可达
- 当前 `server-go` 代码和 ECS 环境变量尚未接入 Redis；不能把 Redis 写成当前业务主链依赖

## 预期用途

- 手机号验证码：验证码短期存储、发送频率限制、错误次数限制
- 限流：用户 / IP / 设备维度的短期频控
- 多实例保护：后续多台 ECS 或回到 SAE 多实例后，用于分布式锁、摘要任务 claim、热点状态
- 缓存：会员状态、配置、今日农情等低风险热点数据

## 当前不做

- 不把聊天正文、图片内容、模型 Key、手机号明文或长期用户画像放进 Redis
- 不把 Redis 作为唯一真相来源；会员、额度、订单、聊天归档和摘要仍以 MySQL 为真相
- 不在代码未接入前把 Redis 密码写入 ECS 环境文件
- 不开放公网访问或 `0.0.0.0/0` 白名单

## 常用检查命令

```powershell
aliyun r-kvstore DescribeInstanceAttribute --InstanceId r-2zet46zvmoo9wu3bic
aliyun r-kvstore DescribeSecurityIps --InstanceId r-2zet46zvmoo9wu3bic
```

从 ECS 检查内网连通：

```powershell
aliyun ecs RunCommand --RegionId cn-beijing --Type RunShellScript --InstanceId.1 i-2ze5nrem0jrchln4f0eh --CommandContent "getent hosts r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com; timeout 3 bash -lc '</dev/tcp/r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com/6379' && echo redis_tcp_ok" --Timeout 120
```

## 接入前检查

- 先确定用途：验证码、限流、缓存或分布式锁，不要为了“已经买了”强行接入
- 设计 key 前缀、TTL、失败降级策略和最大内存策略
- 生产密码只保存到本机 secret 文件和 ECS 环境文件，不进入仓库或聊天记忆
- 接入后同步复核隐私政策 / 第三方信息共享清单 / 个人信息收集清单
