# 企业知识库 Agent 项目学习笔记

> 只记录已经接触并能帮助理解项目主线的内容。一个概念保留一条因果链、必要代码和最多一个重要易错点。

## 快速索引

***项目整体***

- [000. 项目地图](#000-项目地图)

***文档异步入库主线***

- [001. MultipartFile：Spring 交给 Controller 的上传文件](#001-multipartfilespring-交给-controller-的上传文件)
- [002. uploadFileAsync：Controller 的四个职责](#002-uploadfileasynccontroller-的四个职责)
- [003. uploadId：一次后台任务的编号](#003-uploadid一次后台任务的编号)
- [004. DocumentProcessingService：先创建任务，再提交后台处理](#004-documentprocessingservice先创建任务再提交后台处理)
- [005. 线程池：让请求线程不必等待文档处理完成](#005-线程池让请求线程不必等待文档处理完成)
- [006. @Async：Spring 怎样把 Worker 方法提交给线程池](#006-asyncspring-怎样把-worker-方法提交给线程池)

***Spring 阅读方法***

- [007. 注解：谁读取它，它就增加谁的行为](#007-注解谁读取它它就增加谁的行为)

## 当前阅读位置

已经完成：

```text
FileUploadController
  -> DocumentProcessingService
  -> UploadProgress / DocumentFileStorage
  -> DocumentProcessingWorker
  -> AsyncConfig
```

下一条主线：

```text
VectorSearchController
  -> VectorSearchService
  -> Redis 向量检索或关键词降级
  -> 返回相关文档片段
```

---

### 000. 项目地图

这是一个使用 Java、Spring Boot、Vue、MySQL 和 Redis Stack 实现的企业知识库应用。

当前定位更准确地说是 **RAG 知识库系统**：已经有文档入库、向量检索、RAG 问答、会话记忆、JWT 和 SSE，但还没有 Agent Loop、Tool Calling、MCP 和 A2A。

后端先只记这条分层：

```text
Controller：接收 HTTP 输入，返回 HTTP 响应
  -> Service：执行业务判断和流程编排
  -> Repository：读写 MySQL

Entity：表示数据库中的数据
DTO：表示接口需要的输入或输出
Config：创建线程池、Redis、安全规则等基础设施
```

项目有三条核心业务链。

#### 文档异步入库

```text
上传 PDF / Word
  -> 保存原文件
  -> 创建 PENDING 任务
  -> 后台解析、分块和向量化
  -> MySQL 保存正文与分块
  -> Redis Stack 保存向量
  -> 更新任务状态
```

#### RAG 问答

```text
用户问题
  -> 检索相关文档片段
  -> 读取最近会话
  -> 拼接提示词
  -> 调用大模型
  -> SSE 返回回答
```

#### 登录与权限

```text
登录获得 JWT
  -> 后续请求携带 Bearer Token
  -> JwtAuthenticationFilter 验证身份
  -> @PreAuthorize 检查具体权限
  -> 进入 Controller
```

**结论**

第一遍阅读不需要遍历所有文件。选择一条业务链，按 `Controller -> Service -> Repository / 外部系统` 向下跟踪。

---

### 001. MultipartFile：Spring 交给 Controller 的上传文件

**需求**

浏览器上传 PDF、Word 等文件后，Controller 需要读取文件名、大小和内容。

**代码位置**

[FileUploadController.java 第 188 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/FileUploadController.java:188)

```java
public ResponseEntity<?> uploadFileAsync(
        @RequestParam("file") MultipartFile file) {
}
```

`MultipartFile` 是 Spring 解析 HTTP 上传请求后创建的对象，表示请求中名为 `file` 的文件部分。

可以把它记成：

```text
MultipartFile
  = 上传文件的信息
  + 读取或保存文件内容的方法
```

```java
file.getOriginalFilename(); // 原始文件名
file.getSize();             // 文件大小
file.getContentType();      // 客户端声明的类型
file.isEmpty();             // 是否为空
file.getInputStream();      // 读取内容
file.transferTo(...);       // 保存到指定位置
```

调用链：

```text
浏览器发送 multipart/form-data
  -> Spring 解析 file 部分
  -> 创建 MultipartFile
  -> 传给 Controller
```

**结论**

`MultipartFile` 是本次 HTTP 请求中的上传文件，不是数据库实体，也不是已经永久保存的磁盘文件。

**易错点**

请求结束后临时上传内容可能失效，因此交给后台线程前必须先保存到稳定位置。

---

### 002. uploadFileAsync：Controller 的四个职责

**需求**

上传接口需要接收文件、做入口校验、调用业务层，并告诉前端任务已经受理。

**代码位置**

[FileUploadController.java 第 186—207 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/FileUploadController.java:186)

核心代码：

```java
if (file.isEmpty()) {
    return ResponseEntity.badRequest().body("文件不能为空");
}

String uploadId = documentProcessingService.uploadFileAsync(file);

result.put("uploadId", uploadId);
result.put("statusUrl", "/api/files/upload-progress/" + uploadId);
return ResponseEntity.accepted().body(result);
```

四个职责：

```text
接收：@RequestParam 将上传内容绑定为 MultipartFile
校验：检查空文件和支持的文件类型
调用：把文件交给 DocumentProcessingService
返回：HTTP 202 + uploadId + 进度查询地址
```

完整接口路径由类路径与方法路径拼接：

```text
/api/files + /upload-async
  = POST /api/files/upload-async
```

**结论**

Controller 管 HTTP 边界；文件保存、任务创建、解析和向量化属于 Service。

**易错点**

`202 Accepted` 只表示后台任务已经受理，不表示文档已经处理成功。

---

### 003. uploadId：一次后台任务的编号

**需求**

HTTP 请求已经返回后，前端、后台 Worker 和数据库仍然需要确认它们正在处理同一次上传。

**代码位置**

[DocumentProcessingService.java 第 43—45 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingService.java:43)

```java
String uploadId = UUID.randomUUID().toString();
```

`UUID.randomUUID()` 生成一个很难重复的标识符，`toString()` 将其转换成适合放入数据库、JSON、URL 和日志的字符串。

项目中的两个 ID：

| ID | 用途 | 生成位置 |
| --- | --- | --- |
| `Long id` | 数据库内部主键 | MySQL |
| `String uploadId` | 对外查询上传任务 | Java 在任务创建前生成 |

调用链：

```text
生成 uploadId
  -> 写入 UploadProgress
  -> 传给 DocumentProcessingWorker
  -> 返回给前端
  -> 前端用它查询同一条任务记录
```

数据库还为 `uploadId` 设置了唯一约束：

[UploadProgress.java 第 19—20 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/UploadProgress.java:19)

```java
@Column(nullable = false, unique = true, length = 36)
private String uploadId;
```

**结论**

`uploadId` 是贯穿 HTTP 响应、后台处理、数据库进度和日志的任务编号。

**易错点**

`uploadId` 只负责标识任务，不是登录凭证，也不会自动防止用户重复提交同一个文件。

---

### 004. DocumentProcessingService：先创建任务，再提交后台处理

**需求**

后台线程开始处理前，原文件必须能够继续读取，数据库中也必须已经存在可查询的任务记录。

**代码位置**

[DocumentProcessingService.java 第 43—68 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingService.java:43)

```java
String uploadId = UUID.randomUUID().toString();
Path storedFile = documentFileStorage.store(uploadId, file);

UploadProgress progress = new UploadProgress();
progress.setUploadId(uploadId);
progress.setStatus(UploadProgress.UploadStatus.PENDING);
uploadProgressRepository.save(progress);

metricsService.recordDocumentUpload();
documentProcessingWorker.processFileAsync(uploadId);
return uploadId;
```

执行顺序：

```text
1. 生成 uploadId
2. 把临时 MultipartFile 保存到稳定目录
3. 保存 PENDING、0% 的 UploadProgress
4. 记录一次上传受理指标
5. 把 uploadId 提交给后台 Worker
6. 返回 uploadId
```

为什么必须先保存进度：

```text
后台线程可能立即开始
前端也可能立即查询
  -> 两边都必须先能根据 uploadId 找到任务
```

Worker 只接收 `uploadId`，再用它查任务和稳定文件；它不持有请求期间的 `MultipartFile`。

任务状态会继续变化：

```text
PENDING
  -> PARSING
  -> CHUNKING
  -> EMBEDDING
  -> COMPLETED 或 FAILED
```

**结论**

`DocumentProcessingService` 负责可靠受理任务；`DocumentProcessingWorker` 负责后台执行任务。

**易错点**

必须先创建任务记录再提交 Worker，否则后台更新进度时可能查不到对应任务。

---

### 005. 线程池：让请求线程不必等待文档处理完成

**需求**

解析、分块和向量化耗时较长，HTTP 请求不应该一直等待整个流程完成。

异步的第一性原理：

```text
普通方法调用
  -> 当前线程进入方法
  -> 必须等方法返回

把任务提交给线程池
  -> 请求线程完成提交后继续返回响应
  -> 工作线程执行耗时任务
```

线程池配置位置：

[AsyncConfig.java 第 25—54 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/AsyncConfig.java:25)

```java
executor.setCorePoolSize(5);
executor.setMaxPoolSize(10);
executor.setQueueCapacity(100);
executor.setThreadNamePrefix("file-processing-");
executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.AbortPolicy());
```

任务进入线程池的顺序：

```text
工作线程少于 5 个
  -> 创建线程执行

5 个核心线程都忙
  -> 最多 100 个任务进入队列

队列也满
  -> 继续创建线程，最多到 10 个

10 个线程和队列都满
  -> AbortPolicy 拒绝新任务
```

项目中的两条执行路线：

```text
HTTP 请求线程
  -> 保存文件和任务
  -> 提交 uploadId
  -> 返回 HTTP 202

file-processing-* 工作线程
  -> 解析
  -> 保存文档
  -> 分块和向量化
  -> 更新最终状态
```

**结论**

异步不会让单个文档处理得更快；它让请求线程先返回，并限制后台任务怎样并发、排队和拒绝。

**易错点**

任务提交成功不等于处理成功，最终结果仍要查看 `UploadProgress`。

---

### 006. @Async：Spring 怎样把 Worker 方法提交给线程池

**需求**

业务代码希望调用一个普通 Java 方法，同时由 Spring 自动把这次调用提交给指定线程池。

**代码位置**

[DocumentProcessingWorker.java 第 49—50 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingWorker.java:49)

```java
@Async("taskExecutor")
public void processFileAsync(String uploadId) {
}
```

`@Async` 是一个标记：外部调用这个方法时，应使用名为 `taskExecutor` 的线程池。

Spring 在概念上增加了一个外层对象：

```java
class WorkerProxy {
    void processFileAsync(String uploadId) {
        taskExecutor.execute(
                () -> realWorker.processFileAsync(uploadId)
        );
    }
}
```

这段代理代码不需要项目手写，但它表达了真正发生的动作：

```text
DocumentProcessingService
  -> 调用 Spring 注入的 Worker 代理
  -> 代理把任务提交给 taskExecutor
  -> 请求线程返回
  -> file-processing-* 线程调用真实 Worker
```

当前项目把代码拆成两个 Bean：

```text
DocumentProcessingService：受理任务
DocumentProcessingWorker：后台处理
```

这样 Service 调用 Worker 时，调用能够从外部进入 Worker 代理。

**结论**

`@Async` 本身不执行文档处理；代理负责提交任务，线程池负责安排工作线程，真实 Worker 负责业务代码。

**易错点**

同一个对象内部使用 `this.processFileAsync()` 会直接调用自己，不会重新进入外层代理，因此可能仍在当前线程同步执行。

---

### 007. 注解：谁读取它，它就增加谁的行为

**需求**

阅读 Spring 项目时，需要判断一个注解在当前调用链中负责哪一步，而不是一次背完整张注解表。

注解本质上是代码标记。标记不会自己执行，必须由 Java、Spring MVC、Spring Security、JPA 等代码读取后才产生行为。

当前上传主线只需要三组：

| 阶段 | 注解 | 当前作用 |
| --- | --- | --- |
| 应用启动 | `@Service` | 创建 Service / Worker Bean |
| 应用启动 | `@Configuration` + `@Bean` | 创建并配置 `taskExecutor` |
| HTTP 请求 | `@RestController` + `@PostMapping` | 注册上传接口 |
| HTTP 请求 | `@RequestParam` | 取得名为 `file` 的上传内容 |
| 方法执行前 | `@PreAuthorize` | 检查 `document:write` 权限 |
| 方法调用时 | `@Async` | 让外部调用通过代理提交线程池 |

以后遇到新注解，只回答三个问题：

```text
谁读取它？
它在当前代码中增加什么行为？
这个行为发生在启动时，还是请求执行时？
```

**结论**

注解是框架行为的入口标记，不能代替业务代码。`@PostMapping` 不会保存文件，`@Async` 也不会自动设计任务状态。
