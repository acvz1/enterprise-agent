package com.kb.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * MySQL 中持久化的索引同步待办。
 *
 * Document / DocumentChunk 是权威数据；Redis 与 Elasticsearch 失败后，
 * 依靠这条记录继续删旧建新，直到索引重新与 MySQL 对齐。
 */
@Entity
@Table(name = "document_index_sync_tasks", uniqueConstraints =
        @UniqueConstraint(name = "uk_index_sync_document", columnNames = "document_id"))
public class DocumentIndexSyncTask {

    public enum Operation {
        REBUILD,
        DELETE
    }

    public enum Status {
        PENDING,
        RUNNING,
        RETRYING,
        SUCCESS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    /** 文档再次变化时递增，旧执行者不能覆盖新任务状态。 */
    @Column(nullable = false)
    private long generation;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** 当前执行租约；超时的 RUNNING 任务可被调度器重新领取。 */
    @Column(name = "attempt_token", length = 64)
    private String attemptToken;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DocumentIndexSyncTask() {
    }

    public DocumentIndexSyncTask(Long documentId, Operation operation) {
        this.documentId = documentId;
        this.operation = operation;
        this.status = Status.PENDING;
        this.generation = 1;
        this.nextRetryAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getGeneration() { return generation; }
    public void setGeneration(long generation) { this.generation = generation; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getAttemptToken() { return attemptToken; }
    public void setAttemptToken(String attemptToken) { this.attemptToken = attemptToken; }
    public LocalDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(LocalDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
