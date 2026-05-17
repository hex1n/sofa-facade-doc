# SOFABoot Facade 文档与调用平台 PRD

## 1. 背景

公司 SOFABoot / SofaRPC 项目每次新增或修改 facade 接口，都需要人工编写接口文档。人工维护存在几个问题：

- 文档容易滞后于代码。
- 入参、出参字段多时，递归字段和泛型结构容易漏写。
- 测试同学需要另找工具构造 SofaRPC 调用。
- 多个开发分支同时存在时，很难知道接口变更点。

本项目建设一个可部署到内网服务器的独立平台。平台通过只读 Git 凭证拉取业务项目源码，自动识别 SOFA service / facade 接口，生成 Markdown 文档和 JSON 示例，并支持对开发/测试环境的 `directUrl` 发起 SofaRPC 调用。

## 2. 目标

- 支持任意 Java SOFABoot 项目，业务项目零代码改造。
- 支持多项目、多分支，分支可固定配置或通配符匹配。
- 自动识别 `@SofaService`、SOFA XML 发布配置和配置包路径下的 facade 候选接口。
- 从 Java 源码注释生成接口说明、字段说明、必填提示和 Markdown 文档。
- 递归展开入参、出参 DTO，支持常见泛型、集合、Map、枚举、内部类。
- 支持复制/下载 Markdown。
- 支持请求 JSON 骨架、返回结构示例、保存项目共享调用用例。
- 支持开发/测试环境 `directUrl` 的 `bolt + hessian2` 调用。
- 支持分支/快照之间的结构化接口 diff。

## 3. 非目标

- 不依赖 `sofarpc-cli`，不把它作为库或子模块。
- 不要求业务项目引入 starter、SDK 或修改代码。
- 不支持生产环境调用。
- 不做账号体系，第一版仅使用 token。
- 不做调用审计，不保存自动调用历史。
- 不做响应 mock server。
- 不做外部 Wiki / Confluence / GitLab Pages 同步。
- 不输出 OpenAPI / Swagger。
- 不支持 Kotlin / Scala facade。
- 不解析 Maven 依赖源码包或三方 jar。
- 不做暗色模式、国际化、批量调用。

## 4. 用户与权限

第一版只有 token 权限模型。

- Admin token：可重载配置、访问全部项目、触发任意项目扫描，并可管理全部团队和项目配置。
- Team token：绑定到 `teams.<teamId>.tokens`，只能访问和管理归属本团队的项目与分支配置，也只能查看本团队项目的文档、扫描、用例和测试调用。团队自建项目时可填写 `repo` 和项目 `tokens`；项目创建后 `team`、`repo`、项目 `tokens` 锁定，只有 admin token 可改。
- Project token：只能访问绑定项目，可查看文档、触发该项目扫描、复制/下载 Markdown、保存用例、发起测试调用；不能进入项目配置入口。

隔离要求：

- 项目通过 `projects.<projectId>.team` 归属团队。
- Team token 不能看到其他团队项目列表、接口文档、分支、扫描报告、用例和调用入口。
- Team token 不能新增/修改/迁移其他团队项目配置，也不能在项目创建后改写本团队项目的 `team`、`repo`、项目 `tokens`。
- Admin token 可用全局 YAML 入口排障；普通团队配置入口必须是结构化项目配置，不能暴露整份 YAML。

HTTP API 使用：

```http
Authorization: Bearer <token>
```

前端首次进入时输入 token，保存到 `sessionStorage`。

## 5. 部署形态

第一版是单体 Web 服务：

- 后端、Web UI、扫描任务、SQLite、SofaRPC 调用客户端在同一服务内。
- 支持 Docker 部署，也支持直接启动二进制。
- 依赖系统 `git` 命令；Docker 镜像内置 `git`。
- 配置文件使用 YAML。
- SQLite 和 Git 缓存位于统一 `dataDir`。

目录建议：

```text
/data/sofa-facade-doc/
  app.db
  repos/
  worktrees/
```

备份要求：备份 `app.db` 和 YAML 配置即可；`repos/`、`worktrees/` 可重建。

## 6. 源码输入

平台服务器可配置只读 Git 凭证，自动 clone/fetch 业务仓库到平台自己的磁盘缓存。

支持两种 Git 凭证：

- SSH deploy key：YAML 配置 `sshKeyPath`。
- HTTPS token：YAML 配置 `tokenEnv`，token 从环境变量读取。

不把密钥正文写入 YAML。

## 7. 项目配置模型

项目支持：

- `team`
- `repo`
- `baselineBranch`
- `tokens`
- `sourceRoots`
- `resourceRoots`
- `facadePackages`
- `branchDefaults`
- `branches.include`
- `branches.exclude`
- `branches.maxMatched`
- `branchOverrides`

规则：

- `team` 可选；未配置时只有 admin token 和项目自身 token 可访问，team token 不会自动获得该项目权限。
- `teams` 中可配置团队名称和团队 token；团队 token 根据 `projects.<projectId>.team` 自动获得本团队项目权限。
- `sourceRoots/facadePackages/resourceRoots` 使用项目级默认，分支可覆盖。
- 未配置 `sourceRoots` 时自动查找所有 `src/main/java`。
- 未配置 `resourceRoots` 时自动查找所有 `src/main/resources`。
- 忽略 `src/test/java`、`src/test/resources`。
- 忽略 `target/`、`build/`、`generated-sources/`、`.git/`、隐藏目录。
- `facadePackages` 可选；发布配置完整时可完全依赖 `@SofaService` / XML。
- 项目级默认 `directUrl` 可被分支覆盖。
- `branchOverrides` 支持精确分支名和 `*` / `?` 通配符；先按配置顺序叠加通配符覆盖，最后叠加精确分支覆盖。
- 不抽象独立 environment 模型；分支可直接绑定测试环境 `directUrl`。

## 8. Facade 识别

识别优先级：

1. `@SofaService(interfaceType = XxxFacade.class)`。
2. `@SofaService` 未指定 `interfaceType` 时，如果实现类只有一个明确接口，则使用该接口。
3. `@Bean` 方法上的 `@SofaService(interfaceType = ...)`。
4. XML `<sofa:service interface="...">`。
5. `facadePackages` 命中的 public interface 候选。

状态展示：

- 已发布：来自 `@SofaService` 或 XML。
- 源码候选：来自 `facadePackages`，未发现发布配置。
- 发布配置不完整：识别到发布但缺接口、binding 或源码。
- 接口源码缺失：发布配置引用 interface，但源码找不到。

候选接口也允许调用。候选调用规则：

- service 名使用 interface 全限定名。
- binding 固定按 bolt。
- `uniqueId/version` 使用发布记录、分支默认、项目默认或空。

同一个 interface 多个发布记录时：

- 文档聚合为一个 service。
- 发布记录展示实现类、binding、uniqueId、version。
- 多个 bolt 发布记录不自动猜，用户选择或配置默认。

## 9. 发布配置解析

注解解析：

- 识别 `@SofaService`。
- 提取 `interfaceType`、`uniqueId`、`version`、binding 信息。
- 支持 `@Bean` 方法上带明确 `interfaceType` 的 `@SofaService`。
- 复杂无 interfaceType 的 `@Bean` 方法降级为无法确定接口。

XML 解析：

- 支持 `<sofa:service interface="..." unique-id="...">`。
- 支持 `<sofa:binding.bolt>`。
- 支持 `<sofa:global-attrs timeout="..." serialize-type="...">`。
- 提取 `unique-id`、`version`、bolt binding 参数。
- 复杂 profile、动态发布、编程式 API 发布先不识别。

占位符：

- 支持 `${...}` 简单替换。
- 读取 `application*.properties`、`application*.yml`、`application*.yaml`。
- 支持配置 `springProfiles`。
- 未指定 profile 时不猜多 profile 值。
- 无法解析时保留原值并标记 unresolved placeholder。

## 10. 源码注释规则

字段含义只来自源码注释，不依赖 `@ApiModelProperty` 或 `@Schema`。

支持：

- 字段 JavaDoc。
- 字段上一行 `//`。
- 字段同一行 `//`。
- interface JavaDoc。
- 方法 JavaDoc 主体。
- 方法 JavaDoc `@param`。
- 方法 JavaDoc `@return`。
- JavaDoc `@deprecated` 和 `@Deprecated`。

不支持：

- getter/setter 注释。
- package-info.java 包说明。
- 页面人工补充或覆盖字段说明。

缺失字段说明显示 `未填写`。

必填判断：

- 只根据注释关键词。
- 必填关键词：`必填`、`必传`、`不能为空`、`required`。
- 非必填关键词：`非必填`、`选填`、`可为空`、`optional`。
- 无关键词显示 `未知`。
- 不依赖 validation 注解。

## 11. 类型展开

支持：

- Java interface 方法重载。
- 源码参数名，缺失时使用 `arg0/arg1`。
- 常见容器泛型实参替换和递归展开。
- `Result<T>`、`PageResult<T>`、`List<T>`、`Map<String, T>`、`BaseRequest<T>` 等模式。
- 多模块同仓库 DTO 展开。
- 内部 class/interface/enum。
- 枚举常量展示，枚举注释可提取则展示。
- `throws` 类型列表展示，但不展开异常字段。
- `void` 返回显示为 `void`，无返回结构示例。

降级：

- 当前仓库源码找不到的三方/外部类型，只显示类型名，不展开。
- DTO 源码缺失时，文档只显示类型名；可提供高级 JSON 调用模式。
- 复杂泛型、通配符、递归泛型降级显示原始类型。

递归限制：

- 默认最大展开深度 5。
- 循环引用停止展开并标记。

JSON 骨架示例：

```json
{
  "manager": {
    "_note": "circularReference: com.company.User"
  }
}
```

## 12. Markdown 文档

每个方法生成一个 Markdown 页面/区块。

包含：

- 项目、分支、commit。
- 接口全限定名。
- 接口说明。
- 方法名、方法说明。
- 参数名、参数 Java 类型、JSON 输入类型、字段路径、字段说明、必填状态。
- 返回 Java 类型、JSON 类型、字段路径、字段说明。
- 枚举值表。
- `throws` 列表。
- 可复制 JSON 请求骨架。
- 返回结构示例，并标注“结构示例，非真实响应”。
- 发布状态：已发布、源码候选、发布配置不完整等。

不包含：

- `directUrl`。
- Git Web 跳转链接。
- 源码相对路径/行号。

平台页面渲染 Markdown，同时支持复制 Markdown 和下载 `.md`。

## 13. JSON 输入规范

页面展示统一 JSON 输入规范，并由后端转换为 SofaRPC 所需形态。

- `BigDecimal`：字符串 decimal，如 `"123.45"`。
- 日期类型：字符串。
- 枚举：枚举名字符串。
- DTO：普通 JSON object，内部调用时补类型信息。
- List：JSON array。
- Map：JSON object。

支持日期类型：

- `java.util.Date`
- `java.sql.Date`
- `java.time.LocalDate`
- `java.time.LocalDateTime`
- `java.time.LocalTime`

请求骨架使用类型友好的占位值。

多参数方法使用 JSON 数组作为调用输入：

```json
[
  {
    "orderNo": "string"
  },
  "operator"
]
```

单参数方法页面可友好展示对象。

## 14. 调用能力

第一版只支持：

- `directUrl`
- `bolt`
- `hessian2`
- SofaRPC Java 客户端泛化调用

不支持：

- 注册中心发现。
- 生产环境调用。
- 用户覆盖 `directUrl`。
- Java 进程兜底。
- 其他序列化协议。

调用目标：

- 只来自项目/分支配置。
- `targetAppName` 可选，项目默认、分支可覆盖。
- `uniqueId/version` 优先级：发布记录 > 分支默认 > 项目默认 > 空。

页面显示：

- 目标 directUrl。
- TCP 连通性：可连接、不可连接、超时。
- 调用状态。
- 耗时。
- 异常信息。
- JSON 化响应。

响应 Hessian 解码失败时：

- 展示错误摘要、协议状态、响应长度。
- 不展示二进制正文。

调用前校验：

- 非法 JSON 阻止。
- 参数数量不匹配阻止。
- 枚举值明显不合法阻止。
- 字段类型明显错误阻止。
- 未知字段默认警告但允许。
- 必填缺失只对注释关键词识别出的必填字段警告。
- 业务语义错误交给服务端。

不自动保存调用响应，不保存调用历史。只保存用户主动命名的请求用例。

## 15. 调用用例

保存的调用用例按项目共享，不做个人隔离。

维度：

- project
- branch
- service
- methodId
- case name
- note
- JSON args
- updatedAt

打开用例时做接口漂移提示：

- 参数类型变化。
- 字段删除/新增。
- 枚举值变化。

不自动修改旧 JSON。

## 16. 扫描与快照

第一版只支持手动扫描，不做定时扫描。

扫描入口：

- 分支页：刷新当前分支。
- 项目页：可刷新配置匹配到的分支。

分支匹配：

- 固定分支总是匹配。
- 通配符分支按远端最近更新时间匹配，最多 `maxMatched` 个。

扫描流程：

1. `git fetch`。
2. 解析分支/commit。
3. 与上次成功扫描 commit 比较。
4. 如果 commit 相同，不生成快照。
5. 如果 commit 不同，先做路径命中判断。
6. 变更命中 `sourceRoots` 下 Java 或 `resourceRoots` 下 Sofa XML / 配置时，执行扫描。
7. 扫描后计算结构 hash。
8. hash 与上次成功快照相同，不生成新快照。
9. hash 变化时生成新快照。

结构 hash 包含：

- service/method 签名。
- 参数名、参数类型、返回类型。
- DTO 字段路径、字段类型。
- 方法注释、字段注释、`@param`、`@return`。
- 枚举常量和枚举注释。
- Sofa service 发布信息。

实现类逻辑变化只触发检查；如果结构 hash 不变，不生成快照。

历史保留：

- 每分支最近 20 个成功结构快照。
- 失败扫描保留最近 20 条错误日志。
- 不支持手动指定历史 commit 扫描。

失败策略：

- Git 拉取失败、分支不存在、sourceRoot 不存在、完全没有识别到 facade：扫描失败。
- 项目级多分支扫描按分支独立返回结果；单个分支失败算部分成功，不阻断其他分支扫描。
- 少数 Java 文件解析失败：部分成功，生成可解析接口文档，并在扫描报告列出失败文件。
- 未发现发布配置但扫到候选接口：部分成功。
- 既没有发布配置也没有候选接口：失败。
- 失败不覆盖上一版成功快照。

普通 project token 看到简化错误；admin token 可看更详细日志，但不泄漏凭证。

## 17. Diff

diff 基于结构化接口快照，不直接展示 Git 文本 diff。

默认比较：

- 当前分支最新快照 vs `baselineBranch` 最新快照。
- 如果当前分支就是 `baselineBranch`，比较该分支最近两个结构快照。
- 快照不足时显示暂无可比较版本。

分类：

- Breaking：删除方法、参数类型变化、返回类型变化、字段删除、字段类型变化、枚举值删除。
- Non-breaking：新增方法、新增字段、字段注释变化、枚举值新增。
- Info：发布配置变化、方法说明变化。

## 18. 搜索

使用 SQLite FTS5。

搜索覆盖：

- service FQN。
- 方法名。
- 参数类型名。
- 返回类型名。
- 字段名。
- 字段注释。

点击搜索结果后打开对应 service/method，并高亮命中的字段或注释。

## 19. UI

中文界面，不做暗色模式。

布局：

- 左侧：项目、分支、facade/service 列表，显示已发布/候选/不完整状态。
- 中间：Markdown 文档、字段树、diff。
- 右侧或下方：调用面板，选择发布记录、编辑 JSON、执行、保存用例、查看响应。

字段树：

- 按参数/返回值 DTO 分组。
- 支持层级折叠。
- 默认展开第一层。
- Markdown 导出包含完整内容。

URL：

- 接口详情页 URL 可分享。
- URL 包含 project、branch、service、methodId。
- 无 token 时先输入 token，再进入同一接口。
- `methodId` 由 `methodName + paramTypes` 生成稳定 hash。

## 20. 数据存储

SQLite 存结构化数据，不以 Markdown 作为主数据。

主要数据：

- projects
- branch snapshots
- scan reports
- services
- publish records
- methods
- DTO field trees
- enum constants
- method comments
- field comments
- structure hash
- diff results
- saved cases
- FTS search index

Markdown 动态生成，可按 snapshot + methodId 做缓存。

## 21. HTTP API 草案

所有 API 都要求 Bearer token。

项目：

- `GET /api/projects`
- `GET /api/projects/{project}/branches`
- `POST /api/projects/{project}/scan`
- `GET /api/projects/{project}/scan-reports?branch=...&limit=...`
- `POST /api/projects/{project}/branches/scan?branch=...`
- `POST /api/projects/{project}/branches/{branch}/scan`，仅兼容不含 `/` 的简单分支名。

接口：

- `GET /api/projects/{project}/branches/services?branch=...`
- `GET /api/projects/{project}/methods/{methodId}?branch=...`
- `GET /api/projects/{project}/methods/{methodId}/markdown?branch=...`

搜索与 diff：

- `GET /api/projects/{project}/search?q=...`
- `GET /api/projects/{project}/diff?branch=...&base=...`

调用：

- `POST /api/projects/{project}/methods/{methodId}/validate?branch=...`
- `POST /api/projects/{project}/methods/{methodId}/invoke?branch=...&publish=...`
- `GET /api/projects/{project}/methods/{methodId}/probe?branch=...`

`publish` 为可选发布记录下标；不传时默认使用第一个完整的 bolt 发布记录，再降级到分支默认 `uniqueId/version`。

用例：

- `GET /api/projects/{project}/methods/{methodId}/cases?branch=...&service=...`
- `POST /api/projects/{project}/methods/{methodId}/cases?branch=...&service=...`
- `PUT /api/projects/{project}/cases/{caseId}`
- `DELETE /api/projects/{project}/cases/{caseId}`

管理：

- `POST /api/admin/reload-config`

## 22. 验收标准

MVP 完成时，应满足：

- 可用 YAML 配置一个或多个 Git 项目。
- 可通过 SSH key 或 HTTPS token 拉取源码。
- 可手动扫描指定项目/分支。
- 可识别 `@SofaService`、SOFA XML、包规则候选 interface。
- 可展示接口列表、方法详情、字段树和 Markdown。
- 可复制/下载 Markdown。
- 可生成请求 JSON 骨架和返回结构示例。
- 可保存、读取项目共享调用用例。
- 可对配置的测试 `directUrl` 发起 `bolt + hessian2` 调用。
- 可显示连通性、调用耗时、异常和 JSON 响应。
- 可生成结构化 diff，并区分 Breaking / Non-breaking / Info。
- 可搜索 service、method、字段和注释。
