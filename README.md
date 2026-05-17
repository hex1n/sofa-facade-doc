# sofa-facade-doc

独立部署的 SOFABoot / SofaRPC facade 接口文档与测试调用平台。

本项目不依赖 `sofarpc-cli`。第一版目标是作为单体 Web 服务部署在内网服务器上，通过只读 Git 凭证拉取业务项目源码，自动识别 SOFA service / facade 接口，生成 Markdown 文档、请求/返回结构示例、分支 diff，并支持对开发/测试环境 `bolt + hessian2` 服务发起直接调用。

Web UI 提供 Markdown、字段树、JSON 示例、结构化 diff 和调用面板；字段树按入参/出参分组，支持层级折叠，并支持刷新当前分支或刷新项目下全部匹配分支。调用面板会显示当前分支实际 `directUrl`，并随发布记录选择展示最终 `uniqueId/version`。
项目级扫描按分支独立返回结果，某个开发分支失败时会标记为 `failed`，不影响其他分支生成或复用快照。
普通 project token 只展示降级后的失败提示，admin token 可查看扫描排障信息；失败扫描报告按项目/分支保留最近 20 条，并可在页面分支区查看最近扫描记录。
分支运行配置支持项目默认值、`branchOverrides` 通配符覆盖和精确分支覆盖，适合把 `feature/*`、`develop`、`test` 分别绑定到不同测试环境。
admin token 登录后可在页面左侧“项目与分支配置”入口管理全部项目；team token 只能查看和修改本团队的项目/分支配置，也只能看到本团队项目的接口文档；team token 新建本团队项目时可填写 `repo` 和项目 `tokens`，项目创建后这两个字段与 `team` 归属锁定，只有 admin token 可改；project token 只用于查看被授权项目的文档和调用，不能进入配置入口。

## 文档

- [PRD](docs/prd.md)
- [实施计划](docs/implementation-plan.md)
- [MVP 验收清单](docs/acceptance-checklist.md)
- [配置样例](docs/config-example.yml)
- [真实项目验收 Runbook](docs/real-project-validation.md)
- [真实项目配置模板](docs/real-project-config-template.yml)
- [部署说明](docs/deploy.md)
- [UI 设计图](docs/assets/ui-mockup.png)

## 第一版边界

- 后端使用 Java / Spring Boot 单体服务。
- 只支持 Java 源码。
- 只支持开发/测试环境。
- 只支持 `directUrl` + `bolt` + `hessian2`。
- 不做账号体系，使用 admin token、team token 和 project token。
- 不做生产调用、审计日志、响应 mock server、外部 Wiki 同步、OpenAPI 输出。
- Web UI 支持接口 URL 分享、打开项目共享用例时做当前接口结构校验提示，并支持更新/删除用例。
- Web UI 支持 admin/team 在页面编辑项目与分支配置，后端按 `teams` 和 `projects.<id>.team` 做隔离；team token 创建项目后不能再改 `team`、`repo`、项目 `tokens`；全局 YAML 原文接口仅 admin 可用。

## SofaRPC 调用转换

调用接口接收页面里的统一 JSON：

- 单参数方法：`{"args": {...}}`
- 多参数方法：`{"args": [{...}, "operator"]}`

后端在调用 `GenericService#$genericInvoke` 前会按扫描到的字段树转换参数：

- DTO / 外部对象转为 SOFA Hessian `GenericObject`，并携带 Java 类型名。
- 枚举转为 `GenericObject(type)`，字段 `name` 为枚举名。
- `BigDecimal` / `BigInteger` / 数字 / 布尔 / 日期时间会尽量转为对应 Java 类型。
- `List` / `Set` / `Map` 会递归转换内部 DTO 或枚举。
- 调用时 `uniqueId/version` 优先使用选中的 SOFA 发布记录；没有发布记录时使用分支配置默认值。
- 调用前校验只用来挡明显结构错误：参数数量、JSON 类型、枚举值错误会阻断调用；必填缺失和未知字段只作为警告展示。
- 泛化响应会归一化成 JSON 友好的结构，SOFA 泛化对象会保留 `_type`，集合/Map/数组会展开为可读字段。
- DTO 源码缺失时保留 Java 类型名，不展开字段，并在 JSON 示例中标注 `sourceMissing`。

## 接口报文解析质量

接口报文结构基于 JavaParser 解析源码 AST，不依赖运行时反射。当前会按 Java / Hessian 字段名生成字段树和测试调用 JSON，以保证 SofaRPC 泛化调用能映射到 Java 对象字段。

已覆盖的质量规则：

- 递归展开源码范围内 DTO 字段，支持继承、泛型替换、集合、Map、枚举、内部类、循环引用和最大深度截断。
- 从 JavaDoc、字段 JavaDoc、字段行注释提取字段说明；没有注释时保留为未填写。
- 过滤 `static`、`transient`、`serialVersionUID`、`@JsonIgnore` 字段，避免把非报文字段写进文档。
- 识别 `@NotNull`、`@NotBlank`、`@NotEmpty` 和 `@JsonProperty(required = true)` 作为必填依据。
- 记录常见 Bean Validation 约束，如 `@Size`、`@Min`、`@Max`、`@DecimalMin`、`@DecimalMax`、`@Pattern`。
- 记录 `@JsonProperty` 的 Jackson 名称作为展示信息，但不替换 SofaRPC 调用使用的 Java 字段名。

当前不做依赖 jar 反编译；如果 DTO 源码不在 `sourceRoots` 中，会显示 `sourceMissing` 并保留类型名。

## SOFABoot 发布形态

扫描规则按 SOFAStack 官方文档覆盖第一版需要的服务发布入口：

- 注解发布：识别 `@SofaService` / `@SofaServiceBinding(bindingType = "bolt")`，包含 `interfaceType`、`uniqueId`、`version` 等信息。
- XML 发布：识别 `<sofa:service interface="...">` 和其下的 `<sofa:binding.bolt/>`。
- XML 属性支持从 `application.yml`、`application.properties`、`application-{springProfiles}.yml/properties` 解析 `${key}` / `${key:default}` 占位符；无法解析时保留原值并标记发布记录不完整。

参考：

- [Bolt 协议基本使用](https://sofastack.github.io/sofastack.tech/projects/sofa-rpc/bolt-usage/)
- [SOFABoot 环境 XML 配置使用](https://www.sofastack.tech/projects/sofa-rpc/programing-sofa-boot-xml/)
- [Use annotation in SOFABoot](https://www.sofastack.tech/en/projects/sofa-rpc/programing-sofa-boot-annotation/)

## 本地运行

```bash
mvn test
mvn -DskipTests package
java -Dsofa.doc.config=/path/to/config.yml -jar target/sofa-facade-doc-0.1.0-SNAPSHOT.jar
```

然后打开 `http://127.0.0.1:8080`，输入配置中的 token。

如果配置里使用 `server.listen: "127.0.0.1:18080"`，服务会监听对应端口。

Docker 镜像默认读取 `/app/config.yml`，部署时挂载配置文件和 `dataDir` 即可；也可用 `SOFA_DOC_CONFIG` 环境变量覆盖配置路径。

部署后可用零依赖 smoke 脚本验证静态 UI、鉴权、项目/分支、服务、方法详情和 Markdown API：

```bash
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 \
SOFA_DOC_TOKEN=<token> \
SOFA_DOC_PROJECT=loan \
SOFA_DOC_BRANCH=develop \
node scripts/runtime-smoke.mjs
```

接入真实业务项目时，可用验收脚本自动产出 Markdown/JSON 证据；默认不会触发 SofaRPC 调用：

```bash
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 \
SOFA_DOC_TOKEN=<admin-token> \
SOFA_DOC_PROJECT=loan \
SOFA_DOC_BRANCH=develop \
SOFA_DOC_BASELINE=main \
node scripts/real-project-acceptance.mjs
```

## 验证覆盖

当前测试覆盖源码扫描、Git 多分支刷新、项目级扫描部分失败、快照跳过、Markdown、搜索、diff、认证、用例管理、参数校验和 SofaRPC 调用转换。扫描 fixture 覆盖单参数、多参数、重载、泛型返回、枚举、内部类和 DTO 源码缺失降级。

`InvokeServiceSofaRpcIntegrationTest` 会在测试进程内启动一个本地 `bolt + hessian2` SofaRPC provider，验证泛化调用成功、业务异常回传、TCP probe 和响应 JSON 归一化。

前端零依赖逻辑测试：

```bash
node scripts/ui-logic-test.mjs
```

该脚本加载真实 `config-workbench.js` 和 `app.js`，验证路由选中接口、团队配置入口、切换到存在同 methodId 的分支时自动刷新详情，以及切换到缺失该接口的分支时清空详情。
