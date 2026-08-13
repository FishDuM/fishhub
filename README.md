# FishHub

FishHub 是一个基于 Spring Boot 3、Spring Cloud Alibaba 和 Vue 3 的社区项目。浏览器只访问前端和 Gateway，业务服务通过 Nacos 注册发现互相调用。

## 本地环境

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8、Redis（含 RedisBloom 模块）、RocketMQ、Canal、Elasticsearch 7.3、Cassandra、MinIO、Nacos、ZooKeeper
- XXL-JOB 2.4.1

当前开发配置默认从 `192.168.0.100:8848` 连接 Nacos，中间件连接信息由各模块的 `bootstrap.yml`、Nacos配置和 `leaf.properties` 共同提供。XXL-JOB 使用的 MySQL 也应指向 `192.168.0.100:3306`。不要按 `localhost` 假设启动中间件。

笔记点赞、收藏使用 Redis Set/ZSet 缓存互动状态和最近操作记录，数据对齐使用 Redis Set 做每日变更去重。评论点赞缓存使用 RedisBloom 的 `BF.EXISTS`、`BF.ADD`、`BF.MADD` 等命令，因此 Redis 必须加载 RedisBloom 模块；当前代码不依赖 REDIS-ROARING。

## 初始化数据库

`sql/create.sql` 可直接初始化 `fishhub`、`xxl_job` 和 `leaf` 三个数据库：

```bash
mysql -h 192.168.0.100 -u root -p < sql/create.sql
```

清空 MySQL 后应同时清理 Redis 和 Elasticsearch 中的旧业务数据。`note`、`user` 索引不存在时搜索接口会返回空结果；后续文档写入会自动创建索引，Canal 和数据对齐任务负责持续同步。

## 服务端口

| 服务 | 端口 |
|---|---:|
| Gateway | 8000 |
| Auth | 8080 |
| OSS | 8081 |
| User | 8082 |
| KV | 8084 |
| Distributed ID | 8085 |
| Note | 8086 |
| User Relation | 8087 |
| Count | 8090 |
| Data Align | 8091 |
| Search | 8092 |
| Comment | 8093 |
| XXL-JOB Admin | 7777 |

除 Gateway 外，这些端口都属于内部服务，不应直接暴露到公网。

## 构建和启动

构建后端：

```bash
mvn clean package -DskipTests
```

`ops/fishhubctl.sh` 在 macOS 上通过 launchd 托管后端服务。脚本还需要 `xxl-job/xxl-job-admin/target/xxl-job-admin-2.4.1.jar` 已经构建完成。

```bash
zsh ops/fishhubctl.sh start
zsh ops/fishhubctl.sh status
zsh ops/fishhubctl.sh stop
```

前端启动方式见 `fishhub-vue3/README.md`。

## 测试

普通单元测试随 Maven 执行。依赖 Redis、Cassandra 等共享中间件的集成测试默认关闭，需要显式开启：

```bash
FISHHUB_RUN_INTEGRATION_TESTS=true mvn test
```
