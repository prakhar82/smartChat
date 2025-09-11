# ===============================
# Stage 1: Build Angular frontend
# ===============================
FROM node:20 AS build

# Set working directory inside container
WORKDIR /app

# Copy package.json and package-lock.json first (for better caching)
COPY package*.json ./

# Install Angular CLI (matching your Angular 20.x project) + deps
RUN npm install -g @angular/cli@20 \
    && npm install

# Copy source code
COPY . .

# Build Angular app for production
RUN npm run build

# ===============================
# Stage 2: Serve via Nginx
# ===============================
FROM nginx:alpine

# Remove default nginx website
RUN rm -rf /usr/share/nginx/html/*

# Copy built Angular dist from Stage 1
# ⚠️ Adjusted folder name from "smartchat-frontend" → "frontend"
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# Copy custom Nginx config (optional if you need API proxying)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
