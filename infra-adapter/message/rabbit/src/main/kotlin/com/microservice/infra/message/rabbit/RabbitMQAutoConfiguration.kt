package com.microservice.infra.message.rabbit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.microservice.infra.message.api.Envelope
import com.microservice.infra.message.api.MessageListener
import com.microservice.infra.message.api.MessageConfigProperties
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.MessageListenerContainer
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter
import org.springframework.amqp.support.converter.AbstractMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.support.RetryTemplate
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

/**
 * RabbitMQ 自动配置 —— 负责：
 * 1. 创建 ConnectionFactory / RabbitTemplate / RabbitAdmin
 * 2. 扫描 @MessageListener 注解，自动声明 Exchange/Queue/Binding 并注册 Listener
 */
@Configuration
class RabbitMQAutoConfiguration(
    private val properties: MessageConfigProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun rabbitConnectionFactory(): ConnectionFactory {
        val config = properties.rabbitmq
        val factory = CachingConnectionFactory()
        factory.setAddresses(config.addresses)
        factory.setUsername(config.username)
        factory.setPassword(config.password)
        // 设置 publisher confirm 和 return
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED)
        factory.setPublisherReturns(true)
        return factory
    }

    @Bean
    fun rabbitMessageConverter(objectMapper: ObjectMapper): MessageConverter {
        return EnvelopeMessageConverter(objectMapper)
    }

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        messageConverter: MessageConverter
    ): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = messageConverter
        template.setMandatory(true)

        // 发送确认回调
        template.setConfirmCallback { correlationData, ack, cause ->
            if (!ack) {
                log.error("RabbitMQ 消息发送失败: correlationId={}, cause={}",
                    correlationData?.id, cause)
            }
        }
        // 路由失败回调
        template.setReturnsCallback { returned ->
            log.error("RabbitMQ 消息路由失败: exchange={}, routingKey={}, replyText={}",
                returned.exchange, returned.routingKey, returned.replyText)
        }

        // 配置重试（发送端）
        val retryTemplate = RetryTemplate()
        val backoff = ExponentialBackOffPolicy()
        val retryConfig = properties.rabbitmq.retry
        backoff.initialInterval = retryConfig.backoffMs
        backoff.multiplier = retryConfig.multiplier
        retryTemplate.setBackOffPolicy(backoff)
        template.setRetryTemplate(retryTemplate)

        return template
    }

    @Bean
    fun rabbitAdmin(connectionFactory: ConnectionFactory): RabbitAdmin {
        return RabbitAdmin(connectionFactory)
    }

    /**
     * @MessageListener 注解扫描器 —— 在 Bean 初始化后自动注册 RabbitMQ Listener
     */
    @Bean
    fun rabbitListenerRegistrar(
        connectionFactory: ConnectionFactory,
        messageConverter: MessageConverter
    ): BeanPostProcessor {
        return RabbitListenerBeanPostProcessor(connectionFactory, messageConverter)
    }
}

/**
 * Envelope 序列化/反序列化转换器 —— 确保 Envelope 在 MQ 中正确序列化。
 */
class EnvelopeMessageConverter(
    private val objectMapper: ObjectMapper
) : AbstractMessageConverter() {

    override fun fromMessage(message: Message, targetClass: Class<*>): Any? {
        val json = String(message.body, Charsets.UTF_8)
        return objectMapper.readValue(json, targetClass)
    }

    override fun toMessage(obj: Any, messageProperties: MessageProperties): Message {
        messageProperties.contentType = MessageProperties.CONTENT_TYPE_JSON
        val json = objectMapper.writeValueAsString(obj)
        return Message(json.toByteArray(Charsets.UTF_8), messageProperties)
    }
}

/**
 * 扫描 Bean 中的 @MessageListener 注解方法，自动：
 * 1. 声明 Exchange（Topic 类型）、Queue、Binding
 * 2. 注册 RabbitMQ MessageListener
 */
class RabbitListenerBeanPostProcessor(
    private val connectionFactory: ConnectionFactory,
    private val messageConverter: MessageConverter
) : BeanPostProcessor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val registered = mutableSetOf<String>()

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
        val clazz = bean::class.java
        for (method in clazz.methods) {
            val annotation = method.getAnnotation(MessageListener::class.java) ?: continue
            registerListener(bean, method, annotation)
        }
        return bean
    }

    private fun registerListener(bean: Any, method: Method, annotation: MessageListener) {
        val topic = annotation.topic
        val queueName = annotation.queue.ifEmpty { "${topic}.${annotation.group}" }
        val routingKey = "#" // 绑定所有 routing key

        // 避免重复注册
        val key = "$topic:$queueName"
        if (!registered.add(key)) return

        // 声明 Topic Exchange
        val exchange = TopicExchange(topic, true, false)
        // 声明 Queue（绑定 DLQ）
        val dlqName = "dlq.$queueName"
        val queue = QueueBuilder.durable(queueName)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", dlqName)
            .build()
        val dlq = Queue(dlqName, true)
        val binding = BindingBuilder.bind(queue).to(exchange).with(routingKey)

        // 通过 RabbitAdmin 声明
        val admin = RabbitAdmin(connectionFactory)
        admin.declareExchange(exchange)
        admin.declareQueue(queue)
        admin.declareQueue(dlq)
        admin.declareBinding(binding)

        // 注册 Listener
        val container = SimpleMessageListenerContainer(connectionFactory)
        container.setQueueNames(queueName)
        container.setMessageConverter(messageConverter)

        val adapter = MessageListenerAdapter(bean, method.name)
        adapter.setMessageConverter(messageConverter)
        container.setMessageListener(adapter)

        container.afterPropertiesSet()
        container.start()

        log.info("RabbitMQ listener registered: topic={}, queue={}, bean={}.{}",
            topic, queueName, bean::class.simpleName, method.name)
    }
}
