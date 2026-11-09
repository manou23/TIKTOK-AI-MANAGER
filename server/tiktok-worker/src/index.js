const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const SESSION_TTL = 60 * 60 * 24 * 365;

function response(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...JSON_HEADERS, "cache-control": "no-store" } });
}

function hexToBytes(hex) {
  if (!/^[0-9a-f]{64}$/i.test(hex || "")) throw new Error("TOKEN_ENCRYPTION_KEY must be 64 hexadecimal characters");
  return new Uint8Array(hex.match(/.{2}/g).map((v) => parseInt(v, 16)));
}

function toBase64(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value) {
  const binary = atob(value);
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

async function encryptionKey(env) {
  return crypto.subtle.importKey("raw", hexToBytes(env.TOKEN_ENCRYPTION_KEY), "AES-GCM", false, ["encrypt", "decrypt"]);
}

async function encryptJson(value, env) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await encryptionKey(env);
  const ciphertext = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(JSON.stringify(value)));
  return JSON.stringify({ iv: toBase64(iv), data: toBase64(new Uint8Array(ciphertext)) });
}

async function decryptJson(value, env) {
  const envelope = JSON.parse(value);
  const key = await encryptionKey(env);
  const plaintext = await crypto.subtle.decrypt({ name: "AES-GCM", iv: fromBase64(envelope.iv) }, key, fromBase64(envelope.data));
  return JSON.parse(new TextDecoder().decode(plaintext));
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

function randomToken() {
  return Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, "0")).join("");
}

async function storeAccount(openId, account, env) {
  const ttl = Math.max(60, Math.min(SESSION_TTL, account.refresh_expires_at - Math.floor(Date.now() / 1000)));
  await env.TOKENS.put(`account:${openId}`, await encryptJson(account, env), { expirationTtl: ttl });
}

async function refreshIfNeeded(openId, account, env) {
  const now = Math.floor(Date.now() / 1000);
  if (account.access_expires_at > now + 300) return account;
  if (account.refresh_expires_at <= now) throw new Error("TikTok authorization expired. Reconnect your account.");
  const form = new URLSearchParams({
    client_key: env.TIKTOK_CLIENT_KEY,
    client_secret: env.TIKTOK_CLIENT_SECRET,
    grant_type: "refresh_token",
    refresh_token: account.refresh_token
  });
  const res = await fetch("https://open.tiktokapis.com/v2/oauth/token/", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: form
  });
  const body = await res.json();
  const token = body.data || body;
  if (!res.ok || body.error || !token.access_token) throw new Error(body.error_description || body.message || "TikTok token refresh failed");
  account = {
    ...account,
    access_token: token.access_token,
    refresh_token: token.refresh_token || account.refresh_token,
    access_expires_at: now + Number(token.expires_in || 86400),
    refresh_expires_at: now + Number(token.refresh_expires_in || Math.max(0, account.refresh_expires_at - now))
  };
  await storeAccount(openId, account, env);
  return account;
}

async function requireSession(request, env) {
  const header = request.headers.get("authorization") || "";
  const sessionToken = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!sessionToken) throw new Error("Missing session token");
  const key = await sha256Hex(sessionToken);
  const openId = await env.TOKENS.get(`session:${key}`);
  if (!openId) throw new Error("Session expired. Reconnect TikTok.");
  const encrypted = await env.TOKENS.get(`account:${openId}`);
  if (!encrypted) throw new Error("TikTok account session not found");
  let account = await decryptJson(encrypted, env);
  account = await refreshIfNeeded(openId, account, env);
  return { openId, account, sessionKey: key };
}

async function tiktokPost(path, account, body) {
  const res = await fetch(`https://open.tiktokapis.com${path}`, {
    method: "POST",
    headers: { authorization: `Bearer ${account.access_token}`, "content-type": "application/json; charset=UTF-8" },
    body: JSON.stringify(body || {})
  });
  const result = await res.json();
  const apiError = result.error && result.error.code && result.error.code !== "ok";
  if (!res.ok || apiError) throw new Error(result.error?.message || result.message || `TikTok API returned HTTP ${res.status}`);
  return result.data || result;
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: {
        "access-control-allow-origin": "*",
        "access-control-allow-methods": "GET,POST,OPTIONS",
        "access-control-allow-headers": "authorization,content-type"
      }});
    }
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/health") return response({ ok: true, service: "MESUT TikTok API", version: "1.0.0" });

      if (request.method === "POST" && url.pathname === "/oauth/exchange") {
        if (!env.TOKENS || !env.TIKTOK_CLIENT_KEY || !env.TIKTOK_CLIENT_SECRET || !env.TIKTOK_REDIRECT_URI || !env.TOKEN_ENCRYPTION_KEY) {
          return response({ ok: false, error: "Worker secrets or KV binding are not configured" }, 503);
        }
        const input = await request.json();
        if (!input.code || !input.code_verifier || input.redirect_uri !== env.TIKTOK_REDIRECT_URI) {
          return response({ ok: false, error: "Missing authorization code, PKCE verifier, or matching redirect URI" }, 400);
        }
        const form = new URLSearchParams({
          client_key: env.TIKTOK_CLIENT_KEY,
          client_secret: env.TIKTOK_CLIENT_SECRET,
          code: input.code,
          grant_type: "authorization_code",
          redirect_uri: env.TIKTOK_REDIRECT_URI,
          code_verifier: input.code_verifier
        });
        const tokenRes = await fetch("https://open.tiktokapis.com/v2/oauth/token/", {
          method: "POST",
          headers: { "content-type": "application/x-www-form-urlencoded" },
          body: form
        });
        const tokenBody = await tokenRes.json();
        const token = tokenBody.data || tokenBody;
        if (!tokenRes.ok || tokenBody.error || !token.access_token || !token.open_id || !token.refresh_token) {
          return response({ ok: false, error: tokenBody.error_description || tokenBody.message || "TikTok token exchange failed" }, 400);
        }
        const now = Math.floor(Date.now() / 1000);
        const account = {
          open_id: token.open_id,
          access_token: token.access_token,
          refresh_token: token.refresh_token,
          access_expires_at: now + Number(token.expires_in || 86400),
          refresh_expires_at: now + Number(token.refresh_expires_in || SESSION_TTL)
        };
        await storeAccount(token.open_id, account, env);
        const sessionToken = randomToken();
        const sessionHash = await sha256Hex(sessionToken);
        await env.TOKENS.put(`session:${sessionHash}`, token.open_id, { expirationTtl: SESSION_TTL });
        return response({ ok: true, session_token: sessionToken, open_id: token.open_id, expires_in: Number(token.expires_in || 86400) });
      }

      if (request.method === "POST" && url.pathname === "/oauth/disconnect") {
        const session = await requireSession(request, env);
        await env.TOKENS.delete(`session:${session.sessionKey}`);
        await env.TOKENS.delete(`account:${session.openId}`);
        return response({ ok: true });
      }

      if (request.method === "POST" && url.pathname === "/creator-info") {
        const session = await requireSession(request, env);
        const data = await tiktokPost("/v2/post/publish/creator_info/query/", session.account, {});
        return response({ ok: true, creator: data });
      }

      if (request.method === "POST" && url.pathname === "/publish/init") {
        const session = await requireSession(request, env);
        const input = await request.json();
        if (!input.title || !input.privacy_level || !Number.isSafeInteger(input.video_size) || input.video_size <= 0 ||
            !Number.isSafeInteger(input.chunk_size) || input.chunk_size <= 0 ||
            !Number.isSafeInteger(input.total_chunk_count) || input.total_chunk_count <= 0) {
          return response({ ok: false, error: "Invalid title, privacy, or upload chunk metadata" }, 400);
        }
        const creator = await tiktokPost("/v2/post/publish/creator_info/query/", session.account, {});
        const allowed = creator.privacy_level_options || [];
        if (!allowed.includes(input.privacy_level)) return response({ ok: false, error: "Selected privacy level is not allowed for this creator" }, 400);
        const data = await tiktokPost("/v2/post/publish/video/init/", session.account, {
          post_info: {
            title: input.title,
            privacy_level: input.privacy_level,
            disable_duet: Boolean(input.disable_duet),
            disable_stitch: Boolean(input.disable_stitch),
            disable_comment: Boolean(input.disable_comment),
            brand_content_toggle: Boolean(input.brand_content_toggle),
            brand_organic_toggle: Boolean(input.brand_organic_toggle),
            is_aigc: Boolean(input.is_aigc)
          },
          source_info: {
            source: "FILE_UPLOAD",
            video_size: input.video_size,
            chunk_size: input.chunk_size,
            total_chunk_count: input.total_chunk_count
          }
        });
        return response({ ok: true, publish_id: data.publish_id, upload_url: data.upload_url });
      }

      if (request.method === "POST" && url.pathname === "/publish/status") {
        const session = await requireSession(request, env);
        const input = await request.json();
        if (!input.publish_id) return response({ ok: false, error: "publish_id is required" }, 400);
        const data = await tiktokPost("/v2/post/publish/status/fetch/", session.account, { publish_id: input.publish_id });
        return response({ ok: true, status: data });
      }

      return response({ ok: false, error: "Not found" }, 404);
    } catch (error) {
      return response({ ok: false, error: error?.message || "Unexpected worker error" }, 400);
    }
  }
};
