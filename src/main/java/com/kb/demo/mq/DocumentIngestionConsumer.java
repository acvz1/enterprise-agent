package com.kb.demo.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.demo.service.DocumentIngestionService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "${app.mq.ingestion-topic}",
        consumerGroup = "${app.mq.ingestion-consumer-group}",
        maxReconsumeTimes = 3
)
public class DocumentIngestionConsumer implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionConsumer.class);

    private final DocumentIngestionService documentIngestionService;
    private final ObjectMapper objectMapper;
    private final int maxReconsumeTimes;

    public DocumentIngestionConsumer(
            DocumentIngestionService documentIngestionService,
            ObjectMapper objectMapper,
            @Value("${app.mq.max-reconsume-times:3}") int maxReconsumeTimes) {
        this.documentIngestionService = documentIngestionService;
        this.objectMapper = objectMapper;
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    @Override
    public void onMessage(MessageExt message) {
        String uploadId = null;
        try {
            DocumentIngestionMessage msg = objectMapper.readValue(
                    message.getBody(), DocumentIngestionMessage.class);
            uploadId = msg.getUploadId();

            boolean isLastAttempt = message.getReconsumeTimes() >= maxReconsumeTimes;
            log.info("ingestion received uploadId={} reconsumeTimes={} isLastAttempt={}",
                    uploadId, message.getReconsumeTimes(), isLastAttempt);

            documentIngestionService.process(uploadId, isLastAttempt);

        } catch (Exception e) {
            log.error("ingestion consumer error uploadId={}", uploadId, e);
            throw new RuntimeException(e); // → RECONSUME_LATER
        }
    }
}
