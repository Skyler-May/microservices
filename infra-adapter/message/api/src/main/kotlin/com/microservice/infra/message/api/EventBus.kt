package com.microservice.infra.message.api

/**
 * 事件总线 —— 高层抽象，基于事件类型（而非 topic）发送/接收。
 *
 * 适合跨服务的事件驱动通信场景。
 * 底层自动将 event 名称映射到配置的 topic。
 */
interface EventBus : MessageProducer {

    /**
     * 发布事件到默认 topic。
     *
     * @param event 事件类型名称，如 "ORDER_CREATED"
     * @param payload 事件数据
     */
    fun <T> publish(event: String, payload: T)

    /**
     * 发布延时事件。
     */
    fun <T> publishDelayed(event: String, payload: T, delayMs: Long)
}
