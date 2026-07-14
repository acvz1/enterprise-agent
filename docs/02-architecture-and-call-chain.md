# 项目结构与三条调用链

这份文档只解决两个问题：

1. 项目中的文件为什么要分成 Controller、Service、Repository？
2. 用户点一次按钮后，代码按什么顺序执行？

## 1. 先看最普通的后端请求

大部分请求都遵循下面的顺序：

```text
前端发送 HTTP 请求
  -> Controller 接收参数
  -> Service 执行业务步骤
  -> Repository 读写数据库
  -> Service 整理结果
  -> Controller 返回 JSON
```

可以把它想成去银行办业务：

| 项目模块 | 银行类比 | 主要职责 |
|---|---|---|
| Controller | 柜台 | 接收申请、检查基础格式、返回办理结果 |
| Service | 业务人员 | 决定先做什么、后做什么 |
| Repository | 档案系统接口 | 查询和保存数据库记录 |
| Entity | 一张业务记录 | 用 Java 对象表示数据库中的数据 |

这样分层的原因是：接收 HTTP、处理业务和操作数据库是三种不同工作。分开后更容易测试和修改。

## 2. 项目的四个外部部分

```text
Vue 前端
   |
   | HTTP 请求 / 持续推送的回答
   v
Spring Boot 后端
   |-------------------|-------------------|
   v                   v                   v
MySQL               Redis Stack          大模型 API
```

- MySQL：长期保存用户、文档、文档段落等业务数据。
- Redis Stack：保存向量、最近对话和短期缓存。向量用于寻找语义相近的文字。
- 大模型 API：接收问题和参考资料，生成最终文字回答。

一句话区分 MySQL 和 Redis：MySQL 里的业务数据不能轻易丢；Redis 里的搜索索引和缓存即使丢了，也应该能重新生成。

后续还会正式加入 Elasticsearch，但它目前不在运行链路里。加入后的分工是：MySQL 保存权威业务数据，Redis 做语义相似搜索，Elasticsearch 做关键词搜索、条件过滤和高亮。搜索索引都必须能根据 MySQL 数据重新生成。

## 3. 调用链一：用户登录

输入：用户名和密码。

输出：JWT，也就是后续请求携带的登录凭证。

```text
登录页面
 -> AuthController：接收用户名和密码
 -> AuthService：检查登录逻辑
 -> UserRepository：从 MySQL 查询用户
 -> PasswordEncoder：比较密码
 -> JwtTokenProvider：生成 JWT
 -> 前端保存 JWT
```

用户以后访问文档或问答接口时：

```text
请求携带 JWT
 -> JwtAuthenticationFilter 检查 JWT
 -> 检查通过后才进入具体 Controller
```

`Filter` 可以先理解成“所有请求进入 Controller 前经过的检查站”。

## 4. 调用链二：上传一篇文档

输入：PDF、Word、Markdown 等文件。

输出：MySQL 中的文档和段落，以及 Redis 中用于搜索的向量。

```text
上传页面
 -> FileUploadController：接收文件
 -> FileParseService：从文件提取纯文字
 -> DocumentService：把文档保存到 MySQL
 -> DocumentChunkService：把长文档切成小段
 -> EmbeddingModel：把每一小段转成向量
 -> RedisEmbeddingStore：把向量保存到 Redis
```

为什么要切成小段？

假设一本员工手册有 100 页，用户只问“病假需要什么证明”。如果把整本书交给大模型，既浪费输入长度，也会混入大量无关内容。切成段后，可以只找最相关的几段。

当前异步上传存在错误：Controller 把请求中的临时文件直接交给同一个 Service 的 `@Async` 方法。第一阶段会把文件先保存到稳定位置，再只把任务编号交给独立后台模块。

## 5. 调用链三：用户提出问题

输入：问题、会话编号、选择的模型。

输出：普通 JSON 回答，或者逐步显示的流式回答。

```text
聊天页面
 -> AiController：接收问题
 -> AiService：组织整个问答步骤
 -> VectorSearchService：寻找相关文档
      -> 把问题转成向量
      -> 从 Redis 找语义相近的段落
      -> 从 MySQL 做关键词搜索
      -> 合并两份搜索排名
 -> ChatMemoryStore：读取最近几轮聊天
 -> AiService：拼出本次给大模型的材料
 -> ModelFactory：根据配置选择具体模型
 -> 大模型生成回答
 -> Controller 把回答返回前端
```

这里的 RAG 就是中间的“先搜索资料，再让大模型回答”这几步，不是一个神秘的新模型。

完成 Elasticsearch 二开后，问答链中的“MySQL 关键词搜索”会替换成：

```text
先计算当前用户有权访问哪些知识范围
Redis：在这个范围内寻找意思相近的段落
Elasticsearch：使用相同范围寻找关键词匹配的段落
 -> 合并两份排名
 -> 再做一次权限校验
 -> 统一返回文档编号、段落编号、分数和引用信息
```

这样加入 Elasticsearch 是为了让它承担明确职责，不是为了让技术栈看起来更多。

## 6. 普通回答和流式回答有什么区别

普通回答：后端等大模型全部生成完，再一次性返回。

流式回答：大模型生成一点，后端就通过 SSE 推送一点，所以前端看起来像逐字出现。

SSE 的全名是 Server-Sent Events。现在只需把它理解为“服务器保持连接，并持续向浏览器发送消息”。

## 7. 当前为什么还不算 Agent

现在的 `AiService` 已经提前写死了执行顺序：

```text
查知识库 -> 读取历史 -> 调大模型 -> 返回
```

真正的 Agent 会让大模型在运行过程中选择下一步：

```text
收到问题
 -> 大模型判断是否需要工具
 -> 如果需要：调用“查知识库”等工具
 -> 把工具结果交回大模型
 -> 大模型再次判断
 -> 得到足够信息后结束
```

这段“判断—调用工具—读取结果—再次判断”的重复过程，叫 Agent Loop。

后面计划新增三个核心模块：

- `AgentLoop`：控制这次循环什么时候继续、什么时候结束。
- `ToolRegistry`：保存系统有哪些工具，并根据工具名找到对应代码。
- `ContextBuilder`：决定这一次把哪些历史消息、文档段落和工具结果交给大模型。

它们现在还没有实现，不需要现在背类名。

## 8. 第一次读代码的顺序

先只跟问答链，不要同时打开整个项目：

1. `AiController.java`：找到 `/api/ai/ask` 接口。
2. `AiService.java`：看 Controller 接下来调用了哪个方法。
3. `VectorSearchService.java`：看问题怎样变成搜索结果。
4. `ModelFactory.java`：看模型名称怎样对应具体客户端。
5. `ChatMemoryStore.java`：看最近对话怎样保存和读取。

每读一个方法只回答三件事：它收到什么、返回什么、下一步调用谁。先不要逐行研究 Spring 注解。
