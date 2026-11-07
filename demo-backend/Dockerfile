FROM node:20-alpine

WORKDIR /app

# Copy application files
COPY package.json server.js ./

# Default environment variables (overridden by Render and docker-compose)
ENV PORT=8080
ENV SERVER_NAME=backend-instance

EXPOSE ${PORT}

USER node

CMD ["node", "server.js"]
