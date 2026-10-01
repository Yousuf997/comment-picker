import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readFileSync, readdirSync } from "node:fs";
import worker, { type Env } from "../src/index";

const SECRET = "app-secret-must-not-leak";

function env(overrides: Partial<Env> = {}): Env {
  return {
    IG_APP_ID: "123",
    IG_REDIRECT_URI: "https://auth.example.test/ig/callback",
    ANDROID_PACKAGE: "app.giveaway",
    ANDROID_CERT_SHA256: "AA:BB, CC:DD",
    INTEGRITY_ENFORCE: "false",
    IG_APP_SECRET: SECRET,
    RATE_LIMITER: { limit: async () => ({ success: true }) } as unknown as RateLimit,
    ...overrides,
  };
}

function tokenRequest(body: unknown, headers: Record<string, string> = {}): Request {
  return new Request("https://auth.example.test/v1/token", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

/** Instagram stand-in: records calls and answers the two token endpoints. */
function mockInstagram(options: { codeStatus?: number; codeBody?: unknown; longLivedStatus?: number } = {}) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = input.toString();
    calls.push({ url, init });
    if (url.startsWith("https://api.instagram.com/oauth/access_token")) {
      const body = options.codeBody ?? { data: [{ access_token: "short-token", user_id: 1789, permissions: "x" }] };
      return new Response(JSON.stringify(body), { status: options.codeStatus ?? 200 });
    }
    if (url.startsWith("https://graph.instagram.com/access_token")) {
      return new Response(JSON.stringify({ access_token: "long-token", token_type: "bearer", expires_in: 5183944 }), {
        status: options.longLivedStatus ?? 200,
      });
    }
    return new Response("unexpected", { status: 500 });
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

beforeEach(() => vi.restoreAllMocks());
afterEach(() => vi.unstubAllGlobals());

describe("POST /v1/token", () => {
  it("exchanges the code and returns only the long-lived token", async () => {
    const calls = mockInstagram();
    const response = await worker.fetch(tokenRequest({ code: "abc123#_" }), env());

    expect(response.status).toBe(200);
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    const text = await response.text();
    expect(JSON.parse(text)).toEqual({ access_token: "long-token", expires_in: 5183944, user_id: "1789" });
    expect(text).not.toContain(SECRET);
    expect(text).not.toContain("short-token");

    const form = new URLSearchParams(calls[0]!.init!.body as string);
    expect(Object.fromEntries(form)).toEqual({
      client_id: "123",
      client_secret: SECRET,
      grant_type: "authorization_code",
      redirect_uri: "https://auth.example.test/ig/callback",
      code: "abc123",
    });
    const longLived = new URL(calls[1]!.url);
    expect(longLived.searchParams.get("grant_type")).toBe("ig_exchange_token");
    expect(longLived.searchParams.get("access_token")).toBe("short-token");
  });

  it("accepts the flat response shape too", async () => {
    mockInstagram({ codeBody: { access_token: "short-token", user_id: "42" } });
    const response = await worker.fetch(tokenRequest({ code: "abc" }), env());
    expect(await response.json()).toMatchObject({ user_id: "42" });
  });

  it("maps a rejected code to invalid_code without echoing Instagram's body", async () => {
    mockInstagram({ codeStatus: 400, codeBody: { error_message: "Invalid code, secret=" + SECRET } });
    const response = await worker.fetch(tokenRequest({ code: "expired" }), env());
    expect(response.status).toBe(400);
    const text = await response.text();
    expect(JSON.parse(text)).toEqual({ error: "invalid_code" });
    expect(text).not.toContain(SECRET);
  });

  it("returns exchange_failed when Instagram errors or is unreachable", async () => {
    mockInstagram({ longLivedStatus: 500 });
    expect((await worker.fetch(tokenRequest({ code: "abc" }), env())).status).toBe(502);

    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new TypeError("network down"))));
    const response = await worker.fetch(tokenRequest({ code: "abc" }), env());
    expect(response.status).toBe(502);
    expect(await response.json()).toEqual({ error: "exchange_failed" });
  });

  it.each([
    ["not JSON", "{oops", {}],
    ["wrong content type", "code=abc", { "Content-Type": "application/x-www-form-urlencoded" }],
    ["missing code", { integrityToken: "t" }, {}],
    ["code with spaces", { code: "a b" }, {}],
    ["code too long", { code: "x".repeat(2049) }, {}],
    ["non-string integrity token", { code: "abc", integrityToken: 5 }, {}],
    ["oversized body", { code: "abc", padding: "p".repeat(5000) }, {}],
  ])("rejects %s with 400 before calling Instagram", async (_name, body, headers) => {
    const calls = mockInstagram();
    const response = await worker.fetch(tokenRequest(body, headers as Record<string, string>), env());
    expect(response.status).toBe(400);
    expect(calls).toHaveLength(0);
  });

  it("fails closed when integrity enforcement is on", async () => {
    const calls = mockInstagram();
    const response = await worker.fetch(tokenRequest({ code: "abc" }), env({ INTEGRITY_ENFORCE: "true" }));
    expect(response.status).toBe(503);
    expect(calls).toHaveLength(0);
  });

  it("rate limits by client IP", async () => {
    const keys: string[] = [];
    const limiter = {
      limit: async ({ key }: { key: string }) => {
        keys.push(key);
        return { success: false };
      },
    } as unknown as RateLimit;
    const calls = mockInstagram();
    const response = await worker.fetch(
      tokenRequest({ code: "abc" }, { "CF-Connecting-IP": "203.0.113.7" }),
      env({ RATE_LIMITER: limiter }),
    );
    expect(response.status).toBe(429);
    expect(keys).toEqual(["203.0.113.7"]);
    expect(calls).toHaveLength(0);
  });

  it("only accepts POST", async () => {
    const response = await worker.fetch(new Request("https://auth.example.test/v1/token"), env());
    expect(response.status).toBe(405);
    expect(response.headers.get("Allow")).toBe("POST");
  });
});

describe("GET /.well-known/assetlinks.json", () => {
  it("lists the package and every signing certificate", async () => {
    const response = await worker.fetch(new Request("https://auth.example.test/.well-known/assetlinks.json"), env());
    expect(await response.json()).toEqual([
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: { namespace: "android_app", package_name: "app.giveaway", sha256_cert_fingerprints: ["AA:BB", "CC:DD"] },
      },
    ]);
  });
});

describe("GET /ig/callback", () => {
  it("never reflects the query into the page and is not cached", async () => {
    const response = await worker.fetch(
      new Request("https://auth.example.test/ig/callback?code=SECRETCODE&state=<script>alert(1)</script>"),
      env(),
    );
    const html = await response.text();
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    expect(response.headers.get("Content-Security-Policy")).toContain("default-src 'none'");
    expect(html).not.toContain("SECRETCODE");
    expect(html).not.toContain("alert(1)");
    expect(html).toContain('package=" + "app.giveaway"');
  });
});

describe("other routes", () => {
  it("returns 404 for unknown paths", async () => {
    expect((await worker.fetch(new Request("https://auth.example.test/"), env())).status).toBe(404);
  });
});

describe("source policy", () => {
  it("never writes to the console (the helper must not log anything)", () => {
    const sources = readdirSync(new URL("../src", import.meta.url)).filter((f) => f.endsWith(".ts"));
    for (const file of sources) {
      const code = readFileSync(new URL(`../src/${file}`, import.meta.url), "utf8");
      expect(code, file).not.toMatch(/\bconsole\s*\./);
    }
  });
});
