package com.kb.demo.service;

import com.kb.demo.entity.DocumentIndexSyncTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIndexSyncRetrySchedulerTest {

    @Mock private DocumentIndexSyncTaskService taskService;
    @Mock private DocumentChunkService documentChunkService;
    @InjectMocks private DocumentIndexSyncRetryScheduler scheduler;

    @Test
    void retriesRebuildAndDeleteUsingTheirPersistedOperation() {
        DocumentIndexSyncTask rebuild = new DocumentIndexSyncTask(11L, DocumentIndexSyncTask.Operation.REBUILD);
        DocumentIndexSyncTask delete = new DocumentIndexSyncTask(12L, DocumentIndexSyncTask.Operation.DELETE);
        when(taskService.findRetryableTasks()).thenReturn(List.of(rebuild, delete));

        scheduler.retryFailedSyncTasks();

        verify(documentChunkService).retryRebuild(11L);
        verify(documentChunkService).retryDelete(12L);
    }
}
