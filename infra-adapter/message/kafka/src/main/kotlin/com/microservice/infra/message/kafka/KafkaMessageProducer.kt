package com.microservice.infra.message.kafka

import com.microservice.infra.message.api.Envelope
import com.microservice.infra.message.api.MessageProducer
import org.slf4j.LoggerFactory
import org.springframework.kafka.KafkaException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Kafka 消息生产者实现。
 *
 * 将统一的 [MessageProducer] 接口映射到 Kafka Topic 模型：
 * - topic → Kafka Topic
 * - key → Record Key
 */
@Component
class KafkaMessageProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : MessageProducer {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun <T> send(topic: String, key: String, message: Envelope<T>) {
        try {
            log.debug("Kafka send: topic={}, key={}, type={}, id={}",
                topic, key, message.type, message.id)

            val future = kafkaTemplate.send(topic, key, message as Any)
            future.whenComplete { result: SendResult<String, Any>?, ex: Throwable? ->
                if (ex != null) {
                    log.error("Kafka send failed: topic={}, key={}, type={}, error={}",
                        topic, key, message.type, ex.message)
                } else {
                    log.debug("Kafka send success: topic={}, partition={}, offset={}",
                        result?.recordMetadata?.topic(),
                        result?.recordMetadata?.partition(),
                        result?.recordMetadata?.offset())
                }
            }
        } catch (e: KafkaException) {
            log.error("Kafka send failed: topic={}, key={}, type={}, error={}",
                topic, key, message.type, e.message)
            throw e
        }
    }

    override fun <T> sendDelayed(topic: String, key: String, message: Envelope<T>, delayMs: Long) {
        // Kafka 原生不支持延时消息，通过 header "x-delay" + kafka-streams 处理器模拟
        // 或使用 kafka 的 log compaction + 定时消费策略
        log.warn("Kafka 延时消息通过 header 'x-delay' 模拟，需要 Consumer 端配合处理: topic={}, delayMs={}", topic, delayMs)

        try {
            kafkaTemplate.send(topic, key, message as Any) { headers ->
                headers.add("x-delay", delayMs.toString().toByteArray())
            }
        } catch (e: KafkaException) {
            log.error("Kafka delayed send failed: topic={}, key={}, delay={}ms, error={}",
                topic, key, delayMs, e.message)
            throw e
        }
    }
}
