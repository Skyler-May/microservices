# 架构图

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

    S1 & S2 & S3 & S4 & S5 & S6 --> INFRA

    subgraph INFRA["基础设施层"]
        direction TB

        subgraph MSG["消息 / 配置 / 工作流"]
            Nacos["Nacos\n配置 / 注册中心"]
            Kafka["Kafka\n事件总线"]
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
            IM["IM \n即时通讯"]
        end
    end
```
