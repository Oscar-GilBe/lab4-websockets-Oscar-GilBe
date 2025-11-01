package websockets

import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

/**
 * Detecta el tipo de transporte usado por cada cliente.
 *
 * JavaDoc de HandshakeInterceptor:
 * https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/server/HandshakeInterceptor.html
 */
@Component
class WebSocketConnectionLogger : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        // Actúa antes del handshake para detectar el transporte
        val uri = request.uri.toString()
        val transport = detectTransport(uri)
        attributes["transport"] = transport
        // El atributo "transport" queda asociado a la sesión
        // Este es luego accesible como sessionAttributes en STOMP header accessor
        return true // Permite el handshake
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
        // No hacer nada
    }

    /**
     * Detecta el tipo de transporte basado en la URI del request.
     */
    private fun detectTransport(uri: String): String =
        when {
            uri.contains("/ws-stomp") && !uri.contains("sockjs") -> "WebSocket"
            // SockJS transport detection
            uri.contains("websocket") -> "SockJS-WebSocket"
            uri.contains("xhr_streaming") -> "SockJS-Streaming"
            uri.contains("xhr_send") || uri.contains("xhr_recv") -> "SockJS-Polling"
            uri.contains("xhr") -> "SockJS-Polling" // Fallback para cualquier otro xhr
            else -> "Unknown"
        }
}

/**
 * Registra eventos STOMP y muestra estadísticas simples de conexiones por tipo.
 *
 * Documentación de ChannelInterceptor:
 * https://docs.spring.io/spring-integration/reference/channel/interceptors.html
 */
@Component
class StompConnectionLogger : ChannelInterceptor {
    private val logger = LoggerFactory.getLogger(StompConnectionLogger::class.java)
    private val activeSessions = mutableMapOf<String, String>() // clave: sessionId; valor: transport

    // Intercepción de mensajes STOMP para detectar conexiones y desconexiones
    override fun preSend(
        message: Message<*>,
        channel: MessageChannel,
    ): Message<*>? {
        // Extrae el accessor STOMP para inspeccionar el comando
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null) {
            when (accessor.command) {
                StompCommand.CONNECT -> handleConnect(accessor)
                StompCommand.DISCONNECT -> handleDisconnect(accessor)
                else -> {}
            }
        }

        return message
    }

    /**
     * Maneja la lógica al conectar un cliente.
     */
    private fun handleConnect(accessor: StompHeaderAccessor) {
        val sessionId = accessor.sessionId ?: return
        val transport = accessor.sessionAttributes?.get("transport")?.toString() ?: "Unknown"

        // Registra la nueva sesión
        activeSessions[sessionId] = transport

        logger.info("CLIENTE CONECTADO. ${getStats()}")
    }

    /**
     * Maneja la lógica al desconectar un cliente.
     */
    private fun handleDisconnect(accessor: StompHeaderAccessor) {
        val sessionId = accessor.sessionId ?: return

        // Solo logear si la sesión existe
        if (activeSessions.remove(sessionId) != null) {
            logger.info("CLIENTE DESCONECTADO. ${getStats()}")
        }
    }

    /**
     * Genera estadísticas simples de conexiones activas por tipo de transporte.
     */
    private fun getStats(): String {
        val total = activeSessions.size
        val byType =
            activeSessions.values
                .groupingBy { it }
                .eachCount()
                .entries // Convierte el Map en un conjunto de pares clave-valor (Map.Entry)
                // Ejemplo: [("WebSocket", 2), ("SockJS-Streaming", 1)]
                .joinToString(", ") { "${it.key}: ${it.value}" }

        return "Total: $total | $byType"
    }
}
