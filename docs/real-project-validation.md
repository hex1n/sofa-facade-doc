# 真实 SOFABoot 项目验收 Runbook

本文档用于把当前平台接入一个真实业务 SOFABoot / SofaRPC 项目，并产出可复核的验收记录。不要把真实 token、SSH 私钥或业务请求数据提交到仓库。

## 1. 准备信息

需要提前确认：

- Git 仓库 URL，平台服务器可只读访问。
- Git 凭证方式：`sshKeyPath` 或 `tokenEnv`。
- 基准分支：通常是 `develop`。
- 至少一个开发/测试分支，例如 `test` 或 `feature/*`。
- facade 包名，例如 `com.company.project.facade`。
- 源码目录和资源目录，例如 `facade/src/main/java`、`service/src/main/java`、`service/src/main/resources`。
- 测试环境 SofaRPC `directUrl`，只允许开发/测试环境。
- 测试调用样例：至少一个只读或安全可重复调用的方法。
- 团队归属和团队 token，用于验证团队隔离。

## 2. 创建配置

从模板复制一份到服务器：

```bash
cp docs/real-project-config-template.yml /etc/sofa-facade-doc/config.yml
```

按真实项目修改：

- `projects.<id>.repo`
- `baselineBranch`
- `branches.include` / `branches.exclude`
- `sourceRoots`
- `resourceRoots`
- `facadePackages`
- `branchDefaults.directUrl`
- `branchOverrides`
- admin token、team token 和 project token

如果使用 HTTPS token：

```bash
export SOFA_DOC_GIT_TOKEN=xxxxx
```

## 3. 启动平台

JAR 方式：

```bash
mvn -q -DskipTests package
java -Dsofa.doc.config=/etc/sofa-facade-doc/config.yml \
  -jar target/sofa-facade-doc-0.1.0-SNAPSHOT.jar
```

Docker 方式：

```bash
docker build -t sofa-facade-doc:latest .
docker run --rm -p 8080:8080 \
  -v /etc/sofa-facade-doc/config.yml:/app/config.yml:ro \
  -v /data/sofa-facade-doc:/data/sofa-facade-doc \
  sofa-facade-doc:latest
```

健康检查：

```bash
curl http://127.0.0.1:8080/api/health
```

## 4. 验证 Git 和分支匹配

```bash
curl "http://127.0.0.1:8080/api/projects" \
  -H "Authorization: Bearer <admin-token>"

curl "http://127.0.0.1:8080/api/projects/<project>/branches" \
  -H "Authorization: Bearer <admin-token>"
```

团队隔离验证：

```bash
curl "http://127.0.0.1:8080/api/config/projects" \
  -H "Authorization: Bearer <team-token>"

curl "http://127.0.0.1:8080/api/config/projects" \
  -H "Authorization: Bearer <project-token>"
```

记录：

- 项目是否出现。
- `develop/test/feature/*` 是否按配置匹配。
- `exclude` 分支是否未出现。
- team token 是否只看到本团队项目；project token 是否被配置入口拒绝。
- 如果失败，检查 Git 凭证和平台服务器网络。

## 5. 执行扫描

扫描单分支：

```bash
curl -X POST "http://127.0.0.1:8080/api/projects/<project>/branches/scan?branch=develop" \
  -H "Authorization: Bearer <admin-token>"
```

扫描项目匹配分支：

```bash
curl -X POST "http://127.0.0.1:8080/api/projects/<project>/scan" \
  -H "Authorization: Bearer <admin-token>"
```

查看扫描报告：

```bash
curl "http://127.0.0.1:8080/api/projects/<project>/scan-reports?branch=develop&limit=20" \
  -H "Authorization: Bearer <admin-token>"
```

记录：

- 至少一个分支 `status=success` 且 `snapshotCreated=true`。
- 重复扫描同一 commit 应为 `unchanged` 或 `skipped`。
- 若某分支失败，项目级扫描应保留其他分支结果，不应覆盖成功快照。

## 6. 验证接口识别

打开 Web UI：

```text
http://127.0.0.1:8080
```

使用 admin token 或 project token 登录，检查：

- 左侧能看到项目、分支和 service 列表。
- 已发布接口显示 `已发布`，候选接口显示候选/不完整等状态。
- service 名使用 interface 全限定名。
- 注解发布和 XML 发布都能识别到发布记录。
- `uniqueId/version/binding/serializeType/timeout` 与源码或 XML 配置一致。

## 7. 验证文档结构

选择 2 到 3 个典型接口方法，覆盖：

- 单参数 DTO。
- 多参数方法。
- 方法重载。
- 泛型返回，例如分页结果。
- 枚举字段。
- 内部类或内部枚举。
- 缺失源码 DTO 的降级展示。

检查：

- Markdown 有接口说明、方法说明、入参、出参、字段注释。
- 字段树按入参/出参分组，默认展开第一层。
- JSON 示例能反映字段类型。
- `static`、`transient`、`serialVersionUID`、`@JsonIgnore` 字段不应出现在报文字段中。
- `@NotNull`、`@NotBlank`、`@NotEmpty`、`@JsonProperty(required = true)` 应能体现为必填。
- `@JsonProperty` 的 Jackson 名称只作为展示别名；SofaRPC 泛化调用仍使用 Java 字段名。
- Markdown 不展示源码相对路径和行号。
- Markdown 不包含 `directUrl`。
- 复制和下载 Markdown 可用。

## 8. 验证搜索与 Diff

搜索：

- service FQN。
- 方法名。
- 字段名。
- 字段注释。
- 带点号或斜杠的查询，例如 `request.status`、`com.company.project.facade.XxxFacade/method`。

Diff：

```bash
curl "http://127.0.0.1:8080/api/projects/<project>/diff?branch=<branch>&base=develop" \
  -H "Authorization: Bearer <admin-token>"
```

记录 Breaking / Non-breaking / Info 分类是否符合预期。

## 9. 验证测试调用

选择一个安全可重复调用的方法：

1. 在右侧调用面板确认目标 `directUrl`、`uniqueId`、`version`、`targetAppName`。
2. 先点连通性检查。
3. 用生成的 JSON 示例填入真实测试参数。
4. 点击调用。

记录：

- 连通性成功或失败信息。
- 调用状态、耗时、异常或 JSON 响应。
- 参数结构错误时应返回校验失败。
- 必填缺失和未知字段应作为警告展示。

## 10. 验证用例和接口漂移

保存一个项目共享用例，然后：

- 重新打开同一方法，用例应能读取。
- 修改请求 JSON 后更新用例。
- 删除用例后列表应消失。
- 如果切换到接口结构不同的分支，打开旧用例时应提示接口漂移。

## 11. 验收记录模板

可先用脚本生成一份基础验收记录。默认只验证平台和文档主链路，不触发真实 RPC 调用：

```bash
SOFA_DOC_BASE_URL=http://127.0.0.1:8080 \
SOFA_DOC_TOKEN=<admin-token> \
SOFA_DOC_PROJECT=<project> \
SOFA_DOC_BRANCH=<branch> \
SOFA_DOC_BASELINE=<baseline> \
node scripts/real-project-acceptance.mjs
```

常用开关：

- `SOFA_DOC_ACCEPTANCE_SCAN=1`：先扫描目标分支。
- `SOFA_DOC_ACCEPTANCE_SCAN_ALL=1`：先执行项目级多分支扫描。
- `SOFA_DOC_SERVICE=<service-fqn>` / `SOFA_DOC_METHOD=<method-id>`：指定验收方法。
- `SOFA_DOC_ACCEPTANCE_CASE=1`：创建并删除一条临时共享用例。
- `SOFA_DOC_ACCEPTANCE_PROBE=1`：执行连通性检查。
- `SOFA_DOC_ACCEPTANCE_INVOKE=1 SOFA_DOC_ARGS_JSON='<json>'`：使用真实测试参数发起 SofaRPC 调用。
- `SOFA_DOC_REQUIRE_RPC=1`：连通性或调用失败时让脚本退出失败。
- `SOFA_DOC_ACCEPTANCE_FORMAT=json`：输出 JSON 证据，默认输出 Markdown。

| 项目 | 分支 | Commit | 验收人 | 日期 |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

| 验收项 | 结果 | 证据 |
| --- | --- | --- |
| Git 拉取和分支匹配 |  |  |
| 单分支扫描 |  |  |
| 项目级多分支扫描 |  |  |
| 注解发布识别 |  |  |
| XML 发布识别 |  |  |
| 候选接口识别 |  |  |
| Markdown/字段树/JSON 示例 |  |  |
| 搜索 |  |  |
| Diff |  |  |
| 连通性检查 |  |  |
| SofaRPC 调用 |  |  |
| 用例管理 |  |  |
| 分支切换刷新 |  |  |

当所有关键项都有真实项目证据后，才能把真实业务项目验收标记为完成。
