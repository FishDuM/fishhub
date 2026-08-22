# 生产配置模板

`application-prod.yml` 被 .gitignore 忽略（含密码，不入仓库），本目录是**可版本化的模板**。

部署时把对应文件复制到各模块 `src/main/resources/config/application-prod.yml`（或挂载/打入镜像），
所有连接信息用环境变量注入，变量清单见 deploy/README.md 第四点。

以 `SPRING_PROFILES_ACTIVE=prod` 启动（覆盖 application.yml / bootstrap.yml 里的 dev）。
