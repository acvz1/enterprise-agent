package com.kb.demo.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DocumentIngestionProducer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public DocumentIngestionProducer(
            RocketMQTemplate rocketMQTemplate,
            @Value("${app.mq.ingestion-topic}") String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    public void send(String uploadId) {
        rocketMQTemplate.convertAndSend(topic, new DocumentIngestionMessage(uploadId));
        log.info("ingestion message sent uploadId={} topic={}", uploadId, topic);
    }
}
