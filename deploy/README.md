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
1. **Nginx 层（核心清洗）**：启用官方 `ngx_http_realip_module` 模块，配置 `set_real_ip_from` 代理白名单并开启 `real_ip_recursive on` 递归过滤，自动剥离 CDN/SLB 代理节点，将 `$remote_addr` 修正为真实客户端物理 IP；向网关透传时一律用 `$remote_addr` 覆写 `X-Real-IP` 和 `X-Forwarded-For`。
2. **Gateway 层（应用层透传）**：`server.forward-headers-strategy` 置为 `none`（防止客户端自带头干扰）；`AddUserId2HeaderFilter` 剥离前端伪造的 `userId` 头，并从 Nginx 注入的 `X-Real-IP` 读取真实客户端 IP（直连兜底取 TCP 对端 IP）透传给下游微服务与 Sentinel 流控。

---

## 四、生产环境变量参考（application-prod.yml）

所有服务以 `SPRING_PROFILES_ACTIVE=prod` 启动时加载各模块 `application-prod.yml`（已补齐，原来为空），关键连接信息通过环境变量注入，均带本地开发默认值：

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| NACOS_ADDR | Nacos 注册/配置中心地址（所有 bootstrap.yml 已参数化） | 127.0.0.1:8848 |
| SENTINEL_DASHBOARD | Sentinel 控制台地址（业务服务 8060 / 网关 8858） | 127.0.0.1:8060 或 :8858 |
| MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD | MySQL 连接（各服务库名：fishhub_user/fishhub_note/fishhub_comment/fishhub_count） | localhost / 3306 / root / 3057433102 |
| REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DATABASE | Redis | localhost / 6379 / 3057433102 / 0 |
| ROCKETMQ_NAMESRV | RocketMQ NameServer | localhost:9876 |
| ELASTICSEARCH_ADDRESS | ES 地址（search） | localhost:9200 |
| CASSANDRA_HOST / CASSANDRA_PORT / CASSANDRA_KEYSPACE / CASSANDRA_DATACENTER | Cassandra（note / comment 正文直存） | 127.0.0.1 / 9042 / fishhub / datacenter1 |
| MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY | MinIO 对象存储（user） | http://localhost:9000 / fishhub / fish1234 |
| ALIYUN_ACCESS_KEY_ID / ALIYUN_ACCESS_KEY_SECRET | 阿里云短信（user） | 开发值 |
| ALIYUN_OSS_* | 阿里云 OSS 备用存储（user） | 开发值 |
| fishhub.auth.cookie-secure | 登录会话 Cookie 是否加 Secure（HTTPS 生产环境置 true） | false（prod 默认 true） |

启动示例（Linux 容器）：

```bash
SPRING_PROFILES_ACTIVE=prod NACOS_ADDR=10.0.0.5:8848 MYSQL_HOST=10.0.0.6 REDIS_HOST=10.0.0.7 \
  ROCKETMQ_NAMESRV=10.0.0.8:9876 java -jar fishhub-user-biz.jar
```

---

## 五、分布式发号组件（`starter-biz-id` 本地嵌入发号）

- 系统采用自研高性能嵌入式雪花发号组件 `fishhub-spring-boot-starter-biz-id`，直接嵌入在 `user`、`note`、`comment` 微服务中在内存中纳秒级生成全局唯一 ID。
- 具备自动基于 IP 计算 `workerId`、毫秒内起始序列号随机抖动防倾斜、以及时钟回拨保护机制（<=5ms 自动自旋等待追平），彻底消除了跨网络 RPC 发号的网络 RTT 延迟。

---

## 六、网关路径约定（双重前缀是设计，不是 bug）

前端经网关调用使用「外层路由 + 服务内前缀」的双重前缀：

- `/note/note/like`、`/note/note/publish`（note 服务）
- `/note/discover/note/list`、`/note/channel/...`、`/note/topic/...`（发现页/频道/话题都在 /note 路由下）
- `/comment/comment/publish`、`/user/relation/follow`、`/user/user/...`、`/oss/file/...`（由 user 服务承接）、`/search/search/...`
- `/auth/login`（auth 控制器在根路径，单前缀）

网关 `StripPrefix=1` 与此约定一致，**无需修改**；直连服务内部调试时才用服务自身路径（如 `:8086/note/like`）。

