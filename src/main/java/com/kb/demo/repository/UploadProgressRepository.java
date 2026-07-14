package com.kb.demo.repository;

import com.kb.demo.entity.UploadProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
