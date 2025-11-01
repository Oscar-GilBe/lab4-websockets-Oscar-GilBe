package websockets

import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.util.Scanner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Cliente STOMP en Kotlin que replica la funcionalidad del cliente JavaScript stomp-client.js
 *
 * Características:
 * - Conexión WebSocket pura (sin SockJS) al endpoint /ws-stomp
 * - Suscripción al topic /topic/messages para recibir mensajes broadcast
 * - Envío de mensajes al endpoint /app/chat
 * - Detección de session ID propio vs mensajes de otros clientes
 * - Interfaz de consola interactiva
 * - Reconexión automática
 * - Manejo de errores robusto
 */
class StompClientKotlin(
    private val serverUrl: String = "ws://localhost:8080/ws-stomp",
) {
    private var stompSession: StompSession? = null
    private var connected = false
    private var currentSessionId: String? = null
    private val scanner = Scanner(System.`in`) // para leer la entrada del usuario desde la consola

    /**
     * Conecta al servidor WebSocket usando STOMP.
     */
    fun connect() {
        println("\n=== Conectando al servidor WebSocket ===")
        println("URL: $serverUrl")

        try {
            // Crea el cliente WebSocket estándar
            val webSocketClient = StandardWebSocketClient()
            val stompClient = WebSocketStompClient(webSocketClient)

            // Configuramos el convertidor de mensajes para manejar JSON
            // Usamos MappingJackson2MessageConverter para serializar/deserializar JSON automáticamente
            stompClient.messageConverter = MappingJackson2MessageConverter()

            // Conecta al servidor con el handler de sesión
            // Promesa asíncrona que se completará cuando el handshake WebSocket + STOMP CONNECT termine correctamente.
            val sessionFuture: CompletableFuture<StompSession> =
                stompClient
                    .connectAsync(serverUrl, createSessionHandler())

            // Espera la conexión (timeout de 10 segundos)
            // Si no se conecta en ese tiempo, lanzará una excepción TimeoutException
            stompSession = sessionFuture.get(10, TimeUnit.SECONDS)
            connected = true

            println("Conexion establecida correctamente")
            println("========================================\n")
        } catch (e: Exception) {
            println("ERROR: No se pudo conectar al servidor")
            println("Razon: ${e.message}")
            println("Verifica que el servidor este ejecutandose en $serverUrl")
            connected = false
        }
    }

    /**
     * Crea el handler de sesión STOMP.
     * Maneja eventos de conexión, errores y transporte.
     */
    private fun createSessionHandler(): StompSessionHandlerAdapter =
        object : StompSessionHandlerAdapter() {
            /**
             * Callback cuando se establece la conexión.
             */
            override fun afterConnected(
                session: StompSession,
                connectedHeaders: StompHeaders,
            ) {
                println("\n>>> CONECTADO al servidor STOMP")

                // Suscribe al topic /topic/messages
                session.subscribe(
                    "/topic/messages",
                    object : StompFrameHandler {
                        override fun getPayloadType(headers: StompHeaders): Type {
                            // Indica que esperamos recibir un Map (JSON deserializado)
                            // Esto es compatible con ChatMessage del servidor
                            return Map::class.java
                        }

                        override fun handleFrame(
                            headers: StompHeaders,
                            payload: Any?,
                        ) {
                            // Maneja el mensaje recibido
                            handleMessage(payload as? Map<*, *>)
                        }
                    },
                )

                println(">>> Suscrito a /topic/messages")

                // Envía saludo inicial
                // Envía un Map vacío que Jackson serializará como {}
                session.send("/app/greet", emptyMap<String, Any>())
                println(">>> Mensaje de saludo enviado\n")
            }

            /**
             * Manejo de errores de transporte.
             */
            override fun handleTransportError(
                session: StompSession,
                exception: Throwable,
            ) {
                println("\n!!! ERROR de transporte WebSocket !!!")
                println("!!! Detalle: ${exception.message}")
                connected = false
            }

            /**
             * Manejo de errores de frame STOMP.
             * Equivalente a onStompError en stomp-client.js
             */
            override fun handleException(
                session: StompSession,
                command: StompCommand?,
                headers: StompHeaders,
                payload: ByteArray,
                exception: Throwable,
            ) {
                println("\n!!! ERROR STOMP !!!")
                println("!!! Comando: $command")
                println("!!! Mensaje: ${exception.message}")
            }
        }

    /**
     * Maneja los mensajes recibidos del servidor.
     */
    private fun handleMessage(messageMap: Map<*, *>?) {
        if (messageMap == null) return

        try {
            // Extrae campos del Map (JSON deserializado por Jackson)
            val content = messageMap["content"]?.toString() ?: ""
            val sender = messageMap["sender"]?.toString() ?: ""
            val type = messageMap["type"]?.toString() ?: "" // "CHAT" o "SYSTEM"
            val originalSessionId = messageMap["originalSessionId"]?.toString() ?: ""

            // Captura el session ID del primer mensaje (greeting)
            if (currentSessionId == null && originalSessionId.isNotEmpty()) {
                currentSessionId = originalSessionId
                println("\n[Sistema] Session ID del servidor capturado: $currentSessionId")
            }

            // Muestra el mensaje formateado
            when {
                type == "SYSTEM" -> {
                    if (originalSessionId.isNotEmpty() && originalSessionId != currentSessionId) {
                        println("\n[SISTEMA -> Otro Cliente] $content")
                        println("  (Session: ${originalSessionId.take(8)}...)")
                    } else { // Mensaje para nuestra sesión
                        println("\n[SISTEMA] $content")
                    }
                }
                sender == "User" -> {
                    // Nuestro propio mensaje (ya lo mostramos por pantalla al enviarlo)
                }
                sender == "Eliza" -> {
                    // Verifica si es para nuestra sesión o de otro cliente
                    if (originalSessionId.isNotEmpty() && originalSessionId != currentSessionId) {
                        println("\n[Eliza -> Otro Cliente] $content")
                        println("  (Session: ${originalSessionId.take(8)}...)")
                    } else { // Mensaje para nuestra sesión
                        println("\n[Eliza] $content")
                    }
                }
                else -> {
                    println("\n[$sender] $content")
                }
            }
        } catch (e: Exception) {
            println("\n[Mensaje Recibido] $messageMap")
            println("[Error al procesar] ${e.message}")
        }
    }

    /**
     * Envía un mensaje al servidor.
     */
    fun sendMessage(content: String) {
        if (!connected || stompSession == null) {
            println("\n[ERROR] No estas conectado al servidor")
            return
        }

        if (content.trim().isEmpty()) {
            return
        }

        // Crea el mensaje como Map para que Jackson lo serialice correctamente
        val chatMessage =
            mapOf(
                "content" to content,
                "sender" to "User",
            )

        try {
            // Envía al endpoint /app/chat
            // MappingJackson2MessageConverter serializará el Map a JSON automáticamente
            stompSession?.send("/app/chat", chatMessage)

            // Muestra el mensaje del usuario en consola
            println("\n[Tu] $content")
        } catch (e: Exception) {
            println("\n[ERROR] No se pudo enviar el mensaje: ${e.message}")
        }
    }

    /**
     * Desconecta del servidor.
     */
    fun disconnect() {
        if (!connected || stompSession == null) {
            println("\n[INFO] Ya estas desconectado")
            return
        }

        println("\n=== Desconectando del servidor ===")

        try {
            // Envía mensaje de despedida como Map
            val byeMessage =
                mapOf(
                    "content" to "bye",
                    "sender" to "User",
                )
            stompSession?.send("/app/chat", byeMessage)
            println("Mensaje 'bye' enviado")

            // Espera un momento para que se envíe el mensaje
            Thread.sleep(500)

            // Desconecta la sesión
            stompSession?.disconnect()
            connected = false
            currentSessionId = null

            println("Desconectado del servidor")
            println("===================================\n")
        } catch (e: Exception) {
            println("Error al desconectar: ${e.message}")
            connected = false
        }
    }

    /**
     * Inicia el bucle interactivo de consola.
     * Interfaz de usuario para interactuar con el cliente.
     */
    fun startInteractiveMode() {
        println("\n" + "=".repeat(60))
        println("  Cliente STOMP Kotlin")
        println("=".repeat(60))
        println("\nConectando al servidor...")

        // Conecta al servidor
        connect()

        if (!connected) {
            println("\nNo se pudo establecer la conexion. Saliendo...")
            return
        }

        // Menú de ayuda
        println("\nComandos disponibles:")
        println("  - Escribe tu mensaje y presiona Enter para enviar")
        println("  - 'help' - Muestra esta ayuda")
        println("  - 'status' - Muestra estado de la conexion")
        println("  - 'exit' o 'quit' - Desconecta y sale")
        println("\n" + "-".repeat(60))

        // Bucle principal de interacción
        while (connected) {
            try {
                print("\n> ")
                val input = scanner.nextLine().trim()

                when (input.lowercase()) {
                    "" -> continue
                    "exit", "quit" -> {
                        disconnect()
                        break
                    }
                    "help" -> {
                        println("\nComandos disponibles:")
                        println("  - Escribe tu mensaje y presiona Enter para enviar")
                        println("  - 'help' - Muestra esta ayuda")
                        println("  - 'status' - Muestra estado de la conexion")
                        println("  - 'exit' o 'quit' - Desconecta y sale")
                    }
                    "status" -> {
                        println("\nEstado de conexion:")
                        println("  Conectado: $connected")
                        println("  Session ID: ${currentSessionId ?: "N/A"}")
                        println("  URL: $serverUrl")
                    }
                    else -> {
                        sendMessage(input)
                    }
                }
            } catch (e: Exception) {
                println("\nError: ${e.message}")
            }
        }

        println("\nGracias por usar el cliente STOMP Kotlin!")
        println("=".repeat(60) + "\n")
    }
}

/**
 * Función main para ejecutar el cliente.
 */
fun main(args: Array<String>) {
    // Permite personalizar la URL del servidor desde argumentos
    val serverUrl =
        if (args.isNotEmpty()) {
            args[0]
        } else {
            "ws://localhost:8080/ws-stomp"
        }

    println("\n>>> Cliente STOMP Kotlin <<<")
    println(">>> Conectando a: $serverUrl\n")

    val client = StompClientKotlin(serverUrl)

    // Manejo de Ctrl+C para desconexión limpia
    Runtime.getRuntime().addShutdownHook(
        Thread {
            println("\n\nSenal de interrupcion recibida...")
            client.disconnect()
        },
    )

    try {
        client.startInteractiveMode()
    } catch (e: Exception) {
        println("\nError fatal: ${e.message}")
        e.printStackTrace()
    }

    exitProcess(0)
}
