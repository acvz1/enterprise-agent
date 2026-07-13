# 第一阶段学习任务：可靠文档入库

这一阶段先不写 Agent。原因不是降低目标，而是文档入库任务和 Agent Run 具有同一类后端问题：创建任务、状态迁移、后台执行、失败恢复、幂等和进度查询。先在边界更清楚的入库链练会，之后写 Agent 生命周期会顺很多。

## 本阶段目标

把当前实验性的：

```text
Controller -> DocumentProcessingService.uploadFileAsync
           -> 同对象 processFileAsync(@Async)
           -> MultipartFile
```

改成：

```text
Controller
 -> IngestionApplicationService.createJob(file)
    -> FileStorage.save(file)             // 先获得稳定文件地址
    -> IngestionJobRepository.save(job)   // PENDING
    -> IngestionJobDispatcher.dispatch(jobId)
 -> 202 Accepted + jobId

独立 worker bean
 -> load(jobId)
 -> 状态机校验
 -> parse -> persist document -> chunk -> embed
 -> COMPLETED / FAILED
```

## 第一性原理：每个模块为什么存在

| 模块 | 输入 | 输出 | 存在原因 |
|---|---|---|---|
| Controller | Multipart HTTP 请求 | `202 + jobId` | 处理协议，不承载后台业务流程 |
| FileStorage | `MultipartFile` | 稳定的 `StoredFile` | 请求结束后临时文件可能失效 |
| ApplicationService | 上传命令 | 已持久化任务 | 编排创建任务这个用例 |
| Dispatcher | `jobId` | 提交结果 | 隔离线程/消息系统，不传大对象 |
| Worker | `jobId` | 状态变化和业务数据 | 独立 Bean，确保经过 Spring 代理 |
| StateMachine | 当前状态 + 目标状态 | 允许/拒绝 | 防止非法迁移和重复执行 |
| Repository | job 查询/保存 | 持久化 job | 进程重启后仍可查询和恢复 |

## 先读这 6 个文件

按顺序读，不需要先看全部实体：

1. `FileUploadController.java`：HTTP 输入输出和权限入口。
2. `DocumentProcessingService.java`：当前错误编排集中在哪里。
3. `AsyncConfig.java`：`@Async("taskExecutor")` 依赖哪个 Bean。
4. `UploadProgress.java`：已有状态能否承担可靠任务语义。
5. `UploadProgressRepository.java`：持久化边界。
6. `DocumentChunkService.java`：worker 最终要调用的重业务步骤。

## 你先手敲的最小代码

第一小步只写类型，不写线程和文件解析：

1. `IngestionJobStatus` 枚举。
2. `IngestionJob` 的最小字段：`id`、`jobId`、`status`、`storedPath`、`originalFileName`、`attempts`、`errorMessage`、时间字段、乐观锁版本。
3. `canTransitionTo(next)` 或独立状态迁移规则。
4. 状态迁移单元测试，至少覆盖正常路径、终态不可继续、FAILED 重试和非法跳级。

暂时不要写：MQ、分布式锁、分库分表、复杂领域事件。单机持久化任务 + 独立 worker 足够形成最小可靠版本。

## 开始写代码前回答 4 个问题

1. 为什么 Controller 不能把 `MultipartFile` 直接交给后台线程？
2. 为什么把 `processFileAsync` 移到独立 Bean 后 `@Async` 才可靠生效？
3. `COMPLETED` 和 `FAILED` 哪些是终态；FAILED 重试是回到 PENDING 还是新增 attempt？
4. MySQL 文档写成功、Redis 向量写失败时，job 应是什么状态，如何再次执行而不重复创建文档？

下一轮先讨论你的答案和状态机草图，再由你手敲第一组类型。
