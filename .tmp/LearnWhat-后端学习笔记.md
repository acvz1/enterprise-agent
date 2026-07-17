# LearnWhat 后端学习笔记

> 只记录已经接触并能帮助理解项目主线的内容。重复解释、未执行实验和旁支知识不进入笔记。

## 快速索引

- [000. 项目地图](#000-项目地图)
- [001. 数据库四张表](#001-数据库四张表)
- [002. 全局异常处理](#002-全局异常处理)
- [003. @Component 与 @Bean](#003-component-与-bean)
- [004. HandlerInterceptor 与 preHandle](#004-handlerinterceptor-与-prehandle)
- [005. 登录验证逻辑](#005-登录验证逻辑)
- [006. WebConfig 如何接上 AuthInterceptor](#006-webconfig-如何接上-authinterceptor)

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
  -> 创建 WebConfig，并把 AuthInterceptor 传给构造方法
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
