package com.kb.demo.service;

import com.kb.demo.dto.DocumentCategoryDTO;
import com.kb.demo.entity.DocumentCategory;
import com.kb.demo.repository.DocumentCategoryRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档分类服务层
 * @author LiJingLin
 */
@Service
public class DocumentCategoryService {

    @Autowired
    private DocumentCategoryRepository documentCategoryRepository;

    /**
     * 创建或更新分类
     * @param categoryDTO 分类DTO
     * @return 保存后的分类
     */
    @Transactional
    public DocumentCategoryDTO saveCategory(DocumentCategoryDTO categoryDTO) {
        DocumentCategory category;
        if (categoryDTO.getId() != null) {
            // 更新现有分类
            category = documentCategoryRepository.findById(categoryDTO.getId())
                    .orElseThrow(() -> new RuntimeException("分类不存在，ID: " + categoryDTO.getId()));
        } else {
            // 创建新分类
            category = new DocumentCategory();
        }

        // 检查分类名称是否已存在（排除当前分类）
        Optional<DocumentCategory> existingCategory = documentCategoryRepository.findByName(categoryDTO.getName());
        if (existingCategory.isPresent() && !existingCategory.get().getId().equals(categoryDTO.getId())) {
            throw new RuntimeException("分类名称已存在: " + categoryDTO.getName());
        }

        BeanUtils.copyProperties(categoryDTO, category);
        category = documentCategoryRepository.save(category);
        
        return convertToDTO(category);
    }

    /**
     * 获取所有分类
     * @return 分类列表
     */
    public List<DocumentCategoryDTO> getAllCategories() {
        return documentCategoryRepository.findAllByOrderByNameAsc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取分类
     * @param id 分类ID
     * @return 分类DTO
     */
    public DocumentCategoryDTO getCategoryById(Long id) {
        DocumentCategory category = documentCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("分类不存在，ID: " + id));
        return convertToDTO(category);
    }

    /**
     * 删除分类
     * @param id 分类ID
     */
    @Transactional
    public void deleteCategory(Long id) {
        if (!documentCategoryRepository.existsById(id)) {
            throw new RuntimeException("分类不存在，ID: " + id);
        }
        documentCategoryRepository.deleteById(id);
    }

    /**
     * 实体转DTO
     * @param category 分类实体
     * @return 分类DTO
     */
    private DocumentCategoryDTO convertToDTO(DocumentCategory category) {
        DocumentCategoryDTO dto = new DocumentCategoryDTO();
        BeanUtils.copyProperties(category, dto);
        return dto;
    }
}