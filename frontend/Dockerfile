# --- build: static files of the playground ------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# --- runtime: nginx serves the build and proxies /api to the backend with the API key -------
FROM nginx:1.27-alpine
# nginx's entrypoint renders /etc/nginx/templates/*.template with these variables (envsubst)
ENV PORT=80 \
    BACKEND_URL=http://backend:8080 \
    AGENT_API_KEY=
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker/15-basic-auth.sh /docker-entrypoint.d/15-basic-auth.sh
COPY --from=build /app/dist /usr/share/nginx/html
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/healthz" > /dev/null || exit 1
