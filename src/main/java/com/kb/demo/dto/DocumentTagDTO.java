package com.kb.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 文档标签数据传输对象
 * @author LiJingLin
 */
public class DocumentTagDTO {

    private Long id;

    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称长度不能超过100个字符")
    private String name;

    @Size(max = 500, message = "标签描述长度不能超过500个字符")
    private String description;

    @Size(max = 7, message = "颜色代码长度不能超过7个字符")
    private String color;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}