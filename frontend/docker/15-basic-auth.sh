#!/bin/sh
# Turns on HTTP basic auth for the whole site (pages and /api) when FRONTEND_USER and
# FRONTEND_PASSWORD are set; without them the site is open (local use).
set -eu
if [ -n "${FRONTEND_USER:-}" ] && [ -n "${FRONTEND_PASSWORD:-}" ]; then
    printf '%s:{PLAIN}%s\n' "$FRONTEND_USER" "$FRONTEND_PASSWORD" > /etc/nginx/htpasswd
    chown root:nginx /etc/nginx/htpasswd
    chmod 640 /etc/nginx/htpasswd
    printf 'auth_basic "Agent Server";\nauth_basic_user_file /etc/nginx/htpasswd;\n' > /etc/nginx/auth.conf
    echo "15-basic-auth: basic auth on for user $FRONTEND_USER"
else
    : > /etc/nginx/auth.conf
    echo "15-basic-auth: no FRONTEND_USER/FRONTEND_PASSWORD, site is open"
fi
