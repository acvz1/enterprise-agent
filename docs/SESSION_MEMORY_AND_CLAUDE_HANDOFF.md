# 企业知识库 Agent：完整会话记忆与 Claude Code 交接

更新时间：2026-07-20  
当前项目：D:\Project\enterprise-agent

## 0. 文档用途

这不是逐字聊天记录，而是从整段会话中提取出的完整可执行记忆：用户背景、目标、已经确认的理解、项目现状、环境问题、学习协议、失败教训、代码游标、待办和不可擅自改变的边界。

下一位协作者应先读完本文件，再决定是否读取旧规划文档。若本文件与旧规划冲突，以用户最新指令和本文件记录的边界为准。

> 2026-07-23 更新：本文件后面的旧快照保留历史背景，但当前协作规则以项目根目录 `AGENTS.md` 为准，当前排期和开发断点以 `docs/04-six-day-core-plan.md` 为准。

### 2026-07-23 最新工作断点

```text
产品方向：
Redis 向量候选 + Elasticsearch BM25 候选
  -> RRF 融合
  -> Top K 后批量查询 MySQL
  -> RetrievalHit
  -> RAG/Agent 使用 chunk 证据并返回引用

已完成并真实验证：
  Redis RetrievalCandidate 独立链路
  Elasticsearch BM25 RetrievalCandidate 独立链路

Day 2 已写但未完成正式运行验证：
  Elasticsearch Bulk 批量写入及局部失败检查
  DocumentChunkService 收集并批量同步 ES chunk
  deleteByDocumentId() 使用 deleteByQuery + term(documentId)

恢复后的第一步：
  在 processDocumentWithProgress() 的 indexChunks() 之前，
  每篇文档调用一次 deleteByDocumentId(docId)；
  不能放入逐 chunk 循环。

之后：
  文档删除/全量重建同步 ES
  -> RRF 去重与融合
  -> 第 2 天真实运行验收
```

最新带学规则：

```text
先用一句人话说明动作
  -> 一个不做的后果
  -> 一个精确代码位置
  -> 用户确认后再补 API、边界和验收

陌生第三方 API：
先读官方 REST/JSON 请求
  -> 再看 Java 调用外壳
  -> 根据项目数据模型修改示例
  -> 最后从 Response 推导返回值
```

## 1. 当前状态快照

~~~text
当前模式：交接；后续倾向直接实现，不再继续漫长带读
当前项目：D:\Project\enterprise-agent
产品身份：企业知识库 RAG 问答助手
求职方向：Java 后端开发 / Agent 开发 / AI 应用后端
时间约束：用户最后给出的窗口约 10 天，每天通常可投入 4—6 小时
学习计划所有权：由用户制定
最近代码阅读游标：
  文件：src/main/java/com/kb/demo/service/DocumentProcessingWorker.java
  已到：约第 91 行
  已确认：文档先保存取得 documentId，再切分和向量化；回调持续更新进度
  下一行：COMPLETED 100% 及异常/指标收尾
最新工作倾向：用户认为继续读代码和低质量笔记收益太低，要求“别讲了直接写”
当前未确认具体要写的功能：不能替用户擅自选择
~~~

此前明确提出但尚未实现的候选改造：

1. 重构 ResponseEvaluationService 的粗糙规则评分。
2. 在保留知识库问答产品身份的前提下，加入真正可讲的 Agent 能力。

这两个是候选，不代表下一位协作者可以自行决定先做哪一个。

## 2. 用户背景

用户已经阅读 Hello-Agents 主要章节，AI 使用经验较丰富，不需要从“大模型是什么”开始。

已掌握：

- Config、Message、LLM、Agent、Tool、ToolRegistry 的职责。
- SimpleAgent、ReAct、Plan-and-Solve、Reflection 的基本区别。
- MemoryTool、MemoryManager 及 working、episodic、semantic、perceptual 四类记忆。
- Qdrant、Embedding、Neo4j 的基本用途。
- RAG 入库链路：文档 -> Markdown -> chunk -> embedding -> Qdrant。
- RAG 查询链路：问题向量化 -> 相似度检索 -> chunk -> LLM。
- MQE、HyDE 的基本思路。
- ContextPacket、ContextBuilder、GSSC。
- MemoryTool、RAGTool、NoteTool、TerminalTool 的上下文协作关系。
- MCP 的 Host、Client、Server、list_tools、call_tool 及 MCPTool/MCPServer 概念。
- A2A 的 Agent Card、Skill、Task、Message、Artifact 概念。

尚缺少真实项目经验的重点：

- Agent Loop。
- Tool Calling。
- Memory/RAG 与 ContextBuilder 在完整应用中的协作。
- MCP 实际接入。
- 多 Agent/A2A 的任务生命周期、状态跟踪和编排。

Java 与后端技术栈大约速学一个月，后端项目经验几乎为零。因此不能假设用户熟悉 Spring 注解、Servlet、MVC、异步、JPA 等框架机制。

## 3. 长期项目与求职目标

用户计划做两个主要项目：

1. 企业知识库智能 Agent：Java 后端，前端以快速实现为主。
2. 后续包装或二次开发 DeerFlow，预计耗时更长。

第一个项目必须同时体现：

- Java 后端工程化能力。
- AI 应用后端能力。
- RAG 能力。
- 可被面试官追问的 Agent 架构能力。

用户不满足于“能跑的普通 RAG Demo”，但也不允许为了显得 Agent 化就擅自把产品改成别的业务。

明确否决过的错误方向：

- “企业制度审查 Agent”不是用户选择的产品。
- 不要把企业知识库问答助手改造成审查、审批或其他场景。
- Agent 能力应作为知识库问答流程内的增强，例如决策、工具调用、状态、轨迹和循环，而不是换产品。

面试时需要能讲：

- 为什么使用 Java，而不是 Python。
- Java/Spring 后端如何承载 AI 应用。
- 文档入库、检索、上下文构建、模型调用、流式响应的完整链路。
- 项目原有代码与本人二次开发部分的边界。
- 为什么当前固定 RAG 不是 Agent Loop，以及本人怎样补上 Agent 能力。
- 工程上的异常、测试、可观测性、缓存、权限和性能权衡。

所有简历技术点必须来自真实实现和验证，不能把 TODO 写成项目成果。

## 4. 相关项目与资料路径

### 当前项目

~~~text
D:\Project\enterprise-agent
~~~

上游项目：

~~~text
https://github.com/2518350LJL/ai-knowledge-base
~~~

用户已经把 Git 远程地址改成自己的仓库，用于提交二次开发代码。项目中有 UPSTREAM_NOTICE.md，用于说明上游来源。

### Hello-Agents

~~~text
原项目：D:\Project\hello-agents
手动搭建项目：D:\Project\hello-agents-build
主笔记：C:\Users\Administrator\Desktop\Hello-Agents项目阅读笔记.md
Memory/RAG 面试笔记：C:\Users\Administrator\Desktop\Hello-Agents第八章Memory-RAG面试八股.md
上下文工程面试笔记：C:\Users\Administrator\Desktop\Hello-Agents第九章上下文工程面试八股.md
~~~

### 带读 Skill

~~~text
D:\Project\skill\project-reading-coach
~~~

### LearnWhat 基础后端项目

用户曾暂停企业知识库项目，转去更基础的 Java 全栈项目学习后端架构：

~~~text
D:\Project\LearnWhat
笔记目录：C:\Users\Administrator\Desktop\LearnWhat学习笔记
主要笔记：C:\Users\Administrator\Desktop\LearnWhat学习笔记\LearnWhat-后端学习笔记.md
~~~

另一个候选基础项目：

~~~text
D:\Project\campus-trade-platform
~~~

在 LearnWhat 中已经接触：

- Result 与全局异常处理。
- Component 与 Bean。
- HandlerInterceptor 与 preHandle。
- HttpServletRequest、请求头、OPTIONS。
- WebConfig、WebMvcConfigurer、InterceptorRegistry。
- RestController、RequestMapping、GetMapping、PostMapping。
- RequestAttribute、PathVariable。
- Controller、DTO、Entity、Mapper、Service 分层。
- MyBatis 与 MyBatis-Plus 的区别。
- JWT Claims。
- Postman 的基本使用。

这些概念并不代表已经牢固掌握。若再次出现，应结合当前代码需求，用最小实例验证，不能说“之前学过所以跳过”。

## 5. 用户认可的学习方法

用户明确要求以下元方法同时生效：

### 第一性原理

从已经接受的事实开始，按因果关系推出当前代码为什么需要这个机制。

正确顺序：

~~~text
已知事实
  -> 当前代码遇到的具体问题
  -> 现有知识为什么解决不了
  -> 一个必要的新概念
  -> 对应项目代码
  -> 一个结论
~~~

不能先堆术语，再用更多术语解释。

### 苏格拉底式追问

用户会主动追问不懂的点。每次只回答最新缺口，不提前把整个框架讲完，不抢走用户的追问路径。

### 费曼学习法

追问结束后，由用户用自己的话讲一遍。协作者只修正一个真正影响结果的因果缺口。

### 最小 MVP

一次只弄懂一个新概念，形成可以运行、观察或复述的最小闭环。

### 奥卡姆剃刀

删掉无用内容，但不能把因果链删到只剩一句结论。笔记必须让未来的用户看得懂。

### 实操优先

涉及接口、请求头、状态码、上传、鉴权等边界时，应尽快让用户亲自用 Postman 或 Swagger 调一次。

用户不接受“协作者说它会自动运行，所以就当学会了”。

## 6. 会话快捷指令

这些是固定协议，不能遗忘：

~~~text
A = 只回答当前追问
C = 从已保存的代码游标继续
N = 把已确认内容写入学习笔记
Q = 让用户进行一次费曼复述
~~~

C 必须恢复：

~~~text
current_file
current_line
accepted_conclusion
next_unresolved_line
~~~

不能在 C 时总结、换文件、重开章节或自行安排下一课。

## 7. 回答风格

用户希望：

- 简短、多次、像 Sonnet 的交互节奏。
- 先给准确文件入口、行号和最小代码片段。
- 一次只讲一个新概念。
- 代码具体在哪里必须说清楚。
- 从本质和真实需求开始。
- 不解释显而易见的 Python/Java 基础语法，除非用户明确问。
- 陌生语法、框架接口和复杂逻辑才深入。
- 明确指出错误、设计问题和修改原因，不直接大幅重写。
- 用户说“跳过”时立刻停止。
- 用户说“直接写”时停止教学，进入实现。

用户强烈反感：

- 先讲抽象概念，迟迟不给代码。
- 一次加入多个新名词。
- 在文件间来回跳。
- 用“Spring 自动处理”作为最终答案。
- 未确认理解就推进。
- 把简单问题讲复杂。
- 没有实操，只让用户接受结论。
- 每次犯错后重复道歉和承诺，却不修改工作方式。
- 擅自制定学习计划或改变产品方向。
- 笔记只有结论，没有代码和因果链。
- 以为内容越少越符合奥卡姆剃刀。

当前信任状态很低。下一位协作者不要做情绪化保证；用准确的小结果恢复信任。

## 8. 笔记协议

当前企业知识库学习笔记：

~~~text
C:\Users\Administrator\Desktop\企业知识库Agent项目学习笔记.md
~~~

规则：

1. 编辑前完整读取笔记。
2. 保留用户原文、编号、快速索引和无关改动。
3. 每章真正结束时自动记笔记；N 也会触发记录。
4. 只记录已经讲到并确认的内容。
5. 必须保留：

~~~text
需求
  -> 问题
  -> 准确代码位置
  -> 输入
  -> 关键执行过程
  -> 输出或状态变化
  -> 为什么有效
  -> 一个重要易错点
~~~

6. 易错点必须精炼、重要，会改变理解或结果。
7. 不得把别的主题写进相邻编号。
8. 用户删除或否决的内容不能凭记忆重新创建。
9. 不得只写几个术语和一句结论。
10. 不得把未做的实验写成“已验证”。

用户喜欢 XMU-Rollcall 笔记的组织方式：项目地图和学习内容在同一份主笔记中，因果链完整，代码适量，回看时能恢复理解。

## 9. 当前项目的已知架构

### 文档异步入库

~~~text
客户端上传 PDF/Word
  -> FileUploadController 接收 MultipartFile
  -> 校验文件是否为空、类型是否支持
  -> DocumentProcessingService 创建 uploadId 和初始进度
  -> 保存上传内容到稳定位置
  -> DocumentProcessingWorker 在工作线程解析文件
  -> 创建并保存 Document，获得 documentId
  -> DocumentChunkService 切分文本
  -> Embedding 模型生成向量
  -> Redis 向量存储
  -> 回调更新 UploadProgress
  -> COMPLETED 或 FAILED
~~~

核心状态：

- uploadId 表示一次后台上传处理任务，不等于文档主键。
- documentId 表示保存后的文档记录。
- UploadProgress 让前端在 HTTP 请求已经返回后继续查询后台状态。

### 固定 RAG 问答

~~~text
AiController 接收 question、sessionId、model
  -> AiService 检查 Redis 回答缓存
  -> VectorSearchService 检索相关内容
  -> Repository 重新查询完整 Document，避免懒加载窗口外访问
  -> 文档内容转为 context
  -> buildEnhancedPrompt 组织规则、历史、证据和当前问题
  -> ModelFactory 创建模型客户端
  -> 模型生成答案
  -> 缓存答案并保存会话消息
  -> Controller 返回同步结果或通过 SSE 流式发送
~~~

当前本质：

- 这是固定流程的 RAG，不是 Agent Loop。
- 模型没有自主选择工具。
- 模型不会根据观察结果决定是否再次检索。
- 没有显式的 Thought/Action/Observation 或同等状态循环。

### 权限

Controller 中使用类似：

~~~java
@PreAuthorize("hasAuthority('document:read')")
~~~

含义是进入方法前检查当前用户是否拥有 document:read 权限。

### 数据与外部组件

已知使用：

- MySQL/JPA 保存业务实体。
- Redis 用于回答缓存、会话记忆及向量检索相关能力。
- Embedding 模型把文本转换为向量。
- LangChain4j 模型客户端调用 Qwen、DeepSeek、Kimi 或 Ollama。
- SSE 向浏览器持续发送流式事件。

Elasticsearch 是用户明确计划加入的能力，但不能在未检查当前代码和用户优先级前直接实施。

## 10. 已经确认的学习内容

### MultipartFile

MultipartFile 是 Spring 把一次 multipart/form-data 上传中的文件部分包装成的对象。

用户已接触：

~~~java
file.getOriginalFilename()
file.getSize()
file.getContentType()
file.isEmpty()
file.getInputStream()
file.transferTo(...)
~~~

它只代表本次请求中的上传文件，不等于永久存储。

### ResponseEntity

它是 Controller 返回 HTTP 状态、响应头和响应体的包装对象，不是“判断请求是否正确”的对象。

### UUID 与 uploadId

UUID 用于生成低冲突的任务标识。这里的 uploadId 让前端区分并查询每次后台任务。

### 为什么启动异步前先创建进度

HTTP 接口需要立即把可查询的 uploadId 返回给前端。若先启动后台任务再创建记录，前端可能拿到一个尚不存在、甚至因异常永远不会存在的任务。

### Spring 异步

用户最终理解了“外层代理调用内层真实对象方法”的最小模型，但 Spring 代理、注解和 Service 曾造成很大认知负担。

已确认的关键边界：

- @Async 依赖调用经过 Spring 管理的外层对象。
- 同一个对象内部直接调用自身方法可能绕过外层处理，因此不会触发预期异步。
- 项目后来拆出 DocumentProcessingWorker，目的是让调用跨 Spring 对象边界。

不要主动重开这一章，除非实现任务需要。

### 懒加载

用户已能用自己的话解释：

- DocumentChunk 是文档切片。
- chunk.getDocument() 表示取得这个切片所属的 Document。
- 懒加载关系可能只保留可定位目标的引用。
- 在数据库访问窗口结束后再读取尚未加载的标题或正文，会触发 LazyInitializationException。
- 保存 documentId，再通过 Repository 查询完整 Document，可以在有效访问窗口内取得所需字段。

关键教学教训：必须先解释 chunk.getDocument() 实际返回什么，再讲懒加载。

### VectorSearchService

用户已经看懂的概括：

~~~text
创建 Embedding 模型
  -> 创建 Redis 向量存储
  -> 查询文本转成向量检索请求
  -> 执行检索
  -> 通过 Repository 查询完整文档，规避懒加载问题
  -> 返回检索到的文档
~~~

### AiService 同步 RAG

用户已经看完：

- Controller 的请求进入。
- 缓存检查。
- 向量检索。
- Stream 的 doc -> Lambda。
- map() 做逐项转换。
- 文档转换成上下文。
- 构造 prompt。
- ModelFactory 创建客户端。
- 调用模型。
- 缓存和返回。

已确认结论：当前 askQuestion 是“一次检索、一次构建 prompt、一次模型调用”的固定 RAG。

### ModelFactory 与 ModelConfig

用户已学：

- Factory 根据 modelName 选择不同客户端创建方法。
- Builder 填入 apiKey、baseUrl、modelName。
- baseUrl 决定请求发往哪个模型服务。
- ChatLanguageModel 是同步客户端。
- StreamingChatLanguageModel 是流式客户端。
- @ConfigurationProperties 把 application.yml 的配置绑定到 Java 对象。
- static 内部类不意味着配置字段共享或无需实例化。
- API Key 日志中的三元表达式和 substring/Math.min。

### SSE 与流式回答

用户已确认：

- SseEmitter 是一次可以多次发送数据的 HTTP 响应通道。
- Controller、工作线程和 Spring 持有同一个 emitter 引用。
- emitter.send() 向这条尚未关闭的响应发送一次数据。
- 缓存命中时是完整答案按字符模拟流式。
- 缓存未命中时，模型客户端生成一段就通过 onNext 交付一段。
- onComplete 不是项目自己检查句号或长度；由模型客户端在服务端结束生成后调用重写的回调。
- onError 表示生成失败。

### buildEnhancedPrompt

当前笔记记录：

~~~text
最近对话历史
  + 检索证据
  + 当前问题
  + 回答规则
  = 一次模型调用的 prompt
~~~

已发现限制：

- 最近历史只取有限轮数。
- 每篇文档正文只截取前 1500 字符。
- 如果关键信息位于后半段，模型根本看不到。
- 后续应传相关 chunk/证据片段，而不是“整篇文档开头”。

## 11. 实操和环境事实

### Postman

用户此前没用过 Postman，安装时应用曾无法打开，路径为：

~~~text
C:\Users\Administrator\AppData\Local\Postman
~~~

后来已经成功使用 Postman 调通项目接口，并连续确认多次“成功”。具体接口集合应从 Postman 历史或 Controller 重新确认，不要凭本文件编造。

### Java 版本

使用 Java 25 运行 Maven 时出现：

~~~text
Java 25 (69) is not supported by the current version of Byte Buddy
which officially supports Java 22 (66)
~~~

当前可靠做法是使用 JDK 21：

~~~powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
~~~

Spring Boot 运行失败时，最后的 Maven “exit code 1”通常只是外层错误；必须向上找第一条具体异常。

### Docker

用户报告过 Docker 启动错误。随后应用接口通过 Postman 成功，但不能据此断言完整 Docker Compose 已稳定通过。下一次涉及 Docker 时必须重新执行并记录具体容器状态和首个错误。

## 12. 当前学习笔记目录

企业知识库主笔记目前包含：

~~~text
000 项目地图
001 MultipartFile
002 uploadFileAsync 的 Controller 职责
003 uploadId
004 DocumentProcessingService
005 线程池
006 @Async
007 懒加载与 VectorSearch
008 @PreAuthorize
009 askQuestion 同步 RAG
010 ModelFactory 与 ModelConfig
011 SSE 流式问答
012 buildEnhancedPrompt
~~~

现有 009—012 的因果链相对完整。不要因为“需要简洁”把它们缩成几条结论。

曾发生过把错误内容写进 012、把文档切分和 buildEnhancedPrompt 混在一起的问题。用户已经删除过不满意的版本。后续编辑必须按准确章节处理。

## 13. 当前代码游标和未完成阅读

最后明确恢复的源码位置：

[DocumentProcessingWorker.java 第 91 行](../src/main/java/com/kb/demo/service/DocumentProcessingWorker.java#L91)

前一段已经确认：

~~~text
解析文件
  -> 创建 Document
  -> saveDocument 得到 savedDocument.id
  -> processDocumentWithProgress(id, callback)
  -> callback 把 80%—99% 的切分/向量化进度写入 UploadProgress
~~~

若用户以后明确要求 C，下一步才从：

~~~java
updateProgress(uploadId, UploadProgress.UploadStatus.COMPLETED, 100);
~~~

继续，然后看 catch 和 finally。

但用户最新已经切换到“直接写”。不要因为存在阅读游标就自动恢复阅读。

## 14. 已确认的技术债与 TODO

### 回答质量评估

[ResponseEvaluationService.java](../src/main/java/com/kb/demo/service/ResponseEvaluationService.java)

当前评分主要基于：

- 检索文档数量。
- 回答长度。
- 句子数。
- 逻辑连接词。
- “知识库没有”“我确定”等关键词。

问题：

- 没有把回答中的结论与检索证据逐项比较。
- relevance、completeness、hallucination 只是启发式分数。
- 无法可靠证明答案正确或识别幻觉。

已写入路线文档的重构目标：

1. 输入包含问题、完整回答和实际检索证据。
2. 独立评估模型按固定结构返回相关性、完整性、忠实度和扣分原因。
3. 用“结论能否被原文支持”替代粗糙幻觉关键词。
4. 评估失败不能影响主回答返回，但必须记录原因。
5. 测试覆盖有证据回答、无证据拒答、脱离证据编造。

注意：这是 TODO，不是已实现功能。

### Prompt 证据截断

buildEnhancedPrompt 每篇文档取前 1500 字符，可能丢掉真正相关内容。应考虑直接使用排序后的相关 chunk，并携带来源信息。

### Agent 能力缺口

当前项目不能仅凭固定 RAG 自称具备 Agent 架构。

要形成可讲的 Agent 能力，至少需要某个真实实现包含：

~~~text
目标或问题
  -> 模型或规则做出下一步决策
  -> 调用一个明确工具
  -> 获得 observation
  -> 更新状态或上下文
  -> 决定继续调用还是输出最终答案
  -> 保存可观察执行轨迹
~~~

具体工具、循环次数、终止条件和接口必须由用户确认后再设计。

### Elasticsearch

用户明确打算加入 Elasticsearch。它可能用于关键词检索或混合检索，但具体方案、数据同步和排序融合尚未确认。

## 15. 十天约束下的规划边界

用户不接受“协作者自己规划一套，最后无法执行”。

任何后续计划必须对每个功能单元同时给出：

~~~text
用户可见结果
具体代码文件
最小实现
验证方式
面试时能讲的点
预计耗时
前置依赖
放弃或降级条件
~~~

规划与执行必须连接。不能只读代码，也不能一次生成全部重构。

用户此前要求按“每部分功能需要几天”估算，不喜欢机械的 Day 1、Day 2 日历；若用户再次明确要求十天日程，再给日历。

由于当前时间紧，继续阅读只有在阻塞下一项代码实现时才有价值。

## 16. 已经造成信任损失的具体错误

这些不是泛泛的“沟通问题”，而是必须避免的重复故障：

1. 没有从准确文件和行号开始。
2. 用户问一个概念时，一次讲 Spring、代理、注解、线程池、Service 等多个概念。
3. 在用户没有学过 MVC 时，直接用 MVC 内部概念解释。
4. 反复说“Spring 自动调用”，没有展示调用者、对象和时间。
5. 在文件间跳转，用户找不到对应代码。
6. 把代码阅读当作目的，而不是为了实现和面试表达。
7. 一章结束没有自动记录笔记。
8. 笔记过度删减，只剩结论，未来无法恢复理解。
9. 笔记加入了错误章节或无关内容。
10. 忘记 A/C/N/Q。
11. C 时没有准确代码游标。
12. 用户要求实操时，没有立即引导 Postman/Swagger。
13. 用户让制定求职项目时，擅自把产品改成“制度审查 Agent”。
14. 只强调 RAG，忽略用户需要展示 Agent 架构能力。
15. 时间规划与实际执行脱节。
16. 用户说“直接写”后，仍可能擅自选择一个实现方向。
17. 用反复道歉替代修改文件、状态或流程。

## 17. 下一位协作者的启动协议

第一次响应前：

1. 读本文件。
2. 读 D:\Project\skill\project-reading-coach\SKILL.md。
3. 查看 git status，保护用户已有修改。
4. 识别用户最新模式。
5. 不主动复述整份记忆，除非用户要求。

如果用户说 A：

- 只回答最新追问。

如果用户说 C：

- 仅在教学模式下，从本文件记录的游标或用户最新游标继续。

如果用户说 N：

- 完整读取主笔记和 note-writing 规则，再做最小编辑。

如果用户说 Q：

- 只考已经覆盖的内容。

如果用户说“直接写”：

- 确认最后明确的功能目标。
- 若目标明确，直接检查代码、实现和测试。
- 若没有明确目标，只问一句“先写哪个功能”，不要提供一大组新方案。

## 18. 当前工作树提醒

2026-07-20 检查时存在用户或旧协作者留下的未提交改动：

~~~text
M  .omc/project-memory.json
M  .tmp/企业知识库Agent项目学习笔记.current.md
M  docs/03-secondary-development-roadmap.md
?? .omc/sessions/...
?? .omc/state/sessions/...
?? .tmp/project-reading-coach-update-v2/
?? .tmp/project-reading-coach-update-v3/
?? .tmp/project-reading-coach-update-v4/
?? reports/
~~~

本文件和 v5 Skill 暂存目录是本次新增。不要删除、覆盖或回滚其他未提交内容。

旧 reports 中可能存在错误的求职方向判断，尤其是“企业制度审查 Agent”。不要把它当作用户决定。

## 19. 事实与未决项

已经确认：

- 产品是企业知识库 RAG 问答助手。
- Java/Spring 后端是主要展示面。
- 当前固定 RAG 不等于 Agent Loop。
- 用户要在有限时间内完成可写入简历的二次开发。
- 用户现在更倾向直接实现，而不是继续低收益带读。
- ResponseEvaluationService 评分粗糙。
- prompt 传递文档开头会丢失后部关键信息。
- JDK 21 是当前 Maven 兼容运行选择。
- Postman 已有成功实践。

尚未确认：

- 下一项具体实现到底是评估重构、Agent Loop、Elasticsearch，还是其他功能。
- 十天内各功能的最终取舍。
- Agent 的具体工具集合与终止策略。
- Elasticsearch 是替代、补充还是混合检索的一部分。
- Docker Compose 当前是否全量稳定。
- 最终简历项目描述。

不要把未决项当成已决定事项。

## 20. 最后一句执行原则

先确认用户此刻要学习、诊断还是写代码；然后只完成那个模式下最小而真实的闭环。准确的小结果比宏大的新规划更重要。
