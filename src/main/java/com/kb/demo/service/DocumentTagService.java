package com.kb.demo.service;

import com.kb.demo.dto.DocumentTagDTO;
import com.kb.demo.entity.DocumentTag;
import com.kb.demo.repository.DocumentTagRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档标签服务层
 * @author LiJingLin
 */
@Service
public class DocumentTagService {

    @Autowired
    private DocumentTagRepository documentTagRepository;

    /**
     * 创建或更新标签
     * @param tagDTO 标签DTO
     * @return 保存后的标签
     */
    @Transactional
    public DocumentTagDTO saveTag(DocumentTagDTO tagDTO) {
        DocumentTag tag;
        if (tagDTO.getId() != null) {
            // 更新现有标签
            tag = documentTagRepository.findById(tagDTO.getId())
                    .orElseThrow(() -> new RuntimeException("标签不存在，ID: " + tagDTO.getId()));
        } else {
            // 创建新标签
            tag = new DocumentTag();
        }

        // 检查标签名称是否已存在（排除当前标签）
        Optional<DocumentTag> existingTag = documentTagRepository.findByName(tagDTO.getName());
        if (existingTag.isPresent() && !existingTag.get().getId().equals(tagDTO.getId())) {
            throw new RuntimeException("标签名称已存在: " + tagDTO.getName());
        }

        BeanUtils.copyProperties(tagDTO, tag);
        tag = documentTagRepository.save(tag);
        
        return convertToDTO(tag);
    }

    /**
     * 获取所有标签
     * @return 标签列表
     */
    public List<DocumentTagDTO> getAllTags() {
        return documentTagRepository.findAllByOrderByNameAsc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取标签
     * @param id 标签ID
     * @return 标签DTO
     */
    public DocumentTagDTO getTagById(Long id) {
        DocumentTag tag = documentTagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("标签不存在，ID: " + id));
        return convertToDTO(tag);
    }

    /**
     * 删除标签
     * @param id 标签ID
     */
    @Transactional
    public void deleteTag(Long id) {
        if (!documentTagRepository.existsById(id)) {
            throw new RuntimeException("标签不存在，ID: " + id);
        }
        documentTagRepository.deleteById(id);
    }

    /**
     * 实体转DTO
     * @param tag 标签实体
     * @return 标签DTO
     */
    private DocumentTagDTO convertToDTO(DocumentTag tag) {
        DocumentTagDTO dto = new DocumentTagDTO();
        BeanUtils.copyProperties(tag, dto);
        return dto;
    }
}