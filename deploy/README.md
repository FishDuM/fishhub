# FishHub 生产与本地部署指南

本文档提供 FishHub 前端托管（Nginx）、网关限流（Sentinel）与微服务链路的部署配置说明。

---

## 目录结构

```
deploy/
├── nginx/
│   └── nginx.conf                       # Nginx 反向代理与前端静态资源托管模版
├── sentinel/
│   └── fishhub-gateway-flow-rules.json  # Sentinel 网关流控规则模版（可直接导入 Nacos）
└── README.md                            # 部署指南说明
```

---

## 一、Nginx 部署配置

### 1. 前端静态资源构建
在 `fishhub-vue3` 目录下执行构建：
```bash
cd fishhub-vue3
npm install
npm run build
```
构建产物将输出在 `fishhub-vue3/dist`。

### 2. 配置 Nginx
将 `deploy/nginx/nginx.conf` 复制或挂载到 Nginx 配置目录（例如 `/etc/nginx/conf.d/fishhub.conf`）：
- 修改 `root` 指向实际的 `dist` 静态资源目录。
- 修改 `upstream fishhub_gateway` 中的网关地址（默认为 `127.0.0.1:8000`）。
- 执行配置热重载：
```bash
nginx -t && nginx -s reload
```

---

## 二、Sentinel 网关限流与 Nacos 数据源配置

FishHub 网关（`fishhub-gateway`）已集成 Sentinel 网关限流并支持通过 Nacos 动态拉取规则。

### 1. 规则数据源说明
- **Nacos Server 地址**：`${spring.cloud.sentinel.datasource.nacos.server-addr}` (默认 `192.168.0.100:8848`)
- **Group ID**：`DEFAULT_GROUP`
- **Data ID**：`fishhub-gateway-flow-rules`

### 2. 导入限流规则
在 Nacos 控制台的配置管理中，新建一条配置：
- **Data ID**: `fishhub-gateway-flow-rules`
- **Group**: `DEFAULT_GROUP`
- **配置格式**: `JSON`
- **配置内容**: 复制 `deploy/sentinel/fishhub-gateway-flow-rules.json` 中的内容并发布。

网关服务会在运行时自动监听并热加载最新的流控规则。

---

## 三、网络与 IP 透传机制说明

为确保短信验证码限流、登录防爆破、用户操作日志以及 Sentinel 针对客户端真实 IP 限流生效：
1. **Nginx 层**：向网关透传 `X-Real-IP`、`X-Forwarded-For` 及 `X-Forwarded-Proto`，且**一律用 `$remote_addr` 覆写**这两个转发头（不再拼接客户端自带的 `X-Forwarded-For` 前缀，避免伪造 IP）。
2. **Gateway 层**：`server.forward-headers-strategy` 已改为 `none`（不再信任客户端自带的 Forwarded 头重写 remoteAddress）；`AddUserId2HeaderFilter` 会**剥离所有入站转发头**，仅当网关的直连对端命中 `fishhub.gateway.trusted-proxy-ips`（环境变量 `TRUSTED_PROXY_IPS`）时才采信其透传的 `X-Real-IP`/`X-Forwarded-For`，否则一律以 TCP 对端地址为准，最后向微服务注入清洗后的 `X-Real-IP`。

> 部署示例：`TRUSTED_PROXY_IPS=192.168.1.10,10.0.0.5`（填 Nginx 所在机器 IP）；若网关直接对公网开放，则**不要配置**任何可信代理，客户端无法伪造 IP。

---

## 四、生产环境变量参考（application-prod.yml）

所有服务以 `SPRING_PROFILES_ACTIVE=prod` 启动时加载各模块 `application-prod.yml`（已补齐，原来为空），关键连接信息通过环境变量注入，均带本地开发默认值：

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| NACOS_ADDR | Nacos 注册/配置中心地址（所有 bootstrap.yml 已参数化） | 127.0.0.1:8848 |
| SENTINEL_DASHBOARD | Sentinel 控制台地址（业务服务 8060 / 网关 8858） | 127.0.0.1:8060 或 :8858 |
| MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD | MySQL 连接（各服务库名固定：fishhub_user/note/comment/relation/count，search 用 fishhub） | localhost / 3306 / root / 3057433102 |
| REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DATABASE | Redis | localhost / 6379 / 3057433102 / 0 |
| ROCKETMQ_NAMESRV | RocketMQ NameServer | localhost:9876 |
| ELASTICSEARCH_ADDRESS | ES 地址（search） | localhost:9200 |
| CASSANDRA_HOST / CASSANDRA_PORT / CASSANDRA_KEYSPACE / CASSANDRA_DC | Cassandra（kv） | 127.0.0.1 / 9042 / fishhub / datacenter1 |
| MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY | MinIO（oss） | http://localhost:9000 / fishhub / fish1234 |
| ALIYUN_ACCESS_KEY_ID / ALIYUN_ACCESS_KEY_SECRET | 短信（user） | 开发值 |
| ALIYUN_OSS_* | 阿里云 OSS 备用存储（oss） | 开发值 |
| TRUSTED_PROXY_IPS | 网关可信反向代理 IP（逗号分隔；为空则一律使用 TCP 对端地址） | 空 |
| fishhub.auth.cookie-secure | 登录会话 Cookie 是否加 Secure（HTTPS 生产环境置 true） | false（prod 默认 true） |

启动示例（Linux 容器）：

```bash
SPRING_PROFILES_ACTIVE=prod NACOS_ADDR=10.0.0.5:8848 MYSQL_HOST=10.0.0.6 REDIS_HOST=10.0.0.7 \
  ROCKETMQ_NAMESRV=10.0.0.8:9876 java -jar fishhub-user-biz.jar
```

---

## 五、Leaf Snowflake 的 ZK 命名空间（leaf.name 修复后）

- 修复后 workerId 分配路径为 `/snowflake/fishhub`；旧的 `/snowflake/null`（BOM bug 时期）与 `/snowflake/com.sankuai.leaf.opensource.test`（官方样例）已清理。
- workerId 本地缓存：`${java.io.tmpdir}/fishhub/leafconf/{port}/workerID.properties`。容器部署务必保证每实例独立 tmp（勿打进镜像），否则 ZK 不可用时两个实例可能复用同一 workerId。
- ZK forever 节点按 ip:port 累积：IP 每次变化都会新增节点，workerId=节点序号，超过 1023 启动失败。生产建议固定实例 IP，或改用 Redis 分配 workerId。
- 滚动升级：旧/新命名空间实例同跑时 workerId 可能撞号，先停旧再起新。

---

## 六、网关路径约定（双重前缀是设计，不是 bug）

前端经网关调用使用「外层路由 + 服务内前缀」的双重前缀：

- `/note/note/like`、`/note/note/publish`（note 服务）
- `/note/discover/note/list`、`/note/channel/...`、`/note/topic/...`（发现页/频道/话题都在 /note 路由下）
- `/comment/comment/publish`、`/relation/relation/follow`、`/user/user/...`、`/oss/file/...`、`/search/search/...`
- `/auth/login`（auth 控制器在根路径，单前缀）

网关 `StripPrefix=1` 与此约定一致，**无需修改**；直连服务内部调试时才用服务自身路径（如 `:8086/note/like`）。

