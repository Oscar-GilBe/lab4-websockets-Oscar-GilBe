package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.util.Locale
import java.util.Scanner

private val logger = KotlinLogging.logger {}

/**
 * Clase de datos que representa un mensaje enviado por el cliente.
 *
 * @property content Contenido del mensaje del usuario
 * @property sender Identificador del remitente del mensaje
 */
data class ChatMessage(
    val content: String,
    val sender: String = "User",
)

/**
 * Clase de datos que representa la respuesta del doctor Eliza.
 *
 * @property content Respuesta generada por Eliza
 * @property sender Identificador del remitente (siempre "Eliza")
 * @property type Tipo de mensaje (CHAT para conversación normal, SYSTEM para mensajes del sistema)
 * @property originalSessionId ID de sesión del cliente que envió el mensaje original (para Pub/Sub)
 */
data class ChatResponse(
    val content: String,
    val sender: String = "Eliza",
    val type: MessageType = MessageType.CHAT,
    val originalSessionId: String? = null,
)

/**
 * Enumeración de tipos de mensaje.
 *
 * - CHAT: Mensaje de conversación normal
 * - SYSTEM: Mensaje del sistema (conexión, desconexión, etc.)
 */
enum class MessageType {
    CHAT,
    SYSTEM,
}

/**
 * Controlador STOMP para manejar la comunicación con el chatbot Eliza.
 *
 * Este controlador procesa mensajes entrantes del cliente y envía respuestas
 * generadas por Eliza a través del protocolo STOMP. Patrón publish-subscribe para mensajes
 *
 * @property eliza Instancia del chatbot Eliza inyectada por Spring
 */
@Controller
class StompElizaController(
    private val eliza: Eliza,
) {
    /**
     * Maneja mensajes enviados al endpoint /app/chat.
     *
     * Los clientes envían mensajes a /app/chat, este método los procesa
     * y envía la respuesta a /topic/messages donde todos los suscriptores
     * la recibirán.
     *
     * El flujo es el siguiente:
     * 1. Cliente envía mensaje a /app/chat
     * 2. Este método recibe el mensaje y lo procesa con Eliza
     * 3. La respuesta se envía automáticamente a /topic/messages
     * 4. Todos los clientes suscritos a /topic/messages reciben la respuesta
     *
     * @param message Mensaje enviado por el cliente
     * @param headerAccessor Acceso a los headers del mensaje STOMP (para obtener sessionId, etc.)
     * @return Respuesta generada por Eliza que será enviada a /topic/messages
     */
    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    fun handleChatMessage(
        message: ChatMessage,
        headerAccessor: SimpMessageHeaderAccessor,
    ): ChatResponse {
        val sessionId = headerAccessor.sessionId ?: "unknown"
        logger.info { "STOMP Message received from session $sessionId: ${message.content}" }

        // Verifica si el usuario se está despidiendo
        val currentLine = Scanner(message.content.lowercase(Locale.getDefault()))
        if (currentLine.findInLine("bye") != null) {
            logger.info { "User said goodbye in session $sessionId" }
            return ChatResponse(
                content = "Alright then, goodbye!",
                sender = "Eliza",
                type = MessageType.SYSTEM,
                originalSessionId = sessionId,
            )
        }

        // Genera respuesta de Eliza
        val response = eliza.respond(Scanner(message.content))
        logger.info { "STOMP Response sent to session $sessionId: $response" }

        return ChatResponse(
            content = response,
            sender = "Eliza",
            type = MessageType.CHAT,
            originalSessionId = sessionId,
        )
    }

    /**
     * Maneja solicitudes de saludo inicial.
     *
     * Cuando un cliente se conecta y envía un mensaje a /app/greet,
     * este método responde con el mensaje de bienvenida de Eliza.
     *
     * @param headerAccessor Acceso a los headers del mensaje STOMP
     * @return Mensaje de bienvenida de Eliza
     */
    @MessageMapping("/greet")
    @SendTo("/topic/messages")
    fun handleGreeting(headerAccessor: SimpMessageHeaderAccessor): ChatResponse {
        val sessionId = headerAccessor.sessionId ?: "unknown"
        logger.info { "STOMP Greeting request from session $sessionId" }

        return ChatResponse(
            content = "The doctor is in. What's on your mind?",
            sender = "Eliza",
            type = MessageType.SYSTEM,
            originalSessionId = sessionId,
        )
    }
}
