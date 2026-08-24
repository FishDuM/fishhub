# 🐟 FishHub（飞鱼社区）

> 基于 **Spring Boot 3.2 + Spring Cloud Alibaba + JDK 21 + Vue 3** 的现代化高性能微服务互动社区系统（仿小红书/飞鱼社区交互模式）。

---

## 🌟 系统核心架构与技术亮点

1. **现代化微服务栈**：基于 **Java 21（虚拟线程支持）** + Spring Boot 3.2.5 + Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0 + Nacos 2.x + Gateway 响应式网关。
2. **高并发缓存与数据一致性**：
   - **Cache-Aside 缓存架构**：笔记详情、评论列表、用户互动状态多级缓存；
   - **Redis Lua 原子保障**：点赞、收藏、关注/粉丝关系与图形验证码均通过 Lua 脚本原子执行，防超卖、防并发击穿、自动维持独立足迹 TTL；
   - **高可用读写拦截**：在评论发布、点赞等操作前进行笔记存在性与可写性前置校验，防止对已删内容进行脏操作。
3. **高性能聚合计数中枢 (`fishhub-count`)**：
   - 全站计数（点赞数、收藏数、评论数、粉丝数、关注数、笔记数）通过 **Redis Hash 实时计数（防负数拦截）+ RocketMQ 批量聚合异步落库**，极大削减 MySQL 写入压力。
4. **海量正文 KV 存储解耦 (`fishhub-kv`)**：
   - 笔记大文本正文由 Cassandra / KV 引擎独立存储与水平扩容，关系型数据库仅存储结构化元数据。
5. **智能全文检索 (`fishhub-search`)**：
   - 基于 Elasticsearch 7/8 深度定制，支持拼音分词、多维度权重打分（Function Score 结合点赞/热度/时间）、自适应时间格式解析与检索高亮。
6. **企业级基础框架 (`fishhub-framework`)**：
   - 高度内聚的 `fishhub-spring-boot-starter-web`，一站式提供用户上下文透明传递（`LoginUserContextHolder`）、Long 精度防丢 Jackson 序列化、OpenFeign 请求头穿透、虚拟线程调度与全局统一异常处理。
7. **全栈安全与风控**：
   - Sa-Token 响应式网关鉴权；
   - 严格的可信反向代理 IP 清洗与伪造头剥离；
   - 5 分钟有效、连续输错 10 次原子作废的图形验证码防刷机制；
   - Sentinel 网关动态流控规则。

---

## 📂 项目模块结构

```
fishhub
├── fishhub-framework                     # 平台基础框架
│   ├── fishhub-common                    # 底层通用：枚举/常量/工具类/通用响应
│   ├── fishhub-spring-boot-starter-web   # 【核心 Web 聚合 Starter】上下文/Jackson/异常/操作日志
│   ├── fishhub-spring-boot-starter-biz-mq# 【按需组件】RocketMQ 事务消息与幂等消费
│   └── fishhub-spring-boot-starter-biz-redisson # 【按需组件】Redisson 分布式锁与缓存重建锁
│
├── fishhub-gateway                       # 统一接入网关（WebFlux 响应式、Sa-Token 鉴权、Sentinel 限流）
├── fishhub-user                          # 用户服务（注册登录、BCrypt 加密、图形验证码、RBAC 权限）
├── fishhub-note                          # 笔记服务（发布、编辑、话题频道、点赞/收藏、详情组装）
├── fishhub-comment                       # 评论服务（一级/二级嵌套评论、热度排序、实时点赞）
├── fishhub-user-relation                 # 用户关系服务（关注/粉丝列表、双向缓存、事务通知）
├── fishhub-count                         # 计数服务（全站计数聚合、Redis Hash 缓冲、批量 MQ 落库）
├── fishhub-search                        # 搜索服务（Elasticsearch 笔记与用户检索、高亮、权重排序）
├── fishhub-oss                           # 对象存储服务（MinIO / 阿里云 OSS 文件上传凭据与清理）
├── fishhub-kv                            # 海量正文存储服务（Cassandra 大文本解耦）
├── fishhub-distributed-id-generator      # 分布式 ID 服务（美团 Leaf Snowflake / Segment 双模式）
│
├── fishhub-vue3                          # 前端 SPA 应用（Vue 3 + Vite 5 + Pinia + Tailwind CSS）
├── scripts                               # 启动与运维脚本（一键启动脚本 start-all.bat / start-services.ps1）
├── deploy                                # 部署配置（Nginx 模版、Sentinel 规则、环境变量配置指南）
└── sql                                   # 数据库初始化全量脚本（create.sql）
```

---

## 🔌 微服务端口规划

| 服务名称 | 端口 | 模块架构 | 核心职责 |
| :--- | :---: | :---: | :--- |
| **Gateway** | **8000** | 单层 | 统一对外网关（路由转发、Sa-Token 鉴权、IP 清洗、Sentinel 限流） |
| **User** | **8082** | `api + biz` | 用户/认证/角色权限/图形验证码 |
| **OSS** | **8081** | `api + biz` | 对象存储（MinIO 文件上传与清理） |
| **KV** | **8084** | `api + biz` | Cassandra 大文本正文独立存储 |
| **Distributed ID** | **8085** | `api + biz` | 美团 Leaf 分布式 ID 分发（Snowflake / Segment） |
| **Note** | **8086** | `api + biz` | 笔记核心业务（发布、频道话题、点赞/收藏） |
| **User Relation**| **8087** | 单层 `biz` | 关注/取关、粉丝列表、ZSet 关系缓存 |
| **Count** | **8090** | `api + biz` | 全站计数中枢（Redis 实时缓冲 + 批量异步落库） |
| **Search** | **8092** | 单层 `biz` | Elasticsearch 全文搜索、多级分词、相关度排序 |
| **Comment** | **8093** | 单层 `biz` | 评论、二级回复、热度权重、实时点赞 |

> **安全说明**：除 Gateway（8000）对外开放外，其余微服务均为内网 RPC 服务，跨服务调用一律走 OpenFeign 或 RocketMQ 异步通知。

---

## 🚀 快速上手与本地启动

### 1. 环境准备
- **JDK**：21（Java 21 LTS）
- **Maven**：3.8+
- **Node.js**：18+
- **基础中间件**：
  - MySQL 8.0+
  - Redis 6.0+（支持基础 Hash / Set / ZSet 操作）
  - Nacos 2.x（默认连接配置见各模块 `bootstrap.yml`）
  - RocketMQ 4.x / 5.x
  - Elasticsearch 7.x / 8.x
  - MinIO（对象存储）
  - Cassandra（KV 存储）

### 2. 初始化数据库
执行 `sql/create.sql` 单文件全量脚本（自动创建各独立业务库及表结构）：
```bash
mysql -h 127.0.0.1 -u root -p < sql/create.sql
```

### 3. 编译后端工程
```bash
mvn clean package -DskipTests
```

### 4. 启动微服务

可以通过提供的脚本一键拉起或由 IDE 启动各 Application：

- **Windows 批处理启动**：
  ```cmd
  scripts\start-all.bat
  ```
- **PowerShell 启动**：
  ```powershell
  .\scripts\start-services.ps1
  ```
- **IDE 启动**：直接在 IntelliJ IDEA 或 VS Code 中运行各微服务的 `*Application.java`。

### 5. 启动前端应用
```bash
cd fishhub-vue3
npm install
npm run dev
```
启动成功后访问：`http://localhost:5173`。

---

## 🧪 自动化测试

- **运行全项目单元测试套件**：
  ```bash
  mvn test
  ```
- **运行指定服务单测**：
  ```bash
  mvn test -pl fishhub-user/fishhub-user-biz
  ```

---

## 📖 生产部署指南

关于 Nginx 反向代理、Sentinel 限流规则导入与生产环境变量配置，请参阅 [deploy/README.md](file:///D:/AAAPorject/main/fishhub/deploy/README.md)。
