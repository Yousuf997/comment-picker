/**
 * Login helper (spec: Architecture). It exists only because the Meta app secret must never ship in the APK.
 * It keeps no database, writes no logs, and returns nothing but the token. There is deliberately no console
 * output anywhere in this worker; a test enforces it.
 */

export interface Env {
  IG_APP_ID: string;
  IG_REDIRECT_URI: string;
  ANDROID_PACKAGE: string;
  /** Comma-separated SHA-256 fingerprints of the app signing certificates (Play app signing and upload/debug). */
  ANDROID_CERT_SHA256: string;
  /** "false" only in staging. Production fails closed until Play Integrity checks land (build plan task M-15). */
  INTEGRITY_ENFORCE: string;
  IG_APP_SECRET: string;
  RATE_LIMITER: RateLimit;
}

const MAX_BODY_BYTES = 4096;
const MAX_CODE_LENGTH = 2048;

const SECURITY_HEADERS = {
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);
    switch (pathname) {
      case "/.well-known/assetlinks.json":
        return request.method === "GET" ? assetLinks(env) : methodNotAllowed("GET");
      case "/ig/callback":
        return request.method === "GET" ? callbackPage(env) : methodNotAllowed("GET");
      case "/v1/token":
        if (request.method !== "POST") return methodNotAllowed("POST");
        return (await isRateLimited(request, env)) ? error(429, "rate_limited") : exchange(request, env);
      default:
        return new Response(null, { status: 404, headers: SECURITY_HEADERS });
    }
  },
} satisfies ExportedHandler<Env>;

async function isRateLimited(request: Request, env: Env): Promise<boolean> {
  const key = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const { success } = await env.RATE_LIMITER.limit({ key });
  return !success;
}

/** Digital Asset Links, so Android verifies the https callback as an App Link that only this app can receive. */
function assetLinks(env: Env): Response {
  const fingerprints = env.ANDROID_CERT_SHA256.split(",").map((f) => f.trim()).filter(Boolean);
  const body = [
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: { namespace: "android_app", package_name: env.ANDROID_PACKAGE, sha256_cert_fingerprints: fingerprints },
    },
  ];
  return Response.json(body, { headers: { ...SECURITY_HEADERS, "Cache-Control": "public, max-age=3600" } });
}

/**
 * Shown only if the App Link did not open the app. The code stays in the browser: the page builds an intent link
 * from location.search in script, and the server never reads or echoes the query.
 */
function callbackPage(env: Env): Response {
  const pkg = JSON.stringify(env.ANDROID_PACKAGE);
  const html = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Return to the app</title></head>
<body style="font-family:sans-serif;max-width:28rem;margin:3rem auto;padding:0 1rem">
<h1>Almost done</h1><p>Open the app to finish signing in.</p>
<p><a id="open" href="#">Return to the app</a></p>
<script>
document.getElementById("open").href = "intent://" + location.host + location.pathname + location.search +
  "#Intent;scheme=https;package=" + ${pkg} + ";end";
</script></body></html>`;
  return new Response(html, {
    headers: {
      ...SECURITY_HEADERS,
      "Content-Type": "text/html; charset=utf-8",
      "Content-Security-Policy": "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'",
    },
  });
}

async function exchange(request: Request, env: Env): Promise<Response> {
  const parsed = await parseTokenRequest(request);
  if ("error" in parsed) return error(400, parsed.error);

  if (env.INTEGRITY_ENFORCE !== "false") {
    // Fail closed: production must not exchange codes for unverified apps (spec: Network). Lands with M-15.
    return error(503, "integrity_unavailable");
  }

  let shortLived: { accessToken: string; userId: string };
  try {
    shortLived = await exchangeCode(parsed.code, env);
  } catch (e) {
    return e instanceof UpstreamRejected ? error(400, "invalid_code") : error(502, "exchange_failed");
  }

  let longLived: { accessToken: string; expiresIn: number };
  try {
    longLived = await exchangeForLongLived(shortLived.accessToken, env);
  } catch {
    return error(502, "exchange_failed");
  }

  return Response.json(
    { access_token: longLived.accessToken, expires_in: longLived.expiresIn, user_id: shortLived.userId },
    { headers: SECURITY_HEADERS },
  );
}

type TokenRequest = { code: string; integrityToken: string | null };

async function parseTokenRequest(request: Request): Promise<TokenRequest | { error: string }> {
  if (!(request.headers.get("Content-Type") ?? "").startsWith("application/json")) return { error: "bad_request" };
  const declared = Number(request.headers.get("Content-Length") ?? "0");
  if (declared > MAX_BODY_BYTES) return { error: "bad_request" };
  const text = await request.text();
  if (new TextEncoder().encode(text).length > MAX_BODY_BYTES) return { error: "bad_request" };

  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    return { error: "bad_request" };
  }
  if (typeof body !== "object" || body === null) return { error: "bad_request" };
  const { code, integrityToken } = body as Record<string, unknown>;
  if (typeof code !== "string" || code.length === 0 || code.length > MAX_CODE_LENGTH || /\s/.test(code)) {
    return { error: "invalid_code" };
  }
  if (integrityToken !== undefined && typeof integrityToken !== "string") return { error: "bad_request" };
  // Instagram appends "#_" to the redirect; tolerate it if the app forwards it.
  return { code: code.replace(/#_$/, ""), integrityToken: integrityToken ?? null };
}

class UpstreamRejected extends Error {}

/** Instagram API with Instagram Login: code -> short-lived token. Confirm the response shape at build time (A3). */
async function exchangeCode(code: string, env: Env): Promise<{ accessToken: string; userId: string }> {
  const response = await fetch("https://api.instagram.com/oauth/access_token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: env.IG_APP_ID,
      client_secret: env.IG_APP_SECRET,
      grant_type: "authorization_code",
      redirect_uri: env.IG_REDIRECT_URI,
      code,
    }),
  });
  if (response.status === 400) throw new UpstreamRejected();
  if (!response.ok) throw new Error("upstream");
  const json = (await response.json()) as Record<string, unknown>;
  // Documented as either a flat object or { data: [ { ... } ] }.
  const record = (Array.isArray(json.data) ? json.data[0] : json) as Record<string, unknown> | undefined;
  const accessToken = record?.access_token;
  const userId = record?.user_id;
  if (typeof accessToken !== "string" || (typeof userId !== "string" && typeof userId !== "number")) {
    throw new Error("upstream");
  }
  return { accessToken, userId: String(userId) };
}

/** Short-lived -> long-lived token (about 60 days). The app refreshes it directly with Instagram later. */
async function exchangeForLongLived(accessToken: string, env: Env): Promise<{ accessToken: string; expiresIn: number }> {
  const url = new URL("https://graph.instagram.com/access_token");
  url.search = new URLSearchParams({
    grant_type: "ig_exchange_token",
    client_secret: env.IG_APP_SECRET,
    access_token: accessToken,
  }).toString();
  const response = await fetch(url);
  if (!response.ok) throw new Error("upstream");
  const json = (await response.json()) as Record<string, unknown>;
  if (typeof json.access_token !== "string" || typeof json.expires_in !== "number") throw new Error("upstream");
  return { accessToken: json.access_token, expiresIn: json.expires_in };
}

function error(status: number, code: string): Response {
  return Response.json({ error: code }, { status, headers: SECURITY_HEADERS });
}

function methodNotAllowed(allowed: string): Response {
  return new Response(null, { status: 405, headers: { ...SECURITY_HEADERS, Allow: allowed } });
}
