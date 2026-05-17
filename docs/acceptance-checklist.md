# MVP 验收清单

本文档用于把 PRD 和实施计划里的验收项映射到当前仓库中的证据。当前实现形态为 Java / Spring Boot 单体服务，不依赖 `sofarpc-cli`。

## 验证命令

当前基础验证命令：

```bash
mvn -q test
mvn -q -DskipTests package
node --check src/main/resources/static/app.js
node --check src/main/resources/static/config-workbench.js
node --check scripts/ui-logic-test.mjs
node --check scripts/real-project-acceptance.mjs
node scripts/ui-logic-test.mjs
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 SOFA_DOC_TOKEN=<token> node scripts/runtime-smoke.mjs
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 SOFA_DOC_TOKEN=<token> SOFA_DOC_PROJECT=<project> SOFA_DOC_BRANCH=<branch> node scripts/real-project-acceptance.mjs
```

Docker 镜像构建命令：

```bash
docker build -t sofa-facade-doc:latest .
```

当前机器 Docker CLI 存在，但 Docker daemon 未运行时无法执行镜像构建。出现 `Cannot connect to the Docker daemon` 时，不应把 Docker build 记为已验证。

## PRD 验收标准

| 验收项 | 状态 | 当前证据 |
| --- | --- | --- |
| 可用 YAML 配置一个或多个 Git 项目 | 已验证 | `docs/config-example.yml`；`AppConfigLoader`；`ApiIntegrationTest` 使用临时 YAML 启动完整应用 |
| 可按团队隔离项目配置和文档访问 | 已验证 | `teams` + `projects.<id>.team`；`AuthScope`；`ConfigurationAuthorization`；`GET/PUT /api/config/projects`；`POST /api/config/projects/validate`；`ApiIntegrationTest` 覆盖 loan/card 两团队互不可见、project token 不能进入配置入口、team token 不能写其他团队项目、不能把本团队项目迁移到其他团队、不能新增归属其他团队的项目、team token 可创建本团队项目且创建后不能改 `repo`/项目 `tokens`，并覆盖配置保存前校验接口 |
| 可通过 SSH key 或 HTTPS token 拉取源码 | 部分验证 | `GitService` 支持 `sshKeyPath` 和 `tokenEnv`；本地测试覆盖 Git clone/fetch/worktree；真实私服验证步骤见 `docs/real-project-validation.md`；`scripts/real-project-acceptance.mjs` 可在真实配置上产出验收证据 |
| 可手动扫描指定项目/分支 | 已验证 | `POST /api/projects/{project}/branches/scan?branch=...`；`ApiIntegrationTest` 覆盖 slash branch |
| 可项目级扫描多分支且部分失败不阻塞 | 已验证 | `POST /api/projects/{project}/scan`；`ApiIntegrationTest` 覆盖 `feature/no-facade` 失败仍返回部分成功 |
| 可识别 `@SofaService`、SOFA XML、包规则候选 interface | 已验证 | `SourceScannerTest` 覆盖注解发布、XML 发布、`facadePackages` 候选；同时覆盖未配置 `sourceRoots/resourceRoots/facadePackages` 时自动发现 roots，并仅依赖发布配置识别已发布接口 |
| 可展示接口列表、方法详情、字段树和 Markdown | 已验证 | 静态 UI；`ApiIntegrationTest` 覆盖静态资源托管、service/method/markdown API；`config-workbench.js` 承载项目与分支配置入口；浏览器已验证主工作台 |
| 可复制/下载 Markdown | 已验证 | `app.js` 中 `copyMd`、`downloadMd`；Markdown API 和渲染由 `ApiIntegrationTest` 覆盖 |
| 可生成请求 JSON 骨架和返回结构示例 | 已验证 | `SourceScannerTest` 覆盖 request example、return tree、泛型返回和源码缺失降级 |
| 可保存、读取项目共享调用用例 | 已验证 | `StoreService` cases 表；`ApiIntegrationTest` 覆盖保存、更新、读取、删除；`scripts/real-project-acceptance.mjs` 支持 `SOFA_DOC_ACCEPTANCE_CASE=1` 创建并删除临时用例 |
| 可对配置的测试 `directUrl` 发起 `bolt + hessian2` 调用 | 已验证 | `InvokeServiceSofaRpcIntegrationTest` 启动本地 SofaRPC provider，覆盖泛化调用成功 |
| 可显示连通性、调用耗时、异常和 JSON 响应 | 已验证 | `InvokeService` 的 probe/invoke result；`InvokeServiceSofaRpcIntegrationTest` 覆盖 probe、业务异常、协议/解码失败、响应归一化 |
| 可生成结构化 diff，并区分 Breaking / Non-breaking / Info | 已验证 | `DiffService`；`ApiIntegrationTest` 覆盖字段注释变化为 `Non-breaking` |
| 可搜索 service、method、字段和注释 | 已验证 | SQLite FTS5；`ApiIntegrationTest` 覆盖中文字段注释、FQN/方法、字段路径和纯符号查询 |

## 实施计划交付项

| 模块 | 状态 | 当前证据 |
| --- | --- | --- |
| 项目骨架、配置加载、认证、SQLite 初始化 | 已验证 | Spring Boot app、`AppConfigLoader`、`AuthFilter`、`StoreDatabase` 初始化 schema；`ApiIntegrationTest` 覆盖鉴权、reload 和团队配置隔离 |
| Dockerfile 内置 `git` | 已实现，待镜像构建验证 | `Dockerfile` 安装 `git openssh-client`，默认 `SOFA_DOC_CONFIG=/app/config.yml`；本机 Docker daemon 未运行，未执行 build |
| Git cache/worktree、多分支 include/exclude | 已验证 | `GitServiceTest`；`ApiIntegrationTest` 覆盖 slash branch 和排除分支 |
| `branchOverrides` 运行配置覆盖 | 已验证 | 支持精确分支和 `*` / `?` 通配符；`ApiIntegrationTest` 覆盖通配符 directUrl 与精确分支覆盖 |
| Java 源码模型、注释、泛型、内部类、枚举 | 已验证 | `SourceRootResolver` 负责配置/约定根目录解析；`JavaSourceIndexer` 负责 Java source index；`JavaTypeResolver` 负责类型解析并支持 wildcard import；`FacadePayloadTreeBuilder` 负责字段树/泛型/继承展开；`JavaAnnotationReader` 和 `JavaCommentReader` 负责注解/注释读取；`JavaCommentReaderTest` 与 `SourceScannerTest` 覆盖单参数、多参数、重载、泛型返回、内部类、枚举、throws、wildcard import 和注释 |
| 报文字段质量规则 | 已验证 | `PayloadFieldRules` 负责字段包含、JSON 名称、必填和约束文案；`JavaAnnotationReaderTest` 覆盖简单/全限定注解与属性读取；`PayloadFieldRulesTest` 覆盖注释必填推断、`@JsonProperty`、Bean Validation 约束、`@JsonIgnore` 和非 payload 字段过滤；`SourceScannerTest` 覆盖完整扫描输出 |
| SOFA 发布配置识别 | 已验证 | `SourceScannerTest` 覆盖 `@SofaService`、XML `<sofa:service>`、`<sofa:binding.bolt>`、占位符和 `springProfiles`；`SofaAnnotationPublishParser` 负责注解发布记录；`SofaXmlPublishParser` 负责 XML 发布记录和 `application*.properties/yml` 占位符解析，`SofaXmlPublishParserTest` 覆盖 profile yaml、默认值、source location、未解析占位符 |
| 快照、结构 hash、diff、FTS | 已验证 | `ScanPlanner` 负责扫描状态机；`DocumentStructureHasher` 负责稳定结构 hash；`SnapshotStore` 负责快照，`SearchIndexStore` 负责 FTS；`DocumentStructureHasherTest` 覆盖 commit/generatedAt 不影响 hash、接口字段变化影响 hash；`StoreModulesTest` 覆盖快照、FTS、扫描报告和用例存储；`ApiIntegrationTest` 覆盖 commit 未变化、路径未命中、hash 未变化、hash 变化 |
| Markdown 与 JSON 骨架 | 已验证 | `MarkdownRenderer`；`SourceScannerTest` 和 `ApiIntegrationTest` 覆盖字段表、发布记录、JSON 骨架 |
| SofaRPC 泛化调用和校验 | 已验证 | `GenericArgumentConverter` 负责请求转换，`InvocationValidator` 负责校验，`GenericResponseNormalizer` 负责响应归一化，`SofaRpcGenericClient` 负责 probe/transport；`InvokeServiceTest` 覆盖 DTO/枚举/List/Map/日期/数字转换、校验和响应归一化；集成测试覆盖本地 provider |
| 用例管理和接口漂移提示 | 部分验证 | API 保存/更新/删除已由 `ApiIntegrationTest` 覆盖；浏览器 UI 中漂移提示已实现，缺少自动化 UI 测试 |
| Web UI 核心工作流 | 部分验证 | `ApiIntegrationTest` 覆盖静态资源托管和关键前端交互脚本入口；`scripts/ui-logic-test.mjs` 加载真实 `config-workbench.js` + `app.js` 覆盖路由选中、团队配置入口、分支切换同 methodId 刷新、缺失 method 清空；`scripts/runtime-smoke.mjs` 可验证部署后静态 UI/API 主链路，并可用 `SOFA_DOC_SMOKE_CONFIG=1`、`SOFA_DOC_CONFIG_PROJECTS`、`SOFA_DOC_SMOKE_CONFIG_FORBIDDEN=1`、`SOFA_DOC_FORBIDDEN_PROJECT` 校验配置入口和团队隔离 API；`scripts/real-project-acceptance.mjs` 可产出真实项目主链路验收记录；浏览器已验证主要页面；缺少真正浏览器点击式端到端脚本 |

## 尚未完成或证据不足

1. 真实业务 SOFABoot 项目验收尚未执行。当前只有仓库内 fixture 和本地 SofaRPC provider；执行步骤见 `docs/real-project-validation.md`，配置模板见 `docs/real-project-config-template.yml`。
2. Docker 镜像构建尚未执行。原因是当前机器 Docker daemon 未运行。
3. UI 端到端自动化覆盖不足。已有静态资源 smoke 测试、前端逻辑测试、部署后 runtime smoke 脚本和浏览器手工验证，但还没有真正浏览器点击式端到端脚本。

在这些缺口补齐前，不应把总目标标记为完成。
