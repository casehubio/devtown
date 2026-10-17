# Webhook Setup Guide

How to receive live GitHub webhook events in a local devtown instance.

## Prerequisites

- devtown builds and starts (`mvn quarkus:dev -pl app`)
- A GitHub App installed on your target repo
  (see [GitHub App Setup](github-app-setup.md))
- cloudflared installed: `brew install cloudflared`

## Quick Start

Three terminals:

**Terminal 1 — start devtown:**

```bash
export GITHUB_APP_ID=<your-app-id>
export GITHUB_APP_PRIVATE_KEY_PATH=/path/to/private-key.pem
export GITHUB_INSTALLATION_ID=<your-installation-id>
export GITHUB_WEBHOOK_SECRET=<your-webhook-secret>
export DEVTOWN_PUBLIC_URL=  # set after tunnel starts

JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn quarkus:dev -pl app
```

**Terminal 2 — start tunnel:**

```bash
./scripts/tunnel.sh
# Prints: https://<random>.trycloudflare.com
```

Copy the public URL, then set it in Terminal 1 (Quarkus dev mode
picks up env var changes on restart):

```bash
# In Terminal 1, press 's' to restart, or restart with:
export DEVTOWN_PUBLIC_URL=https://<random>.trycloudflare.com
```

**Terminal 3 — update GitHub App webhook URL:**

Go to your GitHub App settings > General > Webhook URL, set it to:

```
https://<random>.trycloudflare.com/api/github/receive
```

Set the **Webhook secret** to match `GITHUB_WEBHOOK_SECRET`.
Enable the **Active** checkbox.

## Verify Delivery

1. Open a PR on the target repo (or push to an existing PR)
2. Go to your GitHub App > Advanced > Recent Deliveries
3. You should see a `pull_request` event with a `200` response
4. devtown logs should show the webhook being processed

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GITHUB_WEBHOOK_SECRET` | Yes | Shared secret for HMAC signature verification |
| `GITHUB_APP_ID` | Yes | GitHub App ID from settings page |
| `GITHUB_APP_PRIVATE_KEY_PATH` | Yes | Path to the App's private key `.pem` file |
| `GITHUB_INSTALLATION_ID` | Yes | Installation ID from the installations URL |
| `DEVTOWN_PUBLIC_URL` | No | Public URL where devtown is reachable. Set automatically by tunnel or by your deployment. When empty, `PublicEndpointResolver` returns empty. |
| `DEVTOWN_WRITE_BACK_ENABLED` | No | Set to `true` to enable GitHub write-back (check runs, PR comments). Default: `false`. |

## Alternative Tunnel Tools

The tunnel script uses cloudflared, but any tool that exposes
`localhost:8080` on a public HTTPS URL works. Set `DEVTOWN_PUBLIC_URL`
manually:

```bash
# frp
export DEVTOWN_PUBLIC_URL=https://your-frp-domain.com

# ngrok
export DEVTOWN_PUBLIC_URL=https://abc123.ngrok.io

# Any other tool
export DEVTOWN_PUBLIC_URL=https://your-public-url.com
```

## Cloud Deployment

When devtown runs on a server with a public hostname, no tunnel is
needed. Set `DEVTOWN_PUBLIC_URL` to the deployment's public URL:

```bash
export DEVTOWN_PUBLIC_URL=https://devtown.your-org.com
```

Set the GitHub App webhook URL to
`https://devtown.your-org.com/api/github/receive`.

## Troubleshooting

**401 on webhook delivery:**
- Verify `GITHUB_WEBHOOK_SECRET` matches the secret in GitHub App settings
- Check devtown logs for `invalid signature` messages
- The dev profile sets `webhook-secret=demo` — make sure your App secret matches

**Webhook delivered but no case started:**
- Check the `X-GitHub-Event` header is `pull_request`
- Check the action is `opened` (not `created`)
- Draft PRs are ignored — convert to ready-for-review first
- Check devtown logs for the response body

**Tunnel URL not working:**
- Verify cloudflared is running (`ps aux | grep cloudflared`)
- Try accessing the URL in a browser — you should see devtown's response
- Quick tunnels generate a new URL each time — update the GitHub App
  webhook URL after each restart
