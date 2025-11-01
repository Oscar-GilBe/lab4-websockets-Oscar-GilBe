package websockets

import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.Transport
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.util.Scanner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Enum que representa los tipos de transporte disponibles en SockJS.
 */
enum class TransportType {
    WEBSOCKET,
    XHR_STREAMING,
    XHR_POLLING,
}

/**
 * Cliente STOMP en Kotlin con soporte SockJS y fallback automatico.
 *
 * Este cliente se conecta al endpoint /ws-stomp-sockjs que tiene habilitado
 * SockJS para proporcionar fallback automatico cuando WebSocket no esta disponible.
 *
 * Caracteristicas:
 * - Conexion WebSocket con SockJS al endpoint /ws-stomp-sockjs
 * - Uso del transporte especificado: WebSocket, XHR-Streaming, o XHR-Polling
 * - Suscripcion al topic /topic/messages para recibir mensajes broadcast
 * - Envio de mensajes al endpoint /app/chat
 * - Deteccion de Session ID del servidor vs mensajes de otros clientes
 * - Interfaz de consola interactiva
 * - Manejo de errores robusto
 */
class SockJsStompClient(
    private val serverUrl: String = "http://localhost:8080/ws-stomp-sockjs",
    private val transportType: TransportType = TransportType.WEBSOCKET,
) {
    private var stompSession: StompSession? = null
    private var connected = false
    private var currentSessionId: String? = null
    private val scanner = Scanner(System.`in`) // para leer la entrada del usuario desde la consola

    /**
     * Conecta al servidor WebSocket usando STOMP con SockJS.
     */
    fun connect() {
        println("\n=== Conectando con SockJS ===")
        println("URL: $serverUrl")
        println("Transporte: ${getTransportName(transportType)}")

        try {
            // Crea la lista de transportes según la configuración
            val transports: List<Transport> = createTransportList(transportType)

            // Crea el cliente SockJS con la lista de transportes
            val sockJsClient = SockJsClient(transports)

            // Crea el cliente STOMP sobre SockJS
            val stompClient = WebSocketStompClient(sockJsClient)

            // Configura el convertidor de mensajes para manejar JSON
            // Usamos MappingJackson2MessageConverter para serializar/deserializar JSON automáticamente
            stompClient.messageConverter = MappingJackson2MessageConverter()

            // Conecta al servidor con el handler de sesion
            // Promesa asíncrona que se completará cuando el handshake WebSocket + STOMP CONNECT termine correctamente.
            val sessionFuture: CompletableFuture<StompSession> =
                stompClient
                    .connectAsync(serverUrl, createSessionHandler())

            // Espera la conexión (timeout de 15 segundos)
            // Si no se conecta en ese tiempo, lanzará una excepción TimeoutException
            stompSession = sessionFuture.get(15, TimeUnit.SECONDS)
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
     * Crea la lista de transportes según el tipo solicitado.
     */
    private fun createTransportList(type: TransportType): List<Transport> =
        when (type) {
            TransportType.WEBSOCKET -> {
                listOf(WebSocketTransport(StandardWebSocketClient()))
            }
            TransportType.XHR_STREAMING -> {
                // Usa el transporte XHR normal (streaming habilitado por defecto)
                listOf(RestTemplateXhrTransport())
            }
            TransportType.XHR_POLLING -> {
                // Para XHR-Polling deshabilitamos el streaming
                val xhrTransport = RestTemplateXhrTransport()
                xhrTransport.setXhrStreamingDisabled(true)
                listOf(xhrTransport)
            }
        }

    /**
     * Obtiene el nombre descriptivo del transporte.
     */
    private fun getTransportName(type: TransportType): String =
        when (type) {
            TransportType.WEBSOCKET -> "WebSocket"
            TransportType.XHR_STREAMING -> "XHR-Streaming"
            TransportType.XHR_POLLING -> "XHR-Polling"
        }

    /**
     * Crea el handler de sesion STOMP.
     * Maneja eventos de conexión, errores y transporte.
     */
    private fun createSessionHandler(): StompSessionHandlerAdapter =
        object : StompSessionHandlerAdapter() {
            /**
             * Callback cuando se establece la conexion.
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
                        // Indica que esperamos recibir un Map (JSON deserializado)
                        // Esto es compatible con ChatMessage del servidor
                        override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

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

                // Envia saludo inicial
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
                println("\n!!! ERROR de transporte SockJS !!!")
                println("!!! Detalle: ${exception.message}")
                connected = false
            }

            /**
             * Manejo de errores de frame STOMP.
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
        println("  Cliente STOMP Kotlin con SockJS")
        println("=".repeat(60))
        println("\nConectando al servidor...")

        // Conecta al servidor
        connect()

        if (!connected) {
            println("\nNo se pudo establecer la conexion. Saliendo...")
            return
        }

        // Menu de ayuda
        println("\nComandos disponibles:")
        println("  - Escribe tu mensaje y presiona Enter para enviar")
        println("  - 'help' - Muestra esta ayuda")
        println("  - 'status' - Muestra estado de la conexion")
        println("  - 'exit' o 'quit' - Desconecta y sale")
        println("\n" + "-".repeat(60))

        // Bucle principal de interaccion
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
                        println("  Transporte: ${getTransportName(transportType)}")
                    }
                    else -> {
                        sendMessage(input)
                    }
                }
            } catch (e: Exception) {
                println("\nError: ${e.message}")
            }
        }

        println("\nGracias por usar el cliente STOMP con SockJS!")
        println("=".repeat(60) + "\n")
    }
}

/**
 * Funcion main para ejecutar el cliente.
 */
fun main(args: Array<String>) {
    // Permite personalizar la URL del servidor y el transporte desde argumentos
    val serverUrl =
        if (args.isNotEmpty() && args[0].startsWith("http")) {
            args[0]
        } else {
            "http://localhost:8080/ws-stomp-sockjs"
        }

    var transportType = TransportType.WEBSOCKET

    // Parsear argumentos para el transporte
    if (args.isNotEmpty()) {
        when (args[0].lowercase()) {
            "websocket", "ws" -> transportType = TransportType.WEBSOCKET
            "xhr-streaming", "streaming", "stream" -> transportType = TransportType.XHR_STREAMING
            "xhr-polling", "polling", "poll" -> transportType = TransportType.XHR_POLLING
        }
    }

    println("\n>>> Cliente STOMP Kotlin con SockJS <<<")
    println(">>> Conectando a: $serverUrl")
    println(
        ">>> Transporte: ${
            when (transportType) {
                TransportType.WEBSOCKET -> "WebSocket"
                TransportType.XHR_STREAMING -> "XHR-Streaming"
                TransportType.XHR_POLLING -> "XHR-Polling"
            }
        }\n",
    )

    val client = SockJsStompClient(serverUrl, transportType)

    // Manejo de Ctrl+C para desconexion limpia
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
