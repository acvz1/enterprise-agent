package com.kb.demo.service;

import com.kb.demo.entity.UploadProgress;
import com.kb.demo.mq.DocumentIngestionProducer;
import com.kb.demo.repository.UploadProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private UploadProgressRepository uploadProgressRepository;

    @Mock
    private MetricsService metricsService;

    @Mock
    private DocumentFileStorage documentFileStorage;

    @Mock
    private DocumentIngestionProducer documentIngestionProducer;

    @InjectMocks
    private DocumentProcessingService documentProcessingService;

    private MockMultipartFile mockFile;

    @BeforeEach
    void setUp() {
        mockFile = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "测试文件内容".getBytes());

        when(documentFileStorage.store(anyString(), any(MockMultipartFile.class)))
                .thenReturn(Path.of("data", "ingestion", "stored.pdf"));
    }

    @Test
    void uploadFileAsyncStoresFileCreatesPendingJobAndSendsToMq() {
        saveProgressSuccessfully();

        String uploadId = documentProcessingService.uploadFileAsync(mockFile);

        assertThat(uploadId).isNotBlank();

        InOrder inOrder = inOrder(documentFileStorage, uploadProgressRepository, documentIngestionProducer);
        inOrder.verify(documentFileStorage).store(uploadId, mockFile);
        inOrder.verify(uploadProgressRepository).save(argThat(progress ->
                progress.getUploadId().equals(uploadId)
                        && progress.getFileName().equals("test-document.pdf")
                        && progress.getFileSize().equals(mockFile.getSize())
                        && progress.getUploadedSize().equals(mockFile.getSize())
                        && progress.getStatus() == UploadProgress.UploadStatus.PENDING
                        && progress.getPercentage() == 0));
        inOrder.verify(documentIngestionProducer).send(uploadId);

        verify(metricsService).recordDocumentUpload();
    }

    @Test
    void uploadFileAsyncGeneratesDifferentIdsForDifferentJobs() {
        saveProgressSuccessfully();

        String firstUploadId = documentProcessingService.uploadFileAsync(mockFile);
        String secondUploadId = documentProcessingService.uploadFileAsync(mockFile);

        assertThat(firstUploadId).isNotEqualTo(secondUploadId);
        verify(documentIngestionProducer).send(firstUploadId);
        verify(documentIngestionProducer).send(secondUploadId);
    }

    @Test
    void uploadFileAsyncDeletesStoredFileWhenInitialJobSaveFails() {
        when(uploadProgressRepository.save(any(UploadProgress.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> documentProcessingService.uploadFileAsync(mockFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(documentFileStorage).delete(anyString(), eq("test-document.pdf"));
        verifyNoInteractions(documentIngestionProducer);
    }

    @Test
    void uploadFileAsyncMarksJobFailedAndKeepsSourceWhenMqSendFails() {
        AtomicReference<UploadProgress> savedProgress = new AtomicReference<>();
        when(uploadProgressRepository.save(any(UploadProgress.class))).thenAnswer(invocation -> {
            UploadProgress progress = invocation.getArgument(0);
            savedProgress.set(progress);
            return progress;
        });
        when(uploadProgressRepository.findByUploadId(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(savedProgress.get()));
        doThrow(new RuntimeException("broker unavailable"))
                .when(documentIngestionProducer).send(anyString());

        assertThatThrownBy(() -> documentProcessingService.uploadFileAsync(mockFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("broker unavailable");

        assertThat(savedProgress.get().getStatus()).isEqualTo(UploadProgress.UploadStatus.FAILED);
        assertThat(savedProgress.get().getErrorMessage()).isEqualTo("后台任务提交失败");
        verify(documentFileStorage, never()).delete(anyString(), anyString());
    }

    private void saveProgressSuccessfully() {
        when(uploadProgressRepository.save(any(UploadProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
