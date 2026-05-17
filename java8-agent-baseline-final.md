# Java 8 Agent Baseline · Final

> 这份文档给 LLM agent 写或评审 Java 8 代码时使用。不是给人通读的教材。
> 删除了 LLM 训练数据中已掌握的常识(SOLID 介绍、常见设计模式定义、抽象方法论等)。
> 保留的全是 LLM 易写错的、反直觉的、版本边界相关的、框架特定的内容。
>
> 使用方式:
>   1. 整体注入 system prompt(约 8-10K tokens);
>   2. 或按 § 标题切分,按任务召回;
>   3. 项目级补充写入 `project-agent-rules.md`,优先级高于本文。

---

## §1 · 总原则

```
正确性  > 优雅性
可读性  > 技巧性
显式    > 隐式
项目约定 > 本文
最小改动 > 大范围重写
```

修代码前:先识别项目的命名风格、异常体系、日志字段、事务边界、测试框架、构建命令。
不确定业务规则时:显式说明假设,不编造。
完成代码后:给出验证命令(`mvn test` / `gradle test` / 项目特定命令)。

规则等级:`MUST` 违反必出严重 bug;`SHOULD` 默认遵守,例外需说明。

---

## §2 · Java 版本边界

**JDK 8 不可用,常被 LLM 误以为可用的 API/语法速查表:**

| API / 语法 | 引入版本 | Java 8 替代 |
|---|---|---|
| `var` | 10 | 显式类型声明 |
| `List.of` / `Set.of` / `Map.of` | 9 | `Arrays.asList` / `new ArrayList<>()` |
| `Stream.toList()` | 16 | `.collect(Collectors.toList())` |
| `Stream.takeWhile` / `dropWhile` | 9 | 普通循环 |
| `Stream.ofNullable` | 9 | `Optional.ofNullable(x).map(Stream::of).orElseGet(Stream::empty)` |
| `Optional.orElseThrow()` 无参 | 10 | `.orElseThrow(() -> new XxxException(...))` |
| `Optional.ifPresentOrElse` | 9 | `if (opt.isPresent()) ... else ...` |
| `Optional.or` / `Optional.stream` | 9 | 普通 if / map |
| `CompletableFuture.orTimeout` | 9 | 见 §J8-CONC-006 |
| `CompletableFuture.completeOnTimeout` | 9 | 见 §J8-CONC-006 |
| `CompletableFuture.failedFuture` | 9 | `CompletableFuture<T> f = new CompletableFuture<>(); f.completeExceptionally(e);` |
| `String.isBlank` | 11 | `s == null || s.trim().isEmpty()` |
| `String.strip` | 11 | `s.trim()`(注意:trim 不去除全部 Unicode 空白) |
| `String.repeat` | 11 | 循环 + StringBuilder |
| `String.lines` | 11 | `s.split("\\R")` |
| `Files.readString` / `writeString` | 11 | `new String(Files.readAllBytes(path), UTF_8)` |
| `Predicate.not` | 11 | `pred.negate()` |
| `Collectors.toUnmodifiableList/Set/Map` | 10 | `Collections.unmodifiableList(new ArrayList<>(list))` |
| `switch expression` | 14 | 普通 switch 语句 |
| `text block` (`"""`) | 15 | 字符串拼接 |
| `record` | 16 | 普通 final class + 字段 |
| `sealed class` | 17 | 普通 abstract class |
| `instanceof` pattern matching | 16 | 显式 cast |

**[J8-VER-001] MUST** 不得使用上表中任何一项(项目明确允许更高版本除外)。

---

## §3 · 核心规则(按违反代价排序)

每条格式:

```
### [ID] [LEVEL] 一行规则
❌ 反例
✅ 正例
why: (仅反直觉/框架陷阱时给出)
```

### §3.1 集合与 Optional

#### [J8-COL-001] MUST `Collectors.toMap` 必须传 merge 函数

即使业务上 key 唯一,也要传一个抛异常的 merger 作为运行时断言。

```java
// ❌
Map<Long, User> m = users.stream()
    .collect(Collectors.toMap(User::getId, Function.identity()));

// ✅ key 应当唯一时
Map<Long, User> m = users.stream()
    .collect(Collectors.toMap(
        User::getId,
        Function.identity(),
        (a, b) -> { throw new IllegalStateException("duplicate user id: " + a.getId()); }
    ));

// ✅ key 可合并时
Map<Long, Integer> qty = items.stream()
    .collect(Collectors.toMap(Item::getSkuId, Item::getQty, Integer::sum));
```

why: 两参版本 value 为 null 直接抛 NPE(JDK 8 实现行为);key 重复抛 IllegalStateException。
显式 merger 让数据异常立刻暴露而不是隐式吞掉。

#### [J8-COL-002] MUST 禁止 `Optional.get()` 直接调用

```java
// ❌
User u = findById(id).get();

// ✅
User u = findById(id).orElseThrow(
    () -> new BusinessException("USER_NOT_FOUND", "user not found: " + id));
```

#### [J8-COL-003] MUST 集合返回类型禁返 null

```java
// ❌
public List<User> find(Query q) {
    if (q.isEmpty()) return null;
    ...
}

// ✅
public List<User> find(Query q) {
    if (q.isEmpty()) return Collections.emptyList();
    ...
}
```

不要把 `Optional<List<User>>` 当方案——集合用空集合表达"没有"。

#### [J8-COL-004] MUST `unmodifiableXxx` 是视图,需先拷贝才能真正隔离

```java
// ❌ 外部仍持有 list 引用,可以修改 list,视图随之变化
return Collections.unmodifiableList(internalList);

// ✅
return Collections.unmodifiableList(new ArrayList<>(internalList));
```

且元素本身可变时,需要更深的防御性拷贝。

#### [J8-COL-005] MUST `equals` 与 `hashCode` 必须同时重写,且基于不变字段

```java
// ❌ 只重写 equals,放进 HashMap 会出错
public class OrderKey {
    @Override public boolean equals(Object o) { ... }
}

// ❌ ORM 实体用自增 ID 作 equals/hashCode
public class Order {
    private Long id;  // persist 前为 null,persist 后被回填
    @Override public int hashCode() { return Objects.hash(id); }
    // 进入 Set 后 id 变化,再也找不到
}

// ✅
public final class OrderKey {
    private final Long orderId;
    private final String channel;
    
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderKey)) return false;
        OrderKey other = (OrderKey) o;
        return Objects.equals(orderId, other.orderId)
            && Objects.equals(channel, other.channel);
    }
    @Override public int hashCode() { return Objects.hash(orderId, channel); }
}
```

进入 `HashMap`/`HashSet` 后,参与 hash 的字段不得再修改。

#### [J8-COL-006] MUST `Arrays.asList` 返回固定大小,不能 add/remove

```java
// ❌
List<String> l = Arrays.asList("a", "b");
l.add("c");  // UnsupportedOperationException

// ✅
List<String> l = new ArrayList<>(Arrays.asList("a", "b"));
l.add("c");
```

---

### §3.2 金额与精度

#### [J8-NUM-001] MUST 金额禁用 `double` / `float`

用 `BigDecimal` 或最小货币单位(`long` 表示分/厘)。

#### [J8-NUM-002] MUST 禁止 `new BigDecimal(double)`

```java
// ❌
BigDecimal a = new BigDecimal(0.1);
// 实际值:0.1000000000000000055511151231257827021181583404541015625

// ✅
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = BigDecimal.valueOf(0.1);  // 内部走 Double.toString,等于 "0.1"
```

why: `new BigDecimal(double)` 接收的已经是不精确的 double,BigDecimal 只能忠实记录这个不精确值。

#### [J8-NUM-003] MUST `BigDecimal` 数值比较用 `compareTo`,不用 `equals`

```java
// ❌
new BigDecimal("1.0").equals(new BigDecimal("1.00"))   // false,scale 不同

// ✅
new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0   // true
```

#### [J8-NUM-004] MUST `BigDecimal.divide` 必须指定 scale 和 `RoundingMode`

```java
// ❌
BigDecimal r = amount.divide(rate);   // 1/3 等无限循环时抛 ArithmeticException

// ✅
BigDecimal r = amount.divide(rate, 2, RoundingMode.HALF_UP);
```

---

### §3.3 并发与线程

#### [J8-CONC-001] MUST 禁止 `new Thread()` 起异步任务

使用项目统一线程池或受控的 `ThreadPoolExecutor`。线程池必须命名:

```java
ThreadFactory factory = r -> {
    Thread t = new Thread(r, "order-worker-" + counter.getAndIncrement());
    t.setUncaughtExceptionHandler((th, e) -> log.error("thread {} died", th.getName(), e));
    return t;
};
```

#### [J8-CONC-002] MUST `CompletableFuture` 异步方法必须显式传 `Executor`

```java
// ❌
CompletableFuture.supplyAsync(() -> remoteClient.query());

// ✅
CompletableFuture.supplyAsync(() -> remoteClient.query(), businessExecutor);
```

why: 不传 Executor 默认用 `ForkJoinPool.commonPool`(CPU 核数 - 1 个线程),
被全应用所有 parallelStream 共享。跑阻塞 I/O 会让整个应用并行能力雪崩。

#### [J8-CONC-003] MUST `ThreadLocal` / `MDC` 写入必须 `try/finally` 清理

```java
// ❌
MDC.put("traceId", traceId);
doWork();
// 任务结束,traceId 留在线程上,污染下一个任务

// ✅
MDC.put("traceId", traceId);
try {
    doWork();
} finally {
    MDC.clear();
}

// ✅ 更好:封装拦截器/装饰器,业务代码无需关心
```

why: 线程池线程长期复用。未清理 = 内存泄露 + 上下文污染(下一个请求的 traceId 是上一个的)。
不要用 `InheritableThreadLocal` 替代显式传播——它只在线程创建时复制一次,对线程池复用无效。

#### [J8-CONC-004] MUST `parallelStream` 禁止用于阻塞 I/O

```java
// ❌ 拖垮全应用并行能力
orders.parallelStream()
    .map(o -> remoteClient.query(o.getId()))
    .collect(Collectors.toList());

// ✅ CPU 密集型 + 独立池
ForkJoinPool pool = new ForkJoinPool(8);
try {
    return pool.submit(() ->
        data.parallelStream().map(this::cpuIntensive).collect(toList())
    ).get();
} finally {
    pool.shutdown();
}
```

#### [J8-CONC-005] MUST 共享可变状态必须线程安全

多线程访问:用 `ConcurrentHashMap` / `AtomicXxx` / 显式锁 / 消息串行化。
禁止多线程修改 `HashMap` / `ArrayList`(Java 8 后不会死循环但会丢数据 + 出错乱)。

#### [J8-CONC-006] MUST Java 8 实现 `CompletableFuture` 超时

```java
// Java 9+ 的 orTimeout/completeOnTimeout 不可用

// 推荐顺序:
// 1. 优先用 HTTP/RPC 客户端自身的超时(最可靠,能中断 I/O)
// 2. 框架级超时:Resilience4j / Hystrix / Dubbo timeout
// 3. ScheduledExecutorService 触发 completeExceptionally:

public <T> CompletableFuture<T> withTimeout(
        CompletableFuture<T> source, long timeout, TimeUnit unit,
        ScheduledExecutorService scheduler) {
    ScheduledFuture<?> task = scheduler.schedule(() -> {
        if (!source.isDone()) {
            source.completeExceptionally(new TimeoutException(
                "timed out after " + timeout + " " + unit));
        }
    }, timeout, unit);
    source.whenComplete((r, e) -> task.cancel(false));
    return source;
}

// 注意:completeExceptionally 只能让等待 future 的下游结束,
// 无法真正中断底层 I/O。真中断必须靠客户端层超时。
```

---

### §3.4 事务与一致性

#### [J8-TX-001] MUST Spring `@Transactional` 不得被同类内部方法直接调用

```java
// ❌ this.doCreate 绕过代理,@Transactional 不生效
@Service
public class OrderService {
    public void create(Request req) {
        doCreate(req);
    }
    @Transactional
    public void doCreate(Request req) { ... }
}

// ✅ 抽出独立 Bean
@Service
public class OrderService {
    private final OrderTxService tx;
    public void create(Request req) { tx.doCreate(req); }
}

@Service
public class OrderTxService {
    @Transactional(rollbackFor = Exception.class)
    public void doCreate(Request req) { ... }
}
```

`@Transactional` 失效的其他常见原因:

```
- 方法非 public(默认只对 public 生效)
- catch 异常后没有重抛
- 抛受检异常但未声明 rollbackFor = Exception.class
- 通过 @Async 在新线程中调用
- 方法被 final 或 private
- 多数据源时事务管理器指定错误
```

#### [J8-TX-002] MUST 受检异常需声明 `rollbackFor`

```java
// ❌ 默认只对 RuntimeException 和 Error 回滚
@Transactional
public void process() throws IOException { ... }

// ✅
@Transactional(rollbackFor = Exception.class)
public void process() throws IOException { ... }
```

#### [J8-TX-003] MUST 事务内禁远程调用 / 慢 I/O

事务边界放在 Application Service 层。事务方法表达一个完整业务用例。
必须远程调用时,显式说明超时控制、失败补偿、锁持有时间。

#### [J8-TX-004] MUST 状态变更 SQL 必须带原状态条件,且检查 affected rows

```sql
-- ❌
UPDATE orders SET status='PAID', paid_at=? WHERE id=?

-- ✅
UPDATE orders SET status='PAID', paid_at=? WHERE id=? AND status='WAIT_PAY'
```

```java
int rows = jdbc.update(sql, ...);
if (rows == 0) {
    // 区分:并发、重复请求、状态非法、数据不存在
    throw new BusinessException("ORDER_STATUS_ILLEGAL", "...");
}
```

#### [J8-TX-005] MUST 写操作必须考虑幂等

支付、退款、发货、发券、状态流转必须有幂等键 + 唯一索引 + 状态机约束之一。

---

### §3.5 外部调用

#### [J8-IO-001] MUST 所有外部调用必须设置超时

HTTP、RPC、数据库、缓存、MQ、对象存储,无一例外。区分连接超时与读超时。

#### [J8-IO-002] MUST 重试必须有最大次数 + 退避;非幂等接口禁止重试

#### [J8-IO-003] MUST 第三方 DTO 不得直接进入领域层

通过 Adapter/Gateway 转换为内部模型。第三方字段命名、类型、枚举值变化时,
adapter 是唯一受影响点。

---

### §3.6 日志

#### [J8-LOG-001] MUST 用 `{}` 占位符,禁止 `+` 拼接

```java
// ❌
log.info("orderId=" + orderId + ", amount=" + amount);

// ✅
log.info("orderId={}, amount={}", orderId, amount);
```

why: 占位符在日志级别关闭时不触发拼接;`+` 拼接会把 null 拼成 "null" 隐藏 NPE。

#### [J8-LOG-002] MUST 异常作为最后一个参数,SLF4J 自动打堆栈

```java
// ❌ 堆栈丢失
log.error("failed " + e);
log.error("failed, orderId={}, e={}", orderId, e);

// ✅
log.error("failed, orderId={}", orderId, e);
```

#### [J8-LOG-003] MUST 不记录敏感信息

password / token / secret / 身份证 / 银行卡 / 私钥 / session id 不进日志。
必须记录时脱敏:`138****8000`。

#### [J8-LOG-004] MUST 生产代码用 SLF4J,禁止 `System.out` / `System.err`

#### [J8-LOG-005] MUST 异步任务传递 traceId / tenantId 等链路上下文

```java
String traceId = MDC.get("traceId");
businessExecutor.submit(() -> {
    MDC.put("traceId", traceId);
    try { doAsync(); } finally { MDC.clear(); }
});
```

推荐封装统一 `Runnable` / `Callable` 装饰器,避免每个调用点重复。

---

### §3.7 异常

#### [J8-EXC-001] MUST 保留异常 cause

```java
// ❌
throw new RemoteServiceException("call failed");

// ✅
throw new RemoteServiceException("call failed", e);
```

#### [J8-EXC-002] MUST 不吞异常

`catch` 空块或只 `log` 不抛且无业务理由 = 违反。

#### [J8-EXC-003] MUST 不用 `RuntimeException("error")` 之类无语义异常

异常类型必须表达问题类别:参数异常、业务异常、权限异常、外部服务异常、数据访问异常。

#### [J8-EXC-004] SHOULD 边界处统一转换异常

Controller、RPC Provider、MQ Consumer、Job Handler 处统一转换,
避免内部异常结构泄漏给外部。异常应在合适边界记录**一次**,不要每层都 log.error。

---

### §3.8 安全

#### [J8-SEC-001] MUST 密码存储用慢哈希 + 盐

```
推荐:Argon2(优先)、BCrypt、scrypt、PBKDF2(迭代次数足够高)
禁止:MD5 / SHA-1 / SHA-256 单轮哈希
禁止:自定义 hash(salt + password) 实现
禁止:明文或可逆加密存储用户密码
```

```java
// ✅
PasswordEncoder enc = new BCryptPasswordEncoder(12);  // strength ≥ 10
String hashed = enc.encode(rawPassword);
boolean ok = enc.matches(rawPassword, hashed);
```

#### [J8-SEC-002] MUST 对称加密禁 ECB,IV 必须 SecureRandom 生成

```
推荐:AES-GCM(带认证)、ChaCha20-Poly1305
禁止:DES、3DES、AES-ECB、AES-CBC 不带 HMAC、固定/重复 IV
```

#### [J8-SEC-003] MUST 安全相关随机数用 `SecureRandom`

```java
// ❌
new Random().nextBytes(salt);
ThreadLocalRandom.current().nextBytes(token);

// ✅
new SecureRandom().nextBytes(salt);
```

适用:盐、IV、token、session id、CSRF token、密钥。

#### [J8-SEC-004] MUST SQL / 命令 / LDAP / 表达式禁止拼接用户输入

SQL 用参数化查询;命令执行避免拼接;模板渲染用安全转义。

#### [J8-SEC-005] MUST 反序列化不可信数据必须 allowlist + 类型限制

禁止直接 `ObjectInputStream.readObject` 不可信数据。
Jackson 启用 default typing 处理不可信输入也是高危。

#### [J8-SEC-006] MUST SSRF 防护用 allowlist,不靠 blacklist

blacklist 无法覆盖内网 IP、重定向、DNS rebinding、IPv6、十六进制 IP 等表示。

#### [J8-SEC-007] MUST 密钥 / 证书 / 密码不得硬编码

不写入源码、日志、普通配置文件。用密钥管理系统(KMS / Vault / 项目方案)。

#### [J8-SEC-008] MUST 后端必须执行权限校验

不能只依赖前端。必须考虑:水平越权、垂直越权、跨租户、批量接口越权、导出接口越权。

#### [J8-SEC-009] MUST 文件路径必须规范化并限制在允许目录内

```java
Path base = Paths.get("/data/uploads").toRealPath();
Path target = base.resolve(userInput).normalize();
if (!target.startsWith(base)) {
    throw new SecurityException("path traversal: " + userInput);
}
```

文件上传校验:大小、扩展名、MIME 类型、内容魔数;不用原始文件名落盘。

#### [J8-SEC-010] MUST XML 解析禁用外部实体(XXE)

```java
SAXParserFactory f = SAXParserFactory.newInstance();
f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
f.setFeature("http://xml.org/sax/features/external-general-entities", false);
f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
```

#### [J8-SEC-011] MUST TLS 最低 1.2,优先 1.3

禁止 SSLv2/v3/TLS 1.0/1.1。HTTPS 客户端不得禁用证书校验。

---

### §3.9 API 与兼容性

#### [J8-API-001] MUST Jackson 必须容忍未知字段

```java
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
```

否则上游加字段会直接打挂消费者,破坏向后兼容。

#### [J8-API-002] MUST 枚举反序列化必须容错

```java
// 全局:未知枚举值 → null
mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

// 字段级:未知值 → 默认值(推荐)
public enum OrderStatus {
    WAIT_PAY, PAID, CANCELLED,
    @JsonEnumDefaultValue UNKNOWN
}
```

消费者必须显式处理 null / UNKNOWN 分支,不能假设枚举非空。

#### [J8-API-003] MUST 公共 API 字段禁止删除或改类型

风险操作:删字段、改类型、改枚举含义、改错误码语义、改默认排序、改时间格式、改金额单位。
安全演进:新增字段 → 标记废弃 → 观察 → 删除。错误码语义稳定。

#### [J8-API-004] MUST 枚举禁止用 `ordinal()` 持久化或传输

用稳定 code:

```java
public enum OrderStatus {
    WAIT_PAY("WAIT_PAY"), PAID("PAID"), CANCELLED("CANCELLED");
    private final String code;
    OrderStatus(String code) { this.code = code; }
    public String getCode() { return code; }
}
```

---

### §3.10 资源与 I/O

#### [J8-RES-001] MUST 用 try-with-resources 关闭资源

```java
try (InputStream in = fileService.open(id)) {
    return parse(in);
}
```

#### [J8-RES-002] MUST 文件 / 导入 / 导出限制大小,不全量入内存

流式处理大文件。压缩包要防 zip bomb(限制解压后总大小和文件数)。

---

### §3.11 数据库

#### [J8-DB-001] MUST 分页必须有上限

```java
int pageSize = Math.min(request.getPageSize(), 100);
```

#### [J8-DB-002] MUST 避免 N+1 查询

```java
// ❌
List<Order> orders = orderRepo.find(q);
for (Order o : orders) {
    o.setUser(userRepo.findById(o.getUserId()));
}

// ✅
List<Order> orders = orderRepo.find(q);
Set<Long> userIds = orders.stream().map(Order::getUserId).collect(toSet());
Map<Long, User> userMap = userRepo.findByIds(userIds).stream()
    .collect(Collectors.toMap(
        User::getId, Function.identity(),
        (a, b) -> { throw new IllegalStateException("duplicate: " + a.getId()); }
    ));
for (Order o : orders) o.setUser(userMap.get(o.getUserId()));
```

#### [J8-DB-003] MUST 不默认 `SELECT *`

只查业务需要的字段。新增查询必须考虑:where 命中索引、order by 可利用索引、分页是否深分页、是否 N+1。

#### [J8-DB-004] SHOULD 数据库变更保持向前兼容

不在同一次发布中同时删字段、改接口、切业务逻辑。顺序:新增可空字段 → 兼容应用上线 → 回填 → 切读取 → 稳定后删旧字段。

---

### §3.12 MQ 与异步

#### [J8-MQ-001] MUST MQ 消费必须幂等

消息可能重复投递。幂等手段:消息 ID 去重表 + 业务幂等键 + 状态机校验 + 唯一索引。

#### [J8-MQ-002] MUST 明确失败重试 + 死信处理

#### [J8-MQ-003] MUST 顺序消费明确顺序维度(订单 ID / 用户 ID),不要求全局顺序

---

### §3.13 缓存

#### [J8-CACHE-001] MUST 缓存 key 包含必要业务维度

`user:profile:{tenantId}:{userId}` 不要遗漏租户、语言、渠道、权限范围。

#### [J8-CACHE-002] MUST 一致性场景必须说明更新策略

可选:先更新 DB 后删缓存、延迟双删、消息驱动失效、按版本号写入。
不接受"更新 DB 后更新缓存"不说明并发与失败场景。

#### [J8-CACHE-003] SHOULD 处理缓存穿透、击穿、雪崩

穿透:空值缓存 / 布隆过滤器。击穿:互斥加载 / 逻辑过期 / 热点预热。雪崩:过期随机化 / 分批预热。

---

## §4 · 任务 Playbook

### 4.1 写新代码时的检查序列

```
1. 这段代码会在 Java 8 编译吗?
   → §2 速查表
2. 公共方法的 null 契约?返回 null 还是空集合?
   → J8-COL-003、J8-COL-002
3. 进入线程池吗?
   → J8-CONC-001/002/003
4. 有外部调用吗?
   → J8-IO-001(超时)、J8-IO-002(重试)、J8-IO-003(模型隔离)
5. 是写操作吗?
   → J8-TX-001(自调用)、J8-TX-003(事务内远程)、J8-TX-004(状态机)、J8-TX-005(幂等)
6. 涉及金额吗?
   → J8-NUM-001/002/003/004
7. 写日志了吗?
   → J8-LOG-001(占位符)、J8-LOG-002(异常参数)、J8-LOG-003(脱敏)
8. 有用户输入吗?
   → J8-SEC-004(注入)、J8-SEC-006(SSRF)、J8-SEC-009(路径)
9. 涉及对象等价性/集合 key 吗?
   → J8-COL-005
```

### 4.2 修 bug 时的检查序列

```
1. 修改的方法属于哪个分层?Controller / Service / Repository?
   → 不要改错分层(Controller 不做业务,Repository 不做业务判断)
2. 改动的最小范围是什么?
   → §1 最小改动原则
3. 异常分支处理变了吗?
   → J8-EXC-001(cause)、J8-EXC-002(不吞)
4. 事务边界变了吗?
   → 改了要说明风险
5. 修复是否引入并发问题?
   → J8-CONC-005
6. 日志足够定位下次出现吗?
   → 是否补足上下文
7. 是否补充了回归测试?
```

### 4.3 评审 PR 时的清单

每条按"是 / 否 / 不适用"过:

```
□ Java 8 兼容(无 9+ API)
□ Collectors.toMap 处理重复 key
□ Optional 不直接 .get()
□ 集合返回非 null
□ equals/hashCode 一致
□ 金额未用 double / new BigDecimal(double)
□ 线程池受控、CompletableFuture 指定 Executor
□ ThreadLocal/MDC 清理
□ @Transactional 无自调用、声明 rollbackFor
□ 状态变更 SQL 带原状态条件
□ 写操作有幂等键
□ 外部调用有超时
□ 重试受控,非幂等不重试
□ 日志用占位符,异常作为最后参数
□ 不记录敏感信息
□ 异常有 cause、不吞、有语义
□ 密码用慢哈希、随机数用 SecureRandom
□ 用户输入未拼接 SQL/命令
□ Jackson 容忍未知字段、枚举容错
□ 公共 API 字段未删未改类型
□ try-with-resources 关闭资源
□ 分页有上限
□ MQ 消费幂等
□ 核心规则有测试覆盖
```

---

## §5 · 附录

### 5.1 项目级规则模板 `project-agent-rules.md`

每个项目应填写:

```yaml
# 基础
java_version: 8
build_tool: maven                       # 或 gradle
build_test_cmd: mvn test
build_verify_cmd: mvn verify

# 框架
framework: spring-boot-2.7.18           # 具体版本
orm: mybatis                            # 或 jpa / hibernate
http_client: okhttp-4.x
mq: rocketmq-4.x                        # 或 kafka / rabbitmq

# 异常体系
business_exception_class: com.xxx.BusinessException
error_code_format: "MODULE_REASON"      # 如 ORDER_NOT_FOUND
error_code_registry: docs/error-codes.md

# 日志
logger: slf4j
mdc_fields: [traceId, tenantId, userId]
sensitive_fields: [password, token, idCard, bankCard, phone, email]
sensitive_mask_strategy: "前 3 后 4 中间星号"

# 事务
tx_strategy: declarative                # 或 programmatic
default_rollback_for: Exception.class

# 数据库
default_page_size_max: 100
slow_query_threshold_ms: 200

# 安全
password_encoder: BCrypt(12)
encryption_algorithm: AES-GCM-256
key_management: vault                    # 或 kms / properties+jasypt

# 命名风格
indent: 4spaces
style: traditional                       # 或 google

# 测试
unit_test_framework: junit-5
mock_framework: mockito-4
integration_test_command: mvn -P it verify

# 部署
feature_toggle: apollo                   # 或 ldconfig / consul
trace_system: skywalking                 # 或 jaeger / zipkin
```

### 5.2 反模式速查(出现即违规)

```
new Thread(...).start()                  → J8-CONC-001
CompletableFuture.supplyAsync(lambda)    → J8-CONC-002 (无 Executor)
new HashMap<>() 多线程修改                → J8-CONC-005
ThreadLocal.set(x) ... 无 try/finally    → J8-CONC-003
parallelStream() 内含远程/数据库调用       → J8-CONC-004
new BigDecimal(0.1)                      → J8-NUM-002
bigDecimal.equals(other)  (比较数值)      → J8-NUM-003
bigDecimal.divide(other)  (无 scale)      → J8-NUM-004
amount * 100  (amount 是 double)         → J8-NUM-001
Optional.get()                           → J8-COL-002
return null;  (返回类型是 List/Map/Set)   → J8-COL-003
Collectors.toMap(k, v)  (两参)           → J8-COL-001
@Transactional 同类自调用                 → J8-TX-001
@Transactional 抛 IOException 无 rollbackFor → J8-TX-002
UPDATE ... SET status='X' WHERE id=?     → J8-TX-004 (无原状态)
log.info("x=" + x)                       → J8-LOG-001
log.error("...", "e=" + e)               → J8-LOG-002 (异常拼接)
log.info("password=" + pwd)              → J8-LOG-003
System.out.println(...)                  → J8-LOG-004
throw new RuntimeException("error")      → J8-EXC-003
throw new XxxException("msg")  (有 e)    → J8-EXC-001
catch (Exception e) {}                   → J8-EXC-002
MessageDigest.getInstance("MD5")  存密码  → J8-SEC-001
Cipher.getInstance("AES")  (默认 ECB)    → J8-SEC-002
new Random()  生成 token/salt            → J8-SEC-003
"SELECT * FROM t WHERE name='" + n + "'" → J8-SEC-004
ObjectInputStream(untrusted).readObject() → J8-SEC-005
String password = "hardcoded"            → J8-SEC-007
List.of(...)                             → J8-VER-001
stream.toList()                          → J8-VER-001
var x = ...                              → J8-VER-001
"".isBlank()                             → J8-VER-001
```

### 5.3 最终判断标准

一段代码是否合格,不看是否"用了 Stream"或"用了设计模式",看:

```
是否正确实现业务规则
是否保持 Java 8 兼容
是否职责清晰、边界明确
是否异常可追踪
是否日志可诊断
是否数据一致性可控
是否外部失败可恢复
是否安全风险可控
是否测试能保护变更
是否符合项目已有规则
```

---

## §6 · 元信息

```
版本:Final v1.0(基于 v1.1 → v1.2 → v2-prototype 收敛)
体量:约 1300 行 / 约 9K tokens
设计:可整体注入 system prompt,也可按 § 切分召回
覆盖:Java 8 兼容、集合、Optional、金额、并发、事务、外部调用、
      日志、异常、安全、API 兼容性、资源、数据库、MQ、缓存
不覆盖:SOLID 原则、常见设计模式定义、抽象方法论
       (LLM 训练数据已掌握,放入只占 context 无边际收益)
更新触发:
  - 线上事故根因属于本文未覆盖项
  - Java / Spring / Jackson 等基础设施升级带来新陷阱
  - 评审中反复出现的规则争议
```
