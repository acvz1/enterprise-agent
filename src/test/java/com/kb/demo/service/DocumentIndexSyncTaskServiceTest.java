package com.kb.demo.service;

import com.kb.demo.entity.DocumentIndexSyncTask;
import com.kb.demo.repository.DocumentIndexSyncTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIndexSyncTaskServiceTest {

    @Mock private DocumentIndexSyncTaskRepository taskRepository;

    @Test
    void persistsFailureAndSchedulesExponentialRetryForTheCurrentAttempt() {
        DocumentIndexSyncTask task = new DocumentIndexSyncTask(7L, DocumentIndexSyncTask.Operation.REBUILD);
        task.setId(1L);
        when(taskRepository.findByDocumentId(7L)).thenReturn(Optional.of(task));
        DocumentIndexSyncTaskService service = new DocumentIndexSyncTaskService(taskRepository, 5, 300, 300);

        service.request(7L, DocumentIndexSyncTask.Operation.REBUILD);
        DocumentIndexSyncTaskService.SyncAttempt attempt = service.claim(7L).orElseThrow();
        service.markFailure(attempt, new IllegalStateException("Elasticsearch 不可用"));

        assertThat(task.getStatus()).isEqualTo(DocumentIndexSyncTask.Status.RETRYING);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getLastError()).contains("Elasticsearch 不可用");
        assertThat(task.getNextRetryAt()).isAfter(task.getLastAttemptAt());
    }

    @Test
    void newerGenerationPreventsAnOlderWorkerFromMarkingTheTaskSuccessful() {
        DocumentIndexSyncTask task = new DocumentIndexSyncTask(7L, DocumentIndexSyncTask.Operation.REBUILD);
        task.setId(1L);
        when(taskRepository.findByDocumentId(7L)).thenReturn(Optional.of(task));
        DocumentIndexSyncTaskService service = new DocumentIndexSyncTaskService(taskRepository, 5, 300, 300);

        DocumentIndexSyncTaskService.SyncAttempt oldAttempt = service.claim(7L).orElseThrow();
        service.request(7L, DocumentIndexSyncTask.Operation.REBUILD);
        service.markSuccess(oldAttempt);

        assertThat(task.getStatus()).isEqualTo(DocumentIndexSyncTask.Status.PENDING);
    }
}
