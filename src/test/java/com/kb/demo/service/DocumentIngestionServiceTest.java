package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.UploadProgress;
import com.kb.demo.repository.UploadProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock UploadProgressRepository uploadProgressRepository;
    @Mock FileParseService fileParseService;
    @Mock DocumentService documentService;
    @Mock DocumentChunkService documentChunkService;
    @Mock MetricsService metricsService;
    @Mock DocumentFileStorage documentFileStorage;
    @Mock DepartmentAccessService departmentAccessService;

    @InjectMocks DocumentIngestionService service;

    private UploadProgress pending(String uploadId) {
        UploadProgress p = new UploadProgress();
        p.setUploadId(uploadId);
        p.setFileName("test.txt");
        p.setFileSize(100L);
        p.setStatus(UploadProgress.UploadStatus.PENDING);
        p.setGeneration(0);
        p.setAttemptToken(null);
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    // -----------------------------------------------------------------------
    // tryClaim
    // -----------------------------------------------------------------------

    @Test
    void tryClaim_skips_when_completed() {
        UploadProgress p = pending("uid1");
        p.setStatus(UploadProgress.UploadStatus.COMPLETED);
        when(uploadProgressRepository.findByUploadId("uid1")).thenReturn(Optional.of(p));

        DocumentIngestionService.ClaimResult result = service.tryClaim("uid1");

        assertThat(result.claimed()).isFalse();
        assertThat(result.skipReason()).isEqualTo("COMPLETED");
    }

    @Test
    void tryClaim_skips_when_failed() {
        UploadProgress p = pending("uid2");
        p.setStatus(UploadProgress.UploadStatus.FAILED);
        when(uploadProgressRepository.findByUploadId("uid2")).thenReturn(Optional.of(p));

        DocumentIngestionService.ClaimResult result = service.tryClaim("uid2");

        assertThat(result.claimed()).isFalse();
        assertThat(result.skipReason()).isEqualTo("FAILED_AWAIT_MANUAL_RETRY");
    }

    @Test
    void tryClaim_skips_when_processing_lease_active() {
        UploadProgress p = pending("uid3");
        p.setStatus(UploadProgress.UploadStatus.PROCESSING);
        p.setUpdatedAt(LocalDateTime.now()); // fresh → lease not expired
        when(uploadProgressRepository.findByUploadId("uid3")).thenReturn(Optional.of(p));

        DocumentIngestionService.ClaimResult result = service.tryClaim("uid3");

        assertThat(result.claimed()).isFalse();
        assertThat(result.skipReason()).isEqualTo("PROCESSING_LEASE_ACTIVE");
    }

    @Test
    void tryClaim_resets_and_claims_when_processing_lease_expired() {
        UploadProgress p = pending("uid4");
        p.setStatus(UploadProgress.UploadStatus.PROCESSING);
        p.setUpdatedAt(LocalDateTime.now().minusMinutes(15)); // expired
        p.setGeneration(1);
        p.setAttemptToken("old-token");

        when(uploadProgressRepository.findByUploadId("uid4"))
                .thenReturn(Optional.of(p));
        when(uploadProgressRepository.claimIfPending(eq("uid4"), anyString()))
                .thenReturn(1);

        // After reset, the re-read for generation/token
        UploadProgress afterClaim = pending("uid4");
        afterClaim.setGeneration(2);
        afterClaim.setAttemptToken("new-token");
        afterClaim.setStatus(UploadProgress.UploadStatus.PROCESSING);
        when(uploadProgressRepository.findByUploadId("uid4"))
                .thenReturn(Optional.of(p))
                .thenReturn(Optional.of(afterClaim));

        DocumentIngestionService.ClaimResult result = service.tryClaim("uid4");

        assertThat(result.claimed()).isTrue();
        verify(uploadProgressRepository).resetToPending("uid4", UploadProgress.UploadStatus.PROCESSING);
    }

    @Test
    void tryClaim_returns_claimed_for_pending() {
        UploadProgress p = pending("uid5");
        UploadProgress claimed = pending("uid5");
        claimed.setGeneration(1);
        claimed.setAttemptToken("tok");

        when(uploadProgressRepository.findByUploadId("uid5"))
                .thenReturn(Optional.of(p))
                .thenReturn(Optional.of(claimed));
        when(uploadProgressRepository.claimIfPending(eq("uid5"), anyString()))
                .thenReturn(1);

        DocumentIngestionService.ClaimResult result = service.tryClaim("uid5");

        assertThat(result.claimed()).isTrue();
        assertThat(result.generation()).isEqualTo(1);
        assertThat(result.token()).isEqualTo("tok");
    }

    @Test
    void tryClaim_skips_when_concurrent_claim_lost() {
        UploadProgress p = pending("uid6");
        when(uploadProgressRepository.findByUploadId("uid6")).thenReturn(Optional.of(p));
        when(uploadProgressRepository.claimIfPending(eq("uid6"), anyString()))
                .thenReturn(0); // lost race

        DocumentIngestionService.ClaimResult result = service.tryClaim("uid6");

        assertThat(result.claimed()).isFalse();
        assertThat(result.skipReason()).isEqualTo("CONCURRENT_CLAIM_LOST");
    }

    // -----------------------------------------------------------------------
    // process — skip path
    // -----------------------------------------------------------------------

    @Test
    void process_does_nothing_when_claim_skipped() {
        UploadProgress p = pending("uid7");
        p.setStatus(UploadProgress.UploadStatus.COMPLETED);
        when(uploadProgressRepository.findByUploadId("uid7")).thenReturn(Optional.of(p));

        service.process("uid7", false);

        verifyNoInteractions(fileParseService);
    }

    // -----------------------------------------------------------------------
    // process — success path
    // -----------------------------------------------------------------------

    @Test
    void process_marks_completed_on_success() throws Exception {
        String uploadId = "uid8";
        UploadProgress pending = pending(uploadId);
        UploadProgress afterClaim = pending(uploadId);
        afterClaim.setGeneration(1);
        afterClaim.setAttemptToken("tok8");
        afterClaim.setStatus(UploadProgress.UploadStatus.PROCESSING);

        when(uploadProgressRepository.findByUploadId(uploadId))
                .thenReturn(Optional.of(pending))   // tryClaim read
                .thenReturn(Optional.of(afterClaim)) // doAtomicClaim re-read
                .thenReturn(Optional.of(afterClaim)) // doIngest read
                .thenReturn(Optional.of(afterClaim)) // updateStatus×3
                .thenReturn(Optional.of(afterClaim))
                .thenReturn(Optional.of(afterClaim))
                .thenReturn(Optional.of(afterClaim)); // markCompleted

        when(uploadProgressRepository.claimIfPending(eq(uploadId), anyString()))
                .thenReturn(1);
        when(documentFileStorage.resolve(eq(uploadId), anyString()))
                .thenReturn(Path.of("/tmp/test.txt"));
        when(fileParseService.parseFile(any(), anyString()))
                .thenReturn(Map.of("title", "T", "content", "C"));
        when(fileParseService.getFileTypeDescription(anyString())).thenReturn("TEXT");

        Document saved = new Document();
        saved.setId(42L);
        when(documentService.saveDocument(any(), eq(false))).thenReturn(saved);

        service.process(uploadId, false);

        verify(documentChunkService).processDocumentWithProgress(eq(42L), any());
        // markCompleted should have saved with COMPLETED status
        verify(uploadProgressRepository, atLeastOnce()).save(argThat(p ->
                p.getStatus() == UploadProgress.UploadStatus.COMPLETED
                        && p.getPercentage() == 100));
    }

    // -----------------------------------------------------------------------
    // process — failure paths
    // -----------------------------------------------------------------------

    @Test
    void process_marks_failed_on_last_attempt() throws Exception {
        String uploadId = "uid9";
        UploadProgress p = pending(uploadId);
        UploadProgress afterClaim = pending(uploadId);
        afterClaim.setGeneration(1);
        afterClaim.setAttemptToken("tok9");
        afterClaim.setStatus(UploadProgress.UploadStatus.PROCESSING);

        when(uploadProgressRepository.findByUploadId(uploadId))
                .thenReturn(Optional.of(p))
                .thenReturn(Optional.of(afterClaim))
                .thenReturn(Optional.of(afterClaim)) // doIngest
                .thenReturn(Optional.of(afterClaim)); // markFailed

        when(uploadProgressRepository.claimIfPending(eq(uploadId), anyString()))
                .thenReturn(1);
        when(documentFileStorage.resolve(eq(uploadId), anyString()))
                .thenReturn(Path.of("/tmp/fail.txt"));
        when(fileParseService.parseFile(any(), anyString()))
                .thenThrow(new RuntimeException("parse error"));

        service.process(uploadId, true);

        verify(uploadProgressRepository, atLeastOnce()).save(argThat(p2 ->
                p2.getStatus() == UploadProgress.UploadStatus.FAILED));
    }

    @Test
    void process_resets_to_pending_and_throws_when_not_last_attempt() throws Exception {
        String uploadId = "uid10";
        UploadProgress p = pending(uploadId);
        UploadProgress afterClaim = pending(uploadId);
        afterClaim.setGeneration(1);
        afterClaim.setAttemptToken("tok10");
        afterClaim.setStatus(UploadProgress.UploadStatus.PROCESSING);

        when(uploadProgressRepository.findByUploadId(uploadId))
                .thenReturn(Optional.of(p))
                .thenReturn(Optional.of(afterClaim))
                .thenReturn(Optional.of(afterClaim)) // doIngest
                .thenReturn(Optional.of(afterClaim)); // resetToRetry

        when(uploadProgressRepository.claimIfPending(eq(uploadId), anyString()))
                .thenReturn(1);
        when(documentFileStorage.resolve(eq(uploadId), anyString()))
                .thenReturn(Path.of("/tmp/retry.txt"));
        when(fileParseService.parseFile(any(), anyString()))
                .thenThrow(new RuntimeException("transient"));

        assertThatThrownBy(() -> service.process(uploadId, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("transient");

        verify(uploadProgressRepository, atLeastOnce()).save(argThat(p2 ->
                p2.getStatus() == UploadProgress.UploadStatus.PENDING));
    }
}
