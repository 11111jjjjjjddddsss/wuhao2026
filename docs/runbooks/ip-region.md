# IP 粗定位 Runbook

最后更新：2026-06-06

## 当前口径

- 目的：让主对话在用户未明确提供地区时，也能拿到一个省 / 市级“用户地点”参考，提高农业建议的基础准确度。
- 当前方案：后端使用开源 `ip2region` 的离线 xdb 数据文件，通过服务器拿到的客户端公网 IP 在本地查询粗略地区。
- 不使用收费定位 API，不把请求发给第三方免费接口，不把完整 IP 注入主模型。
- 查询结果只作为 `region_source=ip`、`region_reliability=unreliable` 的低可信参考；用户当前明确说出的地区、后续 Android 可选 GPS 定位或后台传入的可靠地区永远优先。
- IP 粗定位只能大致到省市，手机流量、代理、企业网关、运营商出口可能漂移；不能当作县、乡、地块或诊断事实。

## 环境变量

```env
IP2REGION_V4_XDB_PATH=/opt/nongjiqiancha/ip2region/ip2region_v4.xdb
IP2REGION_V6_XDB_PATH=
```

兼容旧变量：

```env
IP2REGION_XDB_PATH=/opt/nongjiqiancha/ip2region/ip2region_v4.xdb
```

优先级：

1. `X-User-Region` / `X-Region-Source` / `X-Region-Reliability` 请求头
2. `IP2REGION_V4_XDB_PATH` 或 `IP2REGION_XDB_PATH` 指向的本地离线库
3. `未知 / unreliable`

## 为什么不放 RDS

- RDS 是业务真相：用户、会员、额度、订单、聊天归档、帮助与反馈等。
- IP 库是静态工具数据，不属于业务数据；放 RDS 会增加查询延迟、数据库负担和维护复杂度。
- 当前正确位置是 ECS 本地文件；如果后续多台 ECS，每台同步同一份 xdb，或由发布脚本 / OSS 下发到本地。

## ECS 安装 / 更新

通过 Cloud Assistant 在 ECS 执行，命令里不包含任何密钥：

```bash
set -euo pipefail
install -d -m 0755 /opt/nongjiqiancha/ip2region
tmp='/tmp/ip2region_v4.xdb'
curl -fsSL https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v4.xdb -o "$tmp"
install -m 0644 "$tmp" /opt/nongjiqiancha/ip2region/ip2region_v4.xdb
chown -R root:root /opt/nongjiqiancha/ip2region
grep -q '^IP2REGION_V4_XDB_PATH=' /etc/nongjiqiancha/server.env \
  && sed -i 's#^IP2REGION_V4_XDB_PATH=.*#IP2REGION_V4_XDB_PATH=/opt/nongjiqiancha/ip2region/ip2region_v4.xdb#' /etc/nongjiqiancha/server.env \
  || printf '\nIP2REGION_V4_XDB_PATH=/opt/nongjiqiancha/ip2region/ip2region_v4.xdb\n' >> /etc/nongjiqiancha/server.env
```

更新库文件时重复上述下载 / 覆盖步骤，然后重启或重新部署后端。当前 Go 服务会懒加载并缓存 xdb；更换路径或重启后会重新加载。

## 数据更新与替代库

当前先用 `ip2region` 是因为它满足“免费、本地离线、Go 可用、中国地区中文、省市级、无额外账号 / 密钥”的第一版需求；但它的内置数据更新不保证固定周期。若后续真实用户量上来后发现地点漂移明显，优先评估以下替代：

- MaxMind GeoLite2 City：免费但需要注册账号和 license key，更新频率更规整；需要遵守 GeoLite EULA、下载限制和数据及时更新要求。
- DB-IP Lite：免费月更，CC BY 4.0，需要署名；Lite 是商业库子集，精度降低。
- IP2Location LITE：免费需署名，半月更，官方标注城市级准确度有限。

无论使用哪套 IP 库，结果都仍然是 IP 粗定位，不是 GPS；主模型只把它作为 `unreliable` 地区参考。

## 验证

本机验证：

```powershell
cd D:\wuhao\server-go
go test ./...
go build ./...
```

生产只读验证：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1
```

重点看：

- `redis=ok`
- `IP2REGION_V4_XDB_PATH=set`
- `v4_xdb=present readable=true`
- `/healthz` 仍为 `ok=true`

## 隐私边界

- 服务端日志继续只打脱敏 IP。
- 主模型只接收“用户地点”和“地点可信度”，不接收完整 IP。
- 后续如果要接 Android 可选定位权限，必须同步更新隐私政策、权限说明、Manifest、项目记忆和上线回归清单。
