package com.kb.demo.service;

import com.kb.demo.entity.UploadProgress;
import com.kb.demo.repository.UploadProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DocumentProcessingService 单元测试
 * 测试异步文件上传和进度追踪功能
 */
@ExtendWith(MockitoExtension.class)
@Disabled("等待阶段 1 重构：当前 @Async 自调用并跨线程持有请求期 MultipartFile")
class DocumentProcessingServiceTest {

    @Mock
    private FileParseService fileParseService;

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentChunkService documentChunkService;

    @Mock
    private UploadProgressRepository uploadProgressRepository;

    @InjectMocks
    private DocumentProcessingService documentProcessingService;

    private MockMultipartFile mockFile;

    @BeforeEach
    void setUp() {
        // 准备测试文件
        mockFile = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "测试文件内容".getBytes()
        );
    }

    @Test
    void testUploadFileAsync_ReturnsUploadId() {
        // Given: 模拟保存上传进度
        UploadProgress savedProgress = new UploadProgress();
        savedProgress.setUploadId("test-upload-id");
        savedProgress.setFileName("test-document.pdf");
        savedProgress.setStatus(UploadProgress.UploadStatus.UPLOADING);
        
        when(uploadProgressRepository.save(any(UploadProgress.class)))
                .thenReturn(savedProgress);

        // When: 调用异步上传
        String uploadId = documentProcessingService.uploadFileAsync(mockFile);

        // Then: 验证返回上传ID
        assertThat(uploadId).isNotNull();
        assertThat(uploadId).isNotEmpty();

        // 验证保存了初始进度记录
        verify(uploadProgressRepository, times(1)).save(any(UploadProgress.class));
    }

    @Test
    void testUploadFileAsync_InitializesProgressCorrectly() {
        // Given: 捕获保存的进度对象
        UploadProgress capturedProgress = new UploadProgress();
        when(uploadProgressRepository.save(any(UploadProgress.class)))
                .thenAnswer(invocation -> {
                    UploadProgress progress = invocation.getArgument(0);
                    capturedProgress.setUploadId(progress.getUploadId());
                    capturedProgress.setFileName(progress.getFileName());
                    capturedProgress.setFileSize(progress.getFileSize());
                    capturedProgress.setStatus(progress.getStatus());
                    capturedProgress.setPercentage(progress.getPercentage());
                    return capturedProgress;
                });

        // When: 调用异步上传
        documentProcessingService.uploadFileAsync(mockFile);

        // Then: 验证初始化的进度信息
        verify(uploadProgressRepository, times(1)).save(argThat(progress ->
                progress.getFileName().equals("test-document.pdf") &&
                progress.getFileSize() > 0 &&
                progress.getStatus() == UploadProgress.UploadStatus.UPLOADING &&
                progress.getPercentage() == 0
        ));
    }

    @Test
    void testUploadFileAsync_HandlesLargeFile() {
        // Given: 准备大文件
        byte[] largeContent = new byte[1024 * 1024]; // 1MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large-document.pdf",
                "application/pdf",
                largeContent
        );

        when(uploadProgressRepository.save(any(UploadProgress.class)))
                .thenReturn(new UploadProgress());

        // When: 上传大文件
        String uploadId = documentProcessingService.uploadFileAsync(largeFile);

        // Then: 验证可以处理大文件
        assertThat(uploadId).isNotNull();
        verify(uploadProgressRepository, times(1)).save(any(UploadProgress.class));
    }

    @Test
    void testUploadFileAsync_HandlesDifferentFileTypes() {
        // Given: 准备不同类型的文件
        MockMultipartFile wordFile = new MockMultipartFile(
                "file",
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "Word内容".getBytes()
        );

        when(uploadProgressRepository.save(any(UploadProgress.class)))
                .thenReturn(new UploadProgress());

        // When: 上传 Word 文件
        String uploadId = documentProcessingService.uploadFileAsync(wordFile);

        // Then: 验证可以处理不同文件类型
        assertThat(uploadId).isNotNull();
        verify(uploadProgressRepository, times(1)).save(argThat(progress ->
                progress.getFileName().equals("test.docx")
        ));
    }

    @Test
    void testUploadFileAsync_GeneratesUniqueUploadIds() {
        // Given: 模拟保存操作
        when(uploadProgressRepository.save(any(UploadProgress.class)))
                .thenReturn(new UploadProgress());

        // When: 多次上传相同文件
        String uploadId1 = documentProcessingService.uploadFileAsync(mockFile);
        String uploadId2 = documentProcessingService.uploadFileAsync(mockFile);

        // Then: 验证生成不同的上传ID
        assertThat(uploadId1).isNotEqualTo(uploadId2);
        verify(uploadProgressRepository, times(2)).save(any(UploadProgress.class));
    }
}
