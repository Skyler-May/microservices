package com.microservice.infra.message.api

import java.time.Instant
import java.util.UUID

/**
 * 消息信封 —— 所有 MQ 消息的统一包装格式。
 *
 * 无论底层是 Kafka 还是 RabbitMQ，业务代码接收到的都是这个信封。
 *
 * @param T 业务载荷类型
 * @param id 消息唯一 ID
 * @param type 事件类型，如 "ORDER_CREATED"、"USER_REGISTERED"
 * @param source 来源服务标识，如 "gaming-server"、"unipay-server"
 * @param timestamp 消息产生时间戳（毫秒）
 * @param payload 业务数据
 * @param traceId 链路追踪 ID
 * @param correlationId 关联 ID（用于 request/reply 模式）
 * @param version 消息格式版本
 */
data class Envelope<T>(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val source: String,
    val timestamp: Long = Instant.now().toEpochMilli(),
    val payload: T,
    val traceId: String? = null,
    val correlationId: String? = null,
    val version: Int = 1
) {
    companion object {
        /** 构造事件类型信封的快捷方法 */
        fun <T> event(type: String, source: String, payload: T): Envelope<T> =
            Envelope(type = type, source = source, payload = payload)
    }
}
