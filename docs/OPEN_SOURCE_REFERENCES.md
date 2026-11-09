# Open-source engineering references

This document tracks upstream projects reviewed while developing MESUT TikTok AI Manager. These projects are references, not runtime dependencies unless explicitly listed in Gradle.

## Reusable MIT-licensed references

### Auto Cut
- Repository: https://github.com/tanmoymondal1312/auto-cut
- License: MIT
- Useful areas: frame-aware cutting, background processing, progress reporting, cancellation, filmstrip thumbnails, and offline export.
- Planned use: adapt selected, compatible ideas or small code portions for future batch processing and export reliability. Preserve the upstream copyright and MIT license notice whenever code is reused.

### CheapShot-Editor / ClearCut
- Repository: https://github.com/johncreson/CheapShot-Editor
- License: MIT
- Useful areas: export verification and diagnostics, project persistence/migrations, batch export planning, audio processing, caption/subtitle utilities, and robust recovery from failed exports.
- Planned use: use its architecture and selectively reuse compatible MIT-licensed components with attribution. Do not import its full editor architecture into this lightweight creator app.

## References that require restrictions

### CapCraft for Android
- Repository: https://github.com/artcraft-android/capcraft-android
- License: AGPL-3.0-or-later
- Useful areas: timeline editing, captions, audio effects, export UI, and project model.
- Policy: do not copy or link AGPL code into this project unless the project owner explicitly chooses the AGPL obligations for the combined distribution. Use only as a high-level technical reference for now.

### ActionCut
- Repository: https://github.com/naveenneog/ActionCut
- License: no license file was found during review.
- Useful areas: modular Media3 architecture, timeline domain model, WorkManager export, undo/redo, and editor organization.
- Policy: inspect ideas and public documentation only; do not copy source code unless the repository owner adds a license granting permission.

### TikTok MCP (official API wrapper reference)
- Repository: https://github.com/IvanBBaev/tiktok-mcp
- License: MIT
- Useful areas: OAuth 2.0/PKCE handling, Content Posting API requests, chunked upload, publish-status polling, multi-account token handling, and plan-before-execute safeguards.
- Planned use: borrow the safe publishing workflow concepts and selectively reuse MIT-compatible server-side utilities with attribution. The app will still call TikTok's official APIs; this community project is not an official TikTok SDK.

## TikTok publishing integration

Use TikTok's official Content Posting API documentation:
- Android Login Kit: https://developers.tiktok.com/docs/en/login-kit-android-quickstart-v2
- Android OpenSDK: https://developers.tiktok.com/docs/en/mobile-sdk-android-quickstart
- Direct Post: https://developers.tiktok.com/docs/en/content-posting-api-reference-direct-post
- User access token management: https://developers.tiktok.com/docs/en/oauth-get-a-user-access-token
- Get Started: https://developers.tiktok.com/docs/en/content-posting-api-get-started

Direct publishing requires a registered developer app, approved scopes, user authorization, creator-info lookup, and TikTok's UX/privacy requirements. Android authorization uses TikTok's OpenSDK and PKCE; the authorization code is exchanged server-side. The client secret and refresh token must never be placed in the APK. Unaudited clients are restricted to private posts. Never automate login or simulate taps in the TikTok app as a substitute for the official API.

## Integration rules

1. Keep MESUT's existing working photo-to-video flow intact.
2. Prefer small, testable modules over importing a complete unrelated editor.
3. Preserve MIT notices for any reused MIT code and document all added dependencies.
4. Do not integrate AGPL or unlicensed source code without explicit license clearance.
5. Verify each change with GitHub Actions before describing it as build-ready.
