package com.kb.demo.service;

import com.kb.demo.entity.DocumentIndexSyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 周期性领取失败或超时的索引同步任务。 */
@Component
public class DocumentIndexSyncRetryScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIndexSyncRetryScheduler.class);

    private final DocumentIndexSyncTaskService taskService;
    private final DocumentChunkService documentChunkService;

    public DocumentIndexSyncRetryScheduler(
            DocumentIndexSyncTaskService taskService,
            DocumentChunkService documentChunkService) {
        this.taskService = taskService;
        this.documentChunkService = documentChunkService;
    }

    @Scheduled(fixedDelayString = "${app.index-sync.scheduler-delay-ms:30000}")
    public void retryFailedSyncTasks() {
        for (DocumentIndexSyncTask task : taskService.findRetryableTasks()) {
            try {
                if (task.getOperation() == DocumentIndexSyncTask.Operation.DELETE) {
                    documentChunkService.retryDelete(task.getDocumentId());
                } else {
                    documentChunkService.retryRebuild(task.getDocumentId());
                }
            } catch (Exception exception) {
                // 任务服务已持久化失败原因和下一次重试时间；此处仅保留运行日志。
                logger.warn("索引同步重试失败，documentId={}, operation={}",
                        task.getDocumentId(), task.getOperation(), exception);
            }
        }
    }
}
