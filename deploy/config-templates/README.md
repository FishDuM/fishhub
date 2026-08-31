# 微服务配置模板（开发与生产）

> **安全机制**：
> 1. 项目根目录 `.gitignore` 严格忽略所有真实的 `application-dev.yml` 与 `application-prod.yml`，绝不将个人真实数据库/Redis密码提交到 Git 仓库；
> 2. 本目录下的 `*.example.yml` 均为**脱敏后的公共模板**（仅包含占位符和环境变量注入定义）。

---

### 1. 本地开发配置模板 (`deploy/config-templates/dev/`)
- 供本地快速启动微服务使用；
- 可通过执行根目录 `scripts/init-configs.bat`（或 `scripts/init_configs.py`）一键复制生成各微服务的 `application-dev.yml`；
- 生成后的 `application-dev.yml` 会被 `.gitignore` 自动忽略，您可在本地随意修改连接信息和密码。

### 2. 生产环境配置模板 (`deploy/config-templates/*/application-prod.example.yml`)
- 部署时将对应文件复制为各模块的 `application-prod.yml`（或挂载/打入容器镜像）；
- 所有敏感连接信息（数据库地址、密码、Redis 等）均通过环境变量注入，具体变量清单见 `deploy/README.md`。
