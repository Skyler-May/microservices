package com.microservice.infra.message.api

/**
 * 消息消费者注解 —— 标注在方法上声明消费某个 topic。
 *
 * ## 使用示例
 *
 * ```kotlin
 * @Service
 * class OrderEventService {
 *
 *     @MessageListener(topic = "order.created", group = "gaming-orders")
 *     fun onOrderCreated(envelope: Envelope<OrderCreatedPayload>) {
 *         val order = envelope.payload
 *         // 处理订单...
 *     }
 * }
 * ```
 *
 * 底层 Binder（Kafka / RabbitMQ）自动扫描此注解并注册对应的 Listener 容器。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MessageListener(
    /** Kafka Topic / RabbitMQ Exchange 名称 */
    val topic: String,

    /** 消费者组（Kafka Consumer Group / RabbitMQ Queue 名称后缀） */
    val group: String = "default",

    /** Queue/RoutingKey，仅 RabbitMQ 有效，为空时从 topic:group 自动生成 */
    val queue: String = "",

    /** 是否自动 ACK */
    val autoAck: Boolean = false
)
