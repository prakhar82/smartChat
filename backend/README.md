# 📡 SmartChat Backend + Frontend

A **Dockerized full-stack chat application** built with:

- **Spring Boot 3.5.5 (Java 17)** — API layer
- **PostgreSQL 16** — Relational data (users, auth)
- **MongoDB 7** — Messages & chat history
- **Redis 7** — Caching & session management
- **Angular/React** — Frontend (served at port 80)
- **Flyway** — Database migrations

---

## 🛠️ Setup

### 1. Clone Repository
```bash
git clone https://github.com/your-org/smartchat.git
cd smartchat
```
### 2. Configure Environment Variables
Create a `.env` file in the root directory with the following content:
```env
PROFILE=dev
API_PORT=8080
POSTGRES_DB=smartchat
POSTGRES_USER=smartchat
POSTGRES_PASSWORD=smartchat_pw
POSTGRES_PORT=5432
MONGO_INITDB_ROOT_USERNAME=root
MONGO_INITDB_ROOT_PASSWORD=change_me
MONGO_DB=smartchat
MONGO_PORT=27017
REDIS_PORT=6379
CORS_ORIGINS="http://localhost:4200,http://localhost"
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION_MS=3600000
```


### 3. Build & Run

#### This is often used during development to reset the environment completely.
```bash
docker compose down -v --rmi all
```

```bash
docker-compose up --build
```
#### api logs
```bash
docker-compose logs -f api
```


### 4. Access the Application
- **Frontend**: [http://localhost](http://localhost)
- **API**: [http://localhost:8080/api](http://localhost:8080/api)
- **PostgreSQL**: `localhost:5432` (DB: `smartchat`,

## 🐳 Services    
| Service    | Port  | Description            |
| ---------- | ----- | ---------------------- |
| `api`      | 8080  | Spring Boot backend    |
| `web`      | 80    | Angular/React frontend |
| `postgres` | 5432  | PostgreSQL database    |
| `mongo`    | 27017 | MongoDB database       |
| `redis`    | 6379  | Redis cache            |
| `adminer`   | 8081  | Database admin tool    |
| `mongo-express` | 8082 | MongoDB admin tool |
| `redis-commander` | 8083 | Redis admin tool |
### 5. Admin Tools
- **Adminer (PostgreSQL)**: [http://localhost:8081](http://localhost:8081)
- **Mongo Express (MongoDB)**: [http://localhost:8082](http://localhost:8082)
- **Redis Commander (Redis)**: [http://localhost:8083](http://localhost:8083)

### 6.  ⚡ Testing
#### Health Check
```bash
curl http://localhost:8080/api/health
```
### 7. Register
```bash
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type:
application/json" -d '{"username":"alice","email":"alice@example.com","password":"password123"}'
``` 

#### 8. Login
Use Postman or curl to test API endpoints. Example:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'
``` 
#### 9. Send Message
```bash
curl -X POST http://localhost:8080/api/messages \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"receiverId":2,"content":"Hello Bob!"}'
```
### 10. View Messages
```bash
curl -X GET http://localhost:8080/api/messages/2 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```
## 📂 SmartChat.postman_collection.json
Import the provided Postman collection to test all API endpoints easily.
```bash
{
  "info": {
    "name": "SmartChat API",
    "_postman_id": "b4b7a770-88c6-4cb5-9d10-123456789abc",
    "description": "Postman collection for SmartChat Backend APIs",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Health Check",
      "request": {
        "method": "GET",
        "header": [],
        "url": {
          "raw": "http://localhost:8080/actuator/health",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["actuator", "health"]
        }
      }
    },
    {
      "name": "Auth - Register",
      "request": {
        "method": "POST",
        "header": [{ "key": "Content-Type", "value": "application/json" }],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"username\": \"alice\",\n  \"email\": \"alice@example.com\",\n  \"password\": \"password123\"\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/auth/register",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "auth", "register"]
        }
      }
    },
    {
      "name": "Auth - Login",
      "request": {
        "method": "POST",
        "header": [{ "key": "Content-Type", "value": "application/json" }],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"email\": \"alice@example.com\",\n  \"password\": \"password123\"\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/auth/login",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "auth", "login"]
        }
      }
    },
    {
      "name": "Messages - Send",
      "request": {
        "method": "POST",
        "header": [
          { "key": "Content-Type", "value": "application/json" },
          { "key": "Authorization", "value": "Bearer {{token}}" }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"receiverId\": 2,\n  \"content\": \"Hello Bob!\"\n}"
        },
        "url": {
          "raw": "http://localhost:8080/api/messages",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "messages"]
        }
      }
    },
    {
      "name": "Messages - Fetch",
      "request": {
        "method": "GET",
        "header": [{ "key": "Authorization", "value": "Bearer {{token}}" }],
        "url": {
          "raw": "http://localhost:8080/api/messages/2",
          "protocol": "http",
          "host": ["localhost"],
          "port": "8080",
          "path": ["api", "messages", "2"]
        }
      }
    }
  ],
  "variable": [
    {
      "key": "token",
      "value": ""
    }
  ]
}
```
## 📂 SmartChat.postman_environment.json
Import the provided Postman environment
```bash
{
  "id": "9a3b7d10-5f72-4c5a-b123-abcdef123456",
  "name": "SmartChat Local",
  "values": [
    {
      "key": "baseUrl",
      "value": "http://localhost:8080",
      "enabled": true
    },
    {
      "key": "token",
      "value": "",
      "enabled": true
    },
    {
      "key": "apiPort",
      "value": "8080",
      "enabled": true
    },
    {
      "key": "profile",
      "value": "dev",
      "enabled": true
    }
  ],
  "_postman_variable_scope": "environment",
  "_postman_exported_at": "2025-09-09T18:30:00Z",
  "_postman_exported_using": "Postman/11.0.0"
}
```


## 🗂️ Project Structure
```
smartchat/
├── backend/                # Spring Boot backend
│   ├── src/main/
│   │   ├── java/com/smartchat/ # Java source code  
│   │   └── resources/          # Application properties, DB migrations
│   └── Dockerfile              # Dockerfile for backend
├── frontend/               # Angular/React frontend        
│   ├── src/                 # Frontend source code
│   └── Dockerfile           # Dockerfile for frontend
├── docker-compose.yml      # Docker Compose configuration
└── .env                    # Environment variables
```
## 📊 Architecture Diagram
```plaintext
+-------------------+        +-------------------+
|   Frontend (80)   | <----> |   Backend (8080)   |
+-------------------+        +-------------------+
                                 |
                                 v
                        +-------------------+
                        |   PostgreSQL (5432)|
                        +-------------------+
                                 |
                                 v
                        +-------------------+
                        |    MongoDB (27017) |
                        +-------------------+
                                 |
                                 v 
                        +-------------------+
                        |     Redis (6379)   |
                        +-------------------+
```     
## Class Diagram (UML)
```plaintext
+-------------------+         +---------------------+
| User              |         | Message             |
|-------------------|         |---------------------|
| id: Long          | 1    * | id: Long            |
| username: String  |--------| senderId: Long      |
| email: String     |        | receiverId: Long    |
| password: String  |        | content: String     |
+-------------------+        | timestamp: DateTime |
                             +---------------------+

+-------------------+         +---------------------+
| ChatRoom          |         | RedisCache          |
|-------------------|         |---------------------|
| id: Long          | 1    * | key: String         |
| name: String      |--------| value: Object       |
| members: List<User>         | ttl: Long          |
+-------------------+         +---------------------+

+-------------------+
| AuthService       |
|-------------------|
| login()           |
| register()        |
| validateToken()   |
+-------------------+
+-------------------+
| MessageService    |
|-------------------|
| sendMessage()     |
| getMessages()     |
+-------------------+
+-------------------+
| UserService       |
|-------------------|
| createUser()      |
| getUserById()     |
| getAllUsers()     |
+-------------------+
+-------------------+
| ChatRoomService   |
|-------------------|
| createChatRoom()  |
| getChatRooms()    |
+-------------------+
+-------------------+
| RedisService      |
|-------------------|
| cacheData()       |
| getCachedData()   |
+-------------------+
+-------------------+
| JwtUtil           |
|-------------------|
| generateToken()   |
| validateToken()   |
+-------------------+  
 
``` 
## 📌 Next Steps
- Add WebSocket for real-time chat.
- Add integration tests.
- Deploy with Kubernetes or Docker Swarm.
- Implement user roles and permissions.
- Add file sharing capabilities.
- Implement message search functionality.
- Add notifications (email, push).
- Implement user presence (online/offline status).
- Add typing indicators.
- Implement group chats.
- Add message reactions (likes, emojis).
- Implement message editing and deletion.
- Add user profile management.
- Implement read receipts.
- Add localization and internationalization.
- Integrate with third-party services (e.g., OAuth, social media).
- Implement analytics and reporting.
- Add admin dashboard for managing users and chats.
- Implement backup and restore functionality for databases.
- Enhance security (e.g., rate limiting, IP blocking).
- Optimize performance (e.g., indexing, query optimization).
- Implement CI/CD pipeline for automated testing and deployment.
- Document API with Swagger/OpenAPI.
- Add unit tests for frontend components.
- Implement dark mode for the frontend.
- Add accessibility features to the frontend.
- Integrate with a CDN for faster asset delivery.
- Implement caching strategies for static assets.
- Add a mobile app version using React Native or Flutter.
- Implement end-to-end encryption for messages.
- Add a search feature for users and messages.
- Implement a friend system (add/remove friends).
- Add a status message feature for users.
- Implement a blocking feature for users.
- Add a message forwarding feature.
- Implement a message scheduling feature.
- Add a message draft feature.
- Implement a message quoting feature.
- Add a message pinning feature.
- Implement a message archiving feature.
- Add a message filtering feature (e.g., by date, sender).
- Implement a message sorting feature (e.g., by date, sender).
- Add a message export feature (e.g., to PDF, CSV).
- Implement a message import feature (e.g., from other chat apps).
- Add a message translation feature (e.g., Google Translate API).
- Implement a message summarization feature (e.g., using NLP).
- Add a message sentiment analysis feature (e.g., using NLP).
- Implement a message topic detection feature (e.g., using NLP).
- Add a message keyword highlighting feature.
- Implement a message threading feature.
- Add a message bookmarking feature.
- Implement a message notification settings feature.
- Add a message auto-deletion feature (e.g., after a certain time).
- Implement a message backup feature (e.g., to cloud storage).
- Add a message recovery feature (e.g., from backups).
- Implement a message analytics feature (e.g., most active users, busiest times).
- Add a message moderation feature (e.g., report inappropriate content).
- Implement a message filtering feature (e.g., spam detection).
- Add a message AI assistant feature (e.g., chatbots).

    
-- -

## 🔧 Customization
- Modify the `.env` file to change configurations.
- Update `docker-compose.yml` to add/remove services.
- Change frontend code in the `frontend/` directory.
- Update backend code in the `backend/` directory.
- Use Flyway for database migrations located in `src/main/resources/db/migration`.
- Ensure to rebuild the Docker images after making changes.
- Use `docker-compose down` to stop and remove containers when done.
- Check logs with `docker-compose logs -f` for debugging.
- For production, consider using a reverse proxy like Nginx and secure your environment variables.
- Implement SSL/TLS for secure communication.
- Regularly back up your databases.
- Monitor application performance and health.
- Scale services as needed based on load.
- Keep dependencies up to date for security and performance improvements.
- Document any changes made for future reference.
- Engage with the community for support and contributions.
- Follow best practices for coding, security, and deployment.
- Test thoroughly before deploying to production.

