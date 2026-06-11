# 消息适配层 infra-adapter/message

统一消息中间件适配层，屏蔽底层 MQ 实现差异，让业务服务面向接口编程。

## 设计目标

1. **基础设施抽象** —— 业务服务不直接依赖 RabbitMQ 或 Kafka，依赖统一 API
2. **可切换** —— 通过切换依赖改变底层消息中间件，无需修改业务代码
3. **Kafka 优先** —— 基础设施默认使用 Kafka 作为事件总线
4. **RabbitMQ 兼容** —— 支持对接已有/遗留 RabbitMQ 系统
5. **可扩展** —— 新增 MQ 类型只需实现新 Binder 模块

## 架构

```
┌─────────────────────────────────────────────┐
│              业务服务 (gaming/unipay)        │
│       面向 API 编程，不感知底层 MQ           │
└──────────────────┬──────────────────────────┘
                   │ 依赖
                   ▼
┌─────────────────────────────────────────────┐
│          infra-adapter-message-api (统一接口)        │
│  MessageProducer  EventBus  @MessageListener │
│  Envelope<T>      MessageConfigProperties    │
└──────────────────┬──────────────────────────┘
                   │ SPI 绑定
         ┌─────────┴──────────┐
         ▼                    ▼
┌─────────────────┐  ┌───────────────────┐
│  infra-message-  │  │  infra-message-    │
│  kafka           │  │  rabbit            │
│  (Kafka Binder)  │  │  (RabbitMQ Binder) │
│  ✅ 基础设施默认  │  │  ✅ 遗留系统兼容    │
└────────┬────────┘  └────────┬──────────┘
         │                    │
         ▼                    ▼
   ┌──────────┐        ┌───────────┐
   │  Kafka   │        │ RabbitMQ  │
   │  Broker  │        │  Broker   │
   └──────────┘        └───────────┘
```

## 使用方式

### 1. 添加依赖

在业务服务的 `build.gradle.kts` 中添加：

```kotlin
// 基础设施默认：使用 Kafka
implementation("com.microservice.infra:infra-adapter-message-api:1.0.0")
implementation("com.microservice.infra:infra-adapter-message-kafka:1.0.0")

// 如需对接遗留 RabbitMQ 系统，替换为：
// implementation("com.microservice.infra:infra-adapter-message-rabbit:1.0.0")
```

本地开发时通过 Gradle Composite Build 引用：

```kotlin
// settings.gradle.kts
includeBuild("../microservices/infra-adapter/message")
```

```kotlin
// build.gradle.kts
implementation(project(":api"))
implementation(project(":kafka"))
```

### 2. 配置

```yaml
# application.yaml

# ========== Kafka 配置（默认启用） ==========
infra:
  message:
    kafka:
      enabled: true
      bootstrap-servers: localhost:9092
      retry:
        max-attempts: 3
        backoff-ms: 1000

# ========== RabbitMQ 配置（二选一） ==========
# infra:
#   message:
#     rabbitmq:
#       enabled: true
#       addresses: amqp://localhost:5672
#       username: guest
#       password: guest
#       retry:
#         max-attempts: 3
#         backoff-ms: 1000
```

### 3. 发送消息

```kotlin
@Service
class OrderService(
    private val producer: MessageProducer
) {
    fun createOrder(order: Order) {
        // 业务逻辑...

        // 通过统一接口发送消息
        producer.send(
            topic = "order.events",      // Kafka Topic / RabbitMQ Exchange
            key = "order.created",        // Kafka Key / RabbitMQ RoutingKey
            message = Envelope.event(
                type = "ORDER_CREATED",
                source = "gaming-server",
                payload = OrderEvent(order.id, order.status)
            )
        )
    }
}
```

或者使用高层 `EventBus` 接口：

```kotlin
@Service
class OrderService(
    private val eventBus: EventBus
) {
    fun createOrder(order: Order) {
        // 自动根据 event 名称映射 topic
        eventBus.publish("ORDER_CREATED", OrderEvent(order.id, order.status))
    }
}
```

### 4. 消费消息

```kotlin
@Service
class OrderEventConsumer {

    @MessageListener(topic = "order.events", group = "gaming-orders")
    fun onOrderCreated(payload: OrderEvent) {
        log.info("收到订单事件: orderId={}, status={}", payload.orderId, payload.status)
        // 处理业务...
    }
}
```

> `@MessageListener` 注解自动扫描并注册到底层 MQ（Kafka Consumer / RabbitMQ Listener）。

### 5. 发送延时消息

```kotlin
// Kafka：通过 header "x-delay" 传送，需要消费端配合
producer.sendDelayed("order.events", "order.timeout", envelope, 30_000)

// RabbitMQ：需安装 rabbitmq_delayed_message_exchange 插件
producer.sendDelayed("order.exchange", "order.timeout", envelope, 30_000)
```

## 概念映射

| 本接口 (infra-message) | Kafka 含义 | RabbitMQ 含义 |
|----------------------|-----------|-------------|
| `topic` | Topic | Exchange 名称 |
| `key` | Record Key | Routing Key |
| `Envelope<T>` | Record Value (JSON) | Message Payload (JSON) |
| `group` | Consumer Group | Queue 名称后缀 |
| `@MessageListener` | `@KafkaListener` | `@RabbitListener` |
| 延时消息 | Header `x-delay` | `x-delay` 插件 |
| 死信队列 | `-dlq` 后缀 Topic | DLX + DLQ |

## 各 Binder 能力对比

| 功能 | Kafka Binder | RabbitMQ Binder |
|------|:-----------:|:--------------:|
| 高吞吐量事件流 | ✅ 原生 | ⚠️ 有限 |
| 消息持久化 | ✅ 日志结构 | ✅ 队列持久化 |
| 消息重试 | ⚠️ 需配合 Streams | ✅ 原生 (TTL+DLX) |
| 死信队列 | ✅ 自定义 Topic | ✅ 原生 DLX |
| 延时消息 | ⚠️ Header 模拟 | ✅ 插件支持 |
| 发布确认 | ✅ Idempotent Producer | ✅ ConfirmCallback |
| 路由失败回查 | ❌ N/A | ✅ ReturnsCallback |
| 消息回溯 | ✅ 按时间/偏移量 | ❌ ACK 后消失 |
| 消费者组 | ✅ 原生 | ✅ Queue 绑定 |
| 有序消费 | ✅ 按分区 | ⚠️ 单 Queue |

## 如何扩展新的 Binder

1. 在 `infra-adapter/message/` 下新建模块，如 `pulsar/`
2. 实现 `MessageProducer` 接口
3. 提供 Spring 自动配置类（连接工厂、模板、注解扫描）
4. 注册 `META-INF/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
5. 修改 `settings.gradle.kts` 加入新模块

```kotlin
// infra-adapter/message/pulsar/src/main/kotlin/.../PulsarMessageProducer.kt
class PulsarMessageProducer : MessageProducer {
    override fun <T> send(topic: String, key: String, message: Envelope<T>) {
        // Pulsar 发送实现
    }
    override fun <T> sendDelayed(topic: String, key: String, message: Envelope<T>, delayMs: Long) {
        // Pulsar 延时消息（使用 delayed message delivery）
    }
}
```

## 发布到仓库

```bash
# 发布到本地 Maven
cd infra-message
./gradlew publishToMavenLocal

# 发布到远程仓库（Nexus / GitHub Packages）
./gradlew publish
```

## 与 Docker Compose 基础设施的关系

```
┌──────────────────────────────────────────────────┐
│               Docker Compose 环境                 │
│  (microservice-infra)                             │
│                                                   │
│  Kafka :9092  ←←← 消息中间件（基础设施默认）      │
│                                                   │
│  RabbitMQ （不在本项目中，由外部/遗留系统提供）    │
└──────────────────────────────────────────────────┘
         ↑ 通过网络连接
┌──────────────────────────────────────────────────┐
│               业务服务代码                         │
│  gaming-server / unipay-server                    │
│  → infra-message 代码库（作为 Gradle 依赖）       │
└──────────────────────────────────────────────────┘
```

**关键点：**
- infra-message 是 **代码库**（Library），不是 Docker 容器
- infra-message 的 binder 通过配置连接 Kafka/RabbitMQ 地址
- 在本地开发时连接 Kafka 容器，生产环境连接托管 Kafka
- 业务服务部署到哪里，infra-message 就跟到哪里
