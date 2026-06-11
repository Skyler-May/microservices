package com.microservice.infra.message.api

/**
 * 消息生产者 —— 统一的消息发送接口。
 *
 * 不关心底层是 Kafka 还是 RabbitMQ，业务服务面向这个接口编程。
 *
 * ## 概念映射
 *
 * | 本接口 | Kafka 含义 | RabbitMQ 含义 |
 * |--------|-----------|-------------|
 * | topic | Topic | Exchange 名称 |
 * | key | Record Key | Routing Key |
 * | message | Record Value | Message Payload |
 */
interface MessageProducer {

    /**
     * 发送消息到指定 topic。
     *
     * @param topic 主题（Kafka topic / RabbitMQ exchange）
     * @param key 消息 key（Kafka record key / RabbitMQ routing key）
     * @param message 消息信封
     */
    fun <T> send(topic: String, key: String, message: Envelope<T>)

    /**
     * 发送延时消息。
     *
     * @param delayMs 延时毫秒数
     */
    fun <T> sendDelayed(topic: String, key: String, message: Envelope<T>, delayMs: Long)
}
