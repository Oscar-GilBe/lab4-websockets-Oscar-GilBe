// Variables globales para la conexión STOMP
let stompClient = null;
let connected = false;
let currentSessionId = null;

/**
 * Establece la conexión WebSocket con el servidor usando STOMP.
 *
 * Proceso:
 * 1. Crea un cliente STOMP configurado con la URL del broker
 * 2. Se conecta al servidor usando WebSocket
 * 3. Se suscribe al topic /topic/messages para recibir mensajes
 * 4. Envía un saludo inicial al servidor
 */
function connect() {
  // Crea el cliente STOMP
  // brokerURL: endpoint WebSocket del servidor
  stompClient = new StompJs.Client({
    brokerURL: "ws://localhost:8080/ws-stomp",

    // Configuración de reconexión automática
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,

    // Callback cuando se establece la conexión
    onConnect: function (frame) {
      console.log("Conectado: " + frame);
      connected = true;

      updateConnectionStatus(true);

      // Se suscribe al topic donde el servidor enviará las respuestas
      // Todos los mensajes enviados a /topic/messages serán recibidos aquí
      stompClient.subscribe("/topic/messages", function (message) {
        const response = JSON.parse(message.body);

        // Captura el session ID del primer mensaje recibido (el greeting)
        // Este será nuestro propio session ID
        if (!currentSessionId && response.originalSessionId) {
          currentSessionId = response.originalSessionId;
          console.log("Session ID capturado del greeting:", currentSessionId);
          updateConnectionStatus(true); // Actualizar para mostrar el session ID
        }

        showMessage(response);
      });

      // Envía un mensaje de saludo inicial al servidor
      // El servidor responderá con el mensaje de bienvenida de Eliza
      // y ese mensaje contendrá nuestro session ID en originalSessionId
      stompClient.publish({
        destination: "/app/greet",
        body: JSON.stringify({}),
      });
    },

    // Callback en caso de error
    onStompError: function (frame) {
      console.error("Error de STOMP: " + frame.headers["message"]);
      console.error("Detalles: " + frame.body);
      updateConnectionStatus(false);
      showSystemMessage(
        "Error de conexión STOMP. Por favor, intenta de nuevo."
      );
    },

    // Callback cuando se pierde la conexión
    onWebSocketClose: function (event) {
      console.log("WebSocket cerrado:", event);
      connected = false;
      updateConnectionStatus(false);
    },

    // Callback en caso de error de WebSocket
    onWebSocketError: function (event) {
      console.error("Error de WebSocket:", event);
      updateConnectionStatus(false);
      showSystemMessage(
        "Error de WebSocket. Verifica que el servidor esté ejecutándose."
      );
    },
  });

  // Activa la conexión
  stompClient.activate();
}

/**
 * Desconecta la sesión STOMP del servidor.
 * Envía un mensaje "bye" antes de desconectar para despedirse apropiadamente.
 */
function disconnect() {
  if (stompClient !== null && connected) {
    // Envía mensaje de despedida antes de desconectar
    try {
      const byeMessage = {
        content: "bye",
        sender: "User",
      };

      stompClient.publish({
        destination: "/app/chat",
        body: JSON.stringify(byeMessage),
      });

      console.log("Mensaje 'bye' enviado");

      // Espera un momento para que se envíe el mensaje antes de desconectar
      setTimeout(() => {
        stompClient.deactivate();
        connected = false;
        currentSessionId = null;
        updateConnectionStatus(false);
        showSystemMessage("Desconectado del servidor.");
        console.log("Desconectado");
      }, 500);
    } catch (error) {
      console.error("Error al enviar mensaje de despedida:", error);
      // Desconectar de todas formas
      stompClient.deactivate();
      connected = false;
      currentSessionId = null;
      updateConnectionStatus(false);
    }
  }
}

/**
 * Envía un mensaje al servidor a través de STOMP.
 *
 * El mensaje se envía al destino /app/chat, donde el controlador
 * del servidor lo procesará y enviará la respuesta a /topic/messages.
 */
function sendMessage() {
  const messageInput = document.getElementById("messageInput");
  const messageContent = messageInput.value.trim();

  if (messageContent && stompClient !== null && connected) {
    // Crea el objeto de mensaje con el contenido y el remitente
    const chatMessage = {
      content: messageContent,
      sender: "User",
    };

    // Envía el mensaje al servidor usando STOMP
    // Destino: /app/chat (será manejado por @MessageMapping("/chat"))
    stompClient.publish({
      destination: "/app/chat",
      body: JSON.stringify(chatMessage),
    });

    // Muestra el mensaje del usuario en la interfaz
    showMessage({
      content: messageContent,
      sender: "User",
      type: "CHAT",
    });

    // Limpia el input
    messageInput.value = "";
    messageInput.focus();
  }
}

/**
 * Muestra un mensaje en el área de chat.
 *
 * @param {Object} message - Objeto con content, sender, type y originalSessionId
 */
function showMessage(message) {
  const chatArea = document.getElementById("chatArea");
  const messageDiv = document.createElement("div");

  // Determina la clase según el tipo y remitente del mensaje
  if (message.type === "SYSTEM") {
    messageDiv.className = "message system";
  } else if (message.sender === "User") {
    messageDiv.className = "message user";
  } else {
    messageDiv.className = "message eliza";
  }

  // Crea el contenido del mensaje
  let messageHTML = "";

  if (message.type !== "SYSTEM") {
    messageHTML += `<div class="message-sender">${message.sender}</div>`;
  }

  messageHTML += `<div class="message-content">${escapeHtml(
    message.content
  )}</div>`;

  // Muestra información adicional si el mensaje es de otro cliente (Pub/Sub)
  if (
    message.originalSessionId &&
    message.originalSessionId !== currentSessionId
  ) {
    messageHTML += `<div class="message-meta">📢 Mensaje de otro cliente (Session: ${message.originalSessionId.substring(
      0,
      8
    )}...)</div>`;
  } else if (message.originalSessionId && message.sender === "Eliza") {
    messageHTML += `<div class="message-meta">✅ Respuesta para tu sesión</div>`;
  }

  messageDiv.innerHTML = messageHTML;
  chatArea.appendChild(messageDiv);

  // Hace scroll automático hacia el último mensaje
  chatArea.scrollTop = chatArea.scrollHeight;
}

/**
 * Muestra un mensaje del sistema.
 *
 * @param {string} content - Contenido del mensaje del sistema
 */
function showSystemMessage(content) {
  showMessage({
    content: content,
    sender: "System",
    type: "SYSTEM",
  });
}

/**
 * Actualiza el estado visual de la conexión.
 *
 * @param {boolean} isConnected - Estado de la conexión
 */
function updateConnectionStatus(isConnected) {
  const statusDot = document.getElementById("statusDot");
  const statusText = document.getElementById("statusText");
  const connectButton = document.getElementById("connectButton");
  const disconnectButton = document.getElementById("disconnectButton");
  const messageInput = document.getElementById("messageInput");
  const sendButton = document.getElementById("sendButton");
  const sessionInfo = document.getElementById("sessionInfo");
  const sessionIdSpan = document.getElementById("sessionId");

  if (isConnected) {
    statusDot.classList.add("connected");
    statusText.textContent = "Conectado";
    connectButton.disabled = true;
    disconnectButton.disabled = false;
    messageInput.disabled = false;
    sendButton.disabled = false;
    messageInput.focus();

    // Muestra el session ID
    if (currentSessionId) {
      sessionIdSpan.textContent = currentSessionId;
      sessionInfo.style.display = "block";
    }
  } else {
    statusDot.classList.remove("connected");
    statusText.textContent = "Desconectado";
    connectButton.disabled = false;
    disconnectButton.disabled = true;
    messageInput.disabled = true;
    sendButton.disabled = true;

    // Oculta el session ID
    sessionInfo.style.display = "none";
    sessionIdSpan.textContent = "N/A";
  }
}

/**
 * Escapa caracteres HTML para prevenir XSS.
 *
 * @param {string} text - Texto a escapar
 * @return {string} Texto escapado
 */
function escapeHtml(text) {
  const map = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;",
  };
  return text.replace(/[&<>"']/g, (m) => map[m]);
}

// Desconecta automáticamente cuando se cierra la página
window.addEventListener("beforeunload", function () {
  if (connected) {
    disconnect();
  }
});
