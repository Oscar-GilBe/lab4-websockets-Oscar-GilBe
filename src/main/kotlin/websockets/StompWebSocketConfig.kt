package websockets

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * Configuración de WebSocket con soporte para el protocolo STOMP.
 *
 * Esta clase habilita el uso de STOMP (Simple Text Oriented Messaging Protocol)
 * sobre WebSocket, siguiendo la configuración estándar de Spring Framework.
 *
 * @EnableWebSocketMessageBroker - Activa el soporte para mensajería WebSocket
 * con un broker de mensajes basado en STOMP.
 *
 * Documentación: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html
 */
@Configuration
@EnableWebSocketMessageBroker
class StompWebSocketConfig : WebSocketMessageBrokerConfigurer {
    /**
     * Configura el broker de mensajes para manejar las suscripciones y el enrutamiento de mensajes.
     *
     * Según la documentación de Spring:
     * - /topic: Prefijo para destinos de tipo "publish-subscribe" (broadcast a múltiples suscriptores)
     * - /queue: Prefijo para destinos "point-to-point" (un solo consumidor)
     * - /app: Prefijo para mensajes dirigidos a métodos anotados con @MessageMapping en los controladores
     *
     * El broker simple en memoria es suficiente para aplicaciones pequeñas. Para
     * aplicaciones en producción, se recomienda usar un broker externo como RabbitMQ.
     *
     * @param registry Registro del broker de mensajes donde se configuran los prefijos de destino
     */
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // Habilita un broker simple en memoria para manejar suscripciones
        // Los clientes se suscribirán a destinos que empiecen con /topic o /queue
        registry.enableSimpleBroker("/topic", "/queue")

        // Define el prefijo para mensajes destinados a métodos @MessageMapping
        // Por ejemplo: /app/chat será manejado por un método con @MessageMapping("/chat")
        registry.setApplicationDestinationPrefixes("/app")
    }

    /**
     * Registra los endpoints STOMP que los clientes usarán para conectarse.
     * - /ws-stomp: Endpoint para conexiones WebSocket directas con STOMP
     * - setAllowedOriginPatterns("*"): Permite conexiones desde cualquier origen (útil para desarrollo)
     *
     * En producción, se debe restringir setAllowedOriginPatterns a dominios específicos
     * para evitar problemas de seguridad CORS.
     *
     * @param registry Registro de endpoints donde se configuran los puntos de conexión
     */
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // Registra el endpoint /ws-stomp para conexiones WebSocket con STOMP
        registry
            .addEndpoint("/ws-stomp")
            .setAllowedOriginPatterns("*") // Permite todas las conexiones (solo desarrollo)
    }
}
