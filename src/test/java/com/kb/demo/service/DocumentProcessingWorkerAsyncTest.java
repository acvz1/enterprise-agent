package com.kb.demo.service;

import com.kb.demo.config.AsyncConfig;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.UploadProgress;
import com.kb.demo.repository.UploadProgressRepository;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        AsyncConfig.class,
        DocumentProcessingWorkerAsyncTest.TestConfig.class
})
class DocumentProcessingWorkerAsyncTest {

    private static final String UPLOAD_ID = "c8a1d4a1-9447-4c39-a48d-c3e84ebea105";

    @Autowired
    private DocumentProcessingWorker worker;

    @MockBean
    private FileParseService fileParseService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private DocumentChunkService documentChunkService;

    @MockBean
    private UploadProgressRepository uploadProgressRepository;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private DocumentFileStorage documentFileStorage;

    private Path storedFile;
    private Timer.Sample timer;

    @BeforeEach
    void setUp() {
        UploadProgress progress = new UploadProgress();
        progress.setUploadId(UPLOAD_ID);
        progress.setFileName("employee-handbook.pdf");
        progress.setStatus(UploadProgress.UploadStatus.PENDING);

        storedFile = Path.of("data", "ingestion", UPLOAD_ID + ".pdf");
        timer = mock(Timer.Sample.class);

        when(uploadProgressRepository.findByUploadId(UPLOAD_ID))
                .thenReturn(Optional.of(progress));
        when(uploadProgressRepository.save(any(UploadProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(documentFileStorage.resolve(UPLOAD_ID, "employee-handbook.pdf"))
                .thenReturn(storedFile);
        when(metricsService.startDocumentProcessingTimer()).thenReturn(timer);
        when(fileParseService.getFileTypeDescription("employee-handbook.pdf"))
                .thenReturn("PDF document");

        Document savedDocument = new Document();
        savedDocument.setId(42L);
        when(documentService.saveDocument(any(Document.class), eq(false)))
                .thenReturn(savedDocument);
        when(documentChunkService.processDocumentWithProgress(eq(42L), any()))
                .thenReturn(1);
    }

    @Test
    void asyncAnnotationRunsWorkerOnConfiguredThreadPool() {
        AtomicReference<String> executionThread = new AtomicReference<>();
        when(fileParseService.parseFile(storedFile, "employee-handbook.pdf"))
                .thenAnswer(invocation -> {
                    executionThread.set(Thread.currentThread().getName());
                    return Map.of("title", "employee-handbook", "content", "company policy");
                });

        assertThat(AopUtils.isAopProxy(worker)).isTrue();

        worker.processFileAsync(UPLOAD_ID);

        verify(metricsService, timeout(3_000)).recordDocumentProcessingTime(timer);
        assertThat(executionThread.get()).startsWith("file-processing-");
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        DocumentProcessingWorker documentProcessingWorker(
                FileParseService fileParseService,
                DocumentService documentService,
                DocumentChunkService documentChunkService,
                UploadProgressRepository uploadProgressRepository,
                MetricsService metricsService,
                DocumentFileStorage documentFileStorage) {
            return new DocumentProcessingWorker(
                    fileParseService,
                    documentService,
                    documentChunkService,
                    uploadProgressRepository,
                    metricsService,
                    documentFileStorage);
        }
    }
}
