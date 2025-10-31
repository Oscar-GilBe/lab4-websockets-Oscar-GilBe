package websockets

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

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
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
    }

    /**
     * Registra los endpoints STOMP que los clientes usarán para conectarse.
     * - /ws-stomp: Endpoint para conexiones WebSocket directas con STOMP
     * - setAllowedOriginPatterns("*"): Permite conexiones desde cualquier origen
     *
     * @param registry Registro de endpoints donde se configuran los puntos de conexión
     */
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws-stomp")
            .setAllowedOriginPatterns("*") // Permite todas las conexiones (solo desarrollo)
    }
}
