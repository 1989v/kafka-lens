import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "io.github.kafkalens"
version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT")

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    implementation("org.apache.avro:avro:1.12.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.13")
}

node {
    version.set("20.18.0")
    download.set(true)
    workDir.set(file("${layout.buildDirectory.get().asFile}/nodejs"))
    npmWorkDir.set(file("${layout.buildDirectory.get().asFile}/npm"))
    nodeProjectDir.set(file("web"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmInstall")
    workingDir.set(file("web"))
    args.set(listOf("run", "build"))
    inputs.dir("web/src")
    inputs.file("web/index.html")
    inputs.file("web/package.json")
    inputs.file("web/vite.config.ts")
    outputs.dir("web/dist")
}

tasks.register<Copy>("copyWebDist") {
    dependsOn("npmBuild")
    from("web/dist")
    into("src/main/resources/static")
}

tasks.named("processResources") {
    dependsOn("copyWebDist")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("kafka-lens.jar")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
