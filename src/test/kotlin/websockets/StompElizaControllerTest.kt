package websockets

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Tests de integración que verifican la conexion STOMP y SockJS.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class StompElizaControllerTest {
    @LocalServerPort
    private var port: Int = 0

    private var stompSession: StompSession? = null

    // Cola para almacenar mensajes recibidos
    private val receivedMessages: BlockingQueue<Map<*, *>> = LinkedBlockingQueue()

    @BeforeEach
    fun setup() {
        receivedMessages.clear()
    }

    @AfterEach
    fun tearDown() {
        stompSession?.disconnect()
        stompSession = null
    }

    /**
     * Test que verifica que el contexto de Spring Boot se carga correctamente.
     */
    @Test
    fun `context loads successfully`() {
        assertTrue(port > 0, "El servidor deberia estar ejecutándose en un puerto")
    }

    /**
     * Test de conexion STOMP pura (sin SockJS).
     * Verifica que el endpoint /ws-stomp funciona correctamente.
     */
    @Test
    @Timeout(10) // Limite de tiempo para evitar bloqueos
    fun `should connect to STOMP endpoint successfully`() {
        // Crear cliente WebSocket puro
        val webSocketClient = StandardWebSocketClient()
        val stompClient = WebSocketStompClient(webSocketClient)
        stompClient.messageConverter = MappingJackson2MessageConverter()

        // Conectar al endpoint STOMP puro
        val url = "ws://localhost:$port/ws-stomp"
        val sessionHandler = createSessionHandler()
        val sessionFuture = stompClient.connectAsync(url, sessionHandler)

        // Esperar conexion
        stompSession = sessionFuture.get(5, TimeUnit.SECONDS)

        assertNotNull(stompSession, "La sesion STOMP deberia estar conectada")
        assertTrue(stompSession!!.isConnected, "La sesion STOMP deberia estar activa")
    }

    /**
     * Test de conexion SockJS con WebSocket.
     * Verifica que el endpoint /ws-stomp-sockjs funciona con WebSocket.
     */
    @Test
    @Timeout(10)
    fun `should connect to SockJS endpoint with WebSocket`() {
        // Crear cliente SockJS con WebSocket
        val transports = listOf(WebSocketTransport(StandardWebSocketClient()))
        val sockJsClient = SockJsClient(transports)
        val stompClient = WebSocketStompClient(sockJsClient)
        stompClient.messageConverter = MappingJackson2MessageConverter()

        // Conectar al endpoint SockJS
        val url = "http://localhost:$port/ws-stomp-sockjs"
        val sessionHandler = createSessionHandler()
        val sessionFuture = stompClient.connectAsync(url, sessionHandler)

        // Esperar conexion
        stompSession = sessionFuture.get(5, TimeUnit.SECONDS)

        assertNotNull(stompSession, "La sesion SockJS deberia estar conectada")
        assertTrue(stompSession!!.isConnected, "La sesion SockJS deberia estar activa")
    }

    /**
     * Test de conexion SockJS con XHR (fallback).
     * Verifica que el endpoint /ws-stomp-sockjs funciona con XHR cuando WebSocket no esta disponible.
     */
    @Test
    @Timeout(10)
    fun `should connect to SockJS endpoint with XHR fallback`() {
        // Crear cliente SockJS solo con XHR (sin WebSocket)
        val transports = listOf(RestTemplateXhrTransport())
        val sockJsClient = SockJsClient(transports)
        val stompClient = WebSocketStompClient(sockJsClient)
        stompClient.messageConverter = MappingJackson2MessageConverter()

        // Conectar al endpoint SockJS
        val url = "http://localhost:$port/ws-stomp-sockjs"
        val sessionHandler = createSessionHandler()
        val sessionFuture = stompClient.connectAsync(url, sessionHandler)

        // Esperar conexion
        stompSession = sessionFuture.get(5, TimeUnit.SECONDS)

        assertNotNull(stompSession, "La sesion SockJS con XHR deberia estar conectada")
        assertTrue(stompSession!!.isConnected, "La sesion SockJS con XHR deberia estar activa")
    }

    /**
     * Test de envio de mensaje y recepcion de respuesta de Eliza.
     * Verifica el flujo completo de comunicacion.
     */
    @Test
    @Timeout(15)
    fun `should send message and receive Eliza response`() {
        // Conectar al servidor
        val webSocketClient = StandardWebSocketClient()
        val stompClient = WebSocketStompClient(webSocketClient)
        stompClient.messageConverter = MappingJackson2MessageConverter()

        val url = "ws://localhost:$port/ws-stomp"
        val sessionHandler = createSessionHandler()
        val sessionFuture = stompClient.connectAsync(url, sessionHandler)

        stompSession = sessionFuture.get(5, TimeUnit.SECONDS)

        assertNotNull(stompSession, "La sesion STOMP deberia estar conectada")
        assertTrue(stompSession!!.isConnected, "La sesion STOMP deberia estar activa")

        // Suscribirse al topic
        stompSession!!.subscribe(
            "/topic/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    // Convierte el payload a Map y lo añade a la cola receivedMessages
                    payload?.let { receivedMessages.offer(it as Map<*, *>) }
                }
            },
        )

        // Esperar un momento para asegurar la suscripcion
        Thread.sleep(500)

        // Enviar saludo para obtener el mensaje de bienvenida
        stompSession!!.send("/app/greet", emptyMap<String, Any>())

        // Esperar mensaje de bienvenida
        // Espera hasta 5 segundos para obtener y eliminar el primer elemento de la cola receivedMessages
        val greetingMessage = receivedMessages.poll(5, TimeUnit.SECONDS)
        assertNotNull(greetingMessage, "Deberia recibir mensaje de bienvenida")
        assertEquals("SYSTEM", greetingMessage!!["type"], "El mensaje deberia ser de tipo SYSTEM")
        assertEquals("Eliza", greetingMessage!!["sender"], "El mensaje deberia ser de Eliza")
        assertNotNull(greetingMessage["content"], "La respuesta deberia tener contenido")

        // Enviar mensaje a Eliza
        val chatMessage = mapOf("content" to "Hello Eliza", "sender" to "User")
        stompSession!!.send("/app/chat", chatMessage)

        // Esperar respuesta de Eliza
        val elizaResponse = receivedMessages.poll(5, TimeUnit.SECONDS)
        assertNotNull(elizaResponse, "Deberia recibir respuesta de Eliza")
        assertEquals("CHAT", elizaResponse!!["type"], "El mensaje deberia ser de tipo CHAT")
        assertEquals("Eliza", elizaResponse!!["sender"], "El mensaje deberia ser de Eliza")
        assertNotNull(elizaResponse["content"], "La respuesta deberia tener contenido")
    }

    /**
     * Test de mensaje de despedida.
     * Verifica que Eliza responde correctamente al mensaje 'bye'.
     */
    @Test
    @Timeout(15)
    fun `should receive goodbye message from Eliza`() {
        // Conectar al servidor
        val webSocketClient = StandardWebSocketClient()
        val stompClient = WebSocketStompClient(webSocketClient)
        stompClient.messageConverter = MappingJackson2MessageConverter()

        val url = "ws://localhost:$port/ws-stomp"
        val sessionHandler = createSessionHandler()
        val sessionFuture = stompClient.connectAsync(url, sessionHandler)

        stompSession = sessionFuture.get(5, TimeUnit.SECONDS)

        assertNotNull(stompSession, "La sesion STOMP deberia estar conectada")
        assertTrue(stompSession!!.isConnected, "La sesion STOMP deberia estar activa")

        // Suscribirse al topic
        stompSession!!.subscribe(
            "/topic/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    // Convierte el payload a Map y lo añade a la cola receivedMessages
                    payload?.let { receivedMessages.offer(it as Map<*, *>) }
                }
            },
        )

        // Esperar un momento para asegurar la suscripcion
        Thread.sleep(500)

        // Enviar saludo
        stompSession!!.send("/app/greet", emptyMap<String, Any>())
        val greetingMessage = receivedMessages.poll(5, TimeUnit.SECONDS) // Consumir mensaje de bienvenida
        assertNotNull(greetingMessage, "Deberia recibir mensaje de bienvenida")
        assertEquals("SYSTEM", greetingMessage!!["type"], "El mensaje deberia ser de tipo SYSTEM")
        assertEquals("Eliza", greetingMessage!!["sender"], "El mensaje deberia ser de Eliza")
        assertNotNull(greetingMessage["content"], "La respuesta deberia tener contenido")

        // Enviar mensaje de despedida
        val byeMessage = mapOf("content" to "bye", "sender" to "User")
        stompSession!!.send("/app/chat", byeMessage)

        // Esperar respuesta de despedida de Eliza
        val elizaResponse = receivedMessages.poll(5, TimeUnit.SECONDS)
        assertNotNull(elizaResponse, "Deberia recibir respuesta de despedida de Eliza")
        assertEquals("SYSTEM", elizaResponse!!["type"], "El mensaje deberia ser de tipo CHAT")
        assertEquals("Eliza", elizaResponse!!["sender"], "El mensaje deberia ser de Eliza")
        assertNotNull(elizaResponse["content"], "La respuesta deberia tener contenido")

        val content = elizaResponse["content"]?.toString()?.lowercase() ?: ""
        assertTrue(
            content.contains("bye") || content.contains("goodbye") || content.contains("see you"),
            "La respuesta deberia ser un mensaje de despedida",
        )
    }

    /**
     * Test de multiples clientes simultaneos.
     * Verifica que varios clientes pueden conectarse y recibir mensajes a temas que están suscritos.
     */
    @Test
    @Timeout(15)
    fun `should handle multiple clients messages`() {
        // Crear dos clientes y dos colas para almacenar los mensajes recibidos (una cola para cada cliente)
        val client1Messages: BlockingQueue<Map<*, *>> = LinkedBlockingQueue()
        val client2Messages: BlockingQueue<Map<*, *>> = LinkedBlockingQueue()

        // Cliente 1
        val webSocketClient1 = StandardWebSocketClient()
        val stompClient1 = WebSocketStompClient(webSocketClient1)
        stompClient1.messageConverter = MappingJackson2MessageConverter()

        val url = "ws://localhost:$port/ws-stomp"
        val session1Future = stompClient1.connectAsync(url, createSessionHandler())
        val session1 = session1Future.get(5, TimeUnit.SECONDS)

        session1.subscribe(
            "/topic/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    // Convierte el payload a Map y lo añade a la cola client1Messages
                    payload?.let { client1Messages.offer(it as Map<*, *>) }
                }
            },
        )

        // Cliente 2
        val webSocketClient2 = StandardWebSocketClient()
        val stompClient2 = WebSocketStompClient(webSocketClient2)
        stompClient2.messageConverter = MappingJackson2MessageConverter()

        val session2Future = stompClient2.connectAsync(url, createSessionHandler())
        val session2 = session2Future.get(5, TimeUnit.SECONDS)

        session2.subscribe(
            "/topic/messages",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    // Convierte el payload a Map y lo añade a la cola client2Messages
                    payload?.let { client2Messages.offer(it as Map<*, *>) }
                }
            },
        )

        // Esperar un momento
        Thread.sleep(500)

        // Cliente 1 envia saludo
        session1.send("/app/greet", emptyMap<String, Any>())

        // Ambos clientes deberian recibir el mensaje de sistema
        val msg1Client1 = client1Messages.poll(5, TimeUnit.SECONDS)
        val msg1Client2 = client2Messages.poll(5, TimeUnit.SECONDS)

        // Verificar respuesta recibida en el cliente 1
        assertNotNull(msg1Client1, "Cliente 1 deberia recibir mensaje de sistema")
        assertEquals("SYSTEM", msg1Client1!!["type"], "El mensaje deberia ser de tipo SYSTEM")
        assertEquals("Eliza", msg1Client1!!["sender"], "El mensaje deberia ser de Eliza")
        assertNotNull(msg1Client1["content"], "La respuesta deberia tener contenido")

        // Verificar respuesta recibida en el cliente 2
        assertNotNull(msg1Client2, "Cliente 2 deberia recibir mensaje de sistema")
        assertEquals("SYSTEM", msg1Client2!!["type"], "El mensaje deberia ser de tipo SYSTEM")
        assertEquals("Eliza", msg1Client2!!["sender"], "El mensaje deberia ser de Eliza")
        assertNotNull(msg1Client2["content"], "La respuesta deberia tener contenido")

        // Cliente 1 envia mensaje a Eliza
        val chatMessage = mapOf("content" to "i am feeling happy", "sender" to "User")
        session1.send("/app/chat", chatMessage)

        // Ambos clientes deberian recibir la respuesta de Eliza
        val elizaResponseClient1 = client1Messages.poll(5, TimeUnit.SECONDS)
        val elizaResponseClient2 = client2Messages.poll(5, TimeUnit.SECONDS)

        // Verificar respuesta recibida en el cliente 1
        assertNotNull(elizaResponseClient1, "Cliente 1 deberia recibir respuesta de Eliza")
        assertEquals("CHAT", elizaResponseClient1!!["type"], "El mensaje deberia ser de tipo CHAT")
        assertEquals("Eliza", elizaResponseClient1!!["sender"], "El mensaje deberia ser de Eliza")
        assertNotNull(elizaResponseClient1["content"], "La respuesta deberia tener contenido")

        // Verificar respuesta recibida en el cliente 2
        assertNotNull(elizaResponseClient2, "Cliente 2 deberia recibir respuesta de Eliza")
        assertEquals("CHAT", elizaResponseClient2!!["type"], "El mensaje deberia ser de tipo CHAT")
        assertEquals("Eliza", elizaResponseClient2!!["sender"], "El mensaje deberia ser de Eliza")
        assertNotNull(elizaResponseClient2["content"], "La respuesta deberia tener contenido")

        // Desconectar
        session1.disconnect()
        session2.disconnect()
    }

    /**
     * Crea un handler de sesion STOMP basico.
     */
    private fun createSessionHandler(): StompSessionHandlerAdapter =
        object : StompSessionHandlerAdapter() {
            // Handler minimo, solo para establecer conexion
        }
}
