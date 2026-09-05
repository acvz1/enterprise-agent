package com.kb.demo.mq;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReconsumeDlqConditionTest {

    static final int MAX_RECONSUME_TIMES = 3;

    // 复制自 DocumentIngestionConsumer，保持同步
    static boolean isLastAttempt(int reconsumeTimes) {
        return reconsumeTimes >= MAX_RECONSUME_TIMES;
    }

    @ParameterizedTest(name = "reconsumeTimes={0} → lastAttempt={1}")
    @CsvSource({
        "0, false",
        "1, false",
        "2, false",
        "3, true",   // >= maxReconsumeTimes：保守标 FAILED，兼容旧路径直接入DLQ
        "4, true"    // 兜底：新路径实际入DLQ点
    })
    void dlqTriggerCondition(int reconsumeTimes, boolean expectedLastAttempt) {
        assertThat(isLastAttempt(reconsumeTimes)).isEqualTo(expectedLastAttempt);
    }
}
