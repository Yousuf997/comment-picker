# Login helper

A stateless Cloudflare Worker that exchanges an Instagram login code for a long-lived token. It exists only because the Meta app secret must never ship inside the APK. It keeps no database, writes no logs, and returns nothing but the token.

## Endpoints

| Route | Purpose |
|---|---|
| `POST /v1/token` | Body `{"code": "...", "integrityToken": "..."}`. Exchanges the code, then the short-lived token for a long-lived one. Returns `{access_token, expires_in, user_id}`. Errors: `bad_request`, `invalid_code` (400), `rate_limited` (429), `exchange_failed` (502), `integrity_unavailable` (503). |
| `GET /.well-known/assetlinks.json` | Digital Asset Links, so Android verifies `/ig/callback` as an App Link. |
| `GET /ig/callback` | Fallback page if the App Link doesn't open the app. The code stays in the browser; the server never reads the query. |

Production refuses token exchanges (`503 integrity_unavailable`) until Play Integrity verification is added (build plan task M-15). Staging sets `INTEGRITY_ENFORCE = "false"` so debug builds can sign in.

## Develop

```sh
npm ci
npm run check   # type checks, tests, dry-run bundle
```

## Deploy

Needs the F-01 setup: a Cloudflare account, a Meta app (Instagram API with Instagram Login), and the app's signing certificate fingerprints.

1. Replace every `REPLACE_WITH_...` value in `wrangler.toml` (Meta app ID, hosts, certificate SHA-256 fingerprints).
2. Log in: `npx wrangler login`.
3. Set the secret per environment (never commit it):
   ```sh
   npx wrangler secret put IG_APP_SECRET --env staging
   npx wrangler secret put IG_APP_SECRET --env production
   ```
4. Deploy staging: `npm run deploy:staging`, then sign in with a debug build.
5. In the Meta app settings, add `https://<auth host>/ig/callback` as the OAuth redirect URI.
6. Check the App Link on a device: `adb shell pm verify-app-links --re-verify app.giveaway`.
7. Deploy production: `npm run deploy:production`. Pin the auth host's CA intermediates in the app with a backup pin (build plan section 5, step 7).

Logs, traces and Logpush stay off (`[observability] enabled = false`). Don't enable `wrangler tail` on production.
