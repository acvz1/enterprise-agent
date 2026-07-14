package com.kb.demo.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件解析服务类
 * 提供文件内容解析、类型检查等功能
 * @author LiJingLin
 */
@Service
public class FileParseService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileParseService.class);
    
    // Tika 字符限制：默认100k，改为 2MB（2000000 字符）
    private static final int TIKA_MAX_CHARS = 2000000;
    
    // 初始化 Tika 时设置字符限制
    private final Tika tika = new Tika();
    {
        tika.setMaxStringLength(TIKA_MAX_CHARS);
    }
    
    /**
     * 解析上传的文件内容
     * @param file 上传的文件
     * @return 包含文件内容和元数据的Map
     */
    public Map<String, String> parseFile(MultipartFile file) {
        Map<String, String> result = new HashMap<>();
        
        try {
            // 获取文件名和内容类型
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            
            logger.info("开始解析文件: {}, 类型: {}", originalFilename, contentType);
            
            // 判断文件类型，特殊处理 Markdown
            String content;
            if (originalFilename != null && originalFilename.toLowerCase().endsWith(".md")) {
                // 直接读取 Markdown 文件内容（UTF-8）
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
                logger.info("Markdown 文件直接读取，内容长度: {}", content.length());
            } else {
                // 使用 Tika 解析其他文件格式
                content = tika.parseToString(file.getInputStream());
            }
            
            // 获取文件元数据
            Metadata metadata = new Metadata();
            try (InputStream stream = file.getInputStream()) {
                Parser parser = new AutoDetectParser();
                parser.parse(stream, new BodyContentHandler(), metadata, new ParseContext());
            } catch (Exception e) {
                // 元数据解析失败不影响内容解析
                logger.debug("元数据解析失败: {}", e.getMessage());
            }
            
            // 构建结果
            result.put("title", originalFilename != null ? originalFilename.replaceFirst("[.][^.]+$", "") : "未命名文档");
            result.put("content", content);
            result.put("contentType", contentType);
            result.put("author", metadata.get("Author"));
            result.put("createdDate", metadata.get("Creation-Date"));
            result.put("modifiedDate", metadata.get("Last-Modified"));
            result.put("pageCount", metadata.get("xmpTPg:NPages")); // PDF页数
            
            logger.info("文件解析完成，内容长度: {}", content.length());
            
        } catch (IOException e) {
            logger.error("文件解析失败", e);
            throw new RuntimeException("文件解析失败: " + e.getMessage());
        } catch (TikaException e) {
            logger.error("文件解析失败", e);
            throw new RuntimeException("文件解析失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 检查文件类型是否支持
     * @param file 上传的文件
     * @return 是否支持
     */
    public boolean isFileTypeSupported(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }
        
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        
        // 支持的文件类型：添加 md（Markdown）
        return switch (extension) {
            case "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "html", "htm", "csv", "md" -> true;
            default -> false;
        };
    }
    
    /**
     * 获取文件类型描述
     * @param file 上传的文件
     * @return 文件类型描述
     */
    public String getFileTypeDescription(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "未知类型";
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return "未知类型";
        }
        
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        
        return switch (extension) {
            case "pdf" -> "PDF文档";
            case "doc", "docx" -> "Word文档";
            case "xls", "xlsx" -> "Excel表格";
            case "ppt", "pptx" -> "PowerPoint演示文稿";
            case "txt" -> "文本文件";
            case "rtf" -> "富文本格式";
            case "html", "htm" -> "HTML网页";
            case "csv" -> "CSV数据文件";
            case "md" -> "Markdown文档";
            default -> "未知类型";
        };
    }
}