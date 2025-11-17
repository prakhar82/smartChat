# 🧩 SmartChat — Full Local HTTPS Setup Guide (2025.10)

**Author:** SmartChat Contributors  
**Version:** 2025.10  
**Purpose:**  
Enable full HTTPS support (TLS 1.2/1.3) for the SmartChat local stack — frontend, API, and WebSockets — using Docker Compose with NGINX and mkcert.

---

## 🪜 Table of Contents
1. [Install Prerequisites](#1-install-prerequisites)  
2. [Generate Local SSL Certificates with mkcert](#2-generate-local-ssl-certificates-with-mkcert)  
3. [Fix Windows Permissions](#3-fix-windows-permissions)  
4. [Docker Compose Configuration Changes](#4-docker-compose-configuration-changes)  
5. [NGINX Configuration Changes](#5-nginx-configuration-changes)  
6. [Verification Steps](#6-verification-steps)  
7. [Troubleshooting](#7-troubleshooting)  
8. [Optional Enhancements](#8-optional-enhancements)

---

## 1️⃣ Install Prerequisites

### 🧰 Required Tools
- Docker Desktop (with WSL2 backend)
- PowerShell (Admin)
- Chocolatey package manager
- OpenSSL (for testing)
- mkcert (for generating trusted local certificates)

### 💻 Install Chocolatey
```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force; `
[System.Net.ServicePointManager]::SecurityProtocol = 3072; `
iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))
```

### 🔑 Install mkcert and OpenSSL
```powershell
choco install mkcert openssl.light -y
```

### 🧠 Verify installation
```powershell
mkcert --version
openssl version
```

If OpenSSL isn’t found:
```powershell
setx PATH "$env:PATH;C:\Program Files\OpenSSL-Win64in"
```

---

## 2️⃣ Generate Local SSL Certificates with mkcert

### 📂 Create certificate directory
```powershell
cd C:\Prakhar\app-work\smartChat\infra
mkdir certs
cd certs
```

### 🔐 Install local CA
```powershell
mkcert -install
```

### 🏷️ Generate certificate
```powershell
mkcert localhost 127.0.0.1 ::1
```

Creates:
```
localhost+2.pem          → Certificate
localhost+2-key.pem      → Private key
```

---

## 3️⃣ Fix Windows Permissions

Grant full access to your user and Docker:

```powershell
cd C:\Prakhar\app-work\smartChat\infra\certs
icacls "localhost+2.pem" /inheritance:e /grant "DESKTOP-LMR9IJ7\smart:(F)"
icacls "localhost+2-key.pem" /inheritance:e /grant "DESKTOP-LMR9IJ7\smart:(F)"
```

If ownership errors occur:
```powershell
takeown /f "localhost+2.pem"
takeown /f "localhost+2-key.pem"
```

Verify:
```powershell
icacls "localhost+2.pem"
icacls "localhost+2-key.pem"
```

---

## 4️⃣ Docker Compose Configuration Changes

### ✅ Updated `docker-compose.yml` section
```yaml
smartchat-web:
  build:
    context: ./frontend
    dockerfile: Dockerfile
  container_name: smartchat-web
  hostname: smartchat-web
  restart: on-failure
  depends_on:
    smartchat-api:
      condition: service_healthy
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./infra/certs/localhost+2.pem:/etc/ssl/certs/localhost+2.pem:ro
    - ./infra/certs/localhost+2-key.pem:/etc/ssl/certs/localhost+2-key.pem:ro
    - ./frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro
  healthcheck:
    test: [ "CMD", "curl", "-f", "http://localhost/" ]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 20s
  networks:
    - smartchat-net
```

---

## 5️⃣ NGINX Configuration Changes

### 📜 File: `frontend/nginx.conf`
```nginx
map $http_upgrade $connection_upgrade {
  default upgrade;
  ''      close;
}

server {
  listen 443 ssl;
  http2 on;
  server_name localhost;

  ssl_certificate     /etc/ssl/certs/localhost+2.pem;
  ssl_certificate_key /etc/ssl/certs/localhost+2-key.pem;
  ssl_protocols       TLSv1.2 TLSv1.3;
  ssl_ciphers         HIGH:!aNULL:!MD5;
  ssl_prefer_server_ciphers on;

  root /usr/share/nginx/html;
  index index.html;

  location / {
    try_files $uri $uri/ /index.html;
  }

  location /api/ {
    auth_basic off;
    proxy_set_header Authorization "";
    proxy_hide_header WWW-Authenticate;

    if ($request_method = OPTIONS) {
      add_header 'Access-Control-Allow-Origin' "$http_origin" always;
      add_header 'Access-Control-Allow-Credentials' 'true' always;
      add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, PATCH, DELETE, OPTIONS' always;
      add_header 'Access-Control-Allow-Headers' 'Authorization, Content-Type, Accept, Origin, X-Requested-With' always;
      add_header 'Access-Control-Max-Age' 3600 always;
      return 204;
    }

    proxy_pass http://smartchat-api:8080/;
    proxy_http_version 1.1;
    proxy_redirect off;
    proxy_pass_request_headers on;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto https;
  }

  location /ws-chat {
    proxy_pass http://smartchat-chat:8080/ws-chat;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
  }

  location /nginx-health {
    access_log off;
    return 200 'OK';
  }

  add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
}

server {
  listen 80;
  server_name localhost;
  return 301 https://$host$request_uri;
}
```

---

## 6️⃣ Verification Steps

### ✅ Check container health
```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### ✅ Test HTTPS
```powershell
& "C:\Windows\System32\curl.exe" -vk https://localhost
```

### ✅ Browser check
Visit `https://localhost` → 🟢 Secure lock icon.

### ✅ API check
```powershell
& "C:\Windows\System32\curl.exe" -vk https://localhost/api/health
```

### ✅ WebSocket check
```js
new WebSocket("wss://localhost/ws-chat")
```

---

## 7️⃣ Troubleshooting

| Problem | Cause | Fix |
|----------|--------|-----|
| `cannot load certificate` | Missing/misnamed cert | Check paths |
| `PEM_read_bio_X509_AUX()` | Corrupt cert | Regenerate |
| `Access denied` | Permissions | Fix ACLs |
| Browser not secure | CA not installed | `mkcert -install` |
| Container restart loop | NGINX SSL error | Check logs |

---

## 8️⃣ Optional Enhancements

Mount mkcert CA dynamically:
```yaml
volumes:
  - ${USERPROFILE}\AppData\Local\mkcert:/etc/ssl/mkcert:ro
```

Automate verification:
```powershell
& "C:\Windows\System32\curl.exe" -sk https://localhost/api/health
& "C:\Windows\System32\curl.exe" -sk https://localhost/ws-chat
```

---

## 🏁 Final Summary

✅ HTTPS fully functional  
✅ mkcert CA trusted  
✅ CORS + WebSocket proxy secure  
✅ NGINX serving SPA + backend  
✅ Docker healthchecks passing  

**SmartChat now runs fully secured under HTTPS — locally and Dockerized. 🔒**
