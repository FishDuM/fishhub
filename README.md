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

`sql/create.sql` 是**单文件全量**脚本，按顺序包含：5 个业务库 `fishhub_user/note/comment/relation/count`（含全部业务表）、旧共享库 `fishhub`（历史遗留，全新安装可跳过）、`xxl_job`（调度中心）、`leaf`（发号）、存量数据迁移与对账校验（全新安装自动空跑）以及测试账号数据（可选）。直接整文件执行即可：

```bash
mysql -h 192.168.0.100 -u root -p < sql/create.sql
```

清空 MySQL 后应同时清理 Redis 和 Elasticsearch 中的旧业务数据。Search 服务不会在启动时创建、重建或全量写入 Elasticsearch；启动前须由外部中间件手动创建 `note`、`user` 索引并完成全量同步。服务启动后仅由 Canal 消费 `t_note`、`t_user`、`t_note_count`、`t_user_count` 的增量变更写入 Elasticsearch。

## 服务端口

| 服务 | 端口 | 说明 |
|---|---:|---|
| Gateway | 8000 | 网关（/auth、/user、/note、/relation、/comment、/oss、/search 路由） |
| User | 8082 | 用户/认证/角色权限（auth 已并入） |
| OSS | 8081 | 对象存储（MinIO 上传/访问） |
| Note | 8086 | 笔记/话题/点赞/收藏 |
| Comment | 8093 | 评论/评论点赞/热度 |
| User Relation | 8087 | 关注/粉丝 |
| Count | 8090 | 计数 + XXL-JOB executor（data-align 已删除，对账职责归 count） |
| KV | 8084 | Cassandra 正文存储（独立扩容） |
| Search | 8092 | ES 搜索（Canal 增量，跨库只读 JOIN 组装索引） |
| Distributed ID | 8085 | Leaf 发号（segment + snowflake，保留） |
| XXL-JOB Admin | 7777 | 调度中心 |
除 Gateway 外，这些端口都属于内部服务，不应直接暴露到公网。各业务库（fishhub_user/note/comment/relation/count）相互独立，跨服务数据一律经 Feign / RocketMQ 访问。

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

普通单元测试随 Maven 执行。本机（Windows + JDK 21）Surefire fork 启动偶发崩溃，如遇 `forked VM terminated` 请用 `mvn test -DforkCount=0` 进程内运行。依赖 Redis、Cassandra 等共享中间件的集成测试默认关闭，需要显式开启：

```bash
FISHHUB_RUN_INTEGRATION_TESTS=true mvn test
```
