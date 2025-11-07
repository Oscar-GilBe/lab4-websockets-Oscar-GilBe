[![Build Status](../../actions/workflows/CI.yml/badge.svg)](../../actions/workflows/CI.yml)

# Web Engineering 2025-2026 / Lab 4: WebSocket

A minimal Spring Boot + Kotlin starter for Lab 4. Complete the tasks in `docs/GUIDE.md` to implement WebSocket communication with the ELIZA chatbot.

## Tech stack

- Spring Boot 3.5.3
- Kotlin 2.2.10
- Java 21 (toolchain)
- Gradle Wrapper

## Prerequisites

- Java 21
- Git

## Quick start

```bash
./gradlew clean build
./gradlew test
./gradlew bootRun
# Default: http://localhost:8080
```

## Project structure

- `src/main/kotlin/websockets`: application code (`Eliza.kt`, `ElizaServer.kt`)
- `src/test/kotlin/websockets`: tests (`ElizaServerTest.kt`)
- `docs/GUIDE.md`: assignment instructions

## Assignment tasks

See `docs/GUIDE.md` for detailed steps:

- Implement WebSocket server with ELIZA chatbot
- Handle WebSocket connections and message routing
- Test WebSocket communication

## Code quality and formatting

```bash
./gradlew ktlintFormat ktlintCheck
```

## Testing

```bash
./gradlew test
```

## Bonus opportunities

Be the first to complete **at least two** of the following tasks to earn a bonus:

### 1. **STOMP!** ✅

- **Description**: Support *STOMP* in the server side and create a small client that uses it.
- **Implementation**: Implement STOMP protocol support for WebSocket communication and create a client that demonstrates STOMP messaging.
- **Goal**: Demonstrate understanding of STOMP protocol and message-oriented communication.
- **Benefit**: Shows mastery of advanced WebSocket protocols and messaging patterns.

### 2. **Sock from WebSocket** ✅

- **Description**: Support *SockJS* in the server side and show that polling can be used as transport instead of *WebSocket* when needed.
- **Implementation**: Add SockJS fallback support and demonstrate polling transport when WebSocket is not available.
- **Goal**: Ensure WebSocket compatibility across different network environments.
- **Benefit**: Enhances application reliability and browser compatibility.

### 3. **Turn sides**

- **Description**: Turn the DOCTOR into a client of the server, so, the server acts only as message broker.
- **Implementation**: Refactor the architecture so the server acts as a message broker between multiple ELIZA clients.
- **Goal**: Demonstrate understanding of message broker patterns and distributed systems.
- **Benefit**: Shows understanding of scalable WebSocket architectures.

### 4. **Relay**

- **Description**: Use the server as a relay server to connect to an external message broker (e.g., RabbitMQ).
- **Implementation**: Integrate with external message broker and implement relay functionality.
- **Goal**: Demonstrate integration with enterprise messaging systems.
- **Benefit**: Shows understanding of enterprise messaging patterns and scalability.

### 5. **Add Real-time Analytics Dashboard**

- **Description**: Build a real-time dashboard based on WebSocket that displays connection statistics, message rates, and tracks user activity.
- **Implementation**: Use WebSocket to stream server metrics to the dashboard UI in real time, providing live updates on WebSocket events and usage.
- **Goal**: Showcase real-time visibility into WebSocket server performance and user interactions using WebSocket-based data delivery.
- **Benefit**: Demonstrates practical skills in building observability features directly atop the WebSocket protocol.

### 6. **Implement WebSocket Security with Authentication**

- **Description**: Add JWT-based authentication for WebSocket connections and implement role-based access control.
- **Implementation**: Secure WebSocket handshake, implement token validation, and add authorization for different message types.
- **Goal**: Secure WebSocket communication and protect against unauthorized access.
- **Benefit**: Shows understanding of WebSocket security patterns and authentication flows.

### 7. **Add Message Persistence and History (via Subprotocol)**

- **Description**: Design and implement a custom WebSocket subprotocol (e.g., `chat-history`) that supports commands for message persistence and conversation history retrieval.
- **Implementation**: Define subprotocol messages for storing chat content and requesting conversation history. Store messages in a database on specific subprotocol commands, and send back historical data as protocol responses. Optionally, provide alternate REST endpoints for fallback.
- **Goal**: Enable robust, protocol-aware conversation persistence and historical data access via subprotocol logic.
- **Benefit**: Shows understanding of subprotocol negotiation, protocol design, and combining real-time communication with persistent storage patterns.

### 8. **Implement Session Management and Broadcast**

- **Description**: Add in-memory session management and implement message broadcasting to all connected WebSocket clients.
- **Implementation**: Track active connections using in-memory data structures and broadcast messages to all clients without requiring external coordination.
- **Goal**: Demonstrate efficient, server-local handling of broadcasts and session tracking.
- **Benefit**: Shows understanding of state management and broadcasting patterns in real-time WebSocket applications without the complexity of distributed deployment.

### 9. **Use WebSocket Testing Framework**

- **Description**: Use established testing tools for WebSocket applications, including frameworks for load testing and integration tests.
- **Implementation**: Employ existing WebSocket test clients and frameworks to run load testing scenarios and automated integration tests.
- **Goal**: Ensure WebSocket application reliability and performance.
- **Benefit**: Demonstrates proficiency with industry-standard testing tools and strategies for real-time applications.

### 10. **Implement WebSocket Compression and Optimization**

- **Description**: Add message compression, connection pooling, and performance optimizations for WebSocket communication.
- **Implementation**: Implement per-message compression, optimize message serialization, and add connection management.
- **Goal**: Optimize WebSocket performance and reduce bandwidth usage.
- **Benefit**: Shows understanding of WebSocket performance optimization and efficient real-time communication.

Note: unless the goal specifies or disallows a specific framework you are free to replace the framework used in the original implementation with a different framework.
