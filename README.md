# 微服务基座 microservice-infra

Docker Compose 一键部署的微服务基础设施底座，覆盖数据、消息、网关、监控、链路、日志、认证、调度、通知、存储等全链路。

## 快速启动

```bash
# 1. 复制环境变量并修改
cp .env.example .env

# 2. 启动全部服务
docker compose up -d

# 3. 查看状态
docker compose ps
```

## 服务一览

### 数据层
| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| PostgreSQL 17 | `postgres:17-alpine` | 5432 | 关系数据库（多库初始化） |
| Redis 7 | `redis:7-alpine` | 6379 | 缓存 / 分布式锁 |
| MongoDB 8 | `mongo:8.0` | 27017 | 文档数据库 |
| MySQL 8 | `mysql:8.0` | 3306 | XXL-JOB 调度库 |
| ClickHouse 24 | `clickhouse/clickhouse-server:24.3-alpine` | 8123 | 列式分析引擎 |
| Elasticsearch 8 | `elasticsearch:8.16` | 9200 | 全文搜索（单节点，512m） |

### 消息 & 配置 & 工作流
| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| Kafka 4.3 | `apache/kafka:4.3` | 9092 | 事件总线（基础设施消息中间件） |
| ZooKeeper 3.8 | `zookeeper:3.8` | 2181 | Kafka 协调 |
| Nacos 2.2 | `nacos/nacos-server:v2.2.3` | 8848 | 配置 / 注册中心 |
| Temporal 1.23 | `temporalio/auto-setup:1.23.1` | 7233 | 工作流引擎 |

### 消息适配层（代码库）
| 模块 | 说明 |
|------|------|
| [`infra-adapter/message/api`](infra-adapter/message/api) | 统一消息接口（MessageProducer、EventBus、@MessageListener） |
| [`infra-adapter/message/kafka`](infra-adapter/message/kafka) | Kafka Binder 实现（基于 Kafka Topic 模型） |
| [`infra-adapter/message/rabbit`](infra-adapter/message/rabbit) | RabbitMQ Binder 实现（兼容遗留/外部 RabbitMQ 服务） |

> **设计原则**：业务服务面向 `infra-adapter/message/api` 编程，底层消息中间件可切换。
> Kafka 为基础设施默认事件总线，RabbitMQ binder 用于对接遗留项目或外部系统。
> 详见 [`docs/message-layer.md`](docs/message-layer.md)。

### 适配层目录结构

```
infra-adapter/          ← 基础设施适配层（统一收拢）
├── message/            ← 基础消息（已就绪）
│   ├── api/            ← 接口层
│   ├── kafka/          ← Kafka Binder
│   └── rabbit/         ← RabbitMQ Binder
├── cache/              ← 基础缓存（规划中）
└── storage/            ← 基础存储（规划中）
```

### 网关 & 代理
| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| APISIX 3.7 | `apache/apisix:3.7.0-debian` | 9080/9180/9443 | API 网关 |
| etcd 3.5 | `quay.io/coreos/etcd:v3.5.15` | 2379 | APISIX 配置存储 |
| Nginx | `nginx:alpine` | 8082 | 静态代理 / 反向代理 |

### 认证 & 通知 & 通讯
| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| Keycloak 24 | `quay.io/keycloak/keycloak:24.0.4` | 8080 | IAM / 认证授权 |
| Novu 3.17 | `ghcr.io/novuhq/novu` | 3000/4000/3002 | 多渠道通知中心（API / Dashboard / WS） |
| Open-IM 3.8 | `openim/openim-server:v3.8.3` | 10001/10002 | 即时通讯服务 |
| Open-IM Web | `openim/openim-web-front:release-v3.8.3` | 11001 | 即时通讯 Web 客户端 |

### 可观测性
| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| Prometheus | `prom/prometheus:latest` | 9090 | 指标采集 |
| Grafana 10.4 | `grafana/grafana:10.4.5` | 3001 | 可视化面板（含 Prometheus & Loki 数据源） |
| OTel Collector | `otel/opentelemetry-collector:0.153` | 4317/4318 | 链路数据采集（导出到 Jaeger） |
| Jaeger 1.60 | `jaegertracing/all-in-one:1.60` | 16686 | 链路追踪 UI |
| Loki 3.0 | `grafana/loki:3.0.0` | 3100 | 日志聚合 |

### 存储 & 仓库
| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| MinIO | `minio/minio:latest` | 9005(S3) / 9006(Console) | S3 兼容对象存储 |
| Registry 2 | `registry:2` | 5000 | Docker 私有镜像仓库 |
| LocalStack 0.14 | `localstack/localstack:0.14.5` | 4566 | AWS 本地云模拟 |

### 调度 & 工具
| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| XXL-JOB 2.4 | `xuxueli/xxl-job-admin:2.4.1` | 8081 | 分布式定时调度 |

## 架构图

```mermaid
flowchart TD
    Client["外部客户端（Web / App / API）"]

    Client --> APISIX

    subgraph APISIX["APISIX（API 网关）"]
        GW["路由 · 认证 · 限流 · 熔断 · 日志 · 监控 · 灰度"]
    end

    APISIX --> S1["商城服务"]
    APISIX --> S2["支付服务"]
    APISIX --> S3["交易所"]
    APISIX --> S4["运营后台"]
    APISIX --> S5["会员中心"]
    APISIX --> S6["游戏服务"]
    APISIX --> S7["···"]

    S1 & S2 & S3 & S4 & S5 & S6 --> ADAPTER

    subgraph ADAPTER["消息适配层 infra-message"]
        direction LR
        API["统一 API\nMessageProducer\nEventBus\n@MessageListener"]
        KAFKA_BINDER["Kafka Binder\n✅ 基础设施默认"]
        RABBIT_BINDER["RabbitMQ Binder\n✅ 遗留/外部系统兼容"]
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
            Grafana["Grafana\n可视化面板"]
            OTel["OpenTelemetry\n链路采集"]
            Jaeger["Jaeger\n链路追踪 UI"]
            Loki["Loki\n日志聚合"]
        end

        subgraph DATA["数据 / 存储 / 通知"]
            PG["PostgreSQL\n业务数据"]
            ES["Elasticsearch\n全文搜索"]
            Mongo["MongoDB\n文档数据"]
            MinIO["MinIO\n对象存储"]
            Novu["Novu\n通知中心"]
        end
    end
```

## 端口占用总表

| 端口 | 服务 | 说明 |
|------|------|------|
| 5432 | PostgreSQL | 关系数据库 |
| 6379 | Redis | 缓存 |
| 27017 | MongoDB | 文档库 |
| 3306 | MySQL | XXL-JOB 库 |
| 8123 / 9000 | ClickHouse | 分析引擎 |
| 9200 | Elasticsearch | 搜索索引 |
| 9090 | Prometheus | 指标采集 |
| 3001 | Grafana | 可视化面板 |
| 3100 | Loki | 日志聚合 |
| 16686 | Jaeger | 链路追踪 UI |
| 4317 / 4318 | OTel Collector | OTLP 数据接入 |
| 9080 / 9180 / 9443 | APISIX | API 网关 |
| 2379 | etcd | 网关配置存储 |
| 2181 | ZooKeeper | Kafka 协调 |
| 9092 | Kafka | 事件总线 |
| 8848 | Nacos | 配置 / 注册中心 |
| 7233 | Temporal | 工作流引擎 |
| 8080 | Keycloak | 认证授权 |
| 3000 / 4000 / 3002 | Novu | 通知中心 |
| 8081 | XXL-JOB | 定时调度 |
| 4566 | LocalStack | AWS 模拟 |
| 8082 | Nginx | 反向代理 |
| 5000 | Registry | 私有镜像仓库 |
| 9005 / 9006 | MinIO | 对象存储 |
| 10001 | Open-IM | WebSocket 即时通讯 |
| 10002 | Open-IM | HTTP API |
| 11001 | Open-IM Web | Web 聊天客户端 |

## 设计原则

1. **去 Bitnami 化** —— 所有镜像来自项目官方或官方 registry（Docker Hub / quay.io / ghcr.io / docker.elastic.co），不依赖第三方打包版
2. **可复现** —— 所有镜像固定版本号，避免 latest 漂移导致不兼容
3. **配置分离** —— 敏感配置通过 `.env` 管理，不提交 Git
4. **即插即用** —— 新增服务只需创建子目录 + 在 `compose.yaml` 里加一行 include
