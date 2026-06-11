package com.microservice.infra.message.api

/**
 * 消息适配器配置属性。
 *
 * 每个业务服务通过 application.yaml 或环境变量配置。
 *
 * ## 配置示例
 *
 * ```yaml
 * infra:
 *   message:
 *     # RabbitMQ
 *     rabbitmq:
 *       enabled: true
 *       addresses: amqp://localhost:5672
 *       username: guest
 *       password: guest
 *       retry:
 *         max-attempts: 3
 *         backoff-ms: 1000
 *
 *     # Kafka
 *     kafka:
 *       enabled: false
 *       bootstrap-servers: localhost:9092
 *       retry:
 *         max-attempts: 3
 *         backoff-ms: 1000
 *
 *     # 全局默认配置
 *     default-dlq-prefix: "dlq."
 *     default-enable-dlq: true
 * ```
 */
data class MessageConfigProperties(
    val rabbitmq: RabbitMQProperties = RabbitMQProperties(),
    val kafka: KafkaProperties = KafkaProperties(),
    val defaultDlqPrefix: String = "dlq.",
    val defaultEnableDlq: Boolean = true
) {
    data class RabbitMQProperties(
        val enabled: Boolean = false,
        val addresses: String = "amqp://localhost:5672",
        val username: String = "guest",
        val password: String = "guest",
        val retry: RetryProperties = RetryProperties()
    )

    data class KafkaProperties(
        val enabled: Boolean = false,
        val bootstrapServers: String = "localhost:9092",
        val retry: RetryProperties = RetryProperties()
    )

    data class RetryProperties(
        val maxAttempts: Int = 3,
        val backoffMs: Long = 1000,
        val multiplier: Double = 2.0
    )
}
