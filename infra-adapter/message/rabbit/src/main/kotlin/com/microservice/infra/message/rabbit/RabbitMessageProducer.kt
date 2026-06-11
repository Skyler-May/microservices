package com.microservice.infra.message.rabbit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.microservice.infra.message.api.Envelope
import com.microservice.infra.message.api.MessageProducer
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.AbstractMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * RabbitMQ 消息生产者实现。
 *
 * 将统一的 [MessageProducer] 接口映射到 RabbitMQ 的 Exchange/RoutingKey 模型：
 * - topic → Exchange 名称
 * - key → Routing Key
 */
@Component
class RabbitMessageProducer(
    private val rabbitTemplate: RabbitTemplate
) : MessageProducer {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun <T> send(topic: String, key: String, message: Envelope<T>) {
        try {
            log.debug("RabbitMQ send: exchange={}, routingKey={}, type={}", topic, key, message.type)
            rabbitTemplate.convertAndSend(topic, key, message)
        } catch (e: AmqpException) {
            log.error("RabbitMQ send failed: exchange={}, routingKey={}, type={}, error={}",
                topic, key, message.type, e.message)
            throw e
        }
    }

    override fun <T> sendDelayed(topic: String, key: String, message: Envelope<T>, delayMs: Long) {
        try {
            log.debug("RabbitMQ delayed send: exchange={}, routingKey={}, delayMs={}, type={}",
                topic, key, delayMs, message.type)
            rabbitTemplate.convertAndSend(topic, key, message) { msg ->
                msg.messageProperties.apply {
                    // 通过 x-delay header 实现延时（需安装 rabbitmq_delayed_message_exchange 插件）
                    setHeader("x-delay", delayMs.toInt())
                }
                msg
            }
        } catch (e: AmqpException) {
            log.error("RabbitMQ delayed send failed: exchange={}, routingKey={}, delay={}ms, error={}",
                topic, key, delayMs, e.message)
            throw e
        }
    }
}
