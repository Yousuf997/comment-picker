#!/usr/bin/env node
// Open verifier for giveaway draws, algorithm v1 (docs/draw-spec-v1.md). Node 20 or later, no dependencies.
//
// It runs the algorithm from index.html itself, so the command line and the web page can never disagree.
//
//   Check the published test vectors:
//     node tools/verifier/verify.mjs --vectors docs/test-vectors-v1.json
//   Check a draw from its certificate and the exported entry list:
//     node tools/verifier/verify.mjs --seed <hex> --entries entries.txt --winners 3 --alternates 2 \
//       [--list-hash <hex>] [--picks amy,bob,cat]
//   Check that the page makes no network calls:
//     node tools/verifier/verify.mjs --check-offline

import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

/** The page's own algorithm block, loaded as a module. */
async function loadAlgorithm() {
  const html = await readFile(join(here, "index.html"), "utf8");
  const match = html.match(/<script type="module" id="draw-v1">([\s\S]*?)<\/script>/);
  if (!match) throw new Error("index.html has no draw-v1 script block");
  return import("data:text/javascript;base64," + Buffer.from(match[1]).toString("base64"));
}

function options(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (!arg.startsWith("--")) throw new Error(`Unexpected argument: ${arg}`);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith("--")) out[arg.slice(2)] = true;
    else out[arg.slice(2)] = argv[++i];
  }
  return out;
}

function entriesOf(vector) {
  if (vector.entries) return vector.entries;
  const { prefix, count } = vector.generatedEntries;
  const width = String(count).length;
  return Array.from({ length: count }, (_, n) => prefix + String(n + 1).padStart(width, "0"));
}

async function checkVectors(path, draw) {
  const data = JSON.parse(await readFile(path, "utf8"));
  if (data.algorithm !== draw.ALGORITHM) throw new Error(`Vectors are for ${data.algorithm}, not ${draw.ALGORITHM}`);
  let failures = 0;
  for (const vector of data.vectors) {
    const report = await draw.verifyDraw({
      seedHex: vector.seed,
      entries: entriesOf(vector),
      winners: vector.winners,
      alternates: vector.alternates,
      entryListHash: vector.entryListHash,
      claimed: vector.picks.map((p) => p.username),
    });
    const { picks } = await draw.select(draw.hexToBytes(vector.seed), entriesOf(vector), vector.winners, vector.alternates);
    const ok = report.ok && JSON.stringify(picks) === JSON.stringify(vector.picks);
    console.log((ok ? "OK   " : "FAIL ") + vector.name);
    if (!ok) failures++;
  }
  console.log(`${data.vectors.length - failures}/${data.vectors.length} vectors match`);
  return failures === 0;
}

async function checkDraw(opts, draw) {
  for (const name of ["seed", "entries", "winners", "alternates"]) {
    if (opts[name] === undefined || opts[name] === true) throw new Error(`Missing --${name}`);
  }
  const report = await draw.verifyDraw({
    seedHex: opts.seed,
    entries: draw.parseEntries(await readFile(opts.entries, "utf8")),
    winners: Number(opts.winners),
    alternates: Number(opts.alternates),
    entryListHash: typeof opts["list-hash"] === "string" ? opts["list-hash"] : "",
    claimed: typeof opts.picks === "string" ? opts.picks.split(",").map((s) => s.trim().replace(/^@/, "")) : [],
  });
  const labels = {
    listHash: "Entry list matches the certificate's hash",
    listHashComputed: "Entry list hash",
    picks: "Re-run gives the same winners and alternates",
  };
  for (const check of report.checks) {
    const mark = check.ok === true ? "OK  " : check.ok === false ? "FAIL" : "    ";
    console.log(`${mark} ${labels[check.id]}: ${check.detail}`);
  }
  for (const pick of report.picks) console.log(`     ${pick.position}. ${pick.role.toLowerCase()} @${pick.username}`);
  console.log(report.ok ? "The draw checks out." : "The draw does not match.");
  return report.ok;
}

/** The page must load nothing from the network and must forbid connections in its security policy. */
async function checkOffline() {
  const html = await readFile(join(here, "index.html"), "utf8");
  const problems = [];
  if (/\b(src|href|action)\s*=\s*["']?(https?:)?\/\//i.test(html)) problems.push("loads a remote resource");
  if (/\b(fetch|XMLHttpRequest|WebSocket|EventSource|sendBeacon)\s*\(/.test(html)) problems.push("has network code");
  const csp = html.match(/http-equiv="Content-Security-Policy" content="([^"]+)"/)?.[1] ?? "";
  for (const rule of ["default-src 'none'", "connect-src 'none'"]) {
    if (!csp.includes(rule)) problems.push(`policy lacks ${rule}`);
  }
  problems.forEach((p) => console.log("FAIL " + p));
  console.log(problems.length === 0 ? "OK   index.html makes no network calls" : "index.html is not offline-safe");
  return problems.length === 0;
}

const opts = options(process.argv.slice(2));
try {
  let ok;
  if (opts["check-offline"]) ok = await checkOffline();
  else if (typeof opts.vectors === "string") ok = await checkVectors(opts.vectors, await loadAlgorithm());
  else ok = await checkDraw(opts, await loadAlgorithm());
  process.exit(ok ? 0 : 1);
} catch (error) {
  console.error(error.message);
  process.exit(2);
}
