# LearnWhat 后端学习笔记

> 只记录已经接触并能帮助理解项目主线的内容。重复解释、未执行实验和旁支知识不进入笔记。

## 快速索引

***项目整体***

[000. 项目地图](#000-项目地图)

***sql/schema.sql***

[001. 数据库四张表](#001-数据库四张表)

***common/GlobalExceptionHandler.java***

[002. 全局异常处理](#002-全局异常处理)

***config/interceptor/AuthInterceptor.java***

[003. @Component 与 @Bean](#003-component-与-bean)

[004. HandlerInterceptor 与 preHandle](#004-handlerinterceptor-与-prehandle)

***config/WebConfig.java***

[005. 登录验证逻辑](#005-登录验证逻辑)

[006. WebConfig 如何接上 AuthInterceptor](#006-webconfig-如何接上-authinterceptor)

***controller/（008 同时涉及 AuthInterceptor）***

[007. @RestController 与 JSON](#007-restcontroller-与-json)

[008. @RequestAttribute 从当前请求取值](#008-requestattribute-从当前请求取值)

[009. RequestMapping、PostMapping 与 GetMapping](#009-requestmappingpostmapping-与-getmapping)

[010. @PathVariable 从路径取值](#010-pathvariable-从路径取值)

[011. Controller 模块整理](#011-controller-模块整理)

***dto/***

[012. DTO 模块整理](#012-dto-模块整理)

***entity/***

[013. Entity 模块整理](#013-entity-模块整理)

***mapper/***

[014. MyBatis 与 MyBatis-Plus 对比](#014-mybatis-与-mybatis-plus-对比)

[015. Mapper 模块整理](#015-mapper-模块整理)

***service/***

[016. UserService：注册与登录](#016-userservice注册与登录)

[017. SubjectService：科目管理规则](#017-subjectservice科目管理规则)

[018. DrawService：抽取流程与状态](#018-drawservice抽取流程与状态)

***util/JwtUtil.java***

[019. Claims：JWT 解析后的数据](#019-claimsjwt-解析后的数据)

---

### 000. 项目地图

LearnWhat 后端提供四类功能：注册登录、科目管理、抽取学习内容、查看抽取记录。

先记住一条业务请求主线：

```text
前端发送 HTTP 请求
  -> Controller 接收请求
  -> Service 执行业务规则
  -> Mapper 读写 MySQL
  -> Controller 返回 Result<T>
  -> 前端收到 JSON
```

各层只记一个职责：

| 层 | 职责 |
|---|---|
| `Controller` | 接收请求、调用 Service、返回响应 |
| `Service` | 执行业务判断 |
| `Mapper` | 读写数据库 |
| `Entity` | 表示数据库中的数据 |
| `DTO` | 表示接口需要的输入或输出 |
| `Result<T>` | 统一响应的 `code、message、data` |

登录保护会在 Controller 之前多经过一次检查：

```text
请求 -> AuthInterceptor -> Controller -> Service -> Mapper -> MySQL
```

---

### 001. 数据库四张表

**代码位置**

[schema.sql](D:/Project/LearnWhat/backend/sql/schema.sql:1)

| 表 | 保存什么 |
|---|---|
| `user` | 用户账号 |
| `subject` | 用户创建的科目；`parent_id` 表示父子科目 |
| `draw_session` | 一次抽取过程及其当前状态 |
| `draw_record` | 已经产生的最终抽取记录 |

关系：

```text
user
  -> subject
  -> draw_session
  -> draw_record

subject.parent_id -> subject.id
draw_record.session_id -> draw_session.id
```

`draw_session` 和 `draw_record` 不能合并：前者记录“抽取进行到哪里”，后者记录“最终抽到了什么”。

**重要易错点**

- [schema.sql 第 7—10 行](D:/Project/LearnWhat/backend/sql/schema.sql:7) 会先删除旧表，再重新建表；不能把这份初始化脚本直接当成线上升级脚本执行。
- 外键保证引用的数据存在；“这个用户是否有权操作该数据”仍由 Service 判断。

---

### 002. 全局异常处理

**需求**

业务代码抛出异常后，统一转换成前端能识别的 HTTP 状态码和 `Result` JSON，避免每个 Controller 重复写错误处理。

**代码位置**

[GlobalExceptionHandler.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/common/GlobalExceptionHandler.java:10)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(
            IllegalArgumentException e
    ) {
        return ResponseEntity.badRequest()
                .body(Result.fail(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务器开小差了：" + e.getMessage()));
    }
}
```

调用链：

```text
Controller / Service 抛出异常
  -> 匹配对应的 @ExceptionHandler 方法
  -> ResponseEntity 设置 HTTP 状态码
  -> Result 形成 code、message、data
  -> 返回 JSON
```

**重要易错点**

`HTTP 状态码`和 JSON 里的`code`是两个位置。本项目让二者保持相同，但不能把它们当成同一个字段。

---

### 003. @Component 与 @Bean

两者的共同目的：把一个对象交给 Spring 保存，供其他对象使用。

区别只看对象由谁创建：

```java
@Component
public class AuthInterceptor {
}
```

`@Component` 标在类上：Spring 根据这个类创建对象。

```java
@Configuration
public class AppConfig {
    @Bean
    public SomeClient someClient() {
        return new SomeClient();
    }
}
```

`@Bean` 标在方法上：开发者在方法里创建对象，再把返回值交给 Spring。

**本项目已验证**

删除 [AuthInterceptor.java 第 12 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/interceptor/AuthInterceptor.java:12) 的 `@Component` 后，项目启动失败：

```text
WebConfig required a bean of type 'AuthInterceptor' that could not be found
```

因果关系：

```text
删除 @Component
  -> Spring 没有创建 AuthInterceptor 对象
  -> 创建 WebConfig 时没有对象可以传入
  -> 启动失败
```

结论：本项目使用 `@Component` 让 Spring 创建 `AuthInterceptor`；当前代码没有用 `@Bean` 替代它。

---

### 004. HandlerInterceptor 与 preHandle

**需求**

需要在请求进入 Controller 前检查登录状态。

**代码位置**

[AuthInterceptor.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/interceptor/AuthInterceptor.java:13)

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        return true;
    }
}
```

- `HandlerInterceptor` 规定了拦截器可以实现的方法。
- `preHandle` 在 Controller 方法执行前被调用。
- 返回 `true`：继续进入 Controller。
- 返回 `false`：停止，不进入 Controller。
- `request`：本次请求的信息。
- `response`：本次请求要返回给客户端的响应。

两个属性：

```java
private final JwtUtil jwtUtil;
private final UserMapper userMapper;
```

- `jwtUtil`：从 Token 中取得用户 ID。
- `userMapper`：去数据库确认该用户仍然存在。
- `final`：构造方法赋值后，这两个引用不能再指向其他对象。

仅仅 `implements HandlerInterceptor` 不会让它自动拦截请求；还必须由 `WebConfig` 登记使用范围。

---

### 005. 登录验证逻辑

**代码位置**

[AuthInterceptor.java 的 preHandle](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/interceptor/AuthInterceptor.java:23)

```java
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

按执行顺序理解：

1. `OPTIONS` 是浏览器正式跨域请求前的询问请求，直接放行；正式请求仍会重新验权。
2. 从请求头读取 `Authorization`，要求格式为 `Bearer 具体Token`。
3. `substring(7)` 去掉 `Bearer `；`jwtUtil` 从剩余 Token 中取得 `userId`。
4. `userId == null` 说明 Token 不能提供可信身份；数据库查不到用户说明该身份已经不存在。两种情况都返回 401。
5. 验证成功后，把 `userId` 临时放进当前请求，再放行。

Controller 通过相同名字取出它：

```java
public Result<List<Subject>> list(
        @RequestAttribute("userId") Long userId
) {
    return Result.success(subjectService.list(userId));
}
```

对应位置：[SubjectController.java 第 29 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/SubjectController.java:29)

验证失败时：

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

`preHandle` 的返回类型只能是 `boolean`，不能像 Controller 一样返回 `Result<T>`，所以当前代码直接向 `response` 写入 401 JSON。它的字段形状与 `Result` 相同，但没有创建 `Result` 对象。

完整分支：

```text
OPTIONS -> true
正式请求没有 Bearer Token -> 写 401 -> false
Token 得不到有效用户 -> 写 401 -> false
验证成功 -> request 放入 userId -> true -> Controller
```

---

### 006. WebConfig 如何接上 AuthInterceptor

**代码位置**

[WebConfig.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/WebConfig.java:9)

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

只分清两个阶段。

**启动时：登记规则**

```text
Spring 创建 AuthInterceptor
  -> 创建 WebConfig，并把 AuthInterceptor 传给构造方法 （单例模式构造方法注入不用@Autowired）
  -> 调用 addInterceptors(registry)
  -> WebConfig 把 AuthInterceptor 和路径规则登记进 registry
```

登记结果：

```text
/api/** -> 使用 AuthInterceptor
/api/user/login、/api/user/register -> 不使用 AuthInterceptor
```

`registry` 不是我们调用该方法时传入的。`addInterceptors` 是 `WebMvcConfigurer` 规定的配置方法，Spring 在启动时调用它，并传入自己正在使用的 `InterceptorRegistry`。

**请求时：执行规则**

```text
请求到达
  -> Spring 根据已登记的路径规则判断是否需要 AuthInterceptor
  -> 需要时调用 AuthInterceptor.preHandle()
  -> true：进入 Controller
  -> false：返回 401，停止
```

边界：

- `WebConfig` 只在启动时登记“哪些路径使用哪个拦截器”，不亲自检查每次请求。
- `AuthInterceptor.preHandle()` 负责真正的登录检查，但它不知道自己会被哪些路径使用。
- 两者由 Spring 保存的登记结果连接起来；`preHandle()` 不会反过来调用 `WebConfig`。

最终只记这一条：

```text
启动时：WebConfig 登记规则
请求时：Spring 按规则调用 preHandle
通过后：Controller -> Service -> Mapper -> MySQL
```

---

### 007. @RestController 与 JSON

**代码位置**

[DrawController.java 第 17 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/DrawController.java:17)

```java
@RestController
public class DrawController {
}
```

`@RestController` 表示这个类接收 HTTP 请求，并把方法返回值写入 HTTP 响应正文。它相当于：

```java
@Controller
@ResponseBody
```

其中没有哪个单词单独表示 JSON：

```text
@ResponseBody
  -> 把方法返回值写入响应正文

Spring 的 JSON 转换工具
  -> 把 Result 等 Java 对象转换成 JSON
```

**结论**

`@RestController` 让 Controller 方法直接返回响应数据；对象最终变成 JSON，是 Spring 的转换工具完成的。

---

### 008. @RequestAttribute 从当前请求取值

**代码位置**

- [AuthInterceptor.java 第 40 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/config/interceptor/AuthInterceptor.java:40)
- [DrawController.java 第 27 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/DrawController.java:27)

拦截器先把用户 ID 临时放进当前请求：

```java
request.setAttribute("userId", userId);
```

Controller 再用相同名字取出：

```java
public Result<DrawResult> draw(
        @RequestAttribute("userId") Long userId
) {
    return Result.success("抽奖成功", drawService.draw(userId));
}
```

调用链：

```text
AuthInterceptor 把 userId 放进 request
  -> DrawController 用 @RequestAttribute("userId") 取出
  -> 传给 DrawService
```

**结论**

`@RequestAttribute("userId")` 读取的是服务器此前放进当前请求的临时数据，不是前端直接传来的参数；本次请求结束后，该值也随之消失。

---

### 009. RequestMapping、PostMapping 与 GetMapping

**代码位置**

[DrawController.java 第 18—47 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/DrawController.java:18)

一个接口由“请求方式 + 请求路径”共同确定。

```java
@RequestMapping("/api/draw")
public class DrawController {

    @PostMapping("/sub")
    public Result<DrawResult> drawSub(...) { }

    @GetMapping("/history")
    public Result<List<DrawHistoryItem>> history(...) { }
}
```

| 注解 | 当前文件中的作用 | 最终接口 |
|---|---|---|
| `@RequestMapping("/api/draw")` | 设置整个类的公共路径 | `/api/draw` |
| `@PostMapping("/sub")` | 只接收 POST 请求 | `POST /api/draw/sub` |
| `@GetMapping("/history")` | 只接收 GET 请求 | `GET /api/draw/history` |

类上的公共路径与方法上的路径会拼接起来：

```text
/api/draw + /sub     -> /api/draw/sub
/api/draw + /history -> /api/draw/history
```

**结论**

`@RequestMapping` 在这里提供公共路径；`@PostMapping` 和 `@GetMapping` 进一步限定方法路径与 HTTP 请求方式。请求方式不匹配时，不会进入对应方法。

---

### 010. @PathVariable 从路径取值

**代码位置**

[SubjectController.java 第 38—40 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/SubjectController.java:38)

```java
@PutMapping("/{id}")
public Result<Subject> update(
        @PathVariable Long id
) {
}
```

请求中的实际路径：

```http
PUT /api/subjects/12
```

取值过程：

```text
路径中的 {id}
  -> 字符串 "12"
  -> 转换成 Long 类型的 12
  -> 赋给参数 id
```

**结论**

`@PathVariable Long id` 从网址路径的 `{id}` 位置取值，不从请求体中取值。

---

### 011. Controller 模块整理

三个 Controller 的共同职责：

```text
接收 HTTP 请求和参数
  -> 调用对应 Service
  -> 用 Result<T> 包装结果
  -> 返回 JSON
```

Controller 不负责具体业务规则，也不直接调用 Mapper 或编写 SQL。

#### UserController：用户注册和登录

[UserController.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/UserController.java:12)

公共路径：`/api/user`

| 接口 | 输入 | 调用 |
|---|---|---|
| `POST /api/user/register` | 请求体 `UserRequest` | `userService.register(request)` |
| `POST /api/user/login` | 请求体 `UserRequest` | `userService.login(request)` |

这两个接口用于取得登录身份，因此在 `WebConfig` 中被排除，不经过登录拦截器。

#### SubjectController：科目管理

[SubjectController.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/SubjectController.java:19)

公共路径：`/api/subjects`

| 接口 | 作用 | 主要输入 | 调用 |
|---|---|---|---|
| `GET /api/subjects` | 查询科目 | `userId` | `subjectService.list` |
| `POST /api/subjects` | 新增科目 | `userId + SubjectRequest` | `subjectService.add` |
| `PUT /api/subjects/{id}` | 修改科目 | `userId + id + SubjectRequest` | `subjectService.update` |
| `DELETE /api/subjects/{id}` | 删除科目 | `userId + id` | `subjectService.delete` |

#### DrawController：抽取与查询

[DrawController.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/controller/DrawController.java:17)

公共路径：`/api/draw`

| 接口 | 作用 | 调用 |
|---|---|---|
| `POST /api/draw` | 主科目抽取 | `drawService.draw` |
| `POST /api/draw/sub` | 子科目抽取 | `drawService.drawSub` |
| `POST /api/draw/redraw` | 重抽 | `drawService.redraw` |
| `GET /api/draw/availability` | 查询能否抽取 | `drawService.availability` |
| `GET /api/draw/history` | 查询抽取历史 | `drawService.history` |
| `GET /api/draw/latest` | 查询最近一次记录 | `drawService.latest` |
| `GET /api/draw/stats` | 查询抽取统计 | `drawService.stats` |

DrawController 的所有方法都从当前请求取得 `userId`，再交给 `DrawService`。

**最小心智模型**

```text
UserController    -> UserService
SubjectController -> SubjectService
DrawController    -> DrawService

Controller 只负责 HTTP 边界；真正的业务逻辑继续到 Service 阅读。
```

---

### 012. DTO 模块整理

DTO 是专门装数据的对象，用于在前端、Controller、Service 和 Mapper 之间传递数据；它本身不执行业务逻辑。

#### 输入 DTO：前端传给后端

| DTO | 字段 | 用途 |
|---|---|---|
| [UserRequest](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/dto/UserRequest.java:3) | `username、password` | 接收注册或登录信息，交给 `UserService` |
| [SubjectRequest](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/dto/SubjectRequest.java:3) | `name、color、weight、parentId` | 接收新增或修改科目的信息，交给 `SubjectService` |

调用方向：

```text
前端 JSON
  -> Controller 用 @RequestBody 转成 Request DTO
  -> Service 读取 DTO 中的字段
```

#### 输出 DTO：后端返回给前端

| DTO | 谁准备数据 | 装什么 |
|---|---|---|
| [LoginResponse](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/dto/LoginResponse.java:3) | `UserService` | 登录后的 `token、userId、username` |
| [DrawResult](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/dto/DrawResult.java:8) | `DrawService` | 本次抽取结果、会话 ID、是否需要继续抽、是否完成、能否重抽 |
| [DrawAvailability](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/dto/DrawAvailability.java:7) | `DrawService` | 当前能否抽取、是否等待子抽取或重抽、冷却时间和提示 |
| [DrawHistoryItem](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/dto/DrawHistoryItem.java:5) | `DrawRecordMapper` 查询 | 一条抽取历史，包括科目、父科目、颜色、权重和抽取时间 |
| [DrawStatsItem](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/dto/DrawStatsItem.java:3) | `DrawRecordMapper` 查询 | 一个科目的名称、颜色和累计抽中次数 |

调用方向：

```text
Service / Mapper 准备 Response DTO
  -> Controller 放进 Result<T>
  -> Spring 转换成 JSON
  -> 前端
```

#### DTO 与 Entity 的区别

```text
Entity：描述数据库中保存的数据
DTO：描述某个接口需要接收或返回的数据
```

例如登录只需要向前端返回 `token、userId、username`，所以使用 `LoginResponse`，不把包含密码的 `User` Entity 直接返回。

**最小心智模型**

```text
Request DTO  把前端输入送进业务层
Response DTO 把业务结果送回前端
DTO 只装数据，不做业务判断
DTO 内只有属性与setter和getter方法
```

---

### 013. Entity 模块整理

Entity 表示数据库中的数据。Mapper 查询数据库后把结果装成 Entity；Service 也会创建或修改 Entity，再交给 Mapper 保存。

项目有 4 个 Entity，对应 4 张表：

| Entity | 对应表 | 核心内容 | 主要用途 |
|---|---|---|---|
| [User](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/entity/User.java:5) | `user` | `id、username、password、createdAt` | 注册、登录和确认用户是否存在 |
| [Subject](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/entity/Subject.java:5) | `subject` | 名称、颜色、权重、所属用户、父科目 | 科目管理和按权重抽取 |
| [DrawSession](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/entity/DrawSession.java:5) | `draw_session` | 本次抽取的主科目、最终科目、状态、是否重抽、冷却时间 | 保存一次抽取过程进行到了哪里 |
| [DrawRecord](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/entity/DrawRecord.java:5) | `draw_record` | 抽中的科目、父科目、所属会话和抽取时间 | 保存最终抽取结果，供历史和统计查询 |

#### Subject：学科与父子关系

```text
userId   -> 该科目属于哪个用户
parentId -> 父科目 ID；为 null 表示顶层科目
weight   -> 抽取时使用的权重
```

`childCount` 是例外：它不在 `subject` 表中，而是 [SubjectMapper.java 第 16—18 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/mapper/SubjectMapper.java:16) 查询时用 `COUNT` 计算出来，再装进 `Subject`。

#### DrawSession 与 DrawRecord

```text
DrawSession：过程
  -> 当前状态是什么
  -> 是否还需要抽子科目
  -> 是否用过重抽
  -> 下次何时可以抽

DrawRecord：结果
  -> 最终抽中了哪个科目
  -> 属于哪次抽取会话
  -> 什么时候抽中
```

两者不能合并，因为抽取可能尚未完成，但过程状态仍然需要保存。

#### Entity 之间的连接

```text
User.id
  -> Subject.userId
  -> DrawSession.userId
  -> DrawRecord.userId

Subject.id
  -> Subject.parentId
  -> DrawSession.mainSubjectId / finalSubjectId
  -> DrawRecord.subjectId / parentSubjectId

DrawSession.id
  -> DrawRecord.sessionId
```

#### Entity 与 DTO

```text
Entity：围绕数据库表组织数据
DTO：围绕一次接口的输入或输出组织数据
```

例如 `User` 包含数据库中的密码字段，不能直接作为登录响应；登录响应使用不含密码的 `LoginResponse`。

**最小心智模型**

```text
Mapper <-> Entity <-> Service

User 和 Subject 保存基础数据；
DrawSession 保存抽取过程；
DrawRecord 保存抽取结果。
```

---

### 014. MyBatis 与 MyBatis-Plus 对比

MyBatis-Plus 不是 MyBatis 的替代品，而是在 MyBatis 基础上增加常用功能。

| 对比项 | MyBatis | MyBatis-Plus |
|---|---|---|
| 简单增删改查 | 通常自己写 SQL | `BaseMapper` 已提供常用方法 |
| Mapper 写法 | 普通接口，通过注解或 XML 绑定 SQL | 通常继承 `BaseMapper<Entity>` |
| 条件查询 | 自己编写 SQL 条件 | 可以用 `QueryWrapper` 等对象拼条件 |
| 复杂 SQL | 自己写，控制直接 | 仍然通常需要自己写 |
| 代码量 | SQL 较多 | 简单 CRUD 代码更少 |
| 学习重点 | SQL 与对象如何映射 | 在 MyBatis 基础上掌握自动 CRUD 和条件构造 |

#### MyBatis 写法：当前项目

[UserMapper.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/mapper/UserMapper.java:10)

```java
@Mapper
public interface UserMapper {
    @Select("SELECT id, username, password, created_at " +
            "FROM `user` WHERE id = #{id}")
    User findById(@Param("id") Long id);
}
```

开发者明确写出 SQL，再调用：

```java
User user = userMapper.findById(id);
```

#### MyBatis-Plus 常见写法

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

不需要为按 ID 查询单独写 SQL，可以直接调用已有方法：

```java
User user = userMapper.selectById(id);
```

这只减少重复的简单 CRUD。遇到多表关联、统计或特殊排序时，MyBatis-Plus 仍然允许并经常需要编写自定义 SQL。

#### 当前项目为什么是 MyBatis

```text
pom.xml 使用 mybatis-spring-boot-starter
  + 注解来自 org.apache.ibatis.annotations
  + SQL 完整写在 @Insert / @Select 等注解中
  + Mapper 没有继承 BaseMapper<Entity>
```

**结论**

```text
MyBatis：开发者主要自己控制 SQL
MyBatis-Plus：基于 MyBatis，替开发者生成常见的简单 CRUD
```

---

### 015. Mapper 模块整理

你的理解基本正确：Mapper 把 SQL 操作绑定成有参数类型和返回类型的 Java 方法。

```text
Service 调用 Mapper 方法
  -> MyBatis 读取方法上的 SQL 注解
  -> 把 Java 参数填入 SQL
  -> 执行数据库操作
  -> 把查询结果装成 Entity / DTO
  -> 返回给 Service
```

项目中只定义了 Mapper 接口，没有手写实现类。真正执行上述流程的实现对象由 MyBatis 在项目运行时提供。

#### 四个 Mapper 的分工

| Mapper | 操作对象 | Java 方法 |
|---|---|---|
| [UserMapper](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/mapper/UserMapper.java:10) | `user` 表 | 按用户名查询、按 ID 查询、新增用户 |
| [SubjectMapper](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/mapper/SubjectMapper.java:14) | `subject` 表 | 查询全部/顶层/子科目、按 ID 查询、新增、修改、删除 |
| [DrawSessionMapper](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/mapper/DrawSessionMapper.java:11) | `draw_session` 表 | 新增会话、查询最近会话、更新会话状态 |
| [DrawRecordMapper](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/mapper/DrawRecordMapper.java:15) | `draw_record` 表 | 新增/查询/删除记录，以及查询历史、最近记录和统计数据 |

#### 方法、SQL 与返回值

```java
@Select("SELECT ... FROM `user` WHERE id = #{id}")
User findById(@Param("id") Long id);
```

```text
方法参数 Long id
  -> #{id} 填入 SQL
  -> 数据库返回一行
  -> MyBatis 装成 User
  -> 返回给调用方
```

写操作通常返回受影响的行数：

```java
int insert(User user);
int update(Subject subject);
int deleteByIdAndUserId(Long id, Long userId);
```

查询操作根据结果数量返回：

```text
一行   -> User、Subject、DrawSession 等对象
多行   -> List<Subject> 等集合
统计结果 -> DrawHistoryItem、DrawStatsItem 等 DTO
```

#### Mapper 的职责边界

```text
Mapper 负责：数据库操作和查询结果转换
Service 负责：判断这次操作是否符合业务规则
```

例如 `SubjectMapper` 的 SQL 会同时使用 `id` 和 `userId` 限制数据范围，但“允许不允许修改科目”的完整业务判断仍由 `SubjectService` 负责。

**最小心智模型**

```text
Mapper = SQL 操作对应的 Java 方法入口
MyBatis = 在方法调用和数据库执行之间完成连接
```

---

### 016. UserService：注册与登录

**代码位置**

[UserService.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/service/UserService.java:11)

依赖关系：

```text
UserService
  -> UserMapper：查询和保存用户
  -> BCryptPasswordEncoder：加密或核验密码
  -> JwtUtil：登录成功后生成 Token
```

#### register：注册

```text
UserRequest
  -> 检查用户名和密码
  -> 去掉用户名首尾空格
  -> 查询用户名是否已存在
  -> BCrypt 加密密码
  -> 创建 User 并写入数据库
  -> 使用新用户 ID 生成 Token
  -> 返回 LoginResponse
```

#### login：登录

```text
UserRequest
  -> 检查用户名和密码
  -> 按用户名查询 User
  -> 核验明文密码与数据库中的加密密码
  -> 生成 Token
  -> 返回 LoginResponse
```

`validateUserRequest()` 统一检查：用户名和密码不能为空、用户名不超过 50 个字符、密码至少 4 位。

**职责边界**

```text
UserController：接收注册/登录请求
UserService：执行账号规则、密码处理和 Token 生成
UserMapper：读写 user 表
```

密码写入数据库前会经过 BCrypt 加密，不保存明文密码。

---

### 017. SubjectService：科目管理规则

**代码位置**

[SubjectService.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/service/SubjectService.java:11)

它只依赖 `SubjectMapper`，负责科目的查询、新增、修改和删除。

| 方法 | 主要流程 |
|---|---|
| `list(userId)` | 查询该用户的全部科目 |
| `add(userId, request)` | 校验请求 -> 转成 Subject -> 插入 -> 重新查询并返回 |
| `update(userId, id, request)` | 校验请求 -> 更新 -> 更新不到则报错 -> 重新查询并返回 |
| `delete(userId, id)` | 按 `id + userId` 删除 -> 删除不到则报错 |

真正的业务规则集中在 `toSubject()`：

```text
名称不能为空
  + 颜色必须是 #RRGGBB；未传时使用默认色
  + 权重必须在 1 到 100；未传时使用 1
  + 科目不能成为自己的子科目
  + 父科目必须属于当前用户
  + 子科目只能挂在一级科目下
  + 已有子科目的一级科目不能再变成子科目
```

随后把接口输入转换成数据库对象：

```text
SubjectRequest + userId
  -> Subject
  -> SubjectMapper
```

更新和删除都检查 Mapper 返回的受影响行数：`0` 表示该用户没有这条科目记录，因此抛出“科目不存在”。

**职责边界**

```text
SubjectController：接收参数
SubjectService：校验科目和父子关系
SubjectMapper：执行 subject 表 SQL
```

---

### 018. DrawService：抽取流程与状态

**代码位置**

[DrawService.java](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/service/DrawService.java:21)

依赖关系：

```text
SubjectMapper     -> 查询候选科目
DrawSessionMapper -> 保存一次抽取进行到哪里
DrawRecordMapper  -> 保存最终抽取结果
```

核心状态：

```text
PENDING_CHILD：主科目已经抽出，等待抽子科目
COMPLETED：本轮抽取已经得到最终结果
COOLDOWN_HOURS = 2：完成后冷却两小时
每轮冷却期间最多重抽一次
```

#### draw：开始主抽取

```text
读取最近 DrawSession
  -> 有待完成的子抽取：拒绝
  -> 仍在冷却：拒绝
  -> 按权重抽取一级科目
  -> 创建 DrawSession
  -> 没有子科目：直接完成
  -> 有子科目：返回 requiresSubDraw=true，等待 drawSub
```

#### drawSub：完成子抽取

```text
读取最近 DrawSession
  -> 必须是 PENDING_CHILD
  -> 查询主科目及其子科目
  -> 按权重抽取子科目
  -> 完成会话并写入 DrawRecord
```

#### redraw：使用一次重抽机会

```text
最近会话必须已完成
  -> 必须仍在冷却期
  -> 本轮不能已经重抽过
  -> 删除这次会话原来的 DrawRecord
  -> 重新抽取一级科目
  -> 标记 redrawUsed=true
  -> 重新进入完成或等待子抽取流程
```

#### 查询方法

| 方法 | 返回内容 |
|---|---|
| `availability()` | 当前能否抽、是否需要子抽、能否重抽、剩余冷却时间 |
| `history()` | 全部抽取历史 |
| `latest()` | 最近一条抽取记录 |
| `stats()` | 各科目的抽中次数 |

#### 两个关键辅助方法

```text
weightedRandom()
  -> 按科目 weight 进行加权随机

completeSession()
  -> 将 Session 改为 COMPLETED
  -> 写入最终 DrawRecord
  -> 组装 DrawResult
```

`draw()`、`drawSub()` 和 `redraw()` 使用 `@Transactional`，因为每次操作可能同时修改 Session 和 Record；中途失败时，这次操作涉及的数据库修改需要一起撤销。

**完整状态主线**

```text
开始主抽
  -> 一级科目没有子科目 -> COMPLETED -> DrawRecord
  -> 一级科目存在子科目 -> PENDING_CHILD
       -> 子抽取 -> COMPLETED -> DrawRecord

COMPLETED 且仍在冷却期
  -> 可重抽一次
  -> 冷却结束后可开始下一轮
```

---

### 019. Claims：JWT 解析后的数据

**代码位置**

[JwtUtil.java 第 29—33 行](D:/Project/LearnWhat/backend/src/main/java/com/example/whattolearn/util/JwtUtil.java:29)

`Claims` 是 JWT 库提供的接口：

```java
import io.jsonwebtoken.Claims;
```

它表示 JWT 中已经解析出来的数据，本质上是一组“名字 -> 值”。

本项目生成 Token 时，把用户 ID 放在 `subject` 中：

```java
.setSubject(String.valueOf(userId))
```

读取 Token 时，先解析得到 `Claims`，再从中取出 `subject`：

```java
Claims claims = Jwts.parser()
        .setSigningKey(SECRET)
        .parseClaimsJws(token)
        .getBody();

return Long.valueOf(claims.getSubject());
```

调用链：

```text
Token 字符串
  -> 解析并校验
  -> Claims
  -> claims.getSubject()
  -> userId
```

**结论**

`Claims` 不是整个 Token，也不是项目自己定义的类；它是 JWT 被解析后得到的数据集合。本项目通过它取回生成 Token 时保存的用户 ID。
