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
