# ===============================
# Stage 1: Build Angular frontend
# ===============================
FROM node:20 AS build

WORKDIR /app

# Copy package.json and package-lock.json first (better caching)
COPY package*.json ./

# Install Angular CLI + project dependencies
RUN npm install -g @angular/cli@20 \
        && npm install --legacy-peer-deps \
        && npm install @ctrl/ngx-emoji-mart


# Copy source code
COPY . .

# Ensure polyfills are copied (forces rebuild if changed)
COPY src/polyfills.ts ./src/polyfills.ts

# Build Angular app for production (force baseHref to "/")
RUN npm run build -- --configuration production --base-href=/

# ===============================
# Stage 2: Serve via Nginx
# ===============================
FROM nginx:alpine

# Remove default nginx site
RUN rm -rf /usr/share/nginx/html/*

# Copy built Angular dist (⚠ adjust folder name if your dist is different)
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# Copy custom Nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon of]()
