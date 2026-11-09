const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const GRAPH_DEFAULT_VERSION = "v26.0";
const SESSION_TTL = 60 * 60 * 24 * 60;
const UPLOAD_MAX_BYTES = 95 * 1024 * 1024;
const APP_CALLBACK = "mesut://instagram/callback";

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...JSON_HEADERS, "cache-control": "no-store" }
  });
}

function redirectToApp(query) {
  const target = new URL(APP_CALLBACK);
  for (const [key, value] of Object.entries(query)) target.searchParams.set(key, value);
  return new Response(null, {
    status: 302,
    headers: { location: target.toString(), "cache-control": "no-store" }
  });
}

function randomToken() {
  return Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, "0")).join("");
}

async function hash(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

function bytesFromHex(hex) {
  if (!/^[0-9a-f]{64}$/i.test(hex || "")) throw new Error("TOKEN_ENCRYPTION_KEY must contain 64 hexadecimal characters");
  return new Uint8Array(hex.match(/.{2}/g).map((part) => parseInt(part, 16)));
}

function toBase64(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value) {
  return Uint8Array.from(atob(value), (char) => char.charCodeAt(0));
}

async function encrypt(value, env) {
  const key = await crypto.subtle.importKey("raw", bytesFromHex(env.TOKEN_ENCRYPTION_KEY), "AES-GCM", false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(JSON.stringify(value)));
  return JSON.stringify({ iv: toBase64(iv), data: toBase64(new Uint8Array(data)) });
}

async function decrypt(value, env) {
  const envelope = JSON.parse(value);
  const key = await crypto.subtle.importKey("raw", bytesFromHex(env.TOKEN_ENCRYPTION_KEY), "AES-GCM", false, ["decrypt"]);
  const data = await crypto.subtle.decrypt({ name: "AES-GCM", iv: fromBase64(envelope.iv) }, key, fromBase64(envelope.data));
  return JSON.parse(new TextDecoder().decode(data));
}

function config(env) {
  const required = ["META_APP_ID", "META_APP_SECRET", "META_REDIRECT_URI", "TOKEN_ENCRYPTION_KEY"];
  const missing = required.filter((key) => !env[key]);
  if (missing.length) throw new Error("Instagram Worker configuration missing: " + missing.join(", "));
  if (!env.TOKENS) throw new Error("TOKENS KV binding is not configured");
  return {
    appId: env.META_APP_ID,
    appSecret: env.META_APP_SECRET,
    redirectUri: env.META_REDIRECT_URI,
    version: (env.META_GRAPH_API_VERSION || GRAPH_DEFAULT_VERSION).replace(/^\/?/, "")
  };
}

async function graphRequest(path, token, params = {}, method = "GET") {
  const url = new URL(`https://graph.facebook.com/${path.replace(/^\//, "")}`);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) url.searchParams.set(key, String(value));
  }
  if (token) url.searchParams.set("access_token", token);
  const response = await fetch(url, { method });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || body.error) {
    throw new Error(body.error?.message || `Meta Graph API returned HTTP ${response.status}`);
  }
  return body;
}

async function requireInstagramSession(request, env) {
  const header = request.headers.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token) throw new Error("Missing Instagram session token");
  const sessionHash = await hash(token);
  const igUserId = await env.TOKENS.get(`ig_session:${sessionHash}`);
  if (!igUserId) throw new Error("Instagram session expired. Reconnect your account.");
  const encrypted = await env.TOKENS.get(`ig_account:${igUserId}`);
  if (!encrypted) throw new Error("Instagram account session not found");
  return { igUserId, account: await decrypt(encrypted, env) };
}

async function oauthStart(request, env, url) {
  const cfg = config(env);
  const state = randomToken();
  await env.TOKENS.put(`ig_oauth_state:${await hash(state)}`, "pending", { expirationTtl: 600 });
  const auth = new URL(`https://www.facebook.com/${cfg.version}/dialog/oauth`);
  auth.searchParams.set("client_id", cfg.appId);
  auth.searchParams.set("redirect_uri", cfg.redirectUri);
  auth.searchParams.set("state", state);
  auth.searchParams.set("response_type", "code");
  auth.searchParams.set("scope", "pages_show_list,pages_read_engagement,instagram_basic,instagram_content_publish");
  return Response.redirect(auth.toString(), 302);
}

async function oauthCallback(request, env, url) {
  const cfg = config(env);
  const state = url.searchParams.get("state") || "";
  const code = url.searchParams.get("code") || "";
  const stateKey = `ig_oauth_state:${await hash(state)}`;
  const validState = state ? await env.TOKENS.get(stateKey) : null;
  if (!validState) return redirectToApp({ error: "invalid_state" });
  await env.TOKENS.delete(stateKey);
  if (!code) return redirectToApp({ error: "authorization_denied" });

  const shortToken = await graphRequest(`${cfg.version}/oauth/access_token`, null, {
    client_id: cfg.appId,
    client_secret: cfg.appSecret,
    redirect_uri: cfg.redirectUri,
    code
  });
  const longToken = await graphRequest(`${cfg.version}/oauth/access_token`, null, {
    grant_type: "fb_exchange_token",
    client_id: cfg.appId,
    client_secret: cfg.appSecret,
    fb_exchange_token: shortToken.access_token
  });
  const userToken = longToken.access_token || shortToken.access_token;
  const pages = await graphRequest(`${cfg.version}/me/accounts`, userToken, {
    fields: "id,name,access_token,instagram_business_account{id,username}"
  });
  const page = (pages.data || []).find((item) => item.instagram_business_account?.id && item.access_token);
  if (!page) return redirectToApp({ error: "no_professional_account" });

  const ig = page.instagram_business_account;
  const account = {
    ig_user_id: String(ig.id),
    username: String(ig.username || ""),
    page_id: String(page.id),
    page_access_token: page.access_token,
    connected_at: Date.now()
  };
  await env.TOKENS.put(`ig_account:${ig.id}`, await encrypt(account, env), { expirationTtl: SESSION_TTL });
  const handoff = randomToken();
  await env.TOKENS.put(`ig_handoff:${await hash(handoff)}`, String(ig.id), { expirationTtl: 180 });
  return redirectToApp({ code: handoff });
}

async function exchangeHandoff(request, env) {
  const input = await request.json().catch(() => ({}));
  const code = typeof input.code === "string" ? input.code : "";
  if (!code) return json({ ok: false, error: "One-time OAuth handoff code is required" }, 400);
  const key = `ig_handoff:${await hash(code)}`;
  const igUserId = await env.TOKENS.get(key);
  if (!igUserId) return json({ ok: false, error: "OAuth handoff code expired or already used" }, 400);
  await env.TOKENS.delete(key);
  const sessionToken = randomToken();
  await env.TOKENS.put(`ig_session:${await hash(sessionToken)}`, igUserId, { expirationTtl: SESSION_TTL });
  return json({ ok: true, session_token: sessionToken });
}

async function uploadMedia(request, env, session) {
  if (!env.MEDIA_BUCKET || !env.MEDIA_PUBLIC_BASE_URL) {
    return json({ ok: false, error: "Instagram media storage is not configured. Set the MEDIA_BUCKET R2 binding and MEDIA_PUBLIC_BASE_URL." }, 503);
  }
  const type = (request.headers.get("content-type") || "").split(";")[0].trim().toLowerCase();
  const size = Number(request.headers.get("content-length") || 0);
  if (type !== "video/mp4" || !Number.isSafeInteger(size) || size <= 0 || size > UPLOAD_MAX_BYTES) {
    return json({ ok: false, error: "Upload must be a non-empty MP4 video no larger than 95 MB." }, 400);
  }
  if (!request.body) return json({ ok: false, error: "Video body is missing." }, 400);
  const key = `instagram/${session.igUserId}/${randomToken()}.mp4`;
  await env.MEDIA_BUCKET.put(key, request.body, {
    httpMetadata: { contentType: "video/mp4", cacheControl: "private, max-age=0" },
    customMetadata: { instagramUserId: session.igUserId }
  });
  const base = String(env.MEDIA_PUBLIC_BASE_URL).replace(/\/+$/, "");
  return json({ ok: true, media_key: key, media_url: `${base}/${key}` });
}

async function createReel(request, env, session, cfg) {
  if (!env.MEDIA_BUCKET || !env.MEDIA_PUBLIC_BASE_URL) return json({ ok: false, error: "Instagram media storage is not configured." }, 503);
  const input = await request.json().catch(() => ({}));
  const key = typeof input.media_key === "string" ? input.media_key : "";
  const caption = typeof input.caption === "string" ? input.caption : "";
  if (!key.startsWith(`instagram/${session.igUserId}/`) || caption.length > 2200) {
    return json({ ok: false, error: "Invalid uploaded media key or caption." }, 400);
  }
  const object = await env.MEDIA_BUCKET.head(key);
  if (!object || object.customMetadata?.instagramUserId !== session.igUserId) {
    return json({ ok: false, error: "Uploaded video not found for this Instagram account." }, 404);
  }
  const mediaUrl = `${String(env.MEDIA_PUBLIC_BASE_URL).replace(/\/+$/, "")}/${key}`;
  const container = await graphRequest(`${cfg.version}/${session.igUserId}/media`, session.account.page_access_token, {
    media_type: "REELS",
    video_url: mediaUrl,
    caption,
    share_to_feed: "true"
  }, "POST");
  if (!container.id) throw new Error("Meta did not return a media container ID");
  return json({ ok: true, creation_id: container.id, media_key: key });
}

async function containerStatus(request, env, session, cfg) {
  const input = await request.json().catch(() => ({}));
  if (!input.creation_id) return json({ ok: false, error: "creation_id is required" }, 400);
  const status = await graphRequest(`${cfg.version}/${input.creation_id}`, session.account.page_access_token, {
    fields: "status_code,status"
  });
  return json({ ok: true, status_code: status.status_code || "", status: status.status || "" });
}

async function finalizeReel(request, env, session, cfg) {
  const input = await request.json().catch(() => ({}));
  if (!input.creation_id) return json({ ok: false, error: "creation_id is required" }, 400);
  const status = await graphRequest(`${cfg.version}/${input.creation_id}`, session.account.page_access_token, {
    fields: "status_code"
  });
  if (status.status_code !== "FINISHED") {
    return json({ ok: false, error: "Instagram media container is not ready to publish.", status_code: status.status_code || "UNKNOWN" }, 409);
  }
  const published = await graphRequest(`${cfg.version}/${session.igUserId}/media_publish`, session.account.page_access_token, {
    creation_id: input.creation_id
  }, "POST");
  if (input.media_key && env.MEDIA_BUCKET && String(input.media_key).startsWith(`instagram/${session.igUserId}/`)) {
    const object = await env.MEDIA_BUCKET.head(input.media_key);
    if (object?.customMetadata?.instagramUserId === session.igUserId) await env.MEDIA_BUCKET.delete(input.media_key);
  }
  return json({ ok: true, media_id: published.id || "" });
}

export async function handleInstagramRequest(request, env, url) {
  if (!url.pathname.startsWith("/instagram/")) return null;
  try {
    if (request.method === "GET" && url.pathname === "/instagram/oauth/start") return await oauthStart(request, env, url);
    if (request.method === "GET" && url.pathname === "/instagram/oauth/callback") return await oauthCallback(request, env, url);
    if (request.method === "POST" && url.pathname === "/instagram/session/exchange") return await exchangeHandoff(request, env);

    const cfg = config(env);
    const session = await requireInstagramSession(request, env);
    if (request.method === "POST" && url.pathname === "/instagram/me") {
      return json({ ok: true, account: { id: session.igUserId, username: session.account.username } });
    }
    if (request.method === "POST" && url.pathname === "/instagram/disconnect") {
      const header = request.headers.get("authorization") || "";
      const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
      const sessionHash = await hash(token);
      await env.TOKENS.delete(`ig_session:${sessionHash}`);
      await env.TOKENS.delete(`ig_account:${session.igUserId}`);
      return json({ ok: true });
    }
    if (request.method === "PUT" && url.pathname === "/instagram/media/upload") return await uploadMedia(request, env, session);
    if (request.method === "POST" && url.pathname === "/instagram/publish/container") return await createReel(request, env, session, cfg);
    if (request.method === "POST" && url.pathname === "/instagram/publish/status") return await containerStatus(request, env, session, cfg);
    if (request.method === "POST" && url.pathname === "/instagram/publish/finalize") return await finalizeReel(request, env, session, cfg);
    return json({ ok: false, error: "Instagram endpoint not found." }, 404);
  } catch (error) {
    return json({ ok: false, error: error?.message || "Unexpected Instagram Worker error" }, 400);
  }
}
