/**
 * Play Integrity verdicts (spec: Network, App hardening; plan M-15 and A6). Google decodes the token for this helper
 * with a service account that has only the Play Integrity role. Nothing is stored or logged; the only facts that leave
 * are the booleans the app needs.
 */

export interface IntegrityEnv {
  ANDROID_PACKAGE: string;
  /** Comma-separated SHA-256 fingerprints of the app signing certificates, as "AA:BB:..." hex. */
  ANDROID_CERT_SHA256: string;
  /** Service account key JSON (wrangler secret). */
  GOOGLE_SA_JSON?: string;
}

/** A verdict field the decoder returns; only what the checks read. */
export type Verdict = {
  requestDetails?: { requestPackageName?: string; nonce?: string; requestHash?: string; timestampMillis?: string };
  appIntegrity?: { appRecognitionVerdict?: string; packageName?: string; certificateSha256Digest?: string[] };
  deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
};

/** Google or the service account is unreachable or misconfigured: the check couldn't run. */
export class IntegrityUnavailable extends Error {}

/** Google refused the token itself (malformed, wrong project, expired). */
export class IntegrityRejected extends Error {}

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/playintegrity";
const encoder = new TextEncoder();

export async function decode(token: string, env: IntegrityEnv): Promise<Verdict> {
  const access = await accessToken(env);
  const url = `https://playintegrity.googleapis.com/v1/${encodeURIComponent(env.ANDROID_PACKAGE)}:decodeIntegrityToken`;
  const response = await fetch(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
    body: JSON.stringify({ integrity_token: token }),
  }).catch(() => {
    throw new IntegrityUnavailable();
  });
  if (response.status === 400) throw new IntegrityRejected();
  if (!response.ok) throw new IntegrityUnavailable();
  const json = (await response.json().catch(() => ({}))) as { tokenPayloadExternal?: Verdict };
  if (!json.tokenPayloadExternal) throw new IntegrityUnavailable();
  return json.tokenPayloadExternal;
}

/** The app is this package, signed with one of our certificates, and installed from Google Play. */
export function appGenuine(verdict: Verdict, env: IntegrityEnv): boolean {
  const app = verdict.appIntegrity ?? {};
  const ours = new Set(env.ANDROID_CERT_SHA256.split(",").flatMap((f) => digestForms(f.trim())));
  return (
    verdict.requestDetails?.requestPackageName === env.ANDROID_PACKAGE &&
    app.packageName === env.ANDROID_PACKAGE &&
    app.appRecognitionVerdict === "PLAY_RECOGNIZED" &&
    (app.certificateSha256Digest ?? []).some((d) => ours.has(d.replace(/=+$/, "")))
  );
}

/** The device passes Android's integrity checks (a genuine, certified device). */
export const deviceGenuine = (verdict: Verdict): boolean =>
  (verdict.deviceIntegrity?.deviceRecognitionVerdict ?? []).includes("MEETS_DEVICE_INTEGRITY");

/** The token was made within [maxAgeMs] of [now], allowing the same amount of clock skew. */
export function fresh(verdict: Verdict, now: number, maxAgeMs: number): boolean {
  const made = Number(verdict.requestDetails?.timestampMillis);
  return Number.isFinite(made) && Math.abs(now - made) <= maxAgeMs;
}

/** SHA-256 as unpadded base64url: the classic request's nonce for a login code. */
export async function sha256Base64Url(text: string): Promise<string> {
  return base64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(text))));
}

/** Google returns certificate digests as base64url; accept hex too, in case the format changes. */
function digestForms(colonHex: string): string[] {
  const hex = colonHex.replace(/:/g, "").toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(hex)) return [];
  const bytes = Uint8Array.from(hex.match(/../g) ?? [], (b) => parseInt(b, 16));
  return [base64Url(bytes), hex];
}

/** An OAuth access token for the service account (JWT bearer grant), fetched per request: nothing is cached. */
async function accessToken(env: IntegrityEnv): Promise<string> {
  let account: { client_email?: unknown; private_key?: unknown };
  try {
    account = JSON.parse(env.GOOGLE_SA_JSON ?? "");
  } catch {
    throw new IntegrityUnavailable();
  }
  if (typeof account.client_email !== "string" || typeof account.private_key !== "string") {
    throw new IntegrityUnavailable();
  }
  const now = Math.floor(Date.now() / 1000);
  const claims = { iss: account.client_email, scope: SCOPE, aud: TOKEN_URL, iat: now, exp: now + 300 };
  const assertion = await signJwt(claims, account.private_key);
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  }).catch(() => {
    throw new IntegrityUnavailable();
  });
  if (!response.ok) throw new IntegrityUnavailable();
  const json = (await response.json().catch(() => ({}))) as { access_token?: unknown };
  if (typeof json.access_token !== "string") throw new IntegrityUnavailable();
  return json.access_token;
}

async function signJwt(claims: object, pem: string): Promise<string> {
  const der = Uint8Array.from(atob(pem.replace(/-----[^-]+-----/g, "").replace(/\s/g, "")), (c) => c.charCodeAt(0));
  const key = await crypto.subtle
    .importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"])
    .catch(() => {
      throw new IntegrityUnavailable();
    });
  const part = (value: object) => base64Url(encoder.encode(JSON.stringify(value)));
  const unsigned = `${part({ alg: "RS256", typ: "JWT" })}.${part(claims)}`;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(unsigned));
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
