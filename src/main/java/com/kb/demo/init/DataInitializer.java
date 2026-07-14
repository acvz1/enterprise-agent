package com.kb.demo.init;

import com.kb.demo.entity.Document;
import com.kb.demo.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private DocumentRepository documentRepository;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已有文档数据
        if (documentRepository.count() == 0) {
            // 创建一些示例文档
            Document doc1 = new Document();
            doc1.setTitle("欢迎使用AI知识库系统");
            doc1.setContent("这是一个AI知识库问答系统，您可以在这里管理和查询文档，同时与AI助手进行对话。\n\n主要功能：\n1. 文档管理：添加、编辑、删除文档\n2. AI问答：向AI助手提问并获取答案\n3. 智能搜索：基于文档内容进行智能搜索");

            Document doc2 = new Document();
            doc2.setTitle("系统使用指南");
            doc2.setContent("使用本系统非常简单：\n\n1. 文档管理\n   - 点击\"添加文档\"按钮创建新文档\n   - 点击文档卡片的\"编辑\"按钮修改文档\n   - 点击文档卡片的\"删除\"按钮删除文档\n\n2. AI问答\n   - 在AI助手标签页中输入问题\n   - AI会基于文档内容回答您的问题\n   - 支持多轮对话");

            Document doc3 = new Document();
            doc3.setTitle("技术架构说明");
            doc3.setContent("本系统采用前后端分离架构：\n\n前端技术栈：\n- Vue 3\n- TypeScript\n- Naive UI\n- Tailwind CSS\n- Axios\n\n后端技术栈：\n- Spring Boot\n- Spring Data JPA\n- Spring Web\n- MySQL\n- Redis\n- Elasticsearch\n\nAI集成：\n- LangChain4J\n- 阿里云DashScope API");

            documentRepository.save(doc1);
            documentRepository.save(doc2);
            documentRepository.save(doc3);

            System.out.println("示例文档数据初始化完成");
        }
    }
}