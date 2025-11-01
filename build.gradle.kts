plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.ktlint)
}

group = "es.unizar.webeng"
version = "2025-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.kotlin.logging)

    // SockJS Client para clientes Kotlin
    implementation("org.springframework:spring-websocket")
    implementation("org.springframework:spring-messaging")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.ninjasquad.springmocck)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Configuración para Spring Boot. Clase principal.
springBoot {
    mainClass.set("websockets.ElizaServerKt")
}

// Tarea para ejecutar el cliente STOMP Kotlin
tasks.register<JavaExec>("runStompClientKotlin") {
    group = "application"
    description = "Ejecuta el cliente STOMP en Kotlin"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("websockets.StompClientKotlinKt") // Clase principal del cliente STOMP Kotlin
    standardInput = System.`in` // Permite entrada desde consola

    // Fuerza el uso de Java 21
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}

// Tarea para ejecutar el cliente STOMP Kotlin con SockJS (con fallback)
tasks.register<JavaExec>("runSockJsStompClient") {
    group = "application"
    description = "Ejecuta el cliente STOMP con SockJS y fallback automatico"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("websockets.SockJsStompClientKt")  // Clase principal del cliente STOMP con SockJS
    standardInput = System.`in` // Permite entrada desde consola

    // Fuerza el uso de Java 21
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}
