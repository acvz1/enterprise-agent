package com.kb.demo.controller;

import com.kb.demo.dto.DocumentCategoryDTO;
import com.kb.demo.service.DocumentCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档分类控制器
 * @author LiJingLin
 */
@RestController
@RequestMapping("/api/categories")
public class DocumentCategoryController {

    @Autowired
    private DocumentCategoryService documentCategoryService;

    /**
     * 获取所有分类
     * @return 分类列表
     */
    @GetMapping
    public ResponseEntity<List<DocumentCategoryDTO>> getAllCategories() {
        List<DocumentCategoryDTO> categories = documentCategoryService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * 根据ID获取分类
     * @param id 分类ID
     * @return 分类信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentCategoryDTO> getCategoryById(@PathVariable Long id) {
        DocumentCategoryDTO category = documentCategoryService.getCategoryById(id);
        return ResponseEntity.ok(category);
    }

    /**
     * 创建分类
     * @param categoryDTO 分类DTO
     * @return 创建后的分类
     */
    @PostMapping
    public ResponseEntity<DocumentCategoryDTO> createCategory(@RequestBody DocumentCategoryDTO categoryDTO) {
        DocumentCategoryDTO createdCategory = documentCategoryService.saveCategory(categoryDTO);
        return ResponseEntity.ok(createdCategory);
    }

    /**
     * 更新分类
     * @param id 分类ID
     * @param categoryDTO 分类DTO
     * @return 更新后的分类
     */
    @PutMapping("/{id}")
    public ResponseEntity<DocumentCategoryDTO> updateCategory(@PathVariable Long id, @RequestBody DocumentCategoryDTO categoryDTO) {
        categoryDTO.setId(id);
        DocumentCategoryDTO updatedCategory = documentCategoryService.saveCategory(categoryDTO);
        return ResponseEntity.ok(updatedCategory);
    }

    /**
     * 删除分类
     * @param id 分类ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        documentCategoryService.deleteCategory(id);
        return ResponseEntity.ok().build();
    }
}