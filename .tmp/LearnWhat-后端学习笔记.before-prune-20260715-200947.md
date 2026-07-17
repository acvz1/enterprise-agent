# LearnWhat 后端项目阅读笔记

## 快速索引

- ***项目地图***
- [000. 后端项目地图与主调用链](#000-后端项目地图与主调用链)
- ***schema.sql***
- [001. SQL 表如何设计，为什么使用 MySQL](#001-sql-表如何设计为什么使用-mysql)
- ***common/GlobalExceptionHandler.java***
- [002. 全局异常处理器逐行理解](#002-全局异常处理器逐行理解)
- ***config/interceptor/AuthInterceptor.java***
- [003. 用最小启动实验理解 @Component 与 @Bean](#003-用最小启动实验理解-component-与-bean)
- [004. HandlerInterceptor 与 preHandle 最小框架](#004-handlerinterceptor-与-prehandle-最小框架)
- [005. AuthInterceptor 登录验证完整逻辑](#005-authinterceptor-登录验证完整逻辑)
- ***config/WebConfig.java***
- [006. 如何判断 Spring 配置类](#006-如何判断-spring-配置类)
- [007. WebMvcConfigurer 为什么存在](#007-webmvcconfigurer-为什么存在)
- [008. Spring 后端启动与请求架构图](#008-spring-后端启动与请求架构图)

---

### 000. 后端项目地图与主调用链

**项目要解决的问题**

LearnWhat 后端为前端提供四类能力：用户注册登录、学习科目管理、按权重抽取学习内容，以及抽取历史与统计。后端的本质任务可以先压缩成三步：接收前端请求、根据业务规则作出决定、读取或修改 MySQL 数据。

**整体架构**

```text
前端 HTTP 请求
  -> WebConfig / AuthInterceptor：决定请求能否继续
  -> Controller：接收 HTTP 参数并调用业务层
  -> Service：校验参数并执行真正的业务规则
  -> Mapper：执行 SQL
  -> MySQL：永久保存数据
  -> Service 返回结果
  -> Controller 使用 Result<T> 统一包装
  -> Spring 把 Java 对象转换成 JSON
  -> 前端收到 HTTP 响应
```

注册和登录是例外：用户此时还没有 JWT，因此 `WebConfig` 让 `/api/user/register` 和 `/api/user/login` 跳过 `AuthInterceptor`。

如果 Controller 调用链向外抛出异常：

```text
Service / Controller 抛出异常
  -> GlobalExceptionHandler 选择匹配的处理方法
  -> ResponseEntity 决定 HTTP 状态码
  -> Result<Void> 形成统一错误 JSON
  -> 前端收到错误响应
```

**各层职责边界**

| 模块 | 负责什么 | 不负责什么 |
|---|---|---|
| `Controller` | HTTP 路径、请求参数、调用 Service、返回响应 | 不直接写 SQL，不承载复杂业务规则 |
| `Service` | 参数校验、业务判断、流程编排、事务 | 不处理前端页面，不关心 JSON 如何发送 |
| `Mapper` | 定义并执行数据库 SQL | 不决定业务是否合法 |
| `Entity` | 表示数据库中的用户、科目、会话和记录 | 不负责接收请求和组织业务流程 |
| `DTO` | 表示接口输入或输出的数据形状 | 不负责数据库读写 |
| `Result<T>` | 统一成功和失败响应体 | 不决定 HTTP 状态码和业务结果 |
| `GlobalExceptionHandler` | 把异常转换为 HTTP 错误响应 | 不修复异常，也不执行正常业务 |
| `WebConfig / AuthInterceptor` | 配置跨域和登录校验入口 | 不处理注册、科目、抽奖等业务 |

**三条核心业务链**

1. 用户注册与登录：

```text
UserRequest
  -> UserController
  -> UserService
  -> UserMapper
  -> user 表
  -> JwtUtil 生成 JWT
  -> LoginResponse
  -> Result<LoginResponse>
```

2. 科目增删改查：

```text
JWT -> AuthInterceptor 得到 userId
  -> SubjectController
  -> SubjectService
  -> SubjectMapper
  -> subject 表
  -> Result<Subject> / Result<List<Subject>>
```

3. 抽取学习内容：

```text
JWT -> AuthInterceptor 得到 userId
  -> DrawController
  -> DrawService
  -> SubjectMapper + DrawSessionMapper + DrawRecordMapper
  -> subject + draw_session + draw_record
  -> DrawResult / DrawAvailability / 历史与统计 DTO
  -> Result<T>
```

`draw_session` 保存一轮抽取中会变化的过程状态，`draw_record` 只保存已经完成的最终结果。`DrawService` 是项目最复杂的业务模块，包含权重随机、子转盘、冷却、重抽和事务，依赖前面所有基础模块。

**代码阅读依赖路线**

这只是知识依赖，不是按天安排的学习计划：

```text
schema.sql 与 Entity
  -> Result 与 GlobalExceptionHandler
  -> 用户注册登录完整链路
  -> JWT 与 AuthInterceptor
  -> 科目 CRUD 完整链路
  -> 抽奖状态、权重算法与事务
  -> application.yml、启动类和 pom.xml 的整体组装
```

当前已经学习 `schema.sql` 和 `GlobalExceptionHandler`；`Result.java` 是正在阅读但尚未完成的部分。后续每次只进入一条链或一个文件，由当前问题决定，不按目录一次性灌输。

**最小心智模型**

```text
Controller 接请求，Service 作决定，Mapper 操作数据库；
Interceptor 先确认是谁，Result 和异常处理器统一告诉前端结果。
```

---

### 001. SQL 表如何设计，为什么使用 MySQL

**知识点**

四张表按数据职责拆分：`user` 保存用户，`subject` 保存科目配置，`draw_session` 保存一轮抽奖的过程状态，`draw_record` 保存最终结果。过程与结果分开后，子转盘、冷却和重抽不会制造多条错误的历史记录。

**代码位置**

[schema.sql](D:/Project/LearnWhat/backend/sql/schema.sql:1)

```sql
CREATE TABLE `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE subject (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7) DEFAULT '#3B82F6',
    weight INT DEFAULT 1,
    user_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    CONSTRAINT fk_subject_user
        FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
    CONSTRAINT fk_subject_parent
        FOREIGN KEY (parent_id) REFERENCES subject(id) ON DELETE CASCADE
);
```

```sql
CREATE TABLE draw_session (
    user_id BIGINT NOT NULL,
    main_subject_id BIGINT NOT NULL,
    final_subject_id BIGINT NULL,
    redraw_used TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    next_available_at DATETIME NOT NULL,
    completed_at DATETIME NULL
);

CREATE TABLE draw_record (
    user_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    parent_subject_id BIGINT NULL,
    session_id BIGINT NULL,
    drawn_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

**简明解释**

表关系：

```text
user 1 ── N subject
user 1 ── N draw_session
user 1 ── N draw_record

subject(父) 1 ── N subject(子)
draw_session 1 ── N draw_record（代码目前按一轮一个最终记录使用）
```

- `user`：账号主表。`username UNIQUE` 防止重名，密码列长度 255 用来保存 BCrypt 哈希，不保存明文密码。
- `subject`：`parent_id = NULL` 表示一级科目；有值表示子科目。这叫“邻接表”自关联设计。`weight` 控制抽中概率，`color` 服务前端显示。
- `draw_session`：保存一轮抽奖的可变状态。`main_subject_id` 是主转盘结果，`final_subject_id` 是最终结果，`status`、`redraw_used`、`next_available_at` 共同描述子转盘、重抽和冷却状态。
- `draw_record`：只保存已经完成的最终结果，供历史与统计查询。`parent_subject_id` 可以还原“父科目 / 子科目”，`session_id` 可以定位结果属于哪一轮。

为什么不把 `draw_session` 和 `draw_record` 合成一张表：

```text
draw_session = 过程，会更新
draw_record  = 最终事实，用于历史和统计
```

抽中有子科目的主科目时，这一轮还未结束；重抽时旧结果还要被替换。拆表能避免把中间结果误算进历史。

`ON DELETE CASCADE` 表示父数据删除时自动删除依赖数据。例如删除用户会清理他的科目、会话和记录；删除父科目也会清理子科目。它维护了引用完整性，但删除影响范围较大。

**为什么选择 MySQL**

项目明确使用 MySQL 的证据是 `mysql-connector-j`、`jdbc:mysql:`、`ENGINE=InnoDB` 等配置。作者没有写选择说明，下面是结合代码作出的合理推断：

1. 数据关系清晰，适合关系型数据库和外键约束。
2. 抽奖、重抽会连续更新会话和历史，需要 InnoDB 事务保证操作一起成功或一起回滚。
3. Spring Boot、MyBatis 与 MySQL 集成成熟，学习资料和开发工具多，适合小型 Java Web 项目。
4. 项目是多用户服务，MySQL 比单文件数据库更适合并发访问和持续运行。

MySQL 不是唯一选择：PostgreSQL 同样适合；若只是单机演示，SQLite 会更轻量。这里选择 MySQL 更像是“关系模型合适、Java 生态成熟、部署常见”，不是因为其他数据库无法实现。

**易错点**

- 数据库自关联本身允许三级、四级科目；“只允许一级和子级”是 `SubjectService` 额外限制的业务规则。
- 数据库没有给 `draw_record.session_id` 加唯一约束，因此数据库层仍允许一轮出现多条记录，只是当前业务代码按一轮一个最终记录运行。
- 常用查询会按 `user_id`、时间和状态查找，但脚本没有显式创建组合索引；数据量大后需要结合慢查询补索引。
- 文件开头的 `DROP TABLE` 会清空数据，只适合初始化开发库，不能直接用于已有生产数据。

**可复用经验**

设计表时先区分“主数据、过程状态、最终事实”。会不断变化的流程状态和用于统计的最终记录，通常不应该混在一张表里。

---

### 002. 全局异常处理器逐行理解

**知识点**

`GlobalExceptionHandler` 集中接住 Controller 调用链中未处理的异常，把不同异常转换成统一的 HTTP 状态码和 `Result` JSON。Controller 和 Service 因此只需抛异常，不必在每个接口重复编写错误响应。

**代码位置**

[GlobalExceptionHandler.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/common/GlobalExceptionHandler.java:9)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Result.fail(400, e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey() {
        return ResponseEntity.badRequest().body(Result.fail(400, "数据已存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务器开小差了：" + e.getMessage()));
    }
}
```

**核心逻辑**

```text
Service/Controller 抛出异常
  -> Spring MVC 寻找最匹配的 @ExceptionHandler
  -> 处理方法生成 ResponseEntity<Result<Void>>
  -> ResponseEntity 决定 HTTP 状态码
  -> Result<Void> 变成 JSON 响应体
  -> 前端收到统一错误格式
```

逐行含义：

- `@RestControllerAdvice`：把本类注册成所有 REST Controller 共用的异常处理器；返回值会序列化为 JSON。
- `public class GlobalExceptionHandler`：定义全局异常处理类，本身不承载业务逻辑。
- `@ExceptionHandler(IllegalArgumentException.class)`：声明下面的方法专门处理 `IllegalArgumentException`。本项目用它表达参数错误和不允许的业务操作。
- `ResponseEntity<Result<Void>>`：外层 `ResponseEntity` 控制 HTTP 状态码，内层 `Result<Void>` 是统一 JSON；`Void` 表示失败响应没有业务数据。
- `IllegalArgumentException e`：Spring 把实际捕获到的异常对象传进来，`e.getMessage()` 取得 Service 抛出时写的提示。
- `ResponseEntity.badRequest()`：创建 HTTP 400 响应构建器。
- `.body(Result.fail(400, e.getMessage()))`：把 `{code: 400, message: ..., data: null}` 放入响应体并返回完整响应。
- `@ExceptionHandler(DuplicateKeyException.class)`：处理数据库唯一键重复异常，例如并发注册相同用户名时触发唯一约束。
- `handleDuplicateKey()` 没有参数：方法不需要读取底层数据库异常，只返回固定的“数据已存在”，因此可以不接收异常对象。
- `@ExceptionHandler(Exception.class)`：兜底处理其他未被更具体方法处理的异常。Spring 按异常类型的匹配程度选择，不依赖这三个方法的书写顺序。
- `ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)`：创建 HTTP 500 响应构建器；枚举值比直接写数字更容易读。
- 下一行 `.body(...)`：只是把较长的链式调用换行，仍属于同一条 `return` 语句。

一次参数错误最终会返回：

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json
```

```json
{
  "code": 400,
  "message": "权重必须在 1 到 100 之间",
  "data": null
}
```

**易错点**

- HTTP 状态码和 `Result.code` 是两层状态；这里让二者保持相同，但它们不是同一个字段。
- `Exception.class` 范围最广，却不会抢走更具体异常；Spring 优先选择类型更匹配的处理方法。
- 把所有 `IllegalArgumentException` 都当作 400，依赖开发者只用它表达客户端错误；若代码缺陷也抛出它，可能被误报成用户问题。
- 500 响应拼接 `e.getMessage()` 可能暴露 SQL、表名或内部实现。更安全的做法是服务端记录完整异常，对客户端只返回固定提示和错误编号。
- 这个类主要处理 Spring MVC 请求链中向外抛出的异常，不负责应用启动失败、后台线程异常等其他运行边界。

**可复用经验**

全局异常处理的核心是统一“异常类型 → HTTP 状态 → 对外错误格式”的映射，让业务层负责发现并抛出问题，让 Web 层集中决定怎样告诉客户端。

---

### 003. 用最小启动实验理解 @Component 与 @Bean

**要实现的最小需求**

`WebConfig` 创建时需要一个 `AuthInterceptor` 对象。当前只研究“这个对象从哪里来”，暂时不研究它如何拦截请求。

**代码位置**

- [AuthInterceptor.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/interceptor/AuthInterceptor.java:12)
- [WebConfig.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/WebConfig.java:13)

**MVP 写法一：在类上使用 @Component**

项目当前采用这种写法：

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public AuthInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }
}
```

`WebConfig` 创建时直接声明自己需要这个对象：

```java
private final AuthInterceptor authInterceptor;

public WebConfig(AuthInterceptor authInterceptor) {
    this.authInterceptor = authInterceptor;
}
```

启动时发生的事情：

```text
Spring 看到 @Component
  -> 创建 AuthInterceptor 对象
  -> 创建 WebConfig 时把这个对象传进去
  -> WebConfig 可以正常创建
  -> 项目继续启动
```

**删除 @Component 的实际验证**

把代码临时改成：

```java
// @Component
public class AuthInterceptor implements HandlerInterceptor {
```

项目启动失败，实际错误是：

```text
Parameter 0 of constructor in WebConfig required an AuthInterceptor
that could not be found.
```

这次失败直接证明：

```text
删除 @Component
  -> Spring 没有创建 AuthInterceptor 对象
  -> WebConfig 需要这个对象，但拿不到
  -> 项目无法启动
```

因此，`@Component` 在这里的作用只有一句话：

> 让 Spring 创建 `AuthInterceptor` 对象，供 `WebConfig` 使用。

**MVP 写法二：用 @Bean 明确写出创建过程**

如果不在 `AuthInterceptor` 类上添加 `@Component`，也可以另外写一个创建方法：

```java
@Configuration
public class AuthObjectConfig {

    @Bean
    public AuthInterceptor authInterceptor(
            JwtUtil jwtUtil,
            UserMapper userMapper
    ) {
        return new AuthInterceptor(jwtUtil, userMapper);
    }
}
```

这里的 `@Configuration` 只表示 Spring 需要读取这个类；`@Bean` 表示把该方法返回的对象交给 Spring 使用。

执行过程变成：

```text
Spring 调用 authInterceptor() 方法
  -> 方法执行 new AuthInterceptor(...)
  -> 方法返回 AuthInterceptor 对象
  -> Spring 把这个对象传给 WebConfig
  -> 项目继续启动
```

**两种写法的本质区别**

```text
@Component
  标在类上
  Spring 根据这个类创建对象

@Bean
  标在方法上
  开发者在方法里写出对象怎样创建
```

两种写法最终解决的是同一个问题：让 Spring 得到一个 `AuthInterceptor` 对象。

- 自己编写的类，而且创建过程简单：通常使用 `@Component`。
- 无法修改的外部类，或者需要自己控制创建过程：通常使用 `@Bean`。

本项目的 `AuthInterceptor` 是自己编写的，创建过程也简单，因此选择 `@Component` 更直接。

**易错点**

- `@Component` 只负责创建并提供对象，不代表 `preHandle()` 已经会执行。拦截请求还需要后续代码把这个对象接入请求流程。
- `@Component` 和上面的 `@Bean` 是两种可替换写法，实验时不要同时保留，否则会创建两个 `AuthInterceptor` 对象。
- 判断注解作用不能只背定义。删除它、重新启动并观察失败位置，才能确认下游哪段代码依赖它。

**最小心智模型**

```text
@Component：这个类由 Spring 创建对象。
@Bean：这个方法创建的对象交给 Spring。
```

---

### 004. HandlerInterceptor 与 preHandle 最小框架

**要实现的最小需求**

请求进入 Controller 前，先执行一段检查代码，并根据检查结果决定请求继续还是停止。

**代码位置**

- [AuthInterceptor.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/interceptor/AuthInterceptor.java:13)
- [WebConfig.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/WebConfig.java:29)

**最小框架**

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public AuthInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        return true;
    }
}
```

**HandlerInterceptor 的作用**

`WebConfig` 通过下面的代码接收并使用拦截器：

```java
registry.addInterceptor(authInterceptor);
```

`addInterceptor()` 要求传入符合 `HandlerInterceptor` 规定的对象，因此类声明为：

```java
public class AuthInterceptor implements HandlerInterceptor
```

最小关系：

```text
implements HandlerInterceptor
  -> AuthInterceptor 符合 addInterceptor() 的要求
  -> 可以被加入请求处理流程
```

**preHandle 的作用**

`preHandle()` 在 Controller 处理请求前执行。返回值就是请求能否继续的开关：

```text
return true  -> 继续进入 Controller
return false -> 停止，不进入 Controller
```

三个参数只承担以下职责：

```text
request  -> 读取客户端这次请求的信息
response -> 填写要返回给客户端的内容
handler  -> 这次请求原本准备执行的 Controller 方法
```

**两个属性为什么存在**

```java
private final JwtUtil jwtUtil;
private final UserMapper userMapper;
```

后面的登录检查需要完成两件事：

```text
JwtUtil    -> 从 Token 中取得用户 ID
UserMapper -> 根据用户 ID 查询用户
```

这两行属性不创建对象，只把 `AuthInterceptor` 后面需要反复使用的两个对象保存下来。对象在 `AuthInterceptor` 创建时通过构造方法传入：

```java
public AuthInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
    this.jwtUtil = jwtUtil;
    this.userMapper = userMapper;
}
```

**框架调用链**

```text
HTTP 请求
  -> WebConfig 已经加入 AuthInterceptor
  -> 调用 preHandle()
  -> true：进入 Controller
  -> false：停止请求
```

**易错点**

- `implements HandlerInterceptor` 只让对象符合要求；真正把它加入请求流程的是 `addInterceptor(authInterceptor)`。
- `private final JwtUtil jwtUtil` 只是保存对象，不等于在这一行创建 `JwtUtil`。
- 当前只理解拦截器框架。`OPTIONS`、Token 和用户查询属于下一步具体检查逻辑。

**最小心智模型**

```text
HandlerInterceptor 规定拦截器形式；
preHandle 在 Controller 前决定放行或停止；
两个属性保存检查登录时需要使用的对象。
```

---

### 005. AuthInterceptor 登录验证完整逻辑

**最小需求**

保护需要登录的接口：正式请求只有携带可用 Token，并且 Token 对应的用户仍然存在时，才能进入 Controller。

**代码位置**

- [AuthInterceptor.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/interceptor/AuthInterceptor.java:23)
- [SubjectController.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/SubjectController.java:29)

**最小实现**

```java
@Override
public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
) throws Exception {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
        return true;
    }

    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
        writeUnauthorized(response, "请先登录");
        return false;
    }

    Long userId = jwtUtil.getUserId(header.substring(7));
    if (userId == null || userMapper.findById(userId) == null) {
        writeUnauthorized(response, "登录已过期，请重新登录");
        return false;
    }

    request.setAttribute("userId", userId);
    return true;
}
```

**具体检查顺序**

1. 浏览器跨域询问请求直接放行：

```java
if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
    return true;
}
```

`OPTIONS` 只询问服务器是否允许浏览器发送正式请求，通常不携带 Token。如果这里返回 401，浏览器就不会继续发送真正的 GET 或 POST。放行 `OPTIONS` 不会放行业务请求，后续正式请求仍要继续验权。

2. 检查是否携带正确格式的请求头：

```java
String header = request.getHeader("Authorization");

if (header == null || !header.startsWith("Bearer ")) {
    writeUnauthorized(response, "请先登录");
    return false;
}
```

正确格式是：

```http
Authorization: Bearer 具体Token
```

没有 `Authorization`，或者内容不是以 `Bearer ` 开头，就直接返回 401，不进入 Controller。

3. 从 Token 取得用户 ID，并确认用户仍然存在：

```java
Long userId = jwtUtil.getUserId(header.substring(7));

if (userId == null || userMapper.findById(userId) == null) {
    writeUnauthorized(response, "登录已过期，请重新登录");
    return false;
}
```

`header.substring(7)` 去掉前面的 `Bearer `，只留下 Token。

```text
userId == null
  -> Token 无效、被修改、已过期或无法解析

userMapper.findById(userId) == null
  -> Token 中有用户 ID，但数据库里已经没有这个用户
```

两种情况都表示当前登录凭证不能继续使用，因此统一要求重新登录。提示“登录已过期”是简化表达，更准确的含义是“当前登录凭证无效”。

4. 验证成功后，把用户 ID 交给 Controller：

```java
request.setAttribute("userId", userId);
return true;
```

`setAttribute()` 把用户 ID 临时放入当前请求。Controller 通过同一个名称取出：

```java
public Result<List<Subject>> list(
        @RequestAttribute("userId") Long userId
) {
```

这个值只存在于当前请求，不会写入数据库，请求结束后也不会保留。

**登录失败怎样返回 401**

```java
private void writeUnauthorized(
        HttpServletResponse response,
        String message
) throws Exception {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(
            "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}"
    );
}
```

它依次完成：

```text
设置 HTTP 状态码 401
  -> 声明响应内容是 UTF-8 JSON
  -> 写入 code、message、data
```

返回结构和 `Result` 相同，但这里没有创建 `Result` 对象。原因是 `preHandle()` 只能返回 `true` 或 `false`，不能像 Controller 那样返回 `Result<T>`，所以当前代码直接向 `response` 写 JSON。

**完整调用链**

```text
HTTP 请求
  -> OPTIONS：直接通过
  -> 正式请求：读取 Authorization
  -> 没有 Bearer Token：写入 401，返回 false
  -> Token 无法得到 userId：写入 401，返回 false
  -> 数据库中没有该用户：写入 401，返回 false
  -> 验证成功：把 userId 放进 request
  -> 返回 true
  -> Controller 取得 userId 并继续处理
```

**实操验证**

Postman 可用后先验证一条最小失败路径：

```http
GET http://localhost:8080/api/subjects
```

不添加 `Authorization` 请求头，预期：

```http
HTTP/1.1 401 Unauthorized
```

```json
{
  "code": 401,
  "message": "请先登录",
  "data": null
}
```

**易错点**

- `userId == null` 表示 Token 无法提供可信用户 ID，不是数据库中的用户 ID 字段为空。
- `request.setAttribute()` 只在当前请求内传递数据，不是 Session，也不是数据库存储。
- `writeUnauthorized()` 手写 JSON 只是模仿 `Result` 的字段结构，并没有真正复用 `Result`。

**最小心智模型**

```text
先放过 OPTIONS；
正式请求必须带 Bearer Token；
Token 必须得到仍然存在的用户；
成功就把 userId 交给 Controller，失败就返回 401。
```

---

### 006. 如何判断 Spring 配置类

**最小问题**

看到一个 Java 类时，怎样判断它是不是 Spring 配置类？

**代码位置**

[WebConfig.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/WebConfig.java:9)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
```

**判断依据**

第一，类上明确标记了：

```java
@Configuration
```

这是形式上的直接证据：Spring 会把这个类作为配置类读取。

第二，类中编写的是项目启动时要设置的规则：

```java
addCorsMappings(...)  // 设置跨域规则
addInterceptors(...)  // 设置拦截器规则
```

这是内容上的证据：这些方法不是处理某一次具体请求，而是在项目启动时告诉 Spring，后续请求统一按照什么规则运行。

**它与普通类的本质关系**

配置类不是 Java 提供的新类型。对 Java 来说，`WebConfig` 和 `AuthInterceptor` 都是普通类，也都可以包含属性、方法和 `if-then`。

判断依据不是有没有 `if-then`，而是这段代码在项目中负责什么：

```text
WebConfig
  -> 项目启动时设置后续运行规则

AuthInterceptor
  -> 请求到来时执行一次登录检查
```

因此，本项目中更准确的表达是：

> `WebConfig` 是一个普通 Java 类，但 `@Configuration` 让 Spring 把它作为配置类使用。

**当前项目的最小证据链**

```text
@Configuration
  -> Spring 识别并创建 WebConfig
  -> WebConfig 中保存跨域和拦截器设置
  -> 项目启动时应用这些设置
  -> 后续 HTTP 请求遵守这些规则
```

这里只能先得出“`WebConfig` 是配置类”。Spring 为什么会调用 `addCorsMappings()` 和 `addInterceptors()`，还需要继续理解 `WebMvcConfigurer`。

**易错点**

- 不能根据类里有没有 `if-then` 判断它是不是配置类。
- `@Configuration` 负责让 Spring 识别该类；具体配置方法为什么会被调用，还取决于该类实现的配置接口。

**最小心智模型**

```text
看见 @Configuration：形式上是 Spring 配置类。
看见它在设置全局规则：内容上也在承担配置职责。
```

---

### 007. WebMvcConfigurer 为什么存在

**最小需求**

项目需要在请求进入 Controller 前执行登录检查。要理解 `WebMvcConfigurer`，必须先知道 Spring MVC 在当前项目中的最小作用。

**Spring MVC 的最小作用**

Spring MVC 位于 HTTP 请求和 Controller 之间，负责根据请求地址找到并执行对应的 Controller 方法。

例如：

```text
GET /api/draw/history
  -> Spring MVC 接收请求
  -> 找到 DrawController.history()
  -> 执行该方法
  -> 返回响应
```

本节暂时不展开完整的 Model、View、Controller 理论，只把 Spring MVC 理解为：

> HTTP 请求与 Controller 之间的调度者。

**代码位置**

[WebConfig.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/WebConfig.java:10)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
```

**WebMvcConfigurer 解决什么问题**

Spring MVC 原本只负责把请求交给 Controller。项目现在想增加一条规则：

> 某些请求进入 Controller 前，必须先调用 `AuthInterceptor`。

Spring MVC 需要一个固定的配置入口来接收这条规则。`WebMvcConfigurer` 就规定了这些配置方法，当前项目使用了：

```java
addCorsMappings(...)  // 登记跨域规则
addInterceptors(...)  // 登记拦截器规则
```

因此：

```java
public class WebConfig implements WebMvcConfigurer
```

表示：

> `WebConfig` 按照 Spring MVC 规定的方式提供配置，Spring MVC 可以在启动时调用这些方法读取规则。

**最小实现：登记拦截器规则**

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/register"
                );
    }
}
```

真正登记的规则是：

```java
registry.addInterceptor(authInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/user/login", "/api/user/register");
```

含义：

```text
/api/**
  -> 需要调用 AuthInterceptor

/api/user/login、/api/user/register
  -> 排除，不调用 AuthInterceptor
```

**AuthInterceptor 对象怎样进入 registry**

第一步，Spring 创建 `AuthInterceptor` 对象。

第二步，Spring 创建 `WebConfig` 时，把这个 `AuthInterceptor` 对象传入构造方法：

```java
public WebConfig(AuthInterceptor authInterceptor) {
    this.authInterceptor = authInterceptor;
}
```

此时：

```text
WebConfig
  -> 保存 AuthInterceptor 对象
```

第三步，Spring 创建 `InterceptorRegistry`，然后调用：

```java
webConfig.addInterceptors(registry);
```

第四步，`WebConfig` 把自己保存的 `AuthInterceptor` 对象登记进去：

```java
registry.addInterceptor(authInterceptor);
```

执行完成后，对象关系是：

```text
WebConfig ───────────────┐
                         ▼
                  AuthInterceptor对象
                         ▲
registry 中的路径规则 ───┘
```

`WebConfig` 的确先持有 `AuthInterceptor`，再把同一个对象交给 `registry`。请求阶段不需要重新经过 `WebConfig`，因为 `registry` 已经保存了这个对象和对应路径。

**WebConfig 与 preHandle 的真实关系**

`WebConfig` 不过滤请求，也不会在每次请求时运行。它只在项目启动时登记规则。

可以把启动过程简化为：

```java
// WebConfig 在项目启动时完成的设置
rules.put("/api/**", authInterceptor);
```

请求到来后，是 Spring MVC 查询规则并调用拦截器：

```java
// 用普通 Java 形式简化 Spring MVC 的运行过程
AuthInterceptor interceptor = rules.find(request.getPath());

if (interceptor != null) {
    interceptor.preHandle(request, response, handler);
}
```

因此，真实顺序是：

```text
项目启动
  -> WebConfig 登记路径与 AuthInterceptor 的关系

请求到来
  -> Spring MVC 查找该路径的规则
  -> 需要检查时调用 AuthInterceptor.preHandle()
  -> preHandle 返回 true：执行 Controller
  -> preHandle 返回 false：停止请求
```

`preHandle()` 不会调用 `WebConfig`，也不需要知道 `WebConfig`。它们通过 Spring MVC 连接：

```text
WebConfig 把规则交给 Spring MVC
  -> Spring MVC 保存规则
  -> 请求到来后，Spring MVC 调用 preHandle()
```

**三部分职责边界**

```text
@Configuration
  -> 让 Spring 创建并使用 WebConfig

WebMvcConfigurer
  -> 规定 Spring MVC 可以读取哪些配置方法

addInterceptors()
  -> 登记哪些路径使用哪个拦截器

preHandle()
  -> 请求到来后真正执行登录检查
```

**待实操验证**

临时删除：

```java
implements WebMvcConfigurer
```

但保留方法上的：

```java
@Override
```

预期编译报错，因为 `WebConfig` 不再声明自己遵守 `WebMvcConfigurer`，`@Override` 下面的方法也就没有对应来源。

该实验尚未实际执行，执行并观察错误后再把结果补入笔记。

**易错点**

- `WebConfig` 不执行过滤，它只在启动时登记过滤规则。
- 真正检查请求的是 `AuthInterceptor.preHandle()`；决定何时调用它的是 Spring MVC。
- `preHandle()` 没有使用 `WebConfig`。运行时由 Spring MVC 根据已经登记的规则调用它。

**最小心智模型**

```text
Spring MVC：把 HTTP 请求交给 Controller；
WebMvcConfigurer：提供修改这段处理流程的配置入口；
WebConfig：登记哪些请求需要检查；
preHandle：真正执行检查。
```

---

### 008. Spring 后端启动与请求架构图

**目的**

把“项目启动时设置规则”和“请求到来后执行规则”彻底分开，避免把 `WebConfig` 和 `preHandle()` 理解成同一阶段执行。

**启动阶段：只执行一次**

```text
WhatToLearnApplication.main()
  -> SpringApplication.run()
  -> Spring 开始运行后端主流程
  -> 创建 AuthInterceptor 对象
  -> 创建 WebConfig，并把 AuthInterceptor 传进去
  -> 创建 InterceptorRegistry
  -> 调用 WebConfig.addInterceptors(registry)
  -> WebConfig 把 AuthInterceptor 和路径写入 registry
  -> Spring MVC 保存这些规则
  -> 后端开始等待 HTTP 请求
```

启动完成后，关键关系可以压缩成：

```text
registry
  ├── /api/** -> AuthInterceptor对象
  └── 排除 /api/user/login、/api/user/register
```

**请求阶段：每次请求执行**

```text
浏览器 / Postman
  -> HTTP 请求，例如 GET /api/draw
  -> Spring MVC 接收请求
  -> Spring MVC 查询 registry
  -> /api/draw 符合 /api/**
  -> 从 registry 取得 AuthInterceptor对象
  -> 调用 AuthInterceptor.preHandle()
```

登录检查结果分支：

```text
preHandle() 返回 false
  -> 写入 401 响应
  -> 停止
  -> Controller、Service、Mapper 都不执行

preHandle() 返回 true
  -> Spring MVC 找到 Controller 方法
  -> Controller
  -> Service
  -> Mapper
  -> MySQL
  -> 返回 JSON
```

**各文件的位置**

```text
WhatToLearnApplication
  -> 启动 Spring

WebConfig
  -> 启动时登记路径和拦截器关系

AuthInterceptor
  -> 请求阶段执行登录检查

Controller
  -> 接收已经通过检查的业务请求

Service
  -> 执行业务规则

Mapper
  -> 读写数据库
```

**当前理解边界**

目前只把 Spring MVC 理解为 HTTP 请求和 Controller 之间的调度者。完整 MVC 理论尚未学习，不在这份笔记中假装已经掌握。

**最小心智模型**

```text
启动时：WebConfig 把 AuthInterceptor 和路径登记给 Spring；
请求时：Spring 根据登记结果调用 preHandle；
通过后：Controller -> Service -> Mapper -> MySQL。
```
