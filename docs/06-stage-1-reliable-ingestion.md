# 文档入库部分：修好文档上传

这一部分先学习一条后端业务链，不写 Agent。

原因是“后台处理文档”和“Agent 执行任务”都需要解决同一类问题：任务现在做到哪一步、失败在哪里、能不能重试、重复执行会不会产生脏数据。

## 1. 用户眼中的上传过程

用户希望看到：

```text
选择文件
 -> 点击上传
 -> 很快得到任务编号
 -> 页面显示：等待处理
 -> 页面显示：正在解析
 -> 页面显示：正在生成向量
 -> 完成，或者显示失败原因
```

如果解析一个大 PDF 需要 30 秒，接口不应该让浏览器一直干等 30 秒。

## 2. 当前代码的问题

当前大致流程是：

```text
FileUploadController 收到文件
 -> 调用 DocumentProcessingService.uploadFileAsync
 -> 这个方法又直接调用同一个对象的 processFileAsync
```

`processFileAsync` 虽然写了 `@Async`，但可能仍然在原线程执行。

为什么？

Spring 实现异步时，会在真正的 Service 外面包一层“代理”：

```text
外部调用者 -> Spring 代理 -> 真正的 Service
                 |
                 -> 发现 @Async，切换到后台线程
```

但一个 Service 直接调用自己的另一个方法时，执行路线是：

```text
真正的 Service -> 自己的另一个方法
```

它没有再次经过外面的 Spring 代理，所以异步功能可能不生效。这叫“同类内部调用绕过代理”。

第二个问题是 `MultipartFile`。它表示本次 HTTP 上传中的文件，底层可能使用临时文件。请求结束后，临时文件可能被清理，所以不能直接把它交给长期运行的后台线程。

## 3. 我们要改成什么样

```text
Controller 收到 MultipartFile
 -> 先把文件复制到自己管理的稳定目录
 -> 在 MySQL 创建任务记录
 -> 返回 jobId（任务编号）

独立的后台 Service
 -> 根据 jobId 查询任务
 -> 根据稳定文件路径读取文件
 -> 解析、分段、生成向量
 -> 每一步更新 MySQL 中的任务状态
```

为什么后台模块只接收 `jobId`？

- `jobId` 很小，在线程之间传递简单。
- 后台模块可以随时从数据库重新读取任务。
- 即使应用中途重启，任务记录还在。
- 不依赖已经结束的 HTTP 请求对象。

## 4. 任务需要哪些状态

第一版先使用下面这些状态：

| 状态 | 白话含义 |
|---|---|
| `PENDING` | 已创建任务，还没开始 |
| `PARSING` | 正在从文件提取文字 |
| `CHUNKING` | 正在把长文档切成小段 |
| `EMBEDDING` | 正在把段落转换成向量并保存 |
| `COMPLETED` | 全部成功 |
| `FAILED` | 某一步失败，并保存失败原因 |

正常路线：

```text
PENDING -> PARSING -> CHUNKING -> EMBEDDING -> COMPLETED
```

中间任何一步发生异常，都可以进入 `FAILED`。

这套“哪些状态可以变到哪些状态”的规则叫状态机。它首先是一组业务规则，不是必须引入某个复杂框架。

## 5. 第一天只读三个文件

### `FileUploadController.java`

关注点：上传接口收到什么参数，返回什么结果，接下来调用谁。

### `DocumentProcessingService.java`

关注点：`uploadFileAsync()` 和 `processFileAsync()` 为什么在同一个类里会有问题。

### `UploadProgress.java`

关注点：原项目已经保存了哪些任务状态和进度字段。

其他文件先不要展开。

## 6. 第一个手敲任务

第一步只写 `IngestionJobStatus` 枚举，不创建数据库表、不写线程：

```java
public enum IngestionJobStatus {
    PENDING,
    PARSING,
    CHUNKING,
    EMBEDDING,
    COMPLETED,
    FAILED
}
```

然后思考：是否允许直接从 `PENDING` 跳到 `COMPLETED`？是否允许从 `COMPLETED` 回到 `PARSING`？

下一步才会给枚举增加“能否进入下一个状态”的判断，并为状态变化写测试。

## 7. 写代码前先回答三个问题

1. 为什么接口不能一直等到 PDF 解析和向量生成全部完成？
2. 为什么后台代码不应该直接保存并长期使用 `MultipartFile`？
3. 为什么 `processFileAsync()` 放在独立的 Spring Service 后，更容易让 `@Async` 生效？

下一轮我们先根据当前代码回答这三个问题，再由你创建枚举文件。我不会直接生成整个入库模块。
