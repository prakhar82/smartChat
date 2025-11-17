###############################################################################
# SmartChat :: Frontend (Angular) — Production Dockerfile
# - Multi-stage
# - Reproducible npm ci
# - Production build output served by NGINX
###############################################################################
# Stage 1: Build
###############################################################################
FROM node:20-alpine AS build

LABEL maintainer="SmartChat Frontend Team <frontend@smartchat.app>"

ARG NODE_ENV=production
ENV NODE_ENV=${NODE_ENV}
WORKDIR /app

# Copy dependency manifests first for layer caching
COPY package*.json ./

# Install dependencies (lockfile respected)
RUN npm ci --legacy-peer-deps \
  && npm cache clean --force

# Copy source after install for caching benefits
COPY . .

# Build: explicit project and production configuration
# Ensure `--project=frontend` matches your angular.json project name
RUN npm run build -- --configuration=production --project=frontend

###############################################################################
# Stage 2: Serve with NGINX
###############################################################################
FROM nginx:alpine

LABEL maintainer="SmartChat Frontend Team <frontend@smartchat.app>"

# Install small tools for health checks/logging (kept minimal)
RUN apk add --no-cache curl

# Timezone (optional)
ENV TZ=Asia/Kolkata

# Remove default content and copy built dist
RUN rm -rf /usr/share/nginx/html/*
WORKDIR /usr/share/nginx/html
COPY --from=build /app/dist/frontend/browser/ ./

# Add nginx config (copy your provided nginx.conf next to this Dockerfile)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Stream logs to container logs
RUN ln -sf /dev/stdout /var/log/nginx/access.log \
 && ln -sf /dev/stderr /var/log/nginx/error.log

VOLUME ["/var/log/nginx"]

# Healthcheck: nginx will serve /nginx-health (see nginx.conf)
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fsS http://localhost/nginx-health || exit 1

EXPOSE 80

# Run as nginx default user (no change needed for official image)
CMD ["nginx", "-g", "daemon off;"]
