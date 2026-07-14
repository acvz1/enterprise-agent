package com.kb.demo.controller;

import com.kb.demo.dto.DocumentTagDTO;
import com.kb.demo.service.DocumentTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档标签控制器
 * @author LiJingLin
 */
@RestController
@RequestMapping("/api/tags")
public class DocumentTagController {

    @Autowired
    private DocumentTagService documentTagService;

    /**
     * 获取所有标签
     * @return 标签列表
     */
    @GetMapping
    public ResponseEntity<List<DocumentTagDTO>> getAllTags() {
        List<DocumentTagDTO> tags = documentTagService.getAllTags();
        return ResponseEntity.ok(tags);
    }

    /**
     * 根据ID获取标签
     * @param id 标签ID
     * @return 标签信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentTagDTO> getTagById(@PathVariable Long id) {
        DocumentTagDTO tag = documentTagService.getTagById(id);
        return ResponseEntity.ok(tag);
    }

    /**
     * 创建标签
     * @param tagDTO 标签DTO
     * @return 创建后的标签
     */
    @PostMapping
    public ResponseEntity<DocumentTagDTO> createTag(@RequestBody DocumentTagDTO tagDTO) {
        DocumentTagDTO createdTag = documentTagService.saveTag(tagDTO);
        return ResponseEntity.ok(createdTag);
    }

    /**
     * 更新标签
     * @param id 标签ID
     * @param tagDTO 标签DTO
     * @return 更新后的标签
     */
    @PutMapping("/{id}")
    public ResponseEntity<DocumentTagDTO> updateTag(@PathVariable Long id, @RequestBody DocumentTagDTO tagDTO) {
        tagDTO.setId(id);
        DocumentTagDTO updatedTag = documentTagService.saveTag(tagDTO);
        return ResponseEntity.ok(updatedTag);
    }

    /**
     * 删除标签
     * @param id 标签ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        documentTagService.deleteTag(id);
        return ResponseEntity.ok().build();
    }
}