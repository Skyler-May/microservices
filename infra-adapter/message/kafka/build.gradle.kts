plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    `java-library`
    `maven-publish`
}

group = "com.microservice.infra"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    // API 模块
    api(project(":api"))

    // Spring Kafka
    implementation("org.springframework.kafka:spring-kafka:3.4.3")
    implementation("org.springframework:spring-context:6.3.0")
    implementation("org.springframework:spring-tx:6.3.0")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")

    // 测试
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "infra-adapter-message-kafka"
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
