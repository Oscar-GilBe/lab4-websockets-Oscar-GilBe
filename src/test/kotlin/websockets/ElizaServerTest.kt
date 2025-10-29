@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import jakarta.websocket.Session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class ElizaServerTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun onOpen() {
        logger.info { "This is the test worker" }
        val latch = CountDownLatch(3)
        val list = mutableListOf<String>()

        val client = SimpleClient(list, latch)
        client.connect("ws://localhost:$port/eliza")
        latch.await()
        assertEquals(3, list.size)
        assertEquals("The doctor is in.", list[0])
    }

    @Test
    fun onChat() {
        logger.info { "Test thread" }
        val latch = CountDownLatch(4)
        val list = mutableListOf<String>()

        val client = ComplexClient(list, latch)
        client.connect("ws://localhost:$port/eliza")
        latch.await()
        val size = list.size
        // 1. EXPLAIN WHY size = list.size IS NECESSARY
        // Es necesario capturar el tamaño de la lista en una variable porque la comunicación por WebSocket es asíncrona.
        // Esto significa que podrían llegar nuevos mensajes mientras se ejecutan las validaciones, alterando el valor de list.size()
        // y provocando fallos intermitentes si verificamos con list.size() directamente en las aserciones del test debido a condiciones de carrera.
        // Al guardar list.size() en una variable justo después de latch.await(), trabajamos con una instantánea estable
        // del número de mensajes recibidos hasta ese momento.

        // 2. REPLACE BY assertXXX expression that checks an interval; assertEquals must not be used;
        assert(size >= 4) { "Expected at least 4 messages but got $size" }
        assert(size <= 5) { "Expected at most 5 messages but got $size" }

        // 3. EXPLAIN WHY assertEquals CANNOT BE USED AND WHY WE SHOULD CHECK THE INTERVAL
        // Dado que la comunicación WebSocket es asíncrona, el hilo que ejecuta el test y el que recibe los mensajes
        // pueden avanzar a ritmos diferentes. Aunque el servidor Eliza envía una cantidad fija de mensajes (3 para establecer
        // la conexión y 2 adicionales tras procesar la respuesta del cliente), es posible que el último mensaje aún no haya llegado
        // cuando latch.await() finaliza y comienzan las comprobaciones.
        // Por ello, no podemos emplear assertEquals, ya que el test podría fallar de manera aleatoria
        // dependiendo de si el mensaje se recibe antes o después de liberar el latch.
        // En su lugar, verificamos que el número de mensajes recibidos se encuentre dentro del intervalo esperado (entre 4 y 5),
        // lo que permite confirmar que el flujo de comunicación (greeting + respuesta del servidor) es correcto sin verse afectado
        // por pequeñas variaciones de tiempo de CPU entre hilos.
        // Otra opción sería ajustar el valor inicial del latch a 5 para garantizar que espere hasta que llegue el mensaje final.

        // 4. COMPLETE assertEquals(XXX, list[XXX])
        assertEquals("The doctor is in.", list[0])
        assertEquals("What's on your mind?", list[1])
        assertEquals("---", list[2])

        // Verificamos que la respuesta del doctor sea una de las posibles respuestas de ELIZA para la entrada "I am feeling sad"
        val possibleResponses =
            listOf(
                "Do you believe it is normal to be feeling sad?",
                "Do you enjoy being feeling sad?",
                "How long have you been feeling sad?",
                "I am sorry to hear you are feeling sad.",
            )
        assert(list[3] in possibleResponses) {
            "Expected one of the ELIZA responses but got: ${list[3]}"
        }

        assertEquals("---", list[4])
    }
}

@ClientEndpoint
class SimpleClient(
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @OnMessage
    fun onMessage(message: String) {
        logger.info { "Client received: $message" }
        list.add(message)
        latch.countDown()
    }
}

@ClientEndpoint
class ComplexClient(
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    // Flag para asegurar que el mensaje se envíe solo una vez
    private var messageSent = false

    @OnMessage
    fun onMessage(
        message: String,
        session: Session,
    ) {
        logger.info { "Client received: $message" }
        list.add(message)
        latch.countDown()
        // 5. COMPLETE if (expression) {
        // 6. COMPLETE   sentence
        // }
        // Enviamos un mensaje al servidor solo después de recibir el tercer mensaje del saludo del servidor.
        // Esto simula una conversación donde el cliente responde al doctor.
        // La bandera messageSent asegura que solo se envíe una vez.
        if (message == "---" && !messageSent) {
            session.basicRemote.sendText("I am feeling sad")
            messageSent = true
        }
    }
}

fun Any.connect(uri: String) {
    ContainerProvider.getWebSocketContainer().connectToServer(this, URI(uri))
}
