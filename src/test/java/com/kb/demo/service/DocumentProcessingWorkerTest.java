package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.UploadProgress;
import com.kb.demo.repository.UploadProgressRepository;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingWorkerTest {

    private static final String UPLOAD_ID = "c8a1d4a1-9447-4c39-a48d-c3e84ebea105";

    @Mock
    private FileParseService fileParseService;

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentChunkService documentChunkService;

    @Mock
    private UploadProgressRepository uploadProgressRepository;

    @Mock
    private MetricsService metricsService;

    @Mock
    private DocumentFileStorage documentFileStorage;

    @Mock
    private Timer.Sample timer;

    @InjectMocks
    private DocumentProcessingWorker worker;

    private UploadProgress progress;
    private Path storedFile;
    private List<UploadProgress.UploadStatus> savedStatuses;

    @BeforeEach
    void setUp() {
        progress = new UploadProgress();
        progress.setUploadId(UPLOAD_ID);
        progress.setFileName("employee-handbook.pdf");
        progress.setFileSize(100L);
        progress.setStatus(UploadProgress.UploadStatus.PENDING);
        progress.setPercentage(0);

        storedFile = Path.of("data", "ingestion", UPLOAD_ID + ".pdf");
        savedStatuses = new ArrayList<>();

        when(metricsService.startDocumentProcessingTimer()).thenReturn(timer);
        when(uploadProgressRepository.findByUploadId(UPLOAD_ID)).thenReturn(Optional.of(progress));
        when(uploadProgressRepository.save(any(UploadProgress.class))).thenAnswer(invocation -> {
            UploadProgress saved = invocation.getArgument(0);
            savedStatuses.add(saved.getStatus());
            return saved;
        });
        when(documentFileStorage.resolve(UPLOAD_ID, "employee-handbook.pdf"))
                .thenReturn(storedFile);
    }

    @Test
    void processFileAsyncCompletesIngestionFromDurableFile() {
        when(fileParseService.parseFile(storedFile, "employee-handbook.pdf"))
                .thenReturn(Map.of("title", "employee-handbook", "content", "company policy"));
        when(fileParseService.getFileTypeDescription("employee-handbook.pdf"))
                .thenReturn("PDF文档");

        Document savedDocument = new Document();
        savedDocument.setId(42L);
        when(documentService.saveDocument(any(Document.class), eq(false))).thenReturn(savedDocument);
        when(documentChunkService.processDocumentWithProgress(eq(42L), any()))
                .thenReturn(2);

        worker.processFileAsync(UPLOAD_ID);

        verify(fileParseService).parseFile(storedFile, "employee-handbook.pdf");
        verify(documentService).saveDocument(argThat(document ->
                document.getTitle().equals("employee-handbook")
                        && document.getContent().contains("company policy")), eq(false));
        verify(documentChunkService).processDocumentWithProgress(eq(42L), any());
        verify(metricsService).recordDocumentProcessingTime(timer);

        assertThat(savedStatuses).containsSubsequence(
                UploadProgress.UploadStatus.PARSING,
                UploadProgress.UploadStatus.CHUNKING,
                UploadProgress.UploadStatus.EMBEDDING,
                UploadProgress.UploadStatus.COMPLETED);
    }

    @Test
    void processFileAsyncMarksJobFailedWhenParsingFails() {
        when(fileParseService.parseFile(storedFile, "employee-handbook.pdf"))
                .thenThrow(new IllegalStateException("corrupt PDF"));

        worker.processFileAsync(UPLOAD_ID);

        assertThat(progress.getStatus()).isEqualTo(UploadProgress.UploadStatus.FAILED);
        assertThat(progress.getErrorMessage()).isEqualTo("corrupt PDF");
        verify(metricsService).recordDocumentProcessingTime(timer);
        verifyNoInteractions(documentService, documentChunkService);
    }
}
