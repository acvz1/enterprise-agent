# 企业知识库 Agent 项目学习笔记

> 用途：记录通过二次开发 Java 企业知识库 Agent 学到的后端架构、Spring、RAG、Agent 工程知识、项目理解方法与踩坑。

## 快速索引

- [项目地图](#项目地图)
- [学习记录](#学习记录)
- ***FileUploadController.java***
- [001. `MultipartFile` 是什么，有什么用？](#001-multipartfile-是什么有什么用)
- [002. `FileUploadController.uploadFileAsync()` 的四个部分](#002-fileuploadcontrolleruploadfileasync-的四个部分)
- ***DocumentProcessingService.java***
- [003. `UUID` 是什么，在上传任务里有什么用？](#003-uuid-是什么在上传任务里有什么用)
- [004. 为什么先记录指标、保存进度，再启动异步处理？](#004-为什么先记录指标保存进度再启动异步处理)
- [005. 从同步调用到线程池：一条完整的异步主线](#005-从同步调用到线程池一条完整的异步主线)
- [006. Spring 怎样把方法调用提交给线程池](#006-spring-怎样把方法调用提交给线程池)
- ***Spring 常见注解***
- [007. Spring 常见注解：按程序运行顺序整理](#007-spring-常见注解按程序运行顺序整理)

## 项目地图

### 项目类型

这是一个使用 Java 21、Spring Boot、Vue、MySQL 和 Redis Stack 开发的企业知识库 AI 应用。

它当前已经具备文档上传、解析、分块、向量检索、RAG 问答、会话记忆、登录权限和流式输出，但还没有 Agent Loop、Tool Calling、MCP 和 A2A，因此目前更准确的定位是 **RAG 知识库系统**，不是完整 Agent。

### 整体架构

```text
Vue 前端
  -> HTTP / SSE
  -> Spring Security 校验 JWT
  -> Controller 接收请求
  -> Service 执行业务流程
       ├─ Repository -> MySQL
       ├─ RedisTemplate -> 回答缓存和会话记忆
       ├─ Redis EmbeddingStore -> 文档向量
       ├─ 本地文件目录 -> 上传的原始文件
       └─ ModelFactory -> 大模型 API
  -> Controller 将结果返回前端
```

先把后端分层理解成一条线：

```text
Controller：处理 HTTP 输入输出
    ↓
Service：组织业务步骤
    ↓
Repository：访问 MySQL
    ↓
Entity：对应数据库中的数据
```

`Config` 负责把线程池、Redis、安全规则等基础设施创建出来；`DTO` 负责表达接口传输的数据结构。

### 目录分工

| 目录 | 作用 | 当前是否需要逐个阅读 |
| --- | --- | --- |
| `src/main/java/com/kb/demo/controller` | HTTP 接口入口 | 先读主线 Controller |
| `src/main/java/com/kb/demo/service` | 登录、上传、解析、检索和问答业务 | 核心阅读目录 |
| `src/main/java/com/kb/demo/repository` | 通过 JPA 查询和保存 MySQL 数据 | 跟随对应 Service 阅读 |
| `src/main/java/com/kb/demo/entity` | 文档、用户、任务等数据结构 | 用到哪个读哪个 |
| `src/main/java/com/kb/demo/dto` | 请求和响应的数据结构 | 不需要单独从头读 |
| `src/main/java/com/kb/demo/config` | Spring、线程池、Redis、模型和安全配置 | 跟随具体主线阅读 |
| `src/main/java/com/kb/demo/security` | JWT 创建、解析和请求认证 | 学登录链时阅读 |
| `src/main/java/com/kb/demo/init` | 启动时创建示例数据、角色和权限 | 后读 |
| `src/test/java` | 验证主流程、失败场景和异步线程 | 每学完一条主线就对照测试 |
| `ai-assistant-front/src` | Vue 页面、组件、API 请求和登录状态 | 后端主线理解后再读 |
| `mysql` | 初始化数据库表和默认数据 | 读实体关系时查看 |
| `docker` | 启动 MySQL、Redis、应用和监控 | 学部署时查看 |

`target`、`node_modules`、`dist`、`.m2-cache` 和 `.npm-cache` 都是生成结果或依赖缓存，不属于项目源码，不要阅读。

### 后端关键文件

#### 程序入口与配置

| 文件 | 作用 |
| --- | --- |
| `AiKnowledgeBaseApplication.java` | Java 程序入口，启动 Spring Boot |
| `application.yml` | 统一配置端口、MySQL、Redis、模型、JWT、上传目录和监控 |
| `AsyncConfig.java` | 创建文件处理线程池，供 `@Async("taskExecutor")` 使用 |
| `SecurityConfig.java` | 配置公开接口、JWT 认证、权限检查和密码编码器 |
| `RedisConfig.java` | 创建操作 Redis 的两个 `RedisTemplate` |
| `ModelConfig.java` | 把 `application.yml` 中的模型配置绑定成 Java 对象 |
| `JpaConfig.java` | 开启 JPA 创建时间和更新时间审计 |
| `VirtualThreadConfig.java` | 创建 Spring 默认虚拟线程执行器；不是文件处理专用线程池 |
| `CorsConfig.java` | 配置浏览器跨域访问规则 |

#### HTTP 入口

| 文件 | 作用 |
| --- | --- |
| `FileUploadController.java` | 接收文件、校验类型、调用异步上传并提供进度查询 |
| `AiController.java` | 普通问答、SSE 流式问答、模型列表、会话和回答反馈接口 |
| `DocumentController.java` | 文档增删改查、搜索、分类标签和向量重建接口 |
| `VectorSearchController.java` | 向量检索、混合检索和相关段落接口 |
| `AuthController.java` | 登录、注册和刷新 JWT |
| `AnalyticsController.java` | Dashboard 统计数据接口 |
| `DocumentVersionController.java` | 文档版本创建、比较和回滚接口 |
| `DocumentCategoryController.java` | 分类增删改查接口 |
| `DocumentTagController.java` | 标签增删改查接口 |

#### 文档上传与知识入库

| 文件 | 作用 |
| --- | --- |
| `DocumentProcessingService.java` | 在请求线程中保存原文件、创建 `PENDING` 任务并提交后台 Worker |
| `DocumentProcessingWorker.java` | 在 `file-processing-*` 线程中解析、保存、分块、向量化并更新状态 |
| `DocumentFileStorage.java` | 把临时 `MultipartFile` 保存到请求结束后仍可读取的目录 |
| `FileParseService.java` | 使用 Tika 解析 PDF、Word 等文件正文和元数据 |
| `DocumentService.java` | 保存、修改、查询和删除文档，并协调版本与分块 |
| `DocumentChunkService.java` | 将文档切块，块正文写入 MySQL，向量写入 Redis Stack |
| `UploadProgress.java` | 一次后台上传任务的状态、进度和错误信息 |
| `UploadProgressRepository.java` | 根据 `uploadId` 查询或更新上传任务 |
| `Document.java` | 文档标题、正文、类型和版本等主数据 |
| `DocumentChunk.java` | 文档中的一个分块及其序号 |
| `DocumentRepository.java` | 文档保存以及标题、正文、分类、标签等查询 |
| `DocumentChunkRepository.java` | 文档块保存、查询和删除 |

#### RAG 问答

| 文件 | 作用 |
| --- | --- |
| `AiService.java` | 当前 RAG 总编排器：缓存、检索、记忆、提示词、模型和流式后处理 |
| `VectorSearchService.java` | 向量检索；失败时退回关键词检索，并支持加权 RRF 混合排名 |
| `ChatMemoryStore.java` | 在 Redis 保存最近几轮 user/assistant 消息 |
| `ModelFactory.java` | 根据名称创建 Qwen、DeepSeek、Kimi 或 Ollama 模型客户端 |
| `SessionMessage.java` | Redis 会话记忆中的一条消息，不是 MySQL 实体 |
| `ResponseEvaluationService.java` | 根据启发式规则给回答评分并保存用户反馈 |
| `AnswerEvaluation.java` | 保存回答分数、模型、响应时间和用户反馈 |
| `AnalyticsService.java` | 汇总回答评分和缓存命中情况 |
| `MetricsService.java` | 记录问答、上传、检索、模型和响应耗时指标 |

#### 登录与权限

| 文件 | 作用 |
| --- | --- |
| `AuthService.java` | 登录、注册、刷新 token 的业务流程 |
| `CustomUserDetailsService.java` | 根据用户名从 MySQL 加载用户 |
| `JwtAuthenticationFilter.java` | 每次请求进入 Controller 前解析并验证 JWT |
| `JwtTokenProvider.java` | 创建、解析和验证 JWT |
| `User.java` | 用户数据，并向 Spring Security 提供角色和权限 |
| `Role.java` | ADMIN、USER 等角色及其权限集合 |
| `Permission.java` | `document:read`、`document:write` 等具体权限 |
| `UserRepository.java` | 按用户名或邮箱查询用户 |
| `RoleRepository.java` | 按名称查询角色 |
| `PermissionRepository.java` | 按名称查询权限 |

#### 文档辅助功能

| 文件组 | 作用 |
| --- | --- |
| `DocumentVersionService/Repository/Entity` | 保存历史版本、比较版本和回滚 |
| `DocumentCategoryService/Repository/Entity/DTO` | 管理分类定义 |
| `DocumentTagService/Repository/Entity/DTO` | 管理标签定义 |
| `DocumentCategoryTagService.java` | 设置和查询一篇文档的分类、标签 |
| `DocumentCategoryRelation` 与 Repository | 保存文档和分类的多对多关系 |
| `DocumentTagRelation` 与 Repository | 保存文档和标签的多对多关系 |
| `DocumentCreateDTO.java` | 创建文档时传递标题、正文、分类和标签 |
| `DocumentCategoryTagDTO.java` | 传递文档 ID、分类 ID 列表和标签 ID 列表 |
| `LoginRequest/RegisterRequest/JwtResponse` | 登录注册接口的输入输出结构 |
| `UploadProgressDTO/AnswerEvaluationDTO` | 进度和评分接口的数据结构；当前部分接口仍直接使用 `Map` |
| `DataInitializer.java` | 文档表为空时插入示例文档 |
| `RolePermissionInitializer.java` | 创建默认角色、权限和管理员 |

### 前端关键文件

| 文件 | 作用 |
| --- | --- |
| `src/main.ts` | Vue 应用入口，安装 Router、Pinia 和 Naive UI |
| `src/App.vue` | 前端根组件 |
| `src/router/index.ts` | 页面路由和登录路由守卫 |
| `src/services/api.ts` | 所有后端 API、JWT 请求头、文件上传和 SSE 读取 |
| `src/stores/auth.ts` | 保存登录状态和 token |
| `src/views/HomeView.vue` | 登录后的主界面，组合问答、文档、检索和 Dashboard |
| `ChatComponent.vue` | 发送问题、读取 SSE 并显示回答 |
| `FileUploadComponent.vue` | 选择文件、异步上传和轮询任务进度 |
| `DocumentComponent.vue` | 文档列表、编辑、删除和搜索界面 |
| `VectorSearchComponent.vue` | 向量和混合检索界面 |
| `DashboardComponent.vue` | 使用 ECharts 展示统计数据 |
| `DocumentVersionComponent.vue` | 版本查看、比较和回滚界面 |

`HelloWorld.vue`、`TheWelcome.vue`、`WelcomeItem.vue`、`stores/counter.ts`、`AboutView.vue` 和 `components/icons` 是 Vue 模板遗留，不是业务主线。

### 三条程序主线

#### 1. 异步文档入库

```text
前端选择文件
  -> FileUploadController 接收 MultipartFile
  -> DocumentProcessingService 保存原文件和 PENDING 任务
  -> 调用 DocumentProcessingWorker 的 Spring 代理
  -> taskExecutor 接收任务
  -> Controller 返回 HTTP 202 + uploadId
  -> file-processing-* 线程解析文件
  -> DocumentService 保存文档正文到 MySQL
  -> DocumentChunkService 分块
  -> 分块正文写 MySQL，向量写 Redis Stack
  -> UploadProgress 更新为 COMPLETED 或 FAILED
  -> 前端使用 uploadId 查询进度
```

#### 2. RAG 流式问答

```text
用户输入问题
  -> AiController 创建 SseEmitter
  -> AiService 先查询 Redis 答案缓存
  -> VectorSearchService 检索相关文档
  -> ChatMemoryStore 读取最近对话
  -> AiService 拼接知识库、历史和当前问题
  -> ModelFactory 创建流式模型
  -> 大模型逐段生成回答
  -> SSE 持续发送给前端
  -> 完成后保存缓存、会话记忆和回答评分
```

#### 3. 登录后的接口访问

```text
LoginView 提交用户名和密码
  -> AuthController
  -> AuthService 验证密码
  -> JwtTokenProvider 生成 accessToken 和 refreshToken
  -> 前端保存 token
  -> 后续请求自动携带 Authorization: Bearer <token>
  -> JwtAuthenticationFilter 验证 token
  -> SecurityConfig 和 @PreAuthorize 检查接口权限
  -> 允许请求进入 Controller
```

### 当前能力边界

```text
当前已经有：上传 + RAG + Memory + SSE + JWT + 异步任务

当前还没有：
Agent Loop
Tool / ToolRegistry
ContextBuilder
MCP
A2A / 多 Agent
Elasticsearch
严格的文档权限过滤和精确引用
```

后续二次开发的核心，是从现在集中在 `AiService` 中的“固定检索后问答”，逐步拆出 ContextBuilder、Tool Calling 和 Agent Loop。

### 第一遍阅读顺序

```text
FileUploadController
  -> DocumentProcessingService
  -> UploadProgress
  -> DocumentFileStorage
  -> DocumentProcessingWorker
  -> AsyncConfig

然后再读：

AiController
  -> AiService
  -> VectorSearchService
  -> ChatMemoryStore
  -> ModelFactory
```

分类、标签、版本、Dashboard、Vue 模板文件和部署配置都不是第一遍阅读重点。

## 学习记录

### 001. `MultipartFile` 是什么，有什么用？

**知识点**

`MultipartFile` 是 Spring 在收到 HTTP 上传请求后交给 Controller 的 Java 对象，代表用户上传的一个 PDF、Word 等文件，但这个文件目前还没有被永久存储。

它解决的问题是：Spring 已经把 HTTP 请求中的文件部分解析好，业务代码不用自己处理原始二进制请求、文件边界和临时文件，只需要通过统一的 Java 接口访问上传文件。

**代码位置**

`src/main/java/com/kb/demo/controller/FileUploadController.java`：

```java
public ResponseEntity<?> uploadFileAsync(
        @RequestParam("file") MultipartFile file) {
    // ...
}
```

**简明解释**

为了便于理解，可以把它记成两部分：

```text
MultipartFile
 = 上传文件的信息
 + 访问文件内容的方法
```

```java
file.getOriginalFilename(); // "员工手册.pdf"
file.getSize();             // 2097152 字节
file.getContentType();      // "application/pdf"
file.isEmpty();             // 是否为空
file.getInputStream();      // 读取文件内容
file.transferTo(...);       // 保存到指定位置
```

`MultipartFile` 不是数据库实体、永久磁盘文件或单纯的文件路径。它背后可能只是请求期间的内存数据或临时文件。

**核心逻辑**

```text
用户在浏览器选择文件
 -> 浏览器通过 HTTP multipart/form-data 发送文件
 -> Spring 解析请求
 -> 将其中一个文件部分包装成 MultipartFile
 -> Controller 检查文件并交给后续 Service
```

`MultipartFile` 继承 `InputStreamSource`，表示它能够通过 `getInputStream()` 提供一个读取文件内容的通道。这个继承关系不是当前主线，先理解“可以读取上传内容”即可。

**小例子**

用户上传 `员工手册.pdf` 后，Controller 中的 `file` 大致可以提供：

```text
原始文件名：员工手册.pdf
文件大小：2097152 字节
内容类型：application/pdf
文件内容：通过 getInputStream() 读取
当前状态：只是上传请求中的临时内容，还没有永久保存
```

如果后端希望请求结束后继续处理，需要先把内容保存到稳定位置，例如：

```java
file.transferTo(targetPath);
```

**易错点**

1. **它不是永久文件。** `MultipartFile` 只是请求期间的临时上传内容；交给后台线程前必须先保存到稳定存储，否则请求结束后内容可能失效。
2. **大文件不要直接调用 `getBytes()`。** 它会一次性占用与文件大小相近的内存，应优先使用流式读取，并配置上传大小限制。
3. **文件名和 `Content-Type` 都不能完全信任。** 它们由客户端提供，保存时要生成安全文件名，并结合扩展名、文件签名和实际解析结果校验类型。

---

### 002. `FileUploadController.uploadFileAsync()` 的四个部分

**知识点**

`FileUploadController.uploadFileAsync()` 位于 HTTP 请求边界，负责接收上传文件、做基础校验、调用业务 Service，并把结果包装成 HTTP 响应。

它不负责解析 PDF、保存文档、切分段落或生成向量，这些工作应该交给后续 Service。

**代码位置**

`src/main/java/com/kb/demo/controller/FileUploadController.java:187`：

```java
@PostMapping("/upload-async")
@PreAuthorize("hasAuthority('document:write')")
public ResponseEntity<?> uploadFileAsync(@RequestParam("file") MultipartFile file) {
    try {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("文件不能为空");
        }

        if (!fileParseService.isFileTypeSupported(file)) {
            return ResponseEntity.badRequest().body("不支持的文件类型");
        }

        String uploadId = documentProcessingService.uploadFileAsync(file);

        Map<String, String> result = new HashMap<>();
        result.put("uploadId", uploadId);
        result.put("message", "文件上传开始，请使用uploadId查询进度");
        result.put("statusUrl", "/api/files/upload-progress/" + uploadId);

        return ResponseEntity.accepted().body(result);
    } catch (Exception e) {
        logger.error("文件上传失败", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("文件上传失败: " + e.getMessage());
    }
}
```

完整接口路径是 `/api/files/upload-async`，调用者需要拥有 `document:write` 权限。

**简明解释**

这段 Controller 可以分成四部分。

第一部分，接收 HTTP 输入：

```java
@RequestParam("file") MultipartFile file
```

Spring 从 HTTP `multipart/form-data` 请求中找到名为 `file` 的文件部分，包装成 `MultipartFile` 后传给 Controller。

第二部分，做基础校验：

```java
if (file.isEmpty()) {
    return ResponseEntity.badRequest().body("文件不能为空");
}

if (!fileParseService.isFileTypeSupported(file)) {
    return ResponseEntity.badRequest().body("不支持的文件类型");
}
```

Controller 判断文件是否为空、类型是否支持。校验失败时返回 HTTP 400。

第三部分，调用 Service：

```java
String uploadId = documentProcessingService.uploadFileAsync(file);
```

Controller 把文件交给 `DocumentProcessingService`，取得用于查询进度的 `uploadId`。具体怎样创建任务、保存进度和处理文件，属于 Service 的责任。

第四部分，返回 HTTP 输出：

```java
Map<String, String> result = new HashMap<>();
result.put("uploadId", uploadId);
result.put("message", "文件上传开始，请使用uploadId查询进度");
result.put("statusUrl", "/api/files/upload-progress/" + uploadId);

return ResponseEntity.accepted().body(result);
```

成功时返回 HTTP 202，响应体包含任务编号、提示信息和进度查询地址。`202 Accepted` 表示服务器已经受理任务，但后台处理还没有完成。

**核心逻辑**

浏览器上传 PDF/Word 文件，Controller 接收 `MultipartFile`，调用 `DocumentProcessingService.uploadFileAsync(file)`，拿到 `uploadId`，最后返回包含 `uploadId`、提示信息和进度查询地址的 `ResponseEntity` 响应，HTTP 状态码是 202。

```text
HTTP 上传请求
 -> Spring 将 file 字段包装成 MultipartFile
 -> Controller 检查空文件和文件类型
 -> DocumentProcessingService.uploadFileAsync(file)
 -> 返回 uploadId
 -> Controller 组装 ResponseEntity
 -> 浏览器收到 HTTP 状态码和响应体
```

责任边界：

```text
Controller
 -> 管 HTTP 输入输出、权限入口和基础校验

DocumentProcessingService
 -> 管任务创建、进度保存和具体业务处理
```

**小例子**

用户上传一个支持的 PDF 后，接口成功响应大致是：

```text
HTTP 202

{
  "uploadId": "生成的任务编号",
  "message": "文件上传开始，请使用uploadId查询进度",
  "statusUrl": "/api/files/upload-progress/生成的任务编号"
}
```

不同分支的结果：

| 场景 | HTTP 状态 | 响应体 |
|---|---:|---|
| 文件为空 | 400 | `文件不能为空` |
| 文件类型不支持 | 400 | `不支持的文件类型` |
| Service 或其他代码抛出异常 | 500 | `文件上传失败: ...` |
| 上传请求成功受理 | 202 | `uploadId`、`message`、`statusUrl` |

**易错点**

1. **`ResponseEntity` 只负责表达 HTTP 响应，不负责判断请求是否合法。** 校验由权限规则、`if` 和校验方法完成。
2. **接口返回成功只表示任务已受理，不表示文档处理完成。** 异步任务的最终结果必须通过任务状态查询。
3. **Controller 只处理 HTTP 边界。** 文件解析、持久化和向量化应留在 Service，避免入口层过重且难以测试。
4. **响应结构和异常信息要稳定、安全。** 不要混用 `Map` 与字符串，也不要把 `e.getMessage()` 直接返回给前端；应使用 DTO、统一错误码和安全提示。

紧凑记忆：Controller 管 HTTP 输入输出和基础校验，Service 管具体业务处理。

---

### 003. `UUID` 是什么，在上传任务里有什么用？

**知识点**

`UUID` 全称是 **Universally Unique Identifier（通用唯一标识符）**。它是一个 128 位标识符，Java 可以用 `UUID.randomUUID()` 随机生成。

在这个项目里，`UUID` 不是文件内容，也不是登录凭证，而是一次文件上传任务的**任务编号**。前端、后台异步任务和数据库都用同一个编号确认“我们说的是哪一次上传”。

**代码位置**

`src/main/java/com/kb/demo/service/DocumentProcessingService.java:50`：

```java
public String uploadFileAsync(MultipartFile file) {
    String uploadId = UUID.randomUUID().toString();
    String originalFilename = normalizedFilename(file.getOriginalFilename());

    // 先把请求期间的临时文件保存到稳定目录
    documentFileStorage.store(uploadId, file);

    UploadProgress progress = new UploadProgress();
    progress.setUploadId(uploadId);
    progress.setFileName(originalFilename);
    progress.setStatus(UploadProgress.UploadStatus.PENDING);
    uploadProgressRepository.save(progress);

    // 后台 Worker 只接收任务编号
    documentProcessingWorker.processFileAsync(uploadId);
    return uploadId;
}
```

还会关联到：

- `UploadProgress.uploadId`：把任务编号保存到数据库。
- `UploadProgressRepository.findByUploadId(...)`：根据任务编号查询上传进度。
- Controller 返回的 `uploadId`：前端后续查询进度时使用。

**简明解释**

```java
UUID.randomUUID().toString();
```

可以拆成两步理解：

1. `UUID.randomUUID()`：生成一个新的 `UUID` 对象。
2. `.toString()`：把它变成便于放入 JSON、URL、日志和数据库的字符串。

结果大致如下：

```text
f47ac10b-58cc-4372-a567-0e02b2c3d479
```

项目里同时出现的两个 ID 不要混淆：

| 字段 | 作用 | 由谁生成 |
|---|---|---|
| `Long id` | 数据库表内部主键 | 通常由 MySQL 生成 |
| `String uploadId` | 对外使用的上传任务编号 | Java 在保存记录前生成 UUID |

**核心逻辑**

```text
生成 UUID
    ↓
写入 UploadProgress.uploadId 并保存到数据库
    ↓
把同一个 uploadId 传给后台文件处理任务
    ↓
把 uploadId 返回给 Controller 和前端
    ↓
前端请求 /api/files/upload-progress/{uploadId}
    ↓
Repository 用 findByUploadId(uploadId) 找到同一条任务记录
    ↓
返回该任务当前的处理进度或结果
```

因此，`uploadId` 是连接以下四部分的“线索”：

- HTTP 上传响应
- 后台异步处理任务
- 数据库中的进度记录
- 后续进度查询和日志排查

**小例子**

用户上传 `员工手册.pdf` 后，后端先返回：

```json
{
  "uploadId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

前端不用一直占着原来的上传请求等待解析完成，而是拿这个编号查询：

```text
GET /api/files/upload-progress/f47ac10b-58cc-4372-a567-0e02b2c3d479
```

后端再根据这个编号找到数据库中的对应任务，返回解析进度。

**易错点**

1. **同一次任务必须始终使用同一个 UUID，并在数据库中添加唯一约束。** UUID 碰撞概率极低，但业务正确性不能只依赖概率。
2. **UUID 只是标识符。** 它既不是权限凭证，也不是文件内容指纹；查询仍要鉴权，文件去重应使用 SHA-256 等哈希值。
3. **UUID 不会自动实现幂等。** 重复调用上传接口会生成不同 UUID 和不同任务，防重复提交需要单独设计幂等键或去重策略。

---

### 004. 为什么先记录指标、保存进度，再启动异步处理？

**知识点**

这里有两种用途不同的“记录”：

- `metricsService.recordDocumentUpload()`：给监控系统的计数器加一，用来统计上传请求量。
- `uploadProgressRepository.save(progress)`：向数据库写入一条可查询、可更新的任务状态。

先保存初始状态，再启动后台任务，是为了让异步处理和前端查询开始之前，系统中已经存在这次任务。

**代码位置**

`src/main/java/com/kb/demo/service/DocumentProcessingService.java`：

```java
public String uploadFileAsync(MultipartFile file) {
    String uploadId = UUID.randomUUID().toString();
    String originalFilename = normalizedFilename(file.getOriginalFilename());
    documentFileStorage.store(uploadId, file);

    UploadProgress progress = new UploadProgress();
    progress.setUploadId(uploadId);
    progress.setFileName(originalFilename);
    progress.setFileSize(file.getSize());
    progress.setUploadedSize(file.getSize());
    progress.setStatus(UploadProgress.UploadStatus.PENDING);
    progress.setPercentage(0);
    uploadProgressRepository.save(progress);

    metricsService.recordDocumentUpload();
    documentProcessingWorker.processFileAsync(uploadId);
    return uploadId;
}
```

**简明解释**

`recordDocumentUpload()` 只执行：

```java
documentUploadCounter.increment();
```

它适合 Prometheus、Grafana 等监控系统统计上传量和变化趋势，不保存文件名、进度等业务信息。

数据库中的 `UploadProgress` 才是这次任务的“状态档案”：

```text
uploadId + 文件名 + 文件大小 + 当前状态 + 百分比 + 错误信息
```

为什么必须在启动后台任务之前保存？因为异步线程和 HTTP 线程执行速度不确定。如果先启动异步任务，可能出现：

```text
异步线程准备更新进度
 -> 根据 uploadId 查询数据库
 -> 任务记录还没创建
 -> 找不到上传记录
```

前端拿到 `uploadId` 后也可能立即轮询，所以初始记录同样必须已经存在。

**核心逻辑**

```text
生成 uploadId
 -> 把 MultipartFile 保存到稳定目录
 -> 保存 PENDING、0% 的任务记录
 -> 上传计数器 +1
 -> 只把 uploadId 交给后台 Worker
 -> Controller 把 uploadId 返回前端
 -> 后台线程不断更新同一条记录
 -> 前端根据 uploadId 查询最新状态
```

这个顺序还带来一个直接好处：如果数据库初始记录保存失败，方法会抛出异常，不会继续启动一个没有状态记录、无法追踪的后台任务。

指标放在哪一步，决定它表示什么：

| 计数位置 | 指标含义 |
|---|---|
| 进入 Service 时 | 上传尝试数 |
| 当前代码：数据库保存成功后 | 已创建任务记录的受理数 |
| 状态变成 `COMPLETED` 后 | 成功处理完成的文档数 |

当前计数发生在任务记录保存后、Worker 提交前，因此它不是“处理完成数”；如果线程池随后拒绝任务，这次仍然会被计入受理指标。

**小例子**

假设任务 A 的 `uploadId` 是 `abc-123`：

```text
主线程：先插入 abc-123，状态 PENDING，进度 0%
后台线程：查到 abc-123，更新为 PARSING，进度 10%
前端线程：查到 abc-123，显示“解析中 10%”
```

三条执行路径通过数据库中的同一条记录协作，不需要彼此等待。

**易错点**

1. **监控计数不等于业务记录。** 当前计数器表示已经创建进度记录的受理任务，不表示文档已经处理完成。
2. **当前代码已经修复同类内部调用。** `DocumentProcessingService` 调用独立的 `DocumentProcessingWorker` Bean，使调用可以经过 Worker 的 Spring 代理。
3. **当前 Worker 不再接收 `MultipartFile`。** 请求线程先保存文件，后台线程只接收 `uploadId`，再根据任务记录读取稳定文件。

---

### 005. 从同步调用到线程池：一条完整的异步主线

**这一节解决什么**

这一节始终使用同一个例子：HTTP 请求已经收到文件，现在要执行耗时的“解析文档”任务。

先看完整路线，再逐段拆开：

```text
普通方法调用：请求线程自己执行解析，所以必须等待
        ↓
创建新线程：请求线程把解析任务交给另一条执行路线
        ↓
使用线程池：统一管理线程、等待队列和系统过载
        ↓
映射到项目：请求线程返回 uploadId，工作线程处理文档
```

#### 1. 起点：普通方法调用为什么会阻塞

```java
public void upload() {
    processDocument("task-001");
    System.out.println("返回 HTTP 响应");
}

public void processDocument(String uploadId) {
    // 假设解析、分块、向量化共需要 10 秒
}
```

一条线程在同一时刻只能沿一条路线向下执行：

```text
请求线程进入 upload()
  -> 进入 processDocument()
  -> 等待 10 秒，直到方法结束
  -> 回到 upload()
  -> 返回 HTTP 响应
```

`processDocument()` 写在本类还是另一个普通对象中都不重要。只要仍是普通方法调用，当前线程就要等它返回。

#### 2. 第一次产生异步：把任务交给另一条线程

先不用线程池，直接看最小写法：

```java
public void upload() {
    Thread worker = new Thread(() -> processDocument("task-001"));
    worker.start();

    System.out.println("先返回 HTTP 响应");
}
```

这里的几个角色是：

| 名称 | 在例子中是什么 |
|---|---|
| 任务 | `() -> processDocument("task-001")`，也就是要做的工作 |
| 请求线程 | 调用 `upload()` 的线程 |
| 工作线程 | `worker`，真正执行文档处理的线程 |
| `start()` | 启动另一条执行路线 |

执行路线变成：

```text
请求线程：创建并启动 worker -> 继续向下 -> 返回响应
工作线程：                    -> 执行 processDocument()
```

这里最容易错的是把 `start()` 写成 `run()`：

```java
worker.start(); // 启动新线程，产生两条执行路线
worker.run();   // 只是当前线程普通调用 run()，不会产生新线程
```

#### 3. 为什么还需要线程池

如果每次上传都直接 `new Thread()`：

```text
来 1 个任务 -> 创建 1 个线程
来 1000 个任务 -> 可能创建 1000 个线程
```

线程会占用内存和 CPU 调度资源。任务持续涌入时，系统没有并发上限、等待位置和拒绝规则，最终可能被自己拖垮。

线程池增加了三个控制点：

```text
工作线程数量上限
      +
等待队列容量
      +
队列和线程都满时的拒绝策略
```

用 JDK 原生类写出与本项目参数等价的例子：

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        5,                              // corePoolSize：核心线程数
        10,                             // maximumPoolSize：最大线程数
        60, TimeUnit.SECONDS,           // 非核心空闲线程的存活时间
        new ArrayBlockingQueue<>(100),  // 等待队列容量
        Executors.defaultThreadFactory(),
        new ThreadPoolExecutor.AbortPolicy() // 满载后拒绝任务
);

executor.execute(() -> processDocument("task-001"));
System.out.println("先返回 HTTP 响应");
```

`execute(...)` 的意思是“把任务交给线程池”。它返回只代表线程池已经接收这次提交，不代表文档已经处理完成。

#### 4. `5 / 10 / 100` 按什么顺序生效

当前项目的三个核心参数是：

```text
corePoolSize = 5
maxPoolSize  = 10
queueCapacity = 100
```

当之前提交的任务都还没有完成时，新任务按下面的顺序处理：

```text
第 1 步：当前工作线程少于 5 个？
  是 -> 创建工作线程执行任务
  否 -> 进入第 2 步

第 2 步：等待队列还没满？
  是 -> 任务进入队列等待
  否 -> 进入第 3 步

第 3 步：当前工作线程少于 10 个？
  是 -> 再创建非核心工作线程执行任务
  否 -> 进入第 4 步

第 4 步：线程和队列都满
  -> 执行拒绝策略
```

假设 110 个文档处理任务都很慢，前面的任务一直没有结束：

| 任务编号 | 去哪里 | 原因 |
|---|---|---|
| 1～5 | 5 个核心工作线程 | 先补足核心线程 |
| 6～105 | 等待队列 | 核心线程已忙，但队列还有 100 个位置 |
| 106～110 | 新增的 5 个非核心线程 | 队列已满，才把线程数从 5 增到 10 |
| 111 | 被拒绝 | 10 个线程和 100 个队列位置都已占用 |

最反直觉、也最常考的一点是：**核心线程忙了以后，线程池通常先入队；队列满了以后，才继续创建线程直到最大线程数。** `maxPoolSize = 10` 不代表第 6 个任务一来就马上创建第 6 个线程。

核心线程也不是应用启动时一定立刻创建 5 个。默认情况下通常是任务到来后逐步创建，创建后保持在线等待后续任务。

#### 5. 当前项目的完整线程池代码

`src/main/java/com/kb/demo/config/AsyncConfig.java`：

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("file-processing-");

        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());

        executor.initialize();

        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
```

`ThreadPoolTaskExecutor` 是 Spring 对 JDK 线程池的包装，底层仍然遵守上一小节的核心线程、队列、最大线程和拒绝规则。

逐项对应：

| 配置 | 当前值 | 解决什么问题 |
|---|---:|---|
| `setCorePoolSize` | 5 | 正常情况下最多让 5 个文档任务并行执行 |
| `setMaxPoolSize` | 10 | 队列满后，临时把并行处理能力提高到 10 |
| `setQueueCapacity` | 100 | 工作线程忙时，最多让 100 个任务等待 |
| `setThreadNamePrefix` | `file-processing-` | 从日志看出代码是不是由文件处理线程执行 |
| `setWaitForTasksToCompleteOnShutdown` | `true` | 应用关闭时先等待已提交任务完成 |
| `setAwaitTerminationSeconds` | 60 | 关闭时最多等待 60 秒 |
| `AbortPolicy` | 明确抛异常 | 满载时拒绝新任务，不让请求线程偷偷接手耗时处理 |
| `initialize()` | 初始化 | 根据上述设置创建底层执行器 |

最后一行的安全上下文包装先只记结论：它让后台线程能继承提交任务时的登录用户信息，下一章再解释它和 Spring 代理怎样配合。

#### 6. 映射回文档上传业务

现在可以把代码还原成两条路线：

```text
HTTP 请求线程
  -> 接收 MultipartFile
  -> 把原文件保存到稳定目录
  -> 在 MySQL 创建 PENDING 任务
  -> 把 uploadId 提交给 taskExecutor
  -> 返回 HTTP 202 和 uploadId

file-processing-* 工作线程
  -> 根据 uploadId 查询任务和原文件
  -> 解析正文
  -> 保存 Document
  -> 分块并生成向量
  -> 把任务更新为 COMPLETED 或 FAILED
```

这里异步化的是“文件已经传到后端以后的解析、分块和向量化”，不是浏览器到服务器的文件传输过程。

#### 7. 任务受理成功不等于处理成功

```java
executor.execute(task);
```

可能出现三种结果：

| 阶段 | 结果 | 项目怎样表示 |
|---|---|---|
| 提交前 | 文件或任务记录保存失败 | HTTP 请求直接失败 |
| 提交时 | 线程池已经满载并拒绝 | 任务标记为 `FAILED`，请求返回失败 |
| 提交后 | 后台解析或向量化失败 | HTTP 早已返回，Worker 将任务标记为 `FAILED` |

所以前端不能把拿到 `uploadId` 理解为“文档已经可用”，必须继续查询任务状态。

#### 易错点

1. **普通调用不会因为方法名有 `Async` 就变成异步。** 必须创建新线程，或把任务提交给执行器。
2. **`thread.run()` 不会启动新线程，`thread.start()` 才会。** `run()` 只是当前线程的普通方法调用。
3. **核心线程都忙时通常先入队，不是立刻扩到最大线程数。** 当前项目要等 100 个队列位置占满，才从 5 个线程扩到 10 个。
4. **最大线程和队列都满时任务会被拒绝。** 当前使用 `AbortPolicy` 抛出异常，不能假装任务已受理。
5. **提交完成不等于处理完成。** 真正结果由 `UploadProgress` 的最终状态表示。
6. **异步不等于单个任务更快。** 它让请求线程先返回，并控制多个任务怎样并发和排队。

---

### 006. Spring 怎样把方法调用提交给线程池

**和上一章的连接**

上一章已经得到一条确定结论：真正产生异步的动作，是把任务交给线程池。

如果完全手写，业务代码应该类似：

```java
taskExecutor.execute(
        () -> worker.processFile(uploadId)
);
```

Spring `@Async` 没有改变这个原理。它只是替我们生成“提交任务的外层对象”，避免每个业务方法都手写 `executor.execute(...)`。

本章只沿着这条线解释：

```text
线程池已经存在
  -> Spring 管理线程池和业务对象
  -> Spring 为 @Async 方法创建代理
  -> 外部调用先进入代理
  -> 代理把真实方法提交给线程池
```

#### 1. Bean：先让 Spring 管理这些对象

不用 Spring 时，我们自己创建和连接对象：

```java
DocumentProcessingWorker worker = new DocumentProcessingWorker(...);
DocumentProcessingService service =
        new DocumentProcessingService(..., worker);
```

使用 Spring 后，两个业务类都标记为 `@Service`：

```java
@Service
public class DocumentProcessingService {
}

@Service
public class DocumentProcessingWorker {
}
```

Spring 启动时扫描这些类，创建对象并保存起来。由 Spring 创建和管理的对象叫 **Bean**。

Bean 不是一种新的 Java 语法，本质仍然是 Java 对象。区别在于对象的创建、依赖连接和生命周期由 Spring 负责。

#### 2. 依赖注入：Service 怎样拿到 Worker

当前 `DocumentProcessingService` 通过构造方法声明自己需要 Worker：

```java
private final DocumentProcessingWorker documentProcessingWorker;

public DocumentProcessingService(
        UploadProgressRepository uploadProgressRepository,
        MetricsService metricsService,
        DocumentFileStorage documentFileStorage,
        DocumentProcessingWorker documentProcessingWorker) {
    this.uploadProgressRepository = uploadProgressRepository;
    this.metricsService = metricsService;
    this.documentFileStorage = documentFileStorage;
    this.documentProcessingWorker = documentProcessingWorker;
}
```

Spring 创建 `DocumentProcessingService` 时，会找到自己已经管理的 Worker，并把它传入构造方法。这个过程叫 **依赖注入**。

```text
DocumentProcessingService 需要 Worker
        ↓
Spring 从容器中找到 Worker Bean
        ↓
Spring 调用构造方法，把 Worker 传进去
```

项目代码不需要自己 `new DocumentProcessingWorker(...)`，也不需要到处寻找同一个 Worker 实例。

#### 3. 五个注解按作用链分别做什么

不要把所有注解当成一团。按“先开启能力、再创建对象、最后拦截调用”的作用链看：

| 顺序 | 注解 | 当前项目中的作用 |
|---:|---|---|
| 1 | `@Configuration` | 告诉 Spring：`AsyncConfig` 是配置类 |
| 2 | `@EnableAsync` | 开启对 `@Async` 的识别和代理支持 |
| 3 | `@Bean(name = "taskExecutor")` | 执行配置方法，把返回的线程池注册为名叫 `taskExecutor` 的 Bean |
| 4 | `@Service` | 创建 `DocumentProcessingService` 和 Worker 业务 Bean |
| 5 | `@Async("taskExecutor")` | 告诉 Spring：外部调用该方法时，要交给这个线程池 |

注解本身只是代码上的说明。真正执行扫描、创建 Bean、生成代理和提交线程池的是 Spring 框架。

#### 4. 代理对象到底替我们写了什么

真实 Worker 的业务方法是：

```java
@Async("taskExecutor")
public void processFileAsync(String uploadId) {
    // 解析、保存、分块、向量化
}
```

为了让调用者不用手写 `executor.execute(...)`，Spring 在概念上生成一个外层对象：

```java
class DocumentProcessingWorkerProxy {
    private final DocumentProcessingWorker target; // 真实 Worker
    private final Executor taskExecutor;            // 第五章的线程池

    public void processFileAsync(String uploadId) {
        taskExecutor.execute(
                () -> target.processFileAsync(uploadId)
        );
    }
}
```

这不是项目中需要手写的类，而是帮助理解代理行为的等价代码。

调用发生时：

```text
DocumentProcessingService
  -> 调用 Worker 代理的 processFileAsync()
  -> 代理调用 taskExecutor.execute(...)
  -> 代理立即返回
  -> file-processing-* 工作线程调用真实 Worker 方法
```

所以可以把代理对象理解为：**包在真实 Worker 外面、先接住方法调用的 Java 对象。** 它不负责解析文档，只负责在调用真实方法之前增加“提交线程池”这一步。

#### 5. `@Async` 为什么写在真实业务方法上

```java
@Async("taskExecutor")
public void processFileAsync(String uploadId) {
}
```

这行注解描述的是业务意图：调用 `processFileAsync` 时应该异步执行。

Spring 读取它以后才知道：

```text
要为哪个 Bean 创建代理
代理要拦截哪个方法
代理要把任务交给哪个 Executor
```

代理类是 Spring 运行时生成的，所以我们没有一个现成的“代理方法”可以手动添加注解。注解写在业务方法上，代理读取并实现这个要求。

#### 6. 为什么同一个对象内部调用自己不会异步

先看错误结构：

```java
@Service
public class DocumentProcessingService {

    public String uploadFileAsync(MultipartFile file) {
        this.processFileAsync(uploadId);
        return uploadId;
    }

    @Async("taskExecutor")
    public void processFileAsync(String uploadId) {
        // 耗时处理
    }
}
```

`this` 表示当前真实对象自己，因此路线是：

```text
真实 Service.uploadFileAsync()
  -> 真实 Service.processFileAsync()
```

这次调用没有离开真实对象，也没有重新进入 Spring 代理。没人替它执行 `taskExecutor.execute(...)`，所以仍然在请求线程同步运行。

当前项目的修复结构是：

```java
@Service
public class DocumentProcessingService {
    private final DocumentProcessingWorker documentProcessingWorker;

    public String uploadFileAsync(MultipartFile file) {
        // 保存文件和 PENDING 任务
        documentProcessingWorker.processFileAsync(uploadId);
        return uploadId;
    }
}

@Service
public class DocumentProcessingWorker {
    @Async("taskExecutor")
    public void processFileAsync(String uploadId) {
        // 耗时处理
    }
}
```

现在是一个 Bean 调用另一个 Bean：

```text
真实 ProcessingService
  -> Spring 注入的 Worker 代理
  -> taskExecutor
  -> 真实 Worker
```

拆成两个类的直接目的不是“Service 越多越专业”，而是让异步调用确实从外部经过 Worker 代理。

#### 7. 当前项目的完整运行链

```text
1. http-nio-* 请求线程进入 FileUploadController
        ↓
2. Controller 调用 DocumentProcessingService.uploadFileAsync(file)
        ↓
3. 请求线程保存原文件
        ↓
4. 请求线程向 MySQL 写入 PENDING 任务
        ↓
5. ProcessingService 调用注入的 Worker 代理
        ↓
6. Worker 代理把 uploadId 提交给 taskExecutor
        ↓
7. 代理立即返回，Controller 返回 HTTP 202 + uploadId

与此同时：

8. file-processing-* 工作线程取得任务
        ↓
9. 工作线程调用真实 Worker.processFileAsync(uploadId)
        ↓
10. 解析 -> 保存文档 -> 分块 -> 向量化 -> 更新任务状态
```

这里有一条非常重要的边界：

```text
DocumentProcessingService.uploadFileAsync()
```

前半段仍在 HTTP 请求线程中同步执行。只有调用：

```java
documentProcessingWorker.processFileAsync(uploadId);
```

时才穿过代理，进入异步边界。

#### 8. `DelegatingSecurityContextAsyncTaskExecutor` 为什么包在线程池外面

登录用户信息默认保存在当前请求线程的 `SecurityContext` 中。任务切换到另一个工作线程后，直接使用普通线程池可能拿不到原请求的认证信息。

```java
return new DelegatingSecurityContextAsyncTaskExecutor(executor);
```

可以先理解为：

```text
提交任务时，复制当前登录上下文
        ↓
工作线程执行任务前，恢复这份上下文
        ↓
任务结束后，清理工作线程中的上下文
```

它包装的是第五章已经配置好的线程池，不会改变 `5 / 10 / 100` 的调度规则。

当前 Worker 还没有读取登录用户，但保留这层包装可以避免未来异步审计、权限或操作者记录丢失认证信息。

#### 9. 怎样证明代理和线程池真的生效

不要只看方法名或注解，要看运行证据。

在请求线程和 Worker 中分别记录：

```java
Thread.currentThread().getName()
```

预期看到：

```text
提交任务：http-nio-8080-exec-1
执行任务：file-processing-1
```

两个线程名不同，说明执行路线发生了切换；`file-processing-` 又证明使用的是 `taskExecutor`，不是其他默认执行器。

项目中的 `DocumentProcessingWorkerAsyncTest` 会从 Spring 容器获取 Worker Bean，再验证任务运行在 `file-processing-*` 线程。这里必须从 Spring 容器取 Bean；如果测试自己 `new Worker()`，就测不到代理。

#### 10. 按运行顺序整理的易错点

1. **手动 `new DocumentProcessingWorker()` 会绕过 Spring。** 得到的是普通对象，不会自动经过 `@Async` 代理。
2. **注解本身不创建线程。** `@EnableAsync` 开启处理，代理负责拦截，`taskExecutor` 才负责执行任务。
3. **同类内部的 `this.processFileAsync()` 绕过代理。** 当前项目拆成 Service 和 Worker，就是为了形成外部 Bean 调用。
4. **`@Async("taskExecutor")` 明确选择文件处理线程池。** 它不会使用 `VirtualThreadConfig` 中的默认虚拟线程执行器。
5. **看到 HTTP 202 只表示任务已经提交。** 后台异常不能修改已返回的响应，Worker 必须把数据库状态更新为 `FAILED`。

#### 11. 面试时的简明表达

> Java 异步的本质是把任务提交给其他线程执行。项目先用 `ThreadPoolTaskExecutor` 配置核心线程 5、最大线程 10、队列 100 和 `AbortPolicy`；Spring `@Async` 再通过代理拦截外部 Bean 调用，将真实 Worker 方法提交给这个线程池。由于同类内部调用会绕过代理，我把任务受理和后台处理拆成 `DocumentProcessingService` 与 `DocumentProcessingWorker`，并通过线程名测试确认任务运行在 `file-processing-*` 线程。

#### 12. 完成标准

能够从下面这行代码开始，完整解释它实际经过了谁：

```java
documentProcessingWorker.processFileAsync(uploadId);
```

高分回答顺序：

```text
Spring 注入的是 Worker Bean 的代理引用
  -> 代理识别 @Async("taskExecutor")
  -> 代理把调用封装成任务提交给线程池
  -> 请求线程立即返回
  -> file-processing-* 线程调用真实 Worker
  -> Worker 更新任务的最终状态
```

---

### 007. Spring 常见注解：按程序运行顺序整理

**这一节解决什么**

看到 `@Service`、`@PostMapping`、`@Transactional` 等注解时，不再把它们看成一堆互不相关的“魔法”。

先看一条完整作用链：

```text
应用启动
  -> 创建和连接 Bean
  -> 注册 HTTP 路由
  -> 请求到达并绑定参数
  -> 检查权限
  -> Service 执行业务
  -> 事务或异步代理增加行为
  -> JPA 将 Entity 保存到数据库
```

不同注解负责这条链中的不同阶段。

#### 1. 第一原则：注解只是标记，必须有人读取

Java 注解本质上是附加在类、方法、字段或参数上的元数据。

```java
@Service
public class DocumentService {
}
```

`@Service` 这几个字符不会自己创建对象。真正的过程是：

```text
代码写上注解
  -> 框架扫描或拦截代码
  -> 框架读取注解
  -> 框架执行对应行为
```

| 注解类别 | 谁读取 | 产生什么行为 |
| --- | --- | --- |
| Bean 与配置 | Spring 容器 | 创建对象、连接依赖 |
| Web 接口 | Spring MVC | 注册 URL、绑定 HTTP 参数 |
| 安全 | Spring Security | 认证和权限检查 |
| 事务 | Spring Transaction | 开启、提交或回滚事务 |
| 异步 | Spring Async | 将方法调用提交给线程池 |
| JPA 实体 | Hibernate/JPA | 将 Java 对象映射到数据库 |
| 参数校验 | Bean Validation | 检查字段是否为空、长度是否合法等 |

因此，并不是所有 `@注解` 都来自 Spring。

#### 2. 应用启动：`@SpringBootApplication`

代码位置：`AiKnowledgeBaseApplication.java`。

```java
@SpringBootApplication
public class AiKnowledgeBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiKnowledgeBaseApplication.class, args);
    }
}
```

它是 Spring Boot 应用的总入口，主要组合了三类能力：

```text
配置类
+ 自动配置
+ 从当前包向下扫描组件
```

因为入口类位于 `com.kb.demo`，Spring 默认会向下扫描：

```text
com.kb.demo.controller
com.kb.demo.service
com.kb.demo.repository
com.kb.demo.config
...
```

如果把业务类放到扫描范围之外，又没有额外配置，类上即使写了 `@Service`，Spring 也可能发现不了。

#### 3. 创建业务对象：`@Component`、`@Service`、`@Repository`

这几个注解都能让类被组件扫描发现并创建成 Bean，但表达的职责不同。

| 注解 | 适合放在哪里 | 当前项目例子 |
| --- | --- | --- |
| `@Component` | 无法归入特定层的通用组件 | `JwtTokenProvider` |
| `@Service` | 业务流程 | `AiService`、`DocumentProcessingWorker` |
| `@Repository` | 数据访问层 | `DocumentRepository` 等 Repository |
| `@Controller` | 返回页面或 MVC 模型的 Web 控制器 | 当前项目没有单独使用 |
| `@RestController` | 返回 JSON、字符串等响应体的接口控制器 | `AiController`、`FileUploadController` |

`@RestController` 可以理解成：

```java
@Controller
@ResponseBody
```

因此方法返回的 `Map`、DTO 或 Entity 会被序列化到 HTTP 响应体，而不是被当成页面名称。

这些注解不是为了让类名更好看，而是同时告诉 Spring“创建这个对象”和“它属于哪一层”。

#### 4. 创建配置对象：`@Configuration` 与 `@Bean`

当对象不是项目自己定义的业务类，或者创建过程需要手动配置时，通常使用 `@Configuration + @Bean`。

代码位置：`AsyncConfig.java`。

下面只截取与 `@Configuration`、`@Bean` 有关的结构；线程池全部参数见第 005 节。

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.initialize();
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
```

分工是：

| 注解 | 作用 |
| --- | --- |
| `@Configuration` | 告诉 Spring：这个类中包含对象创建规则 |
| `@Bean` | 执行这个方法，并把方法返回值交给 Spring 管理 |
| `name = "taskExecutor"` | 给 Bean 指定名称，供 `@Async("taskExecutor")` 精确选择 |

与 `@Service` 的区别：

```text
@Service
  -> Spring 调用这个类的构造方法创建对象

@Bean
  -> Spring 调用配置方法，管理方法返回的对象
```

当前线程池来自 Spring 类，且需要设置多个参数，所以通过 `@Bean` 创建比给第三方类加 `@Service` 更合适。

#### 5. 读取配置：`@ConfigurationProperties` 与 `@Value`

`application.yml` 中的数据不会自动出现在任意字段里，需要绑定到 Java 对象或参数。

##### 5.1 一组相关配置：`@ConfigurationProperties`

代码位置：`ModelConfig.java`。

```java
@Component
@ConfigurationProperties(prefix = "langchain4j")
public class ModelConfig {
    private String defaultModel;
    private ModelProperties qwen;
    private ModelProperties deepseek;
}
```

它把同一前缀下的一组配置映射为有结构的 Java 对象：

```text
langchain4j.defaultModel -> modelConfig.defaultModel
langchain4j.qwen.apiKey  -> modelConfig.qwen.apiKey
langchain4j.deepseek.*   -> modelConfig.deepseek.*
```

适合模型配置这种字段较多、层级明确的情况，也更利于类型检查。

##### 5.2 单个配置值：`@Value`

```java
@Value("${spring.data.redis.host:localhost}")
private String redisHost;
```

拆开看：

```text
spring.data.redis.host  配置键
localhost               没有配置时的默认值
```

少量独立配置使用 `@Value` 很方便；字段较多时，应优先使用 `@ConfigurationProperties`，避免大量字符串散落在代码中。

#### 6. 连接 Bean：构造注入与 `@Autowired`

旧代码中经常看到字段注入：

```java
@Autowired
private AiService aiService;
```

含义不是“创建一个 `AiService`”，而是：

```text
Spring 已经管理 AiService Bean
  -> 创建 AiController 时
  -> 将 AiService Bean 放到这个字段中
```

更推荐当前异步模块使用的构造注入：

```java
private final DocumentProcessingWorker documentProcessingWorker;

public DocumentProcessingService(
        DocumentProcessingWorker documentProcessingWorker) {
    this.documentProcessingWorker = documentProcessingWorker;
}
```

如果类只有一个构造方法，现代 Spring 可以省略构造方法上的 `@Autowired`。

构造注入的优势：

- 依赖在对象创建时必须提供，不容易出现 `null`。
- 字段可以声明为 `final`。
- 单元测试可以直接传入替身对象。
- 从构造方法就能看到这个类依赖谁。

#### 7. 注册 HTTP 接口：`@RequestMapping` 和具体请求注解

代码位置：`FileUploadController.java`。

```java
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @PostMapping("/upload-async")
    public ResponseEntity<?> uploadFileAsync(...) {
    }
}
```

类级别和方法级别路径会拼接：

```text
/api/files + /upload-async
= /api/files/upload-async
```

| 注解 | HTTP 方法 | 常见用途 |
| --- | --- | --- |
| `@GetMapping` | GET | 查询数据 |
| `@PostMapping` | POST | 新建资源、提交命令、上传文件 |
| `@PutMapping` | PUT | 更新完整资源 |
| `@PatchMapping` | PATCH | 更新部分字段；当前项目没有使用 |
| `@DeleteMapping` | DELETE | 删除资源 |
| `@RequestMapping` | 可指定任意方法 | 类级公共路径或需要更详细配置时使用 |

这些注解负责“什么 URL 进入什么 Java 方法”。它们不负责数据库保存或业务处理。

#### 8. HTTP 参数从哪里来：`@RequestBody`、`@RequestParam`、`@PathVariable`

这是 Controller 最容易混淆的一组注解。

##### 8.1 JSON 请求体：`@RequestBody`

```java
public ResponseEntity<JwtResponse> login(
        @Valid @RequestBody LoginRequest loginRequest) {
}
```

请求：

```json
{
  "username": "admin",
  "password": "123456"
}
```

Spring 把 JSON 反序列化成 `LoginRequest`。

##### 8.2 查询参数、表单字段或上传文件：`@RequestParam`

```java
public ResponseEntity<?> uploadFileAsync(
        @RequestParam("file") MultipartFile file) {
}
```

这里从 `multipart/form-data` 中取得名为 `file` 的文件部分。

普通查询参数也可以使用它：

```text
GET /api/vector-search/documents?query=Spring&maxResults=10
```

```java
@RequestParam String query,
@RequestParam(defaultValue = "10") int maxResults
```

##### 8.3 URL 路径中的变量：`@PathVariable`

```java
@GetMapping("/upload-progress/{uploadId}")
public ResponseEntity<?> getUploadProgress(
        @PathVariable String uploadId) {
}
```

请求：

```text
GET /api/files/upload-progress/abc-123
```

得到：

```text
uploadId = "abc-123"
```

紧凑区分：

| 数据位置 | 注解 | 例子 |
| --- | --- | --- |
| JSON 请求体 | `@RequestBody` | 登录对象、创建文档对象 |
| `?key=value` 或表单字段 | `@RequestParam` | 搜索条件、上传文件 |
| URL 路径 `{id}` | `@PathVariable` | 文档 ID、任务 ID |

#### 9. 参数校验：`@Valid` 和校验注解

代码位置：`AuthController`、`LoginRequest`、`RegisterRequest`。

```java
public ResponseEntity<JwtResponse> login(
        @Valid @RequestBody LoginRequest loginRequest) {
}
```

DTO 中定义规则：

```java
@NotBlank
@Size(min = 3, max = 50)
private String username;
```

作用链：

```text
@RequestBody 创建 LoginRequest
  -> @Valid 触发校验
  -> 检查 @NotBlank、@Size 等规则
  -> 通过后才进入方法
  -> 不通过则由 Spring 返回参数校验错误
```

常见校验注解：

| 注解 | 检查什么 |
| --- | --- |
| `@NotNull` | 不能是 `null`，但字符串可以是空串 |
| `@NotEmpty` | 不能是 `null`，字符串/集合长度不能为 0 |
| `@NotBlank` | 字符串不能是 `null`、空串或全空格 |
| `@Size(min, max)` | 字符串、集合等长度范围 |
| `@Email` | 字符串是否符合邮箱格式 |

这些来自 Jakarta Bean Validation，不是 Spring 自己定义的注解；Spring Boot 集成了校验器，所以能在 Controller 中使用。

#### 10. 方法执行前检查权限：`@PreAuthorize`

代码位置：`FileUploadController.java`。

```java
@PreAuthorize("hasAuthority('document:write')")
public ResponseEntity<?> uploadFileAsync(...) {
}
```

`SecurityConfig` 先开启方法权限：

```java
@EnableMethodSecurity(prePostEnabled = true)
```

之后外部调用受保护方法时，Spring Security 会在进入方法前检查当前用户是否拥有 `document:write`。没有权限就拒绝，不执行方法体。

常见写法：

```java
@PreAuthorize("hasAuthority('document:write')")
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("isAuthenticated()")
```

项目中的 `User.getAuthorities()` 同时生成：

```text
ROLE_ADMIN                 角色
document:read              具体权限
document:write             具体权限
```

因此：

```text
hasRole("ADMIN")
```

会按 `ROLE_ADMIN` 检查；

```text
hasAuthority("document:write")
```

则按完整权限字符串检查。

#### 11. 事务边界：`@Transactional`

代码位置：`DocumentService`、`DocumentChunkService` 等。

```java
@Transactional
public Document updateDocument(Long id, Document document) {
    // 多次数据库读写
}
```

它解决的问题是：一组数据库操作应该作为一个整体成功或失败。

```text
进入事务方法
  -> 开启事务
  -> 执行多次查询和保存
  -> 正常结束：提交事务
  -> 发生需要回滚的异常：回滚事务
```

Spring 通常同样通过代理在真实方法前后添加事务行为：

```text
调用者
  -> 事务代理开启事务
  -> 真实 Service 方法
  -> 事务代理提交或回滚
```

默认情况下，未捕获的 `RuntimeException` 和 `Error` 会触发回滚；受检异常是否回滚需要查看或配置 `rollbackFor`。

```java
@Transactional(rollbackFor = Exception.class)
```

和 `@Async` 一样，同类内部直接调用可能绕过默认代理，导致期望的事务增强没有应用到这次调用。

#### 12. 异步边界：`@EnableAsync` 与 `@Async`

这组注解已经在第 006 节详细解释，这里只放回总图中：

```java
@EnableAsync
```

开启 Spring 异步代理支持。

```java
@Async("taskExecutor")
public void processFileAsync(String uploadId) {
}
```

外部 Bean 调用时：

```text
调用 Worker 代理
  -> 代理把任务提交给 taskExecutor
  -> 请求线程返回
  -> file-processing-* 线程执行真实方法
```

`@Async` 不负责配置线程数；核心线程、最大线程和队列容量仍由 `AsyncConfig` 中的 Executor 决定。

#### 13. JPA 实体映射注解：它们不是 Controller 注解

代码位置：`Document.java`、`User.java`、`UploadProgress.java` 等 Entity。

这一组主要来自 Jakarta Persistence，由 Hibernate 读取。

| 注解 | 作用 |
| --- | --- |
| `@Entity` | 这个类是可持久化实体 |
| `@Table(name = "...")` | 指定数据库表名 |
| `@Id` | 标记主键字段 |
| `@GeneratedValue` | 主键由数据库或指定策略生成 |
| `@Column` | 配置列名、长度、是否为空、是否唯一等 |
| `@Enumerated(EnumType.STRING)` | 将枚举按字符串保存，而不是按数字序号 |
| `@ManyToOne` | 多条当前记录关联同一条目标记录 |
| `@OneToMany` | 一条当前记录拥有多条目标记录 |
| `@ManyToMany` | 两边都可能关联多条记录，通常需要中间表 |
| `@JoinColumn` | 指定关联使用的外键列 |
| `@JoinTable` | 指定多对多中间表 |
| `@EntityListeners` | 给实体注册审计等监听器 |
| `@CreatedDate` | 自动记录创建时间 |
| `@LastModifiedDate` | 自动记录最后修改时间 |

最小例子：

```java
@Entity
@Table(name = "upload_progress")
public class UploadProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uploadId;

    @Enumerated(EnumType.STRING)
    private UploadStatus status;
}
```

它只描述 Java 对象怎样对应数据库表，不负责决定 HTTP 接口路径。

#### 14. Repository 查询：`@Query`、`@Param`、`@Modifying`

Spring Data JPA 已经通过 `JpaRepository` 提供基础增删改查。特殊查询可以写：

```java
@Query("SELECT d FROM Document d WHERE d.title LIKE %:title%")
List<Document> search(@Param("title") String title);
```

| 注解 | 作用 |
| --- | --- |
| `@Query` | 自定义 JPQL 或原生 SQL |
| `@Param` | 将方法参数绑定到查询中的命名参数 |
| `@Modifying` | 表明 `@Query` 执行的是更新、删除或插入，而不是查询 |

例如项目中原生插入文档块：

```java
@Modifying
@Transactional
@Query(value = "INSERT INTO document_chunks ...", nativeQuery = true)
void insertChunk(...);
```

`@Modifying` 不能替代 `@Transactional`：前者说明 SQL 类型，后者管理事务边界。

#### 15. 跨域：`@CrossOrigin`

```java
@CrossOrigin(originPatterns = {"*"}, allowCredentials = "true")
```

它允许指定来源的浏览器跨域调用 Controller 或方法。

当前项目同时在 `CorsConfig`、`SecurityConfig` 和部分 Controller 中配置了 CORS，职责存在重复。理解时先以 `SecurityConfig` 的全局规则为主，不需要在每个接口上重复添加。

#### 16. 一次异步上传请求经过哪些注解

把前面的知识映射回项目：

```text
应用启动
  -> @SpringBootApplication 扫描项目
  -> @Configuration + @Bean 创建 taskExecutor
  -> @Service 创建 ProcessingService 和 Worker
  -> @RestController 创建 FileUploadController
  -> @RequestMapping + @PostMapping 注册上传 URL

请求到达
  -> JwtAuthenticationFilter 恢复登录用户
  -> @RequestParam 将文件绑定为 MultipartFile
  -> @PreAuthorize 检查 document:write
  -> Controller 调用 ProcessingService
  -> ProcessingService 保存文件和任务记录
  -> 调用带 @Async 的 Worker 代理
  -> 代理把 uploadId 提交给 taskExecutor
  -> Controller 返回 HTTP 202
  -> 后台线程执行真实 Worker
  -> @Transactional 管理分块等数据库操作
  -> @Entity/@Column 等指导 Hibernate 写入 MySQL
```

注解不是互相独立的知识点，而是在同一条请求链中各管一段。

#### 17. 常见注解速查表

| 想解决的问题 | 优先想到的注解 |
| --- | --- |
| 启动 Spring Boot | `@SpringBootApplication` |
| 创建通用 Bean | `@Component` |
| 创建业务 Bean | `@Service` |
| 标记数据访问层 | `@Repository` |
| 创建 REST 接口 | `@RestController` |
| 手动创建第三方或复杂对象 | `@Configuration` + `@Bean` |
| 绑定一组 YAML 配置 | `@ConfigurationProperties` |
| 读取单个配置值 | `@Value` |
| 注册 HTTP 路径 | `@GetMapping`、`@PostMapping` 等 |
| 读取 JSON 请求体 | `@RequestBody` |
| 读取查询/表单参数 | `@RequestParam` |
| 读取 URL 路径变量 | `@PathVariable` |
| 触发 DTO 校验 | `@Valid` |
| 方法执行前检查权限 | `@PreAuthorize` |
| 管理数据库事务 | `@Transactional` |
| 提交后台线程 | `@Async` |
| 映射数据库实体 | `@Entity`、`@Table`、`@Column` |
| 自定义 Repository 查询 | `@Query`、`@Param` |

#### 18. 易错点

1. **不是所有注解都来自 Spring。** `@Entity` 属于 Jakarta Persistence，`@NotBlank` 属于 Bean Validation，`@Override` 属于 Java。
2. **`@Autowired` 不负责凭空创建对象。** Spring 先创建 Bean，再把已有 Bean 注入依赖位置；单构造方法通常可以省略它。
3. **`@RequestBody`、`@RequestParam`、`@PathVariable` 取数据的位置不同。** 分别对应请求体、查询/表单、URL 路径。
4. **DTO 写了 `@NotBlank` 不代表一定会校验。** Controller 参数还需要通过 `@Valid` 触发校验。
5. **`@Transactional` 和 `@Async` 通常依赖代理。** 同类内部直接调用可能绕过代理，不能只看方法上有没有注解。
6. **`hasRole("ADMIN")` 和 `hasAuthority("document:write")` 检查的字符串不同。** 前者按 `ROLE_ADMIN` 角色检查，后者按完整权限名检查。
7. **注解只应承担边界和框架能力，不能代替业务代码。** `@PostMapping` 不会保存文档，`@Async` 不会自动设计任务状态，`@Transactional` 也不会替你决定业务步骤。

