# GitHub App Setup Guide

## Overview

devtown authenticates with GitHub via a GitHub App, which provides:
- Higher rate limits (5000 req/hr per installation vs 60 unauthenticated)
- Bot identity (`devtown[bot]`) on PR comments and check runs
- Fine-grained permissions scoped to specific repos
- No personal token expiry or rotation needed

## Option A: One-Click Creation via Manifest

1. Open the manifest file at `github/src/main/resources/github-app-manifest.json`
   and replace `REPLACE_WITH_YOUR_DOMAIN` with your actual domain
   (e.g., `devtown.example.com`).

2. Navigate to `https://github.com/settings/apps/new` and paste the
   manifest content, or use the GitHub manifest flow URL.

3. GitHub will show the App configuration. Click **Create GitHub App**.

4. GitHub generates the App ID and private key automatically. Download
   the private key file (`.pem`) — this is shown only once.

5. Note the **App ID** shown on the App settings page.

## Option B: Manual Creation

1. Go to **Settings > Developer settings > GitHub Apps > New GitHub App**

2. Fill in:
   - **App name:** `devtown` (or `devtown-<org>` if name is taken)
   - **Homepage URL:** `https://github.com/casehubio/devtown`
   - **Webhook URL:** `https://<your-domain>/api/github/receive`
   - **Webhook secret:** generate a secure random string

3. Set permissions:

   | Permission | Access |
   |-----------|--------|
   | Pull requests | Read & write |
   | Checks | Read & write |
   | Contents | Read |
   | Metadata | Read |

4. Subscribe to events:
   - `pull_request`
   - `pull_request_review`
   - `check_suite`
   - `check_run`

5. Set **Where can this GitHub App be installed?** to
   **Only on this account** (unless multi-org).

6. Click **Create GitHub App**.

7. On the App settings page, click **Generate a private key**.
   Save the downloaded `.pem` file securely.

## Installing on a Repository

1. Go to the App settings page > **Install App** (left sidebar)
2. Select the organization or account
3. Choose **Only select repositories** and pick the repos devtown manages
4. Click **Install**
5. Note the **Installation ID** from the URL:
   `https://github.com/settings/installations/<INSTALLATION_ID>`

## Configuring devtown

Set three environment variables:

```bash
export GITHUB_APP_ID=<app-id-from-settings-page>
export GITHUB_APP_PRIVATE_KEY_PATH=/path/to/your-app.private-key.pem
export GITHUB_INSTALLATION_ID=<installation-id-from-url>
```

For Kubernetes, mount the private key as a secret volume:

```yaml
volumes:
  - name: github-app-key
    secret:
      secretName: devtown-github-app-key
containers:
  - env:
      - name: GITHUB_APP_ID
        value: "123456"
      - name: GITHUB_APP_PRIVATE_KEY_PATH
        value: /secrets/github/private-key.pem
      - name: GITHUB_INSTALLATION_ID
        value: "78901234"
    volumeMounts:
      - name: github-app-key
        mountPath: /secrets/github
        readOnly: true
```

## PAT Fallback (Dev Mode)

When App config is not set, devtown falls back to a Personal Access Token:

```bash
export GITHUB_TOKEN=ghp_your_personal_access_token
```

This is suitable for local development. The PAT user identity appears on
API calls instead of `devtown[bot]`.

If neither App config nor PAT is set, API calls go out unauthenticated
(rate-limited to 60 requests/hour). This is acceptable when using NoOp
service stubs in dev mode.

## Verifying the Integration

After starting devtown with App config:

1. Check startup logs for: `Refreshing GitHub installation token for installation <id>`
2. The first webhook delivery should succeed without auth errors
3. Check run and PR comment operations should show `devtown[bot]` as the author

If you see `401 Unauthorized` errors:
- Verify the App ID matches the settings page
- Verify the private key file is readable and in PEM format
- Verify the installation ID (check the installations URL)
- Ensure the App has the required permissions on the target repo
