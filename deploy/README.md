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

为确保短信验证码限流、用户操作日志以及 Sentinel 针对客户端真实 IP 限流生效：
1. **Nginx 层**：向网关透传 `X-Real-IP`、`X-Forwarded-For` 及 `X-Forwarded-Proto`。
2. **Gateway 层**：已配置 `server.forward-headers-strategy: framework`，自动将反向代理头部还原为客户端原始连接地址；同时在全局过滤器中清洗并向下游微服务注入安全的 `X-Real-IP` 请求头。
