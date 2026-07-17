package com.kb.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Stores uploaded source files outside the HTTP request lifecycle.
 */
@Service
public class DocumentFileStorage {

    private final Path storageRoot;

    public DocumentFileStorage(
            @Value("${app.ingestion.storage-dir:./data/ingestion}") String storageDirectory) {
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    /**
     * Copies an HTTP upload to application-managed storage before background processing starts.
     */
    public Path store(String uploadId, MultipartFile file) {
        Path target = resolve(uploadId, file.getOriginalFilename());
        boolean targetCreated = false;

        try {
            Files.createDirectories(storageRoot);
            try (InputStream input = file.getInputStream()) {
                try (OutputStream output = Files.newOutputStream(
                        target,
                        StandardOpenOption.CREATE_NEW)) {
                    targetCreated = true;
                    input.transferTo(output);
                }
            }
            return target;
        } catch (IOException e) {
            if (targetCreated) {
                deleteIfExists(target);
            }
            throw new IllegalStateException("保存上传文件失败", e);
        }
    }

    /**
     * Resolves the deterministic storage path from job data persisted in UploadProgress.
     */
    public Path resolve(String uploadId, String originalFilename) {
        if (uploadId == null || !uploadId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("非法上传ID");
        }

        String extension = safeExtension(originalFilename);
        Path resolved = storageRoot.resolve(uploadId + extension).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException("非法存储路径");
        }
        return resolved;
    }

    public void delete(String uploadId, String originalFilename) {
        deleteIfExists(resolve(uploadId, originalFilename));
    }

    private String safeExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }

        String extension = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? "." + extension : "";
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup after a failed synchronous handoff.
        }
    }
}
