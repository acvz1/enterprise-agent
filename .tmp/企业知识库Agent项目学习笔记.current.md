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

***向量检索主线***

- [007. 懒加载：为什么先保存 documentId 再查 Document](#007-懒加载为什么先保存-documentid-再查-document)
- [008. @PreAuthorize：进入接口前检查用户权限](#008-preauthorize进入接口前检查用户权限)

***RAG 问答主线***

- [009. askQuestion：从用户问题到知识库回答](#009-askquestion从用户问题到知识库回答)
- [010. ModelFactory 与 ModelConfig：从配置到模型客户端](#010-modelfactory-与-modelconfig从配置到模型客户端)



## 当前阅读位置

已经完成：

```text
文档异步入库主线
向量检索主线
AiController -> AiService.askQuestion 同步 RAG 主线
ModelConfig -> ModelFactory 多模型创建主线
```

下一条主线：

```text
AiController.askStream
  -> SseEmitter 怎样持续发送模型生成片段
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

### 007. 懒加载：为什么先保存 documentId 再查 Document

**需求**

向量检索先找到匹配的文档切片 `DocumentChunk`，最终需要拿到切片所属的完整 `Document`。

**第一步：先看 `DocumentChunk` 保存了什么**

[DocumentChunk.java 第 22—24、72—74 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/DocumentChunk.java:22)

```java
@ManyToOne(fetch = FetchType.LAZY)
private Document document;

public Document getDocument() {
    return document;
}
```

一篇 `Document` 可以被切成多个 `DocumentChunk`。因此每个切片都要记录：自己属于哪篇完整文档。

```text
员工手册 Document（id = 12）
  -> DocumentChunk 1：第一段
  -> DocumentChunk 2：第二段
  -> DocumentChunk 3：第三段
```

字段 `document` 保存的就是这层所属关系。`chunk.getDocument()` 只是它的普通 getter：返回这个切片所属的 `Document`。

**第二步：为什么不在查询切片时立刻查询完整文档**

一次向量检索可能找到很多切片。如果查询每个切片时，都顺便读取文档标题、正文等全部字段，就可能产生很多暂时用不到的数据库查询。

所以这里使用：

```java
fetch = FetchType.LAZY
```

`LAZY` 的意思是“需要时再加载”：

```text
查询 DocumentChunk
  -> 先取得切片自身的数据
  -> 知道它关联的 Document id
  -> 暂时不读取 Document 的标题、正文等字段
```

此时 `document` 不是 `null`，标题等字段也不是“空字符串”；它们只是**尚未从数据库加载**。

**第三步：数据库访问窗口关闭后，Java 对象并不会消失**

Repository 查询数据库时，当前代码会暂时拥有继续读取数据库的条件。可以把这段时间理解成“数据库访问窗口”。查询返回后，这个窗口会关闭。

但查询得到的 `chunk` 仍然是内存中的 Java 对象：

```text
Repository 查询结束
  -> 数据库访问窗口关闭
  -> chunk 对象仍然存在
  -> 已经加载的字段仍然能读
  -> 尚未加载的字段不能再自动去数据库补查
```

例如：

```java
Document document = chunk.getDocument();
Long id = document.getId();       // ID 已知，可以读取
String title = document.getTitle(); // 标题若未加载，就需要再次查数据库
```

如果读取 `title` 时数据库访问窗口已经关闭，这次补查无法完成，就会出现 `LazyInitializationException`。

**第四步：当前代码怎样避开这个问题**

[VectorSearchService.java 第 123—149 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/VectorSearchService.java:123)

```java
DocumentChunk chunk = chunks.get(0);
Long docId = chunk.getDocument().getId();
Document doc = documentRepository.findById(docId).orElse(null);
```

这里先后拿到了两种状态的 `Document`：

```text
chunk.getDocument()
  -> 返回切片关联的 Document
  -> 此时可能只确定了 ID，其他字段尚未加载

documentRepository.findById(docId)
  -> 明确根据 ID 查询数据库
  -> 返回已经读取了普通字段的 Document
```

完整调用链：

```text
findByContentContaining(...) 查询匹配的 DocumentChunk
  -> chunk.getDocument() 找到切片所属的文档
  -> 只读取已经知道的 documentId
  -> 把 documentId 保存进 matchedDocumentIds
  -> documentRepository.findById(documentId)
  -> 明确查询完整 Document
  -> 保存进 matchedDocuments
```

**第五步：串起整个向量查询过程**

[VectorSearchService.java 第 84—149 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/VectorSearchService.java:84)

每次调用 `searchDocuments()`，当前代码都会执行下面这条主线：

```text
接收查询文本 query
  -> 创建 EmbeddingModel
  -> 创建连接 Redis 的 EmbeddingStore
  -> embeddingModel.embed(query)，把查询文本转成向量
  -> 用查询向量、结果数量和最低分数构造 EmbeddingSearchRequest
  -> embeddingStore.search(request)，执行 Redis 向量检索
  -> 得到相似的 TextSegment 文本片段
  -> 根据片段内容查询对应的 DocumentChunk
  -> 从 DocumentChunk 取得 documentId
  -> documentRepository.findById(documentId)
  -> 得到完整 Document
  -> 返回匹配文档列表
```

Redis 向量检索不会直接返回最终的 `Document`。它先返回相似的 `TextSegment`，后面的 Repository 查询负责把文本片段重新对应到数据库中的文档。

**结论**

`DocumentChunk` 中的 `document` 负责表示“切片属于哪篇文档”。由于它采用懒加载，第一次查询切片时可能只确定文档 ID。当前代码先保存稳定的 ID，再通过 Repository 明确查询完整 `Document`，避免数据库访问窗口关闭后才读取尚未加载的字段。

**易错点**

“尚未加载”不等于“字段为空”，Java 对象也没有消失。异常的真正原因是：读取尚未加载的字段需要再次查数据库，但数据库访问窗口已经关闭。

---

### 008. @PreAuthorize：进入接口前检查用户权限

**需求**

向量检索会读取企业文档，不能让没有文档读取权限的用户执行这个接口。

**代码位置**

[VectorSearchController.java 第 36—44 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/VectorSearchController.java:36)

```java
@GetMapping("/documents")
@PreAuthorize("hasAuthority('document:read')")
public ResponseEntity<List<Document>> searchDocuments(...) {
    return ResponseEntity.ok(
            vectorSearchService.searchDocuments(...));
}
```

`@PreAuthorize` 表示：在进入下面的方法以前，先检查括号中的条件。

```text
hasAuthority('document:read')
  -> 检查当前用户是否拥有 document:read 权限
```

可以先把它理解成：

```java
if (当前用户拥有("document:read")) {
    searchDocuments(...);
} else {
    拒绝请求;
}
```

执行顺序：

```text
请求到达 /api/vector-search/documents
  -> 检查当前用户是否拥有 document:read
  -> 有权限：进入 searchDocuments()
  -> 没有权限：直接拒绝，不执行方法内部代码
```

`document:read` 只是项目约定的权限名称，含义是“允许读取文档”。它不是 `Document` 类的字段，也不会执行文档查询。

**结论**

`@PreAuthorize("hasAuthority('document:read')")` 是这个接口入口处的权限门卫：先检查权限，通过后才允许 Controller 调用 `VectorSearchService`。

**易错点**

注解检查发生在方法执行之前；没有权限时，`searchDocuments()` 内部的第一行代码都不会运行。

---

### 009. askQuestion：从用户问题到知识库回答

**需求**

系统需要接收用户问题，从企业知识库找证据，把证据和问题一起交给大模型，并返回有依据的答案。

#### 第一步：Controller 接收 JSON

[AiController.java 第 42—58 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/AiController.java:42)

```java
@PostMapping("/ask")
public Map<String, Object> ask(
        @RequestBody Map<String, String> request) {

    String question = request.get("question");
    String sessionId = request.get("sessionId");
    String model = request.get("model");
}
```

请求体可以是：

```json
{
  "question": "公司的请假制度是什么？",
  "sessionId": "session-001",
  "model": "qwen"
}
```

`@RequestBody` 让 Spring 读取 HTTP 请求体中的 JSON，并转换成 `request`。三次 `request.get(...)` 再分别取出问题、会话编号和模型名称。

`model` 是可选参数：

```text
传了 model
  -> askQuestion(question, sessionId, model)

没传或传入空字符串
  -> askQuestion(question, sessionId)
  -> 后面补上默认模型
```

#### 第二步：两种入口汇合到同一套问答逻辑

[AiService.java 第 60—64 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:60)

```java
public Map<String, Object> askQuestion(
        String question, String sessionId) {
    return askQuestion(
            question,
            sessionId,
            modelConfig.getDefaultModel());
}
```

两个参数的方法只补上默认模型。无论是否指定模型，最终都会进入三个参数的 `askQuestion()`，项目不需要维护两套 RAG 逻辑。

#### 第三步：先检查 Redis 回答缓存

[AiService.java 第 65—72 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:65)

```java
String cacheKey = "ai:answer:"
        + sessionId + ":"
        + question.hashCode() + ":"
        + modelName;

String cachedAnswer =
        redisTemplate.opsForValue().get(cacheKey);

if (cachedAnswer != null) {
    return Map.of(
            "answer", cachedAnswer,
            "fromCache", true,
            "model", modelName);
}
```

缓存键的三部分分别区分会话、问题和模型。查到缓存就直接返回，不再检索知识库和调用模型。

`question.hashCode()` 只是把长问题转换成较短数字，不是加密，也不能保证绝对不重复。

#### 第四步：检索文档并转换成上下文

[AiService.java 第 74—80 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:74)

```java
List<Document> relevantDocuments =
        vectorSearchService.searchDocuments(question);

String context = relevantDocuments.stream()
        .map(doc ->
                "标题: " + doc.getTitle()
                + "\n内容: " + doc.getContent())
        .collect(Collectors.joining("\n\n"));
```

转换过程：

```text
question
  -> VectorSearchService 检索相关 Document
  -> map：逐个把 Document 转成“标题 + 内容”字符串
  -> joining：用空行连接多个字符串
  -> 得到一个 context
```

`doc -> ...` 表示“拿到当前 `Document`，按箭头右侧的规则转换”。这里的输入输出是：

```text
Document -> String
```

`.map()` 是逐个转换数据，不是保存键值对的 `Map` 类型，也不会修改原来的 `Document`。

#### 第五步：把规则、知识库内容和问题拼成提示词

[AiService.java 第 82—85 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:82)

```java
String prompt =
        "请仅根据以下知识库内容回答问题。" +
        "若证据不足，请明确说明知识库中没有足够信息，" +
        "不要使用模型自身知识补充事实。\n\n" +
        "知识库内容:\n" + context + "\n\n" +
        "问题: " + question;
```

大模型不能直接读取 Java 对象，所以必须把内容组织成普通文本：

```text
回答规则
  + context：检索到的知识库证据
  + question：用户问题
  = prompt
```

证据不足时要求模型明确说明不知道，是为了降低脱离知识库编造答案的概率。

#### 第六步：调用模型、缓存并返回答案

[AiService.java 第 87—96 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:87)

```java
ChatLanguageModel model =
        modelFactory.createModel(modelName);

String answer = model.generate(prompt);

redisTemplate.opsForValue().set(
        cacheKey, answer, 5, TimeUnit.MINUTES);

return Map.of(
        "answer", answer,
        "fromCache", false,
        "model", modelName);
```

`createModel()` 创建的是调用模型服务的 Java 客户端，不是在本地训练新模型。`generate(prompt)` 把提示词发送给模型，并得到字符串答案。

新答案会在 Redis 保存五分钟。`fromCache=false` 表示这次确实执行了检索和模型调用。

#### 当前模型错误怎样处理

```text
模型名称未知
  -> ModelFactory 记录警告
  -> 自动改用 Qwen

API Key 错误、网络失败或 model.generate() 报错
  -> AiService 没有 try-catch
  -> 异常继续返回给 Spring
  -> HTTP 500
```

未知名称自动回退还存在一个不一致：实际可能使用 Qwen，但响应中的 `model` 仍然是用户传入的错误名称。

#### 完整调用链

```text
POST /api/ai/ask
  -> @RequestBody 读取 question、sessionId、model
  -> 没指定模型时补上默认模型
  -> 查询 Redis 回答缓存
  -> 未命中缓存：VectorSearchService 检索相关文档
  -> Document 转成 context
  -> 规则 + context + question 组成 prompt
  -> ModelFactory 创建 ChatLanguageModel
  -> model.generate(prompt)
  -> 答案写入 Redis 五分钟
  -> 返回 answer、fromCache、model
```

**结论**

当前同步问答是一个最简 RAG 流程：检索一次、构建一次提示词、调用一次模型并返回答案。它还不是 Agent Loop，因为模型不会自主选择工具并循环执行。

**易错点**

知识库存放在数据库和 Redis 中，不代表大模型自动知道这些内容；只有检索出来并放进 `prompt` 的文本，模型本次调用才能看到。

---

### 010. ModelFactory 与 ModelConfig：从配置到模型客户端

**需求**

项目支持 Qwen、DeepSeek、Kimi 和 Ollama。`AiService` 不应该分别掌握四种客户端的创建细节，只需要根据模型名称取得统一、可调用的模型对象。

#### 第一步：ModelFactory 隔离不同模型的创建方式

[ModelFactory.java 第 32—50 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:32)

```java
public ChatLanguageModel createModel(String modelName) {
    switch (modelName.toLowerCase()) {
        case "qwen":
            return createQwenModel();
        case "deepseek":
            return createDeepseekModel();
        case "kimi":
            return createKimiModel();
        case "ollama":
            return createOllamaModel();
        default:
            return createQwenModel();
    }
}
```

输入输出：

```text
输入：String modelName
  -> ModelFactory 选择具体创建方法
输出：统一的 ChatLanguageModel
```

`toLowerCase()` 让 `"QWEN"`、`"Qwen"` 和 `"qwen"` 都进入同一个分支。

上层 `AiService` 不需要区分具体模型，统一调用：

```java
model.generate(prompt);
```

#### 第二步：Builder 把配置组装成客户端

[ModelFactory.java 第 76—86 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:76)

```java
ModelConfig.ModelProperties config =
        modelConfig.getQwen();

return QwenChatModel.builder()
        .apiKey(config.getApiKey())
        .modelName(config.getModelName())
        .build();
```

Builder 的执行过程：

```text
builder() 创建配置器
  -> apiKey(...) 填入访问凭证
  -> modelName(...) 填入具体模型名
  -> build() 生成最终 QwenChatModel
```

这里的“创建模型”不是训练新模型，而是创建一个能够调用远程模型服务的 Java 客户端。

#### 第三步：baseUrl 决定请求发到哪里

[ModelFactory.java 第 88—113 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:88)

DeepSeek 和 Kimi 都使用：

```java
OpenAiChatModel.builder()
        .apiKey(config.getApiKey())
        .baseUrl(config.getBaseUrl())
        .modelName(config.getModelName())
        .build();
```

三个配置的职责：

```text
baseUrl   -> 请求发到哪个模型服务器
apiKey    -> 使用什么凭证访问
modelName -> 请求服务器上的哪个模型
```

DeepSeek 和 Kimi 支持与 OpenAI 相似的接口格式，因此可以复用 `OpenAiChatModel`。类名不表示请求一定发给 OpenAI，真正的目标由 `baseUrl` 决定。

#### 第四步：Ollama 创建本地模型客户端

[ModelFactory.java 第 116—127 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:116)

```java
return OllamaChatModel.builder()
        .baseUrl(config.getBaseUrl())
        .modelName(config.getModelName())
        .timeout(Duration.ofMinutes(5))
        .build();
```

Ollama 通常运行在本机，默认不需要云平台 API Key。五分钟表示一次请求最多等待五分钟，不是每五分钟调用一次模型。

#### 第五步：同步客户端与流式客户端

[ModelFactory.java 第 54—72 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:54)

```text
ChatLanguageModel
  -> 等模型完成全部生成
  -> 一次得到完整答案

StreamingChatLanguageModel
  -> 模型生成一段，程序接收一段
  -> 多次收到答案片段
```

因此工厂提供两个入口：

```java
createModel(modelName)
createStreamingModel(modelName)
```

二者选择模型的逻辑相似，但创建的客户端类型不同。流式客户端只解决“从模型逐段接收”，答案怎样逐段发给浏览器由后面的 SSE 负责。

#### 第六步：ModelConfig 把 YAML 转成 Java 对象

[ModelConfig.java 第 11—20 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/ModelConfig.java:11)

```java
@Component
@ConfigurationProperties(prefix = "langchain4j")
public class ModelConfig {
    private String defaultModel;
    private ModelProperties qwen;
    private ModelProperties deepseek;
    private ModelProperties kimi;
    private ModelProperties ollama;
}
```

两个注解的分工：

```text
@Component
  -> 让 Spring 创建并管理 ModelConfig 对象

@ConfigurationProperties(prefix = "langchain4j")
  -> 读取 application.yml 中 langchain4j 下的配置
  -> 按属性名称和类型填入 ModelConfig
```

[application.yml 第 75—100 行](D:/Project/enterprise-agent/src/main/resources/application.yml:75) 与 Java 字段的对应关系：

```text
langchain4j.defaultModel
  -> modelConfig.defaultModel

langchain4j.qwen.apiKey
  -> modelConfig.qwen.apiKey

langchain4j.deepseek.baseUrl
  -> modelConfig.deepseek.baseUrl
```

环境变量的传递链：

```text
DEEPSEEK_API_KEY
  -> application.yml 的 ${DEEPSEEK_API_KEY}
  -> ModelConfig.deepseek.apiKey
  -> ModelFactory
  -> OpenAiChatModel
```

#### 第七步：static 内部类不等于静态数据

```java
public static class ModelProperties {
    private String apiKey;
    private String baseUrl;
    private String modelName;
}
```

`ModelProperties` 是四种模型共用的配置结构。Spring 仍然会创建四个不同实例：

```text
qwen     -> 一个 ModelProperties 对象
deepseek -> 另一个 ModelProperties 对象
kimi     -> 另一个 ModelProperties 对象
ollama   -> 另一个 ModelProperties 对象
```

`static` 只表示创建 `ModelProperties` 时不需要隐式绑定某个外层 `ModelConfig` 对象：

```java
new ModelConfig.ModelProperties();
```

它不表示四种模型共享同一份配置，也不表示 `apiKey`、`baseUrl` 和 `modelName` 是静态字段。

配置对象与模型客户端的先后顺序：

```text
Spring 启动
  -> 创建 ModelConfig 和各个 ModelProperties
  -> 从 YAML 填入配置

业务需要调用模型
  -> ModelFactory 读取 ModelProperties
  -> 创建真正的模型客户端
```

#### API Key 日志中的三元表达式

```java
config.getApiKey() != null
    ? config.getApiKey().substring(
            0,
            Math.min(8, config.getApiKey().length())) + "..."
    : "null"
```

等价逻辑：

```text
API Key 不为 null
  -> 记录前 8 位或实际长度中更短的一段，再加 ...

API Key 为 null
  -> 记录字符串 "null"
```

`Math.min(8, length)` 防止长度不足 8 时 `substring(0, 8)` 越界。

#### 完整调用链

```text
环境变量
  -> application.yml
  -> @ConfigurationProperties 绑定 ModelConfig
  -> ModelFactory 根据 modelName 选择创建方法
  -> Builder 填入 apiKey、baseUrl、modelName
  -> build() 创建具体模型客户端
  -> 以 ChatLanguageModel 或 StreamingChatLanguageModel 返回
  -> AiService 统一使用
```

**结论**

`ModelConfig` 负责把外部配置变成有类型的 Java 数据；`ModelFactory` 负责把模型名称和配置变成统一的模型客户端。两者共同把“配置管理”和“业务问答”分开。

**易错点**

即使只打印前几位，日志仍会泄露部分 API Key。更安全的做法是完全不记录密钥内容。
