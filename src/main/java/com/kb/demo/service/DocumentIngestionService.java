package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.UploadProgress;
import com.kb.demo.repository.UploadProgressRepository;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    // PROCESSING lease：超时视为死亡，允许 re-claim
    private static final int PROCESSING_LEASE_MINUTES = 10;

    private final UploadProgressRepository uploadProgressRepository;
    private final FileParseService fileParseService;
    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final MetricsService metricsService;
    private final DocumentFileStorage documentFileStorage;
    private final DepartmentAccessService departmentAccessService;

    public DocumentIngestionService(
            UploadProgressRepository uploadProgressRepository,
            FileParseService fileParseService,
            DocumentService documentService,
            DocumentChunkService documentChunkService,
            MetricsService metricsService,
            DocumentFileStorage documentFileStorage,
            DepartmentAccessService departmentAccessService) {
        this.uploadProgressRepository = uploadProgressRepository;
        this.fileParseService = fileParseService;
        this.documentService = documentService;
        this.documentChunkService = documentChunkService;
        this.metricsService = metricsService;
        this.documentFileStorage = documentFileStorage;
        this.departmentAccessService = departmentAccessService;
    }

    /**
     * 幂等入口：Consumer 调用此方法。
     * isLastAttempt=true 时失败才标 FAILED；否则抛异常让 MQ 重试。
     */
    public void process(String uploadId, boolean isLastAttempt) {
        ClaimResult claim = tryClaim(uploadId);
        if (!claim.claimed()) {
            log.info("ingestion skip uploadId={} reason={}", uploadId, claim.skipReason());
            return;
        }

        Timer.Sample timer = metricsService.startDocumentProcessingTimer();
        try {
            doIngest(uploadId, claim.token());
            markCompleted(uploadId, claim.generation(), claim.token());
        } catch (Exception e) {
            log.error("ingestion failed uploadId={} lastAttempt={}", uploadId, isLastAttempt, e);
            if (isLastAttempt) {
                markFailed(uploadId, claim.generation(), claim.token(), safeMessage(e));
            } else {
                resetToRetry(uploadId, claim.generation(), claim.token());
                throw new RuntimeException(e); // 让 RocketMQ RECONSUME_LATER
            }
        } finally {
            metricsService.recordDocumentProcessingTime(timer);
        }
    }

    // -----------------------------------------------------------------------
    // claim
    // -----------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult tryClaim(String uploadId) {
        UploadProgress p = uploadProgressRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalStateException("找不到上传记录: " + uploadId));

        return switch (p.getStatus()) {
            case COMPLETED -> ClaimResult.skip("COMPLETED");
            case FAILED    -> ClaimResult.skip("FAILED_AWAIT_MANUAL_RETRY");
            case PROCESSING -> {
                boolean leaseExpired = p.getUpdatedAt() != null
                        && p.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(PROCESSING_LEASE_MINUTES));
                if (!leaseExpired) {
                    yield ClaimResult.skip("PROCESSING_LEASE_ACTIVE");
                }
                // lease 过期 → 重置为 PENDING 再 claim
                uploadProgressRepository.resetToPending(uploadId, UploadProgress.UploadStatus.PROCESSING);
                yield doAtomicClaim(uploadId);
            }
            case PENDING, UPLOADING, PARSING, CHUNKING, EMBEDDING -> doAtomicClaim(uploadId);
        };
    }

    private ClaimResult doAtomicClaim(String uploadId) {
        String token = UUID.randomUUID().toString();
        int affected = uploadProgressRepository.claimIfPending(uploadId, token);
        if (affected == 0) {
            return ClaimResult.skip("CONCURRENT_CLAIM_LOST");
        }
        UploadProgress p = uploadProgressRepository.findByUploadId(uploadId).orElseThrow();
        return ClaimResult.claimed(p.getGeneration(), token);
    }

    // -----------------------------------------------------------------------
    // core ingestion
    // -----------------------------------------------------------------------

    private void doIngest(String uploadId, String token) throws Exception {
        UploadProgress progress = uploadProgressRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalStateException("找不到上传记录: " + uploadId));

        Path storedFile = documentFileStorage.resolve(uploadId, progress.getFileName());

        updateStatus(uploadId, UploadProgress.UploadStatus.PARSING, 10);
        Map<String, String> parseResult = fileParseService.parseFile(storedFile, progress.getFileName());

        updateStatus(uploadId, UploadProgress.UploadStatus.CHUNKING, 40);
        Document document = new Document();
        document.setTitle(parseResult.get("title"));
        document.setContent(parseResult.get("content"));
        document.setFileType(fileParseService.getFileTypeDescription(progress.getFileName()));
        document.setFilePath(storedFile.toString());

        if (progress.getVisibleDepartmentIds() != null && !progress.getVisibleDepartmentIds().isBlank()) {
            departmentAccessService.applyBackgroundDocumentDepartments(
                    document, parseDepartmentIds(progress.getVisibleDepartmentIds()));
        }

        Document saved = documentService.saveDocument(document, false);

        updateStatus(uploadId, UploadProgress.UploadStatus.EMBEDDING, 80);
        documentChunkService.processDocumentWithProgress(saved.getId(), (cur, total) -> {
            int pct = 80 + (int) ((cur / (double) total) * 19);
            updateStatus(uploadId, UploadProgress.UploadStatus.EMBEDDING, Math.min(pct, 99));
        });
    }

    // -----------------------------------------------------------------------
    // mark terminal states
    // -----------------------------------------------------------------------

    private void markCompleted(String uploadId, int generation, String token) {
        uploadProgressRepository.findByUploadId(uploadId).ifPresent(p -> {
            if (!matchesCurrentAttempt(p, generation, token)) return;
            p.setStatus(UploadProgress.UploadStatus.COMPLETED);
            p.setPercentage(100);
            p.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(p);
        });
    }

    private void markFailed(String uploadId, int generation, String token, String error) {
        uploadProgressRepository.findByUploadId(uploadId).ifPresent(p -> {
            if (!matchesCurrentAttempt(p, generation, token)) return;
            p.setStatus(UploadProgress.UploadStatus.FAILED);
            p.setLastError(error);
            p.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(p);
        });
    }

    private void resetToRetry(String uploadId, int generation, String token) {
        uploadProgressRepository.findByUploadId(uploadId).ifPresent(p -> {
            if (!matchesCurrentAttempt(p, generation, token)) return;
            p.setStatus(UploadProgress.UploadStatus.PENDING);
            p.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(p);
        });
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private boolean matchesCurrentAttempt(UploadProgress p, int generation, String token) {
        return p.getGeneration() == generation && token.equals(p.getAttemptToken());
    }

    private void updateStatus(String uploadId, UploadProgress.UploadStatus status, int percentage) {
        uploadProgressRepository.findByUploadId(uploadId).ifPresent(p -> {
            p.setStatus(status);
            p.setPercentage(percentage);
            p.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(p);
        });
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? "文档处理失败" : m;
    }

    private Set<Long> parseDepartmentIds(String value) {
        return Arrays.stream(value.split(","))
                .filter(s -> !s.isBlank())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    // -----------------------------------------------------------------------
    // inner result type
    // -----------------------------------------------------------------------

    public record ClaimResult(boolean claimed, int generation, String token, String skipReason) {
        static ClaimResult claimed(int generation, String token) {
            return new ClaimResult(true, generation, token, null);
        }
        static ClaimResult skip(String reason) {
            return new ClaimResult(false, 0, null, reason);
        }
    }
}
