# MESUT Social API Worker — TikTok + Instagram

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

## 3. Configure Instagram (optional until Meta credentials are approved)

Create a Meta app with Instagram Graph API / Facebook Login. Add the exact callback URL `https://YOUR-WORKER.workers.dev/instagram/oauth/callback` to Valid OAuth Redirect URIs. The supported first path requires a Business or Creator Instagram account linked to a Facebook Page.

Set the Worker configuration:

```bash
npx wrangler secret put META_APP_ID
npx wrangler secret put META_APP_SECRET
npx wrangler secret put META_REDIRECT_URI
npx wrangler secret put META_GRAPH_API_VERSION
npx wrangler secret put MEDIA_PUBLIC_BASE_URL
```

Use `v26.0` for `META_GRAPH_API_VERSION` unless your Meta dashboard/docs specify a newer supported version. Reuse the existing `TOKEN_ENCRYPTION_KEY` secret; never put it in the app.

Create an R2 bucket and attach it to the Worker by adding this to `wrangler.toml` (replace the bucket name if needed):

```toml
[[r2_buckets]]
binding = "MEDIA_BUCKET"
bucket_name = "mesut-instagram-media"
```

Set `MEDIA_PUBLIC_BASE_URL` to an HTTPS custom domain mapped to that bucket. The domain must serve uploaded objects publicly to Meta; do not expose the bucket listing. Add an R2 lifecycle rule to delete objects under `instagram/` after 1 day as a safety net. The Worker currently accepts MP4 uploads up to 95 MB.

The Meta permissions requested by the Facebook Login flow are `pages_show_list`, `pages_read_engagement`, `instagram_basic`, and `instagram_content_publish`. Meta App Review / Advanced Access may be required for users outside app roles.

## 4. Deploy

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
- `POST /oauth/disconnect` — disconnect the current TikTok app session and account token.
- `GET /instagram/oauth/start` — begin Meta OAuth with a one-time state value.
- `GET /instagram/oauth/callback` — exchange the Meta code, discover the linked professional account, and return a short-lived app handoff code.
- `POST /instagram/session/exchange` — exchange the one-time handoff for an opaque app session.
- `POST /instagram/me` — return the connected Instagram username.
- `PUT /instagram/media/upload` — stream an MP4 into the R2 bucket (95 MB maximum).
- `POST /instagram/publish/container` — create a Reel container from the owned R2 object.
- `POST /instagram/publish/status` — check container processing state.
- `POST /instagram/publish/finalize` — publish a FINISHED Reel and delete the temporary R2 object.
- `POST /instagram/disconnect` — revoke the local app session and remove the encrypted account record.

Authenticated endpoints require `Authorization: Bearer <session_token>`. Instagram uses its own session token, separate from TikTok.

## Important limitations

- TikTok must approve the app and the `video.publish` scope before public direct posting is available. Unaudited clients are limited to private posts.
- Android must send the video bytes to the exact `upload_url` returned by TikTok. The Worker does not proxy large video files.
- This Worker does not schedule posts itself. Scheduling is an app-side feature and will only execute after the user has explicitly configured and authorized the official TikTok integration.
- Use HTTPS for the registered redirect URI, Worker URL, and R2 public media domain.
- Instagram Reels are implemented for MP4 video only in this app flow; image posts, carousels, and Stories are not exposed by the current UI.
- Meta must be able to fetch the R2 media URL without authentication. Keep the URL unpredictable and short-lived, and configure lifecycle deletion for abandoned uploads.
- The app waits up to three minutes for Meta to finish processing a Reel; it does not claim success before `FINISHED` and `media_publish` return successfully.
