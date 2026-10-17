#!/usr/bin/env bash
set -euo pipefail

PORT="${1:-8080}"

if ! command -v cloudflared &>/dev/null; then
    echo "cloudflared not found. Install with: brew install cloudflared"
    exit 1
fi

LOGFILE=$(mktemp /tmp/cloudflared-XXXXXX.log)

cleanup() {
    if [ -n "${CF_PID:-}" ]; then
        kill "$CF_PID" 2>/dev/null || true
        wait "$CF_PID" 2>/dev/null || true
    fi
    rm -f "$LOGFILE"
}
trap cleanup EXIT INT TERM

echo "Starting cloudflared tunnel to localhost:$PORT..."
cloudflared tunnel --url "http://localhost:$PORT" 2>"$LOGFILE" &
CF_PID=$!

TIMEOUT=15
ELAPSED=0
TUNNEL_URL=""
while [ $ELAPSED -lt $TIMEOUT ]; do
    TUNNEL_URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOGFILE" 2>/dev/null | head -1 || true)
    if [ -n "$TUNNEL_URL" ]; then
        break
    fi
    sleep 1
    ELAPSED=$((ELAPSED + 1))
done

if [ -z "$TUNNEL_URL" ]; then
    echo "Failed to get tunnel URL within ${TIMEOUT}s. Log:"
    cat "$LOGFILE"
    exit 1
fi

echo ""
echo "Tunnel active:"
echo "  Public URL:  $TUNNEL_URL"
echo "  Webhook URL: $TUNNEL_URL/api/github/receive"
echo ""
echo "Set in your environment or .env:"
echo "  export DEVTOWN_PUBLIC_URL=$TUNNEL_URL"
echo ""
echo "Press Ctrl-C to stop the tunnel."

wait "$CF_PID"
