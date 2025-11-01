package websockets

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * Configuración de WebSocket con soporte para el protocolo STOMP y SockJS fallback.
 *
 * Esta clase habilita el uso de STOMP (Simple Text Oriented Messaging Protocol)
 * sobre WebSocket con fallback automático a HTTP polling cuando WebSocket no está disponible.
 *
 * @EnableWebSocketMessageBroker - Activa el soporte para mensajería WebSocket
 * con un broker de mensajes basado en STOMP.
 *
 * Documentación oficial de Spring Framework:
 * - STOMP: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html
 * - SockJS Fallback: https://docs.spring.io/spring-framework/reference/web/websocket/fallback.html
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
     *
     * Cconfiguramos dos endpoints principales:
     * 1. /ws-stomp: Endpoint para conexiones WebSocket directas con STOMP (sin fallback)
     * 2. /ws-stomp-sockjs: Endpoint con soporte SockJS para compatibilidad y fallback
     *
     * SockJS es una librería JavaScript que proporciona una API similar a WebSocket
     * pero con mecanismos de fallback automático cuando WebSocket no está disponible.
     * Esto es crucial en redes corporativas donde proxies restrictivos pueden bloquear
     * WebSocket.
     *
     * Transportes de fallback de SockJS (en orden de preferencia):
     * 1. WebSocket: Protocolo nativo, bidireccional, baja latencia (preferido)
     * 2. HTTP Streaming (xhr-streaming): Mantiene conexión HTTP abierta
     * 3. HTTP Long Polling (xhr-polling): Funciona en cualquier navegador/red
     *
     * Flujo de selección de transporte:
     * - Cliente envía GET /info para obtener información del servidor
     * - Cliente intenta WebSocket primero
     * - Si WebSocket falla (bloqueado por proxy/firewall), intenta HTTP Streaming
     * - Si HTTP Streaming falla, usa HTTP Long Polling como último recurso
     *
     * SockJS envía heartbeats cada 25 segundos (por defecto) para evitar que proxies
     * cierren la conexión por inactividad.
     *
     * Documentación oficial:
     * - SockJS: https://docs.spring.io/spring-framework/reference/web/websocket/fallback.html
     * - Enabling SockJS: https://docs.spring.io/spring-framework/reference/web/websocket/fallback.html#websocket-fallback-sockjs-enable
     *
     * En producción, se debe restringir setAllowedOriginPatterns a dominios específicos
     * para evitar problemas de seguridad CORS.
     *
     * @param registry Registro de endpoints donde se configuran los puntos de conexión
     */
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // Endpoint 1: WebSocket stomp puro sin fallback
        // Recomendado para aplicaciones modernas con soporte garantizado de WebSocket
        registry
            .addEndpoint("/ws-stomp")
            .setAllowedOriginPatterns("*") // Permite conexiones desde cualquier origen (solo desarrollo)

        // Endpoint 2: WebSocket con SockJS fallback
        // Recomendado para máxima compatibilidad y fiabilidad en diferentes redes
        registry
            .addEndpoint("/ws-stomp-sockjs")
            .setAllowedOriginPatterns("*") // Permite todas las conexiones (solo desarrollo)
            .withSockJS() // Habilita el fallback SockJS con configuración por defecto
            .setStreamBytesLimit(512 * 1024) // Límite de bytes para streaming
    }
}
