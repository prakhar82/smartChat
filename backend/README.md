# 💬 SmartChat — Real-Time Messaging Platform

**Version:** 2025.10  
**Author:** SmartChat Contributors  
**License:** Apache 2.0  
**Architecture:** Microservices (Spring Boot + Angular + RabbitMQ + Redis + NGINX)  
**Deployment:** Docker Compose

---

## 🧩 Overview

**SmartChat** is a full-stack, microservices-based real-time chat platform.  
It’s designed to demonstrate **distributed event-driven communication** using:

- **Spring Boot microservices**
- **RabbitMQ** (for STOMP & message brokering)
- **Redis** (for caching and presence tracking)
- **PostgreSQL / MongoDB** (for persistent storage)
- **NGINX** (as HTTPS reverse proxy and WebSocket gateway)
- **Angular** frontend (SPA)

---

## ⚙️ System Architecture

```
                   ┌────────────────────────────┐
                   │        Angular SPA         │
                   │      (Frontend / HTTPS)    │
                   └──────────────┬─────────────┘
                                  │
                        HTTPS + WSS via NGINX
                                  │
                 ┌────────────────┴────────────────┐
                 │                                 │
      ┌──────────▼──────────┐           ┌──────────▼──────────┐
      │ SmartChat API GW    │           │ SmartChat Chat Svc  │
      │ (Spring Boot, 8080) │           │ (WebSocket + STOMP) │
      └──────────┬──────────┘           └──────────┬──────────┘
                 │                                 │
         REST / RabbitMQ STOMP Bus         Publishes/Consumes
                 │                                 │
       ┌─────────▼───────────┐           ┌─────────▼───────────┐
       │  Auth / Contact /   │           │    RabbitMQ Broker  │
       │  Discovery Services │           │   (Exchange + Queue)│
       └─────────┬───────────┘           └─────────┬───────────┘
                 │                                 │
                 ▼                                 ▼
        PostgreSQL / MongoDB               Redis (Presence Cache)
```

---

## 🏗️ Module Overview

| Module                | Purpose                                  | Exposed Port                      |
|-----------------------|------------------------------------------|-----------------------------------|
| `smartchat-discovery` | Eureka service registry                  | `8761`                            |
| `smartchat-api`       | Central API gateway (REST + aggregation) | `8080`                            |
| `smartchat-auth`      | Authentication service (JWT + OAuth2)    | `8081`                            |
| `smartchat-chat`      | WebSocket/STOMP messaging microservice   | `8082`                            |
| `smartchat-contact`   | Contact and profile management           | `8083`                            |
| `smartchat-web`       | Angular frontend served via NGINX        | `80`, `443`                       |
| `smartchat_rabbitmq`  | Message broker for chat                  | `5672`, `15672`, `15674`, `61613` |
| `smartchat_redis`     | Session + presence store                 | `6379`                            |
| `smartchat_postgres`  | Relational DB for auth/contact           | `5432`                            |
| `smartchat_mongo`     | NoSQL storage for chat history           | `27017`                           |

---

## 🛡️ SmartChat Security Architecture

The SmartChat platform uses a **hybrid security model** — combining Spring WebFlux (Reactive) for the API Gateway and
traditional Spring MVC (Servlet) for backend microservices.  
This design ensures **scalability**, **stateless JWT authentication**, and **clean modular separation** between gateway
and core services.

---

### 🚀 Security Layer Organized

---

### 🧩 Security Stack Summary

| Module                | Framework                 | Security Base                           | JWT Filter                        | Key Responsibility                                                    |
|-----------------------|---------------------------|-----------------------------------------|-----------------------------------|-----------------------------------------------------------------------|
| **smartchat-api**     | Spring WebFlux (Reactive) | `ReactiveSecurityConfigBase`            | `JwtReactiveAuthenticationFilter` | Central Gateway — routes all API traffic, validates JWTs reactively   |
| **smartchat-auth**    | Spring MVC (Servlet)      | `SecurityConfigBase`                    | `JwtAuthenticationFilter`         | Authentication & user management (JWT issuing, refresh, verification) |
| **smartchat-chat**    | Spring MVC (Servlet)      | `SecurityConfigBase`                    | `JwtAuthenticationFilter`         | Handles chat operations, validates JWT tokens                         |
| **smartchat-contact** | Spring MVC (Servlet)      | `SecurityConfigBase`                    | `JwtAuthenticationFilter`         | Contact management microservice                                       |
| **common**            | Shared                    | Contains security bases & JWT utilities | —                                 | Provides base configs, utilities, and whitelisted endpoints           |

---

### 🔐 Core Features

- **Stateless JWT Authentication** → no server sessions, purely token-based.
- **Unified CORS Control** → global configuration for frontend clients.
- **Centralized Public Endpoint List** → managed in `PublicEndpoints.java`.
- **Reactive + Servlet Coexistence** → gateway uses WebFlux, services use MVC.
- **Clean Modular Design** → security code lives in `common`, no duplication.

---

### 🧠 Quick Notes

- Update CORS origins in production under:  
  `ReactiveSecurityConfigBase` → `setAllowedOrigins(List.of("https://your-frontend.app"))`
- `JwtUtil` is shared across all modules for signing, parsing, and validating JWTs.
- Add new public routes only in `PublicEndpoints.java` (keeps consistency across services).
- Logging for security events is handled via **Logback** (`/var/log/smartchat-*/*.log`).

---

💡 *SmartChat Security Layer = Clean, Reactive, Stateless, and Centralized.*

---

## 🚀 Setup (Using Docker Compose)

### 🧰 Prerequisites

- Docker Desktop (WSL2 enabled)
- `mkcert` + local CA installed (for HTTPS)
- Generated certificates under:  
  `infra/certs/localhost+2.pem` and `infra/certs/localhost+2-key.pem`

### ▶️ Build and Start the Entire Stack

```bash
docker-compose up -d --build
```

### 📋 View Status

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

All containers should be in `(healthy)` state.

### 🌐 Access Points

| Service           | URL                                              |
|-------------------|--------------------------------------------------|
| Frontend (HTTPS)  | [https://localhost](https://localhost)           |
| API Gateway       | [https://localhost/api](https://localhost/api)   |
| Chat WebSocket    | `wss://localhost/ws-chat`                        |
| RabbitMQ Admin UI | [http://localhost:15672](http://localhost:15672) |
| Eureka Discovery  | [http://localhost:8761](http://localhost:8761)   |

---

## 💡 Chat Module (STOMP + RabbitMQ)

### 🧠 Concept

SmartChat uses **STOMP-over-WebSocket** for real-time bidirectional communication.

When a user sends a chat message:

1. The **Angular frontend** sends it via STOMP over WebSocket (`/ws-chat`).
2. The **SmartChat Chat Service** (Spring Boot) handles `/topic` and `/queue` destinations.
3. The message is published to **RabbitMQ** via the **STOMP bridge**.
4. RabbitMQ routes it to subscribed clients through its **exchange/queue bindings**.
5. The receiver’s WebSocket session receives it instantly.

### 🧱 RabbitMQ Configuration (Inside Chat Service)

- **Exchange:** `chat.exchange`
- **Queues:** `chat.message.queue`, `chat.notification.queue`
- **Routing keys:** `chat.message.*`, `chat.notification.*`
- **Protocol:** AMQP (5672) for microservices, STOMP (61613) for WebSockets.

### 📡 Spring STOMP Mapping

```java

@MessageMapping("/chat.send")
@SendTo("/topic/public")
public ChatMessage sendMessage(ChatMessage message) { ...}
```

### 🗨️ Frontend STOMP Client (Angular)

```typescript
this.stompClient = new Client({
    brokerURL: 'wss://localhost/ws-chat',
    connectHeaders: {Authorization: `Bearer ${token}`},
    debug: console.log
});
```

---

## 🐇 RabbitMQ — Diagnostics & CLI Commands

### 🧩 Check RabbitMQ Logs

```bash
docker logs -f smartchat_rabbitmq
```

### 🧭 Access RabbitMQ Admin UI

- URL: [http://localhost:15672](http://localhost:15672)
- Default credentials:
  ```
  username: guest
  password: guest
  ```

### 🧪 Inspect Queues & Exchanges

```bash
docker exec -it smartchat_rabbitmq rabbitmqctl list_queues
docker exec -it smartchat_rabbitmq rabbitmqctl list_exchanges
docker exec -it smartchat_rabbitmq rabbitmqctl list_bindings
```

### 📦 View Running Connections

```bash
docker exec -it smartchat_rabbitmq rabbitmqctl list_connections
```

### 🔍 View Consumers

```bash
docker exec -it smartchat_rabbitmq rabbitmqctl list_consumers
```

### 🚫 Purge or Delete Queues (for testing)

```bash
docker exec -it smartchat_rabbitmq rabbitmqctl purge_queue chat.message.queue
```

---

## 🔎 Docker Commands for Diagnostics

### 📊 Check Logs of Any Service

```bash
docker logs -f smartchat-chat
docker logs -f smartchat-api
```

### 🧰 Exec into Container

```bash
docker exec -it smartchat-chat sh
```

### 🧼 Clean & Rebuild Everything

```bash
docker-compose down -v
docker-compose up --build
```

---

## 🧠 SmartChat Messaging Flow Summary

| Step | Action                           | Component               |
|------|----------------------------------|-------------------------|
| 1    | User sends message via WebSocket | Frontend (STOMP)        |
| 2    | Message routed to broker         | Chat Service → RabbitMQ |
| 3    | Broker fans out messages         | RabbitMQ exchange/queue |
| 4    | Receiving user notified          | STOMP client via WSS    |
| 5    | Message persisted                | MongoDB                 |
| 6    | Delivery ACK via Redis           | Chat Service            |

---

## ✅ Health Monitoring

| Service      | Health Endpoint    |
|--------------|--------------------|
| API Gateway  | `/actuator/health` |
| Chat Service | `/actuator/health` |
| Auth Service | `/actuator/health` |

Check:

```bash
curl -sk https://localhost/api/health
```

---

## 🧾 License

Licensed under the **Apache License 2.0**.  
Copyright © 2025 SmartChat Contributors.

---

**SmartChat** — Secure, Distributed, Real-Time Communication made simple. 🔒💬🚀
