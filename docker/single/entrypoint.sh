#!/bin/bash
# Single image: starts the backend on 127.0.0.1:BACKEND_PORT and nginx on PORT in front of it.
# The first of the two to exit stops the container, so the platform restarts both.
set -euo pipefail

work=/tmp/nginx
mkdir -p "$work"

# The playground's proxy key: generated at each start when not given; the backend always accepts it
if [ -z "${AGENT_API_KEY:-}" ]; then
    AGENT_API_KEY=$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')
fi
AGENT_API_KEYS="${AGENT_API_KEYS:+$AGENT_API_KEYS,}$AGENT_API_KEY"
export AGENT_API_KEY AGENT_API_KEYS

# Basic auth on the playground (pages and /api) when FRONTEND_USER and FRONTEND_PASSWORD are set
if [ -n "${FRONTEND_USER:-}" ] && [ -n "${FRONTEND_PASSWORD:-}" ]; then
    printf '%s:{PLAIN}%s\n' "$FRONTEND_USER" "$FRONTEND_PASSWORD" > "$work/htpasswd"
    AUTH_REALM="Agent Server"
    echo "entrypoint: basic auth on for user $FRONTEND_USER"
else
    : > "$work/htpasswd"
    AUTH_REALM=off
    echo "entrypoint: WARNING no FRONTEND_USER/FRONTEND_PASSWORD, the playground is open to anyone who can reach it"
fi
chmod 600 "$work/htpasswd"
export PORT BACKEND_PORT AUTH_REALM

envsubst '${PORT} ${BACKEND_PORT} ${AGENT_API_KEY} ${AUTH_REALM}' \
    < /etc/agent-server/nginx.conf.template > "$work/nginx.conf"

# The backend only listens inside the container; PORT belongs to nginx
# shellcheck disable=SC2086
PORT="$BACKEND_PORT" SERVER_ADDRESS=127.0.0.1 java $JAVA_OPTS -jar /app/app.jar &
java_pid=$!
nginx -e stderr -c "$work/nginx.conf" -g 'daemon off;' &
nginx_pid=$!

trap 'kill -TERM "$java_pid" "$nginx_pid" 2>/dev/null || true' TERM INT
status=0
wait -n || status=$?
kill -TERM "$java_pid" "$nginx_pid" 2>/dev/null || true
wait || true
exit "$status"
