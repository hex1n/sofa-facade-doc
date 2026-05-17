# 部署说明

## 部署形态

第一版按单体服务部署：

- 一个 Spring Boot JAR。
- 一个 SQLite 数据库文件。
- 一个服务端 `dataDir`，用于保存 Git 仓库缓存、worktree、SQLite 和扫描结果。
- 一个 YAML 配置文件，声明 admin/team/project token、项目仓库、分支规则、源码目录和测试环境 `directUrl`。

平台服务器需要能访问 Git 仓库和测试环境 SofaRPC provider。业务测试环境服务器只需要照常部署业务 JAR，不需要安装本平台。

## JAR 运行

```bash
mvn -DskipTests package
java -Dsofa.doc.config=/etc/sofa-facade-doc/config.yml \
  -jar target/sofa-facade-doc-0.1.0-SNAPSHOT.jar
```

健康检查：

```bash
curl http://127.0.0.1:8080/api/health
```

其他 `/api/**` 接口需要 `Authorization: Bearer <token>`。

## Token 与团队隔离

第一版不接账号体系，但 token 分三类：

- `auth.adminTokens`：平台管理员，可查看和修改全部项目配置，也可访问全局 YAML 原文接口。
- `teams.<teamId>.tokens`：团队 token，只能看到和修改 `projects.<projectId>.team` 等于本团队的项目与分支配置，也只能访问这些项目的文档、扫描、用例和测试调用。团队 token 新建本团队项目时可以填写 `repo` 和项目 `tokens`，项目创建后 `team`、`repo`、项目 `tokens` 锁定，只有 admin token 可改。
- `projects.<projectId>.tokens`：项目 token，只能访问单个项目文档和调用，不能进入配置入口。

页面左侧“项目与分支配置”入口使用结构化项目配置 API。团队 token 打开后只会返回本团队项目，后端保存时会再次校验 team 归属，不能通过改请求体去写其他团队项目，也不能在项目创建后改写 `repo` 或项目 `tokens`。

## Docker 运行

镜像内包含运行时需要的 JRE、`git` 和 `openssh-client`：

```bash
docker build -t sofa-facade-doc:latest .
docker run --rm -p 8080:8080 \
  -v /srv/sofa-facade-doc/config.yml:/app/config.yml:ro \
  -v /srv/sofa-facade-doc/data:/data/sofa-facade-doc \
  sofa-facade-doc:latest
```

镜像默认读取 `/app/config.yml`，也可以通过 `-e SOFA_DOC_CONFIG=/path/to/config.yml` 覆盖。配置中的 `server.dataDir` 建议指向挂载目录，例如 `/data/sofa-facade-doc`。

仓库提供 `.dockerignore`，避免把本地构建产物、运行数据和本地密钥配置打进镜像构建上下文。

## Git 拉取方式

平台会在 `dataDir` 下自动维护仓库缓存和分支 worktree：

- `repos/<project>`：项目 Git 缓存。
- `worktrees/<project>/<branch>`：扫描指定分支时使用的 detached worktree。
- 每次手动扫描会先 `fetch --prune origin`，再 checkout 到目标分支的最新 `origin/<branch>`。

SSH 仓库配置：

```yaml
git:
  sshKeyPath: "/run/secrets/git_deploy_key"
```

HTTPS token 仓库配置：

```yaml
git:
  tokenEnv: "SOFA_DOC_GIT_TOKEN"
```

启动服务时注入环境变量：

```bash
export SOFA_DOC_GIT_TOKEN=xxxxx
```

## 配置重载

修改 YAML 后，可用 admin token 触发重载：

```bash
curl -X POST http://127.0.0.1:8080/api/admin/reload-config \
  -H "Authorization: Bearer <admin-token>"
```

重载适合调整 token、项目、团队、分支规则、source/resource roots、`directUrl` 等运行配置。`branchOverrides` 可使用精确分支名，也可用 `feature/*` 这类通配符把一批开发分支绑定到同一个测试环境；多个通配符按配置顺序叠加，精确分支配置最后覆盖。监听端口和 SQLite `dataDir` 属于进程级配置，修改后建议重启服务。

## 扫描与快照

第一版使用手动扫描：

```bash
curl -X POST "http://127.0.0.1:8080/api/projects/loan/branches/scan?branch=develop" \
  -H "Authorization: Bearer <token>"
```

项目级扫描会扫描配置匹配到的所有分支：

```bash
curl -X POST "http://127.0.0.1:8080/api/projects/loan/scan" \
  -H "Authorization: Bearer <token>"
```

项目级扫描按分支独立返回 `success`、`skipped`、`unchanged` 或 `failed`。某个分支 Git、sourceRoot 或 facade 识别失败时，只影响该分支结果，不覆盖上一版成功快照，也不阻断其他分支。
失败报告会按项目/分支保留最近 20 条。使用 project token 时，失败详情会降级为通用提示；使用 admin token 时可看到排障信息，返回前会清理常见 token/HTTPS 凭证形态。

查看最近扫描报告：

```bash
curl "http://127.0.0.1:8080/api/projects/loan/scan-reports?branch=develop&limit=20" \
  -H "Authorization: Bearer <token>"
```

扫描会按以下顺序减少无效快照：

- commit 未变化：直接复用最新快照。
- commit 变化但没有接口相关文件变化：记录 scan report，不生成快照。
- 接口结构 hash 未变化：记录 scan report，不生成快照；结构 hash 不包含 commit，只反映接口文档结构。
- 接口结构变化：生成新快照并刷新搜索索引。

## 部署后 Smoke

JAR 或 Docker 启动后，可以用仓库内零依赖脚本做一次运行时 smoke。它会验证静态 UI 文件、鉴权、项目/分支列表、服务列表、方法详情和 Markdown API：

```bash
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 \
SOFA_DOC_TOKEN=<token> \
SOFA_DOC_PROJECT=loan \
SOFA_DOC_BRANCH=develop \
node scripts/runtime-smoke.mjs
```

如果用 admin/team token 验证配置入口，可以额外加上：

```bash
SOFA_DOC_SMOKE_CONFIG=1 \
SOFA_DOC_CONFIG_PROJECTS=loan \
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 \
SOFA_DOC_TOKEN=<team-token> \
SOFA_DOC_PROJECT=loan \
SOFA_DOC_BRANCH=develop \
node scripts/runtime-smoke.mjs
```

如果用 project token 验证不能进入配置入口：

```bash
SOFA_DOC_SMOKE_CONFIG_FORBIDDEN=1 \
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 \
SOFA_DOC_TOKEN=<project-token> \
SOFA_DOC_PROJECT=loan \
SOFA_DOC_BRANCH=develop \
node scripts/runtime-smoke.mjs
```

团队隔离还可以用 `SOFA_DOC_FORBIDDEN_PROJECT=<other-project>` 断言当前 token 访问其他团队项目时返回 403。

如果目标分支还没有快照，可以先在页面点击“刷新当前分支”，或在 smoke 时允许脚本触发一次扫描：

```bash
SOFA_DOC_SMOKE_SCAN=1 \
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 \
SOFA_DOC_TOKEN=<token> \
SOFA_DOC_PROJECT=loan \
SOFA_DOC_BRANCH=develop \
node scripts/runtime-smoke.mjs
```

更完整的真实项目验收可使用：

```bash
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 \
SOFA_DOC_TOKEN=<admin-token> \
SOFA_DOC_PROJECT=loan \
SOFA_DOC_BRANCH=develop \
SOFA_DOC_BASELINE=main \
node scripts/real-project-acceptance.mjs
```

该脚本会检查健康状态、项目可见性、分支匹配、接口列表、方法详情、Markdown、搜索、可选 Diff、参数结构校验，并可用 `SOFA_DOC_ACCEPTANCE_CASE=1` 创建后删除一条临时共享用例。默认不触发真实 SofaRPC 调用；只有显式设置 `SOFA_DOC_ACCEPTANCE_PROBE=1` 或 `SOFA_DOC_ACCEPTANCE_INVOKE=1` 时才会访问测试 RPC 服务，调用时必须提供 `SOFA_DOC_ARGS_JSON`。

## 真实项目验收

接入真实 SOFABoot 项目时，建议按 [真实项目验收 Runbook](real-project-validation.md) 执行，并从 [真实项目配置模板](real-project-config-template.yml) 复制项目配置。验收记录应包含 Git 拉取、扫描、发布识别、Markdown/字段树、搜索、diff、连通性、SofaRPC 调用和用例管理的证据。
