package com.kb.demo.service;

import com.kb.demo.entity.DocumentIndexSyncTask;
import com.kb.demo.repository.DocumentIndexSyncTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 管理可恢复的索引同步任务，不直接读写 Redis 或 Elasticsearch。 */
@Service
public class DocumentIndexSyncTaskService {

    public record SyncAttempt(Long documentId, DocumentIndexSyncTask.Operation operation,
                              long generation, String token, Integer targetVersion) {
    }

    private final DocumentIndexSyncTaskRepository taskRepository;
    private final long retryBaseSeconds;
    private final long retryMaxSeconds;
    private final long runningTimeoutSeconds;

    public DocumentIndexSyncTaskService(
            DocumentIndexSyncTaskRepository taskRepository,
            @Value("${app.index-sync.retry-base-seconds:5}") long retryBaseSeconds,
            @Value("${app.index-sync.retry-max-seconds:300}") long retryMaxSeconds,
            @Value("${app.index-sync.running-timeout-seconds:300}") long runningTimeoutSeconds) {
        this.taskRepository = taskRepository;
        if (retryBaseSeconds <= 0 || retryMaxSeconds < retryBaseSeconds || runningTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("索引同步重试配置必须为正数，且最大重试间隔不能小于基础间隔");
        }
        this.retryBaseSeconds = retryBaseSeconds;
        this.retryMaxSeconds = retryMaxSeconds;
        this.runningTimeoutSeconds = runningTimeoutSeconds;
    }

    /** 新的写入意图覆盖旧意图；generation 使已经在跑的旧任务无法误报成功。 */
    /**
     * Outbox 写入必须加入调用方的业务事务：Document 成功提交时任务才存在，
     * Document 回滚时任务也随之回滚。
     */
    @Transactional
    public void request(Long documentId, DocumentIndexSyncTask.Operation operation, Integer targetVersion) {
        LocalDateTime now = LocalDateTime.now();
        DocumentIndexSyncTask task = taskRepository.findByDocumentId(documentId)
                .orElseGet(() -> new DocumentIndexSyncTask(documentId, operation));

        if (task.getId() != null) {
            task.setGeneration(task.getGeneration() + 1);
            task.setOperation(operation);
            task.setStatus(DocumentIndexSyncTask.Status.PENDING);
        }
        task.setTargetVersion(targetVersion);
        task.setAttemptCount(0);
        task.setAttemptToken(null);
        task.setLastAttemptAt(null);
        task.setNextRetryAt(now);
        task.setLastError(null);
        task.setUpdatedAt(now);
        taskRepository.save(task);
    }

    /** 领取一个待执行任务；正在执行且未超时的任务不会被重复领取。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SyncAttempt> claim(Long documentId) {
        Optional<DocumentIndexSyncTask> optionalTask = taskRepository.findByDocumentId(documentId);
        if (optionalTask.isEmpty()) {
            return Optional.empty();
        }

        DocumentIndexSyncTask task = optionalTask.get();
        LocalDateTime now = LocalDateTime.now();
        if (task.getStatus() == DocumentIndexSyncTask.Status.SUCCESS) {
            return Optional.empty();
        }
        if (task.getStatus() == DocumentIndexSyncTask.Status.RUNNING
                && task.getLastAttemptAt() != null
                && task.getLastAttemptAt().isAfter(now.minusSeconds(runningTimeoutSeconds))) {
            return Optional.empty();
        }

        String token = UUID.randomUUID().toString();
        task.setStatus(DocumentIndexSyncTask.Status.RUNNING);
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setAttemptToken(token);
        task.setLastAttemptAt(now);
        task.setNextRetryAt(now.plusSeconds(runningTimeoutSeconds));
        task.setUpdatedAt(now);
        taskRepository.save(task);
        return Optional.of(new SyncAttempt(task.getDocumentId(), task.getOperation(), task.getGeneration(), token, task.getTargetVersion()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(SyncAttempt attempt) {
        taskRepository.findByDocumentId(attempt.documentId()).ifPresent(task -> {
            if (!matchesCurrentAttempt(task, attempt)) {
                return;
            }
            task.setStatus(DocumentIndexSyncTask.Status.SUCCESS);
            task.setLastError(null);
            task.setNextRetryAt(null);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /** 调度器读取到旧任务快照时，把错误领取的任务立即还回待处理状态。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(SyncAttempt attempt) {
        taskRepository.findByDocumentId(attempt.documentId()).ifPresent(task -> {
            if (!matchesCurrentAttempt(task, attempt)) {
                return;
            }
            task.setStatus(DocumentIndexSyncTask.Status.PENDING);
            task.setAttemptToken(null);
            task.setNextRetryAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /** 失败永不静默吞掉：保留错误并按指数退避继续重试。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(SyncAttempt attempt, Exception exception) {
        taskRepository.findByDocumentId(attempt.documentId()).ifPresent(task -> {
            if (!matchesCurrentAttempt(task, attempt)) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            task.setStatus(DocumentIndexSyncTask.Status.RETRYING);
            task.setLastError(compactError(exception));
            task.setAttemptToken(null);
            task.setNextRetryAt(now.plusSeconds(retryDelaySeconds(task.getAttemptCount())));
            task.setUpdatedAt(now);
            taskRepository.save(task);
        });
    }

    @Transactional(readOnly = true)
    public List<DocumentIndexSyncTask> findRetryableTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<DocumentIndexSyncTask> result = new ArrayList<>(taskRepository
                .findByStatusInAndNextRetryAtLessThanEqual(
                        EnumSet.of(DocumentIndexSyncTask.Status.PENDING, DocumentIndexSyncTask.Status.RETRYING), now));
        result.addAll(taskRepository.findByStatusAndLastAttemptAtLessThanEqual(
                DocumentIndexSyncTask.Status.RUNNING, now.minusSeconds(runningTimeoutSeconds)));
        return result;
    }

    private boolean matchesCurrentAttempt(DocumentIndexSyncTask task, SyncAttempt attempt) {
        return task.getGeneration() == attempt.generation()
                && task.getOperation() == attempt.operation()
                && attempt.token().equals(task.getAttemptToken());
    }

    private long retryDelaySeconds(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 20);
        long delay;
        try {
            delay = Math.multiplyExact(retryBaseSeconds, 1L << exponent);
        } catch (ArithmeticException exception) {
            delay = retryMaxSeconds;
        }
        return Math.min(delay, retryMaxSeconds);
    }

    private String compactError(Exception exception) {
        String message = exception.getMessage();
        String value = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
