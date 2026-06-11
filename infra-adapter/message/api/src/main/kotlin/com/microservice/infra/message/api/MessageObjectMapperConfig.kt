package com.microservice.infra.message.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 通用 ObjectMapper 配置 —— 对 Envelope 中的 Java 8 时间类型提供支持。
 */
@Configuration
class MessageObjectMapperConfig {

    @Bean
    fun messageObjectMapper(): ObjectMapper {
        return JsonMapper.builder()
            .addModule(kotlinModule())
            .addModule(JavaTimeModule())
            .build()
    }
}
