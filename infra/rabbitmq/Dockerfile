# ============================================================
# 🐇 RabbitMQ 3.13 + STOMP + Web STOMP + Management UI + Auto Config
# ============================================================

FROM rabbitmq:3.13-management

# ------------------------------------------------------------
# 🔌 Enable necessary plugins (done once, persisted in image)
# ------------------------------------------------------------
RUN rabbitmq-plugins enable --offline \
    rabbitmq_stomp \
    rabbitmq_web_stomp \
    rabbitmq_management \
    rabbitmq_management_agent \
    rabbitmq_web_dispatch

# ------------------------------------------------------------
# ⚙️ Copy configuration and definitions
# ------------------------------------------------------------
COPY rabbitmq.conf /etc/rabbitmq/rabbitmq.conf
COPY definitions.json /etc/rabbitmq/definitions.json

# ✅ DO NOT COPY `enabled_plugins`
# The plugins are already enabled above; including this file overrides them.

# ------------------------------------------------------------
# 🧠 Environment defaults
# ------------------------------------------------------------
ENV RABBITMQ_DEFAULT_VHOST=smartchat
ENV RABBITMQ_DEFAULT_USER=smartchat_user
ENV RABBITMQ_DEFAULT_PASS=smartchat123
# Auto-load definitions (RabbitMQ 3.11+)
ENV RABBITMQ_LOAD_DEFINITIONS=/etc/rabbitmq/definitions.json

# ------------------------------------------------------------
# 🌐 Expose essential ports
# ------------------------------------------------------------
EXPOSE 5672 15672 61613 15674

# ------------------------------------------------------------
# 🚀 Run RabbitMQ
# ------------------------------------------------------------
CMD ["rabbitmq-server"]
