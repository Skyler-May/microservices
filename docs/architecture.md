# 架构图

## 整体架构

```mermaid
flowchart TD
    Client["外部客户端（Web / App / API）"]

    Client --> APISIX

    subgraph APISIX["APISIX（API 网关）"]
        GW["路由 · 认证 · 限流 · 熔断 · 日志 · 监控 · 灰度"]
    end

    APISIX --> S1["商城服务\nSpring"]
    APISIX --> S2["支付服务\nSpring"]
    APISIX --> S3["交易所\nSpring"]
    APISIX --> S4["运营后台\nSpring"]
    APISIX --> S5["会员中心\nSpring"]
    APISIX --> S6["游戏服务\nSpring"]
    APISIX --> S7["···"]

    %% 业务服务通过消息适配层与基础设施通信
    S1 & S2 & S3 & S4 & S5 & S6 --> ADAPTER

    subgraph ADAPTER["消息适配层 infra-message"]
        direction LR
        API["统一 API 接口\nMessageProducer · EventBus\n@MessageListener · Envelope"]
        KAFKA_BINDER["Kafka Binder\n✅ 基础设施默认\n高吞吐 · 事件溯源 · 流处理"]
        RABBIT_BINDER["RabbitMQ Binder\n✅ 遗留系统兼容\n可靠投递 · 延时消息 · 死信队列"]
        API --> KAFKA_BINDER
        API --> RABBIT_BINDER
    end

    KAFKA_BINDER --- KAFKA_BROKER
    RABBIT_BINDER --- RABBIT_BROKER

    ADAPTER --> INFRA

    subgraph INFRA["基础设施层"]
        direction TB

        subgraph MSG["消息 / 配置 / 工作流"]
            Nacos["Nacos\n配置 / 注册中心"]
            KAFKA_BROKER["Kafka\n事件总线"]
            RABBIT_BROKER["RabbitMQ\n（外部/遗留系统）"]
            Temporal["Temporal\n工作流引擎"]
        end

        subgraph CACHE["认证 / 缓存 / 调度"]
            Keycloak["Keycloak\nIAM / 认证"]
            Redis["Redis\n缓存 / 分布式锁"]
            XXLJOB["XXL-JOB\n定时任务"]
        end

        subgraph OBS["可观测性"]
            Prometheus["Prometheus\n指标采集"]
            Grafana["Grafana\n可视化"]
            OTel["OpenTelemetry\n链路追踪"]
        end

        subgraph DATA["数据 / 通知 / 通讯"]
            PG["PostgreSQL\n业务数据"]
            Novu["Novu\n通知中心"]
            IM["IM\n即时通讯"]
        end
    end
```

## 消息流（时序）

```mermaid
sequenceDiagram
    participant SVC as 业务服务
    participant API as infra-message-api
    participant KAFKA as Kafka Binder
    participant RABBIT as RabbitMQ Binder
    participant BROKER as 消息中间件
    participant CONSUMER as 消费服务

    Note over SVC,CONSUMER: 场景一：Kafka 事件总线（默认路径）
    SVC->>API: publish("ORDER_CREATED", orderPayload)
    API->>KAFKA: send("order.events", "order.created", envelope)
    KAFKA->>BROKER: Kafka Topic: order.events
    BROKER->>CONSUMER: Topic 分发
    CONSUMER->>API: @MessageListener(topic="order.events")

    Note over SVC,CONSUMER: 场景二：RabbitMQ 兼容（遗留系统对接）
    SVC->>API: send("order.exchange", "order.buyer", envelope)
    API->>RABBIT: convertAndSend("order.exchange", "order.buyer", envelope)
    RABBIT->>BROKER: RabbitMQ Exchange → Queue
    BROKER->>CONSUMER: Queue 分发
```

## 目录结构

```
microservices/
├── compose.yaml                 ← 基础设施 Docker Compose
├── infra-adapter/               ← ← ← 基础设施适配层（统一收拢）
│   ├── message/                 ← 基础消息
│   │   ├── settings.gradle.kts
│   │   ├── api/                 ← 统一接口层
│   │   │   ├── build.gradle.kts
│   │   │   └── src/main/kotlin/.../
│   │   │       ├── Envelope.kt
│   │   │       ├── MessageProducer.kt
│   │   │       ├── EventBus.kt
│   │   │       ├── MessageListener.kt
│   │   │       └── MessageConfigProperties.kt
│   │   ├── kafka/               ← Kafka Binder
│   │   │   ├── build.gradle.kts
│   │   │   └── src/main/kotlin/.../
│   │   │       ├── KafkaMessageProducer.kt
│   │   │       └── KafkaAutoConfiguration.kt
│   │   └── rabbit/              ← RabbitMQ Binder（兼容层）
│   │       ├── build.gradle.kts
│   │       └── src/main/kotlin/.../
│   │           ├── RabbitMessageProducer.kt
│   │           └── RabbitMQAutoConfiguration.kt
│   ├── cache/                   ← 基础缓存（规划中）
│   └── storage/                 ← 基础存储（规划中）
├── kafka/                       ← Kafka 容器配置
├── docs/
│   ├── architecture.md
│   └── message-layer.md         ← ← ← 适配层详细文档
├── README.md
└── ...
```
