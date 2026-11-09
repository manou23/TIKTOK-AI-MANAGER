import test from "node:test";
import assert from "node:assert/strict";
import { handleInstagramRequest } from "../src/instagram.js";

test("Instagram handler leaves unrelated routes untouched", async () => {
  const response = await handleInstagramRequest(
    new Request("https://worker.example/health", { method: "GET" }),
    {},
    new URL("https://worker.example/health")
  );
  assert.equal(response, null);
});

test("Instagram OAuth handoff rejects a missing code", async () => {
  const response = await handleInstagramRequest(
    new Request("https://worker.example/instagram/session/exchange", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({})
    }),
    { TOKENS: { get: async () => null, delete: async () => {} } },
    new URL("https://worker.example/instagram/session/exchange")
  );
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "One-time OAuth handoff code is required");
});

test("Instagram OAuth start fails closed when Meta secrets are not configured", async () => {
  const response = await handleInstagramRequest(
    new Request("https://worker.example/instagram/oauth/start", { method: "GET" }),
    { TOKENS: { put: async () => {} } },
    new URL("https://worker.example/instagram/oauth/start")
  );
  assert.equal(response.status, 400);
  assert.match((await response.json()).error, /META_APP_ID/);
});

test("Instagram protected endpoints require a bearer session", async () => {
  const response = await handleInstagramRequest(
    new Request("https://worker.example/instagram/me", { method: "POST" }),
    {
      TOKENS: { get: async () => null },
      META_APP_ID: "test-app",
      META_APP_SECRET: "test-secret",
      META_REDIRECT_URI: "https://worker.example/instagram/oauth/callback",
      TOKEN_ENCRYPTION_KEY: "0".repeat(64)
    },
    new URL("https://worker.example/instagram/me")
  );
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "Missing Instagram session token");
});
