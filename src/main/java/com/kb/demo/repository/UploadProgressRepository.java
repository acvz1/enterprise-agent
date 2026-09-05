package com.kb.demo.repository;

import com.kb.demo.entity.UploadProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 上传进度Repository
 */
@Repository
public interface UploadProgressRepository extends JpaRepository<UploadProgress, Long> {

    /**
     * 根据上传ID查找进度
     */
    Optional<UploadProgress> findByUploadId(String uploadId);

    /**
     * 删除已完成的上传记录（定期清理）
     */
    void deleteByStatus(UploadProgress.UploadStatus status);

    /** 原子抢占：PENDING → PROCESSING，generation 递增，写入新 token。返回受影响行数。 */
    @Modifying
    @Transactional
    @Query("""
        UPDATE UploadProgress u
        SET u.status = 'PROCESSING',
            u.generation = u.generation + 1,
            u.attemptToken = :token,
            u.lastError = null,
            u.updatedAt = CURRENT_TIMESTAMP
        WHERE u.uploadId = :uploadId
          AND u.status = 'PENDING'
        """)
    int claimIfPending(@Param("uploadId") String uploadId, @Param("token") String token);

    /** 重置为 PENDING（lease 超时后 re-claim，或人工 retry）。返回受影响行数。 */
    @Modifying
    @Transactional
    @Query("""
        UPDATE UploadProgress u
        SET u.status = 'PENDING',
            u.updatedAt = CURRENT_TIMESTAMP
        WHERE u.uploadId = :uploadId
          AND u.status = :#{#fromStatus.name()}
        """)
    int resetToPending(@Param("uploadId") String uploadId,
                       @Param("fromStatus") UploadProgress.UploadStatus fromStatus);
}
