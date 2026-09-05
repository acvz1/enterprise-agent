package com.kb.demo.controller;

import com.kb.demo.entity.UploadProgress;
import com.kb.demo.mq.DocumentIngestionProducer;
import com.kb.demo.repository.UploadProgressRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/ingestion/tasks")
public class DocumentIngestionController {

    private final UploadProgressRepository uploadProgressRepository;
    private final DocumentIngestionProducer documentIngestionProducer;

    public DocumentIngestionController(
            UploadProgressRepository uploadProgressRepository,
            DocumentIngestionProducer documentIngestionProducer) {
        this.uploadProgressRepository = uploadProgressRepository;
        this.documentIngestionProducer = documentIngestionProducer;
    }

    @PostMapping("/{uploadId}/retry")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable String uploadId) {
        UploadProgress p = uploadProgressRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("找不到上传记录: " + uploadId));

        if (p.getStatus() != UploadProgress.UploadStatus.FAILED) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "只有 FAILED 状态才能重试，当前状态: " + p.getStatus()));
        }

        p.setStatus(UploadProgress.UploadStatus.PENDING);
        p.setLastError(null);
        p.setUpdatedAt(LocalDateTime.now());
        uploadProgressRepository.save(p);

        documentIngestionProducer.send(uploadId);

        return ResponseEntity.ok(Map.of("uploadId", uploadId, "status", "PENDING"));
    }
}
