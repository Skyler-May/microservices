package com.microservice.infra.message.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.microservice.infra.message.api.Envelope
import com.microservice.infra.message.api.MessageListener
import com.microservice.infra.message.api.MessageConfigProperties
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.MessageListener as KafkaMessageListener
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.support.RetryTemplate
import java.lang.reflect.Method
import java.util.*

/**
 * Kafka 自动配置 —— 负责：
 * 1. 创建 KafkaTemplate / ConsumerFactory
 * 2. 自动创建 Topics
 * 3. 扫描 @MessageListener 注解，注册 Kafka Consumer
 */
@Configuration
class KafkaAutoConfiguration(
    private val properties: MessageConfigProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun kafkaProducerConfig(): Map<String, Any> {
        val config = properties.kafka
        return mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to config.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to config.retry.maxAttempts,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
    }

    @Bean
    fun kafkaConsumerConfig(): Map<String, Any> {
        val config = properties.kafka
        return mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to config.bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            JsonDeserializer.TRUSTED_PACKAGES to "com.microservice.infra.message.api"
        )
    }

    @Bean
    fun kafkaProducerFactory(): ProducerFactory<String, Any> {
        return DefaultKafkaProducerFactory(kafkaProducerConfig())
    }

    @Bean
    fun kafkaConsumerFactory(): ConsumerFactory<String, Any> {
        val factory = DefaultKafkaConsumerFactory<String, Any>(kafkaConsumerConfig())
        factory.setKeyDeserializer(StringDeserializer())
        factory.setValueDeserializer(JsonDeserializer(Envelope::class.java))
        return factory
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory)
    }

    @Bean
    fun kafkaAdmin(producerFactory: ProducerFactory<String, Any>): KafkaAdmin {
        val admin = KafkaAdmin(kafkaProducerConfig())
        return admin
    }

    /**
     * @MessageListener 注解扫描器 —— 自动注册 Kafka Consumer
     */
    @Bean
    fun kafkaListenerRegistrar(
        consumerFactory: ConsumerFactory<String, Any>,
        kafkaTemplate: KafkaTemplate<String, Any>
    ): BeanPostProcessor {
        return KafkaListenerBeanPostProcessor(consumerFactory, kafkaTemplate)
    }
}

/**
 * 扫描 @MessageListener 注解，自动创建 Topic 并注册 Kafka ConcurrentMessageListenerContainer。
 */
class KafkaListenerBeanPostProcessor(
    private val consumerFactory: ConsumerFactory<String, Any>,
    private val kafkaTemplate: KafkaTemplate<String, Any>
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
        val group = annotation.group
        val key = "$topic:$group"
        if (!registered.add(key)) return

        // 自动创建 Topic（初始分区数 3，副本 1）
        try {
            val admin = AdminClient.create(kafkaTemplate.producerFactory.configurationProperties)
            val topicResult = admin.createTopics(listOf(NewTopic(topic, 3, 1.toShort())))
            topicResult.all().get()
            admin.close()
            log.info("Kafka topic created/verified: topic={}, partitions=3", topic)
        } catch (e: Exception) {
            log.debug("Kafka topic may already exist: topic={}, error={}", topic, e.message)
        }

        // 注册 Consumer Container
        val containerProperties = ContainerProperties(topic)
        containerProperties.groupId = group
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD)

        val container = ConcurrentMessageListenerContainer<String, Any>(
            consumerFactory, containerProperties
        )
        container.concurrency = 3

        container.setupMessageListener(KafkaMessageListener { record ->
            try {
                val envelope = record.value()
                if (envelope is Envelope<*>) {
                    // 反序列化 payload 并调用目标方法
                    val payloadClass = method.parameterTypes.firstOrNull()
                    if (payloadClass != null) {
                        val objectMapper = JsonMapper.builder()
                            .addModule(kotlinModule())
                            .addModule(JavaTimeModule())
                            .build()
                        val payload = objectMapper.convertValue(envelope.payload, payloadClass)
                        method.invoke(bean, payload)
                    } else {
                        method.invoke(bean, envelope)
                    }
                }
            } catch (e: Exception) {
                log.error("Kafka message processing failed: topic={}, group={}, error={}",
                    topic, group, e.message)
                // 不抛出异常，避免 consumer 阻塞
            }
        })

        container.beanName = "kafka-listener-$topic-$group"
        container.afterPropertiesSet()
        container.start()

        log.info("Kafka listener registered: topic={}, group={}, bean={}.{}",
            topic, group, bean::class.simpleName, method.name)
    }
}
