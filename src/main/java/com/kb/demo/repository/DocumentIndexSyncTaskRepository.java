package com.kb.demo.repository;

import com.kb.demo.entity.DocumentIndexSyncTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentIndexSyncTaskRepository extends JpaRepository<DocumentIndexSyncTask, Long> {

    Optional<DocumentIndexSyncTask> findByDocumentId(Long documentId);

    List<DocumentIndexSyncTask> findByStatusInAndNextRetryAtLessThanEqual(
            Collection<DocumentIndexSyncTask.Status> statuses, LocalDateTime deadline);

    List<DocumentIndexSyncTask> findByStatusAndLastAttemptAtLessThanEqual(
            DocumentIndexSyncTask.Status status, LocalDateTime deadline);
}
