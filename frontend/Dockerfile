###############################################################################
# 🌐 SmartChat Frontend - Dockerfile (Angular + Nginx)
###############################################################################

# ===============================
# Stage 1: Build Angular frontend
# ===============================
FROM node:20 AS build

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies (fast & reproducible)
RUN npm install --legacy-peer-deps \
    && npm install -g @angular/cli@20 \
    && npm install @ctrl/ngx-emoji-mart

# Copy source code
COPY . .

# Build Angular app for production
RUN npm run build -- --configuration production --base-href=/

# ===============================
# Stage 2: Serve via Nginx
# ===============================
FROM nginx:alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

WORKDIR /usr/share/nginx/html
RUN rm -rf ./*

# Copy Angular build output
# ⚠️ Adjust folder if Angular output path changes
COPY --from=build /app/dist/frontend/browser ./

# Copy Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Healthcheck endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
  CMD curl -f http://localhost/health || exit 1

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
