package com.kb.demo.service;

import com.kb.demo.entity.UploadProgress;
import com.kb.demo.mq.DocumentIngestionProducer;
import com.kb.demo.repository.UploadProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Accepts upload requests and publishes durable ingestion jobs to RocketMQ.
 */
@Service
public class DocumentProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final UploadProgressRepository uploadProgressRepository;
    private final MetricsService metricsService;
    private final DocumentFileStorage documentFileStorage;
    private final DocumentIngestionProducer documentIngestionProducer;

    public DocumentProcessingService(
            UploadProgressRepository uploadProgressRepository,
            MetricsService metricsService,
            DocumentFileStorage documentFileStorage,
            DocumentIngestionProducer documentIngestionProducer) {
        this.uploadProgressRepository = uploadProgressRepository;
        this.metricsService = metricsService;
        this.documentFileStorage = documentFileStorage;
        this.documentIngestionProducer = documentIngestionProducer;
    }

    /**
     * Persists the request-scoped file and creates a queryable ingestion job.
     */
    public String uploadFileAsync(MultipartFile file) {
        return uploadFileAsync(file, Set.of());
    }

    /** Stores the authorized department range with the durable upload job. */
    public String uploadFileAsync(MultipartFile file, Set<Long> visibleDepartmentIds) {
        String uploadId = UUID.randomUUID().toString();
        String originalFilename = normalizedFilename(file.getOriginalFilename());
        Path storedFile = documentFileStorage.store(uploadId, file);
        boolean progressSaved = false;

        try {
            UploadProgress progress = new UploadProgress();
            progress.setUploadId(uploadId);
            progress.setFileName(originalFilename);
            progress.setFileSize(file.getSize());
            progress.setUploadedSize(file.getSize());
            progress.setStatus(UploadProgress.UploadStatus.PENDING);
            progress.setPercentage(0);
            progress.setVisibleDepartmentIds(visibleDepartmentIds == null ? "" : visibleDepartmentIds.stream()
                    .sorted().map(String::valueOf).collect(Collectors.joining(",")));
            uploadProgressRepository.save(progress);
            progressSaved = true;

            metricsService.recordDocumentUpload();
            documentIngestionProducer.send(uploadId);

            logger.info(
                    "文档入库任务已受理: uploadId={}, fileName={}, storedFile={}",
                    uploadId,
                    originalFilename,
                    storedFile);
            return uploadId;
        } catch (RuntimeException e) {
            if (progressSaved) {
                markSubmissionFailed(uploadId, e);
            } else {
                documentFileStorage.delete(uploadId, originalFilename);
            }
            throw e;
        }
    }

    public Map<String, Object> getUploadProgress(String uploadId) {
        UploadProgress progress = uploadProgressRepository.findByUploadId(uploadId)
                .orElse(null);

        Map<String, Object> result = new HashMap<>();
        if (progress != null) {
            result.put("uploadId", progress.getUploadId());
            result.put("fileName", progress.getFileName());
            result.put("fileSize", progress.getFileSize());
            result.put("uploadedSize", progress.getUploadedSize());
            result.put("percentage", progress.getPercentage());
            result.put("status", progress.getStatus().getDescription());
            result.put("statusCode", progress.getStatus().name());
            result.put("errorMessage", progress.getErrorMessage());
            result.put("createdAt", progress.getCreatedAt());
            result.put("updatedAt", progress.getUpdatedAt());
        }
        return result;
    }

    private void markSubmissionFailed(String uploadId, RuntimeException exception) {
        uploadProgressRepository.findByUploadId(uploadId).ifPresent(progress -> {
            progress.setStatus(UploadProgress.UploadStatus.FAILED);
            progress.setErrorMessage("后台任务提交失败");
            progress.setUpdatedAt(LocalDateTime.now());
            uploadProgressRepository.save(progress);
        });
        logger.error("后台任务提交失败: uploadId={}", uploadId, exception);
    }

    private String normalizedFilename(String originalFilename) {
        return originalFilename == null || originalFilename.isBlank()
                ? "unnamed-upload"
                : originalFilename;
    }
}
