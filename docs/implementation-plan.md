# 实施计划

## 1. 项目骨架

- 新建独立 Java / Spring Boot 单体项目，不依赖 `sofarpc-cli`。
- 后端使用 Spring MVC HTTP API。
- 前端第一版使用无构建依赖的静态工作台页面，由 Spring Boot 托管；后续可替换为 React 或 Vue。
- SQLite 作为内置数据库。
- YAML 配置加载、admin reload 和团队级项目配置隔离。
- Dockerfile 内置 `git`。

交付：

- 服务可启动。
- 可读取配置。
- 可初始化 SQLite。
- 可用 token 保护 API。
- admin/team/project token 权限边界清晰，team token 只能管理本团队项目；团队自建项目创建后 `team`、`repo`、项目 `tokens` 由 Configuration Authorization Module 锁定。

## 2. Git 与扫描工作区

- 实现 `dataDir` 管理。
- 实现 repo mirror/cache。
- 支持 SSH key 和 HTTPS token。
- 支持固定分支和通配符分支匹配。
- 实现手动 scan API。
- 实现 commit/path 变更检测。

交付：

- 可 fetch 项目。
- 可 checkout/worktree 到指定分支。
- 可判断是否需要扫描。

## 3. Java 源码模型

- 实现轻量 Java 源码索引：FQN 到文件。
- 解析 public class/interface/enum。
- 解析字段、方法、参数名、返回类型、throws。
- 解析 import、package、extends、implements。
- 解析泛型模板和常见泛型替换。
- 解析内部类和内部枚举。
- 解析注释：interface、method、field、enum constant、`@param`、`@return`、`@deprecated`。

交付：

- 可对同仓库多模块源码建立类型模型。
- 可展开 DTO 字段树。
- 可生成请求/返回结构。

## 4. SOFA 发布配置识别

- 识别 `@SofaService`。
- 识别 `@Bean` 方法上明确 `interfaceType` 的 `@SofaService`。
- 识别 XML `<sofa:service>`、`<sofa:binding.bolt>`、`<sofa:global-attrs>`。
- 解析 `uniqueId/version/binding`。
- 实现 `${...}` 简单占位符替换和 `springProfiles`。
- 合并发布记录与包规则候选接口。

交付：

- 可区分已发布、源码候选、发布配置不完整、源码缺失。

## 5. 快照、Hash 与 Diff

- 设计 SQLite schema。
- 保存结构化快照。
- 计算结构 hash。
- 实现快照保留策略。
- 实现结构化 diff。
- 分类 Breaking / Non-breaking / Info。
- 建立 FTS5 搜索索引。

交付：

- 扫描后可查询历史快照。
- 可比较当前分支和 baseline。
- 可全文搜索。

## 6. Markdown 与 JSON 骨架

- 从结构化模型生成 Markdown。
- 生成字段表、枚举表、请求骨架、返回结构示例。
- 支持复制/下载 Markdown。
- 生成类型友好的占位值。
- 支持深度截断和循环引用标记。

交付：

- 页面和 API 都能获取 Markdown。
- Markdown 不包含 directUrl。

## 7. SofaRPC 调用

- 实现 SofaRPC Java 客户端 `bolt + hessian2` 泛化调用。
- 实现 JSON 输入规范到 SofaRPC 参数的转换。
- 支持 BigDecimal、日期、枚举、DTO、List、Map。
- 支持 `uniqueId/version/targetAppName`，调用时发布记录优先于分支默认值。
- 实现 TCP probe。
- 实现调用前结构校验：参数数量、JSON 类型、枚举值错误阻断调用；必填缺失和未知字段只警告。
- 展示响应 JSON、异常、耗时、解码错误；SOFA 泛化对象响应需要归一化为带 `_type` 的 JSON 友好结构。

交付：

- 可对测试环境 directUrl 发起调用。
- 不保存自动调用历史。
- 本地测试用进程内 SofaRPC provider 覆盖调用成功和业务异常。

## 8. 用例管理

- 保存项目共享请求用例。
- 按 project/branch/service/methodId 查询。
- 打开用例时做接口漂移提示。
- 不自动修改旧 JSON。

交付：

- 可保存、更新、删除、复用调用用例。

## 9. Web UI

- 中文界面。
- 三栏工作台布局。
- 左侧提供独立的“项目与分支配置”入口。
- 配置入口按 token scope 展示：admin 可管理全部项目，team token 只展示本团队项目，project token 不可进入；team token 可创建本团队项目，但项目创建后不能改 `team`、`repo`、项目 `tokens`。
- 项目/分支/service 列表。
- Markdown/字段树/diff 展示。
- JSON 调用面板。
- 搜索结果定位和高亮。
- 可分享接口 URL。
- sessionStorage 保存 token。

交付：

- 完成核心工作流：输入 token -> 选项目分支 -> 扫描 -> 看文档 -> diff -> 调用 -> 保存用例。

## 10. MVP 验收

- 使用一个真实 SOFABoot 项目验证。
- 覆盖注解发布、XML 发布、候选接口。
- 覆盖单参数、多参数、重载、泛型返回、枚举、内部类。
- 覆盖 DTO 缺失降级。
- 覆盖 commit 未变化、路径未命中、hash 未变化、hash 变化。
- 覆盖调用成功、业务异常、网络不可达、Hessian 解码失败。
