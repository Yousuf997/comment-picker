import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import worker, { type Env } from "../src/index";

// The signing certificate as configured ("AA:BB:..." hex) and as Google reports it (base64url of the same bytes).
const CERT_HEX = Array.from({ length: 32 }, (_, i) => (i * 7).toString(16).padStart(2, "0").toUpperCase()).join(":");
const CERT_DIGEST = Buffer.from(CERT_HEX.replace(/:/g, ""), "hex").toString("base64url");

let serviceAccount = "";
let publicKey: CryptoKey;

beforeAll(async () => {
  const pair = (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  publicKey = pair.publicKey;
  const pkcs8 = Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64");
  const pem = `-----BEGIN PRIVATE KEY-----\n${pkcs8.match(/.{1,64}/g)!.join("\n")}\n-----END PRIVATE KEY-----\n`;
  serviceAccount = JSON.stringify({ client_email: "integrity@project.iam.gserviceaccount.com", private_key: pem });
});

afterEach(() => vi.unstubAllGlobals());

function env(overrides: Partial<Env> = {}): Env {
  return {
    IG_APP_ID: "123",
    IG_REDIRECT_URI: "https://auth.example.test/ig/callback",
    ANDROID_PACKAGE: "app.giveaway",
    ANDROID_CERT_SHA256: `${CERT_HEX}, AA:BB`,
    INTEGRITY_ENFORCE: "true",
    IG_APP_SECRET: "secret",
    GOOGLE_SA_JSON: serviceAccount,
    RATE_LIMITER: { limit: async () => ({ success: true }) } as unknown as RateLimit,
    ...overrides,
  };
}

const sha256url = (text: string) =>
  crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)).then((d) => Buffer.from(d).toString("base64url"));

type Verdict = Record<string, unknown>;

/** A decoded verdict like Google's, for a genuine Play install, with fields to override. */
function verdict(details: Record<string, unknown>, app: Record<string, unknown> = {}, device: string[] = []): Verdict {
  return {
    requestDetails: { requestPackageName: "app.giveaway", timestampMillis: String(Date.now()), ...details },
    appIntegrity: {
      appRecognitionVerdict: "PLAY_RECOGNIZED",
      packageName: "app.giveaway",
      certificateSha256Digest: [CERT_DIGEST],
      versionCode: "1",
      ...app,
    },
    deviceIntegrity: { deviceRecognitionVerdict: device },
    accountDetails: { appLicensingVerdict: "LICENSED" },
  };
}

/** Google and Instagram stand-ins. [payload] is what decodeIntegrityToken returns; null makes Google unreachable. */
function mockServices(payload: Verdict | null, decodeStatus = 200) {
  const calls: { url: string; init?: RequestInit }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      calls.push({ url, init });
      if (payload === null && url.includes("googleapis.com")) throw new TypeError("network down");
      if (url === "https://oauth2.googleapis.com/token") {
        return Response.json({ access_token: "google-access", expires_in: 3599, token_type: "Bearer" });
      }
      if (url.startsWith("https://playintegrity.googleapis.com/v1/app.giveaway:decodeIntegrityToken")) {
        return Response.json({ tokenPayloadExternal: payload }, { status: decodeStatus });
      }
      if (url.startsWith("https://api.instagram.com/")) {
        return Response.json({ data: [{ access_token: "short", user_id: 7 }] });
      }
      if (url.startsWith("https://graph.instagram.com/")) {
        return Response.json({ access_token: "long", expires_in: 5000 });
      }
      return new Response("unexpected", { status: 500 });
    }),
  );
  return calls;
}

const post = (path: string, body: unknown) =>
  new Request(`https://auth.example.test${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

describe("POST /v1/token with Play Integrity", () => {
  it("exchanges the code when the token is bound to it and the app is genuine", async () => {
    const calls = mockServices(verdict({ nonce: await sha256url("code-1") }));
    const response = await worker.fetch(post("/v1/token", { code: "code-1", integrityToken: "itk" }), env());
    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ access_token: "long" });

    const decodeCall = calls.find((c) => c.url.includes("decodeIntegrityToken"))!;
    expect((decodeCall.init!.headers as Record<string, string>).Authorization).toBe("Bearer google-access");
    expect(JSON.parse(decodeCall.init!.body as string)).toEqual({ integrity_token: "itk" });
  });

  it("signs the service account assertion with its key", async () => {
    const calls = mockServices(verdict({ nonce: await sha256url("code-1") }));
    await worker.fetch(post("/v1/token", { code: "code-1", integrityToken: "itk" }), env());
    const form = new URLSearchParams(calls.find((c) => c.url.includes("oauth2"))!.init!.body as string);
    expect(form.get("grant_type")).toBe("urn:ietf:params:oauth:grant-type:jwt-bearer");
    const [header, claims, signature] = form.get("assertion")!.split(".");
    expect(JSON.parse(Buffer.from(claims!, "base64url").toString())).toMatchObject({
      iss: "integrity@project.iam.gserviceaccount.com",
      scope: "https://www.googleapis.com/auth/playintegrity",
      aud: "https://oauth2.googleapis.com/token",
    });
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      publicKey,
      Buffer.from(signature!, "base64url"),
      new TextEncoder().encode(`${header}.${claims}`),
    );
    expect(valid).toBe(true);
  });

  it.each([
    ["a nonce for another code", async () => verdict({ nonce: await sha256url("other-code") })],
    ["an app not from Play", async () => verdict({ nonce: await sha256url("code-1") }, { appRecognitionVerdict: "UNRECOGNIZED_VERSION" })],
    ["another signing certificate", async () => verdict({ nonce: await sha256url("code-1") }, { certificateSha256Digest: ["zzz"] })],
    ["another package", async () => verdict({ nonce: await sha256url("code-1"), requestPackageName: "evil.app" })],
    ["a stale token", async () => verdict({ nonce: await sha256url("code-1"), timestampMillis: String(Date.now() - 120_000) })],
  ])("refuses %s without contacting Instagram", async (_, make) => {
    const calls = mockServices(await make());
    const response = await worker.fetch(post("/v1/token", { code: "code-1", integrityToken: "itk" }), env());
    expect(response.status).toBe(403);
    expect(await response.json()).toEqual({ error: "integrity_failed" });
    expect(calls.some((c) => c.url.includes("instagram.com"))).toBe(false);
  });

  it("refuses a token Google rejects", async () => {
    mockServices({}, 400);
    const response = await worker.fetch(post("/v1/token", { code: "code-1", integrityToken: "itk" }), env());
    expect(response.status).toBe(403);
  });

  it("answers 503 when the check can't run, and still exchanges nothing", async () => {
    const calls = mockServices(null);
    const response = await worker.fetch(post("/v1/token", { code: "code-1", integrityToken: "itk" }), env());
    expect(response.status).toBe(503);
    expect(calls.some((c) => c.url.includes("instagram.com"))).toBe(false);
    const misconfigured = await worker.fetch(
      post("/v1/token", { code: "code-1", integrityToken: "itk" }),
      env({ GOOGLE_SA_JSON: undefined }),
    );
    expect(misconfigured.status).toBe(503);
  });
});

describe("POST /v1/integrity", () => {
  it("returns only three booleans for a genuine device and matching draw", async () => {
    mockServices(verdict({ requestHash: "draw-hash" }, {}, ["MEETS_DEVICE_INTEGRITY", "MEETS_BASIC_INTEGRITY"]));
    const response = await worker.fetch(post("/v1/integrity", { token: "std", requestHash: "draw-hash" }), env());
    expect(response.status).toBe(200);
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(await response.json()).toEqual({ appRecognized: true, deviceIntegrity: true, hashMatches: true });
  });

  it("reports each failure separately", async () => {
    mockServices(verdict({ requestHash: "other" }, { appRecognitionVerdict: "UNEVALUATED" }, ["MEETS_BASIC_INTEGRITY"]));
    const response = await worker.fetch(post("/v1/integrity", { token: "std", requestHash: "draw-hash" }), env());
    expect(await response.json()).toEqual({ appRecognized: false, deviceIntegrity: false, hashMatches: false });
  });

  it("validates input and maps Google's answers", async () => {
    mockServices(verdict({ requestHash: "h" }));
    expect((await worker.fetch(post("/v1/integrity", { token: "", requestHash: "h" }), env())).status).toBe(400);
    expect((await worker.fetch(post("/v1/integrity", { token: "t" }), env())).status).toBe(400);
    expect((await worker.fetch(post("/v1/integrity", [1, 2]), env())).status).toBe(400);
    mockServices({}, 400);
    expect((await worker.fetch(post("/v1/integrity", { token: "t", requestHash: "h" }), env())).status).toBe(400);
    mockServices(null);
    expect((await worker.fetch(post("/v1/integrity", { token: "t", requestHash: "h" }), env())).status).toBe(503);
  });

  it("only accepts POST and is rate limited", async () => {
    const get = await worker.fetch(new Request("https://auth.example.test/v1/integrity"), env());
    expect(get.status).toBe(405);
    const limited = env({ RATE_LIMITER: { limit: async () => ({ success: false }) } as unknown as RateLimit });
    expect((await worker.fetch(post("/v1/integrity", { token: "t", requestHash: "h" }), limited)).status).toBe(429);
  });
});
