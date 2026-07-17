package com.kb.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFileStorageTest {

    private static final String UPLOAD_ID = "c8a1d4a1-9447-4c39-a48d-c3e84ebea105";

    @TempDir
    Path tempDirectory;

    @Test
    void storeCopiesRequestFileToApplicationManagedDirectory() throws Exception {
        DocumentFileStorage storage = new DocumentFileStorage(tempDirectory.toString());
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "employee-handbook.pdf",
                "application/pdf",
                "durable-content".getBytes());

        Path storedFile = storage.store(UPLOAD_ID, upload);

        assertThat(storedFile).isEqualTo(tempDirectory.resolve(UPLOAD_ID + ".pdf"));
        assertThat(Files.readString(storedFile)).isEqualTo("durable-content");
        assertThat(storage.resolve(UPLOAD_ID, "employee-handbook.pdf")).isEqualTo(storedFile);
    }

    @Test
    void resolveUsesOnlySafeExtensionFromClientFilename() {
        DocumentFileStorage storage = new DocumentFileStorage(tempDirectory.toString());

        Path resolved = storage.resolve(UPLOAD_ID, "../../employee-handbook.pdf");

        assertThat(resolved.getParent()).isEqualTo(tempDirectory);
        assertThat(resolved.getFileName().toString()).isEqualTo(UPLOAD_ID + ".pdf");
    }

    @Test
    void storeDoesNotOverwriteOrDeleteAnExistingSourceFile() throws Exception {
        DocumentFileStorage storage = new DocumentFileStorage(tempDirectory.toString());
        MockMultipartFile original = new MockMultipartFile(
                "file", "handbook.pdf", "application/pdf", "original".getBytes());
        MockMultipartFile duplicate = new MockMultipartFile(
                "file", "handbook.pdf", "application/pdf", "duplicate".getBytes());

        Path storedFile = storage.store(UPLOAD_ID, original);

        assertThatThrownBy(() -> storage.store(UPLOAD_ID, duplicate))
                .isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(storedFile)).isEqualTo("original");
    }
}
