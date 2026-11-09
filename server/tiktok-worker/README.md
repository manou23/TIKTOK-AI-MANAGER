# MESUT TikTok API Worker

This Cloudflare Worker keeps TikTok client secrets and refresh tokens off the Android APK. It exchanges the Login Kit authorization code, stores TikTok tokens encrypted in Cloudflare KV, refreshes access tokens, and proxies creator-info, publish initialization, and publish-status requests.

## 1. Create the KV namespace

From this directory:

```bash
npx wrangler kv namespace create MESUT_TIKTOK_TOKENS
```

Copy the returned namespace ID into `wrangler.toml` in place of `REPLACE_WITH_YOUR_CLOUDFLARE_KV_NAMESPACE_ID`.

## 2. Configure secrets

Register an Android app in TikTok for Developers. Add Login Kit and Content Posting API, configure the Android package/signing fingerprints, and register the exact redirect URI used by the app.

Then set the Worker secrets:

```bash
npx wrangler secret put TIKTOK_CLIENT_KEY
npx wrangler secret put TIKTOK_CLIENT_SECRET
npx wrangler secret put TIKTOK_REDIRECT_URI
npx wrangler secret put TOKEN_ENCRYPTION_KEY
```

Generate the encryption key locally with:

```bash
openssl rand -hex 32
```

Never put the TikTok client secret, encryption key, access token, or refresh token in the Android app or Git repository.

## 3. Deploy

```bash
npm install
npm run deploy
```

Check the deployment:

```bash
curl https://YOUR-WORKER.workers.dev/health
```

## Endpoints

- `POST /oauth/exchange` — exchange an authorization code and PKCE verifier; returns an opaque app session token, not TikTok refresh credentials.
- `POST /creator-info` — fetch the creator's current posting options.
- `POST /publish/init` — validate creator privacy options and initialize a TikTok video upload.
- `POST /publish/status` — check the TikTok publishing status.
- `POST /videos` — retrieve the latest video list and available engagement metrics (requires `video.list`).
- `POST /oauth/disconnect` — disconnect the current app session and account token.

Authenticated endpoints require `Authorization: Bearer <session_token>`.

## Important limitations

- TikTok must approve the app and the `video.publish` scope before public direct posting is available. Unaudited clients are limited to private posts.
- Android must send the video bytes to the exact `upload_url` returned by TikTok. The Worker does not proxy large video files.
- This Worker does not schedule posts itself. Scheduling is an app-side feature and will only execute after the user has explicitly configured and authorized the official TikTok integration.
- Use HTTPS for the registered redirect URI and Worker URL.
