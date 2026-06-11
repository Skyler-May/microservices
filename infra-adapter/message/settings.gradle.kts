rootProject.name = "infra-adapter-message"

// infra-adapter/message/ —— 基础消息适配层
// 父项目 infra-adapter 会通过 includeBuild 引用此 settings

include(
    "api",
    "kafka",
    "rabbit"
)

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
