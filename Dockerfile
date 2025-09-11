# ===============================
# Stage 1: Build Angular frontend
# ===============================
FROM node:20 AS build

# Set working directory inside container
WORKDIR /app

# Copy package.json and package-lock.json (for cached installs)
COPY package*.json ./

# Install dependencies
RUN npm install -g @angular/cli@17 \
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
COPY --from=build /app/dist/smartchat-frontend/browser /usr/share/nginx/html

# Copy custom Nginx config (optional if you need API proxying)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
# End of Dockerfile
# ===============================
# To build and run:
# docker build -t smartchat-frontend .
# docker run -d -p 80:80 smartchat-frontend
# ===============================
