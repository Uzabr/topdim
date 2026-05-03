# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS build

WORKDIR /app

ARG APP_PATH
ARG VITE_API_URL
ARG VITE_API_BASE_URL

RUN test -n "$APP_PATH"

COPY ${APP_PATH}/package*.json ./
RUN npm ci

COPY ${APP_PATH}/ ./
RUN VITE_API_URL="${VITE_API_URL}" \
    VITE_API_BASE_URL="${VITE_API_BASE_URL:-$VITE_API_URL}" \
    npm run build

FROM nginx:1.27-alpine

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
