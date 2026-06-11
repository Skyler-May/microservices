plugins {
    kotlin("jvm") version "2.2.21"
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
    // 纯 API 层，零 MQ 依赖 —— 业务代码只需依赖本模块
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    implementation("org.slf4j:slf4j-api:2.0.17")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "infra-adapter-message-api"
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
