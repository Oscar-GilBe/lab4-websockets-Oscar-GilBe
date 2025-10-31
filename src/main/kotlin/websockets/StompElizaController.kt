package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.util.Locale
import java.util.Scanner

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
 */
data class ChatResponse(
    val content: String,
    val sender: String = "Eliza",
    val type: MessageType = MessageType.CHAT,
)

/**
 * Enumeración de tipos de mensaje.
 */
enum class MessageType {
    CHAT,
    SYSTEM,
}

/**
 * Controlador STOMP para manejar la comunicación con el chatbot Eliza.
 * @property eliza Instancia del chatbot Eliza inyectada por Spring
 */
@Controller
class StompElizaController(
    private val eliza: Eliza,
) {
    /**
     * Maneja mensajes enviados al endpoint /app/chat.
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

        // Verifica si el usuario se está despidiendo
        val currentLine = Scanner(message.content.lowercase(Locale.getDefault()))
        if (currentLine.findInLine("bye") != null) {
            return ChatResponse(
                content = "Alright then, goodbye!",
                sender = "Eliza",
                type = MessageType.SYSTEM,
            )
        }

        // Genera respuesta de Eliza
        val response = eliza.respond(Scanner(message.content))

        return ChatResponse(
            content = response,
            sender = "Eliza",
            type = MessageType.CHAT,
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

        return ChatResponse(
            content = "The doctor is in. What's on your mind?",
            sender = "Eliza",
            type = MessageType.SYSTEM,
        )
    }
}
