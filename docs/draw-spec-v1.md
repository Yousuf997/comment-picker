# Draw algorithm v1

This is the open specification of how [APP NAME] picks giveaway winners. Anyone with the revealed seed and the entry list can reproduce a draw exactly.

Test vectors: [`test-vectors-v1.json`](test-vectors-v1.json). Implementations: the app (`core-draw`), an independent [Python reference](../tools/reference-impl/draw_v1.py), and the open [web verifier](../tools/verifier/index.html) with its command-line twin [`verify.mjs`](../tools/verifier/verify.mjs).

## What a draw shows

1. **Seed.** When a giveaway opens, the app generates a secret 32-byte seed with a cryptographic random generator. It stays encrypted on the phone until the draw.
2. **Reveal.** After the draw, the certificate shows the seed and the entry list hash, and the draw record is signed on the device.
3. **Deterministic selection.** Winners follow only from the seed and the final entry list, by the rules below. The same inputs always give the same winners, on any device and in any language.

So anyone can check that a certificate's winners follow from its seed and entry list. No code is posted before entries close, so a certificate doesn't show that the seed was fixed before the organizer saw the entries, or that no earlier draw was replaced.

The certificate also lists every manually excluded entry with its reason, and the exported entry list lets anyone check the entry list hash.

## Inputs

- `seed`: 32 bytes.
- `entries`: the usernames of the valid entries. With "one entry per person" on, each username appears once; otherwise once per valid comment.
- `winners`, `alternates`: non-negative integers.

## 1. Commit hash

`commitHash = lowercase hex of SHA-256(seed)` (64 characters). The app stores it with the sealed seed, checks the seed against it at the draw, and includes it in the signed draw record. It isn't posted anywhere.

## 2. Canonical entry list

1. Lowercase every username using Unicode's default (locale-independent) lowercase mapping.
2. Sort by **Unicode code point**, comparing code point by code point; a shorter string that is a prefix of a longer one sorts first. (This is not UTF-16 code unit order, which differs for characters above U+FFFF.)
3. Keep duplicates.
4. Join with `\n` (U+000A), with no trailing newline. An empty list is the empty string.
5. `entryListHash = SHA-256(UTF-8 bytes of that text)` (32 bytes; shown as lowercase hex).

## 3. Random stream

Block `k` (k = 0, 1, 2, ...) is

```
HMAC-SHA256(key = seed, message = entryListHash || uint64_big_endian(k))
```

Blocks are concatenated into one byte stream, read 4 bytes at a time as **big-endian unsigned 32-bit integers**.

## 4. Uniform integers

To pick a uniform integer in `[0, n)` for `n >= 1`:

```
limit = 2^32 - (2^32 mod n)
repeat:
    x = next uint32 from the stream
    if x < limit: return x mod n
```

Values at or above `limit` are discarded so that no result is more likely than another (no modulo bias). At least one value is always read, even when `n = 1`.

## 5. Selection

```
pool   = canonical list (as an array, length N)
target = min(winners + alternates, number of distinct usernames in pool)
picked = empty list
i = 0
while length(picked) < target:
    j = i + uniform(N - i)
    swap pool[i] and pool[j]
    if pool[i] not in picked: append pool[i] to picked
    i = i + 1
```

This is a partial Fisher–Yates shuffle. Skipping a username that was already picked means someone with several entries has proportionally more chances but can't win twice.

Positions start at 1. The first `min(winners, length(picked))` picks are winners, in order; the rest are alternates, in order.

## 6. Edge cases

| Case | Result |
|---|---|
| Empty entry list | No picks. The app blocks the draw before this point. |
| Fewer distinct people than `winners + alternates` | Everyone is picked; winners are filled first. |
| `winners = 0` | All picks are alternates. |

## Checking a draw

Anyone with a certificate and the exported entry list can check a draw:

1. Open `tools/verifier/index.html` in a browser (it works offline and makes no network requests), or run
   `node tools/verifier/verify.mjs --seed <revealed seed> --entries entries.txt --winners N --alternates M`.
2. The verifier recomputes the entry list hash and re-runs section 5. Add `--list-hash` and `--picks` (or fill in
   those fields on the page) to compare with the certificate.

## Versioning

The certificate records `algorithmVersion = "v1"`. Any change to these rules gets a new version and new test vectors; v1 results stay verifiable with this document.
