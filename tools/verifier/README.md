# Draw verifier

Checks a giveaway draw made with algorithm v1 ([specification](../../docs/draw-spec-v1.md)) from the certificate and
the exported entry list.

- `index.html`: a single self-contained page. Open it from disk or from GitHub Pages; it loads nothing and its
  Content Security Policy blocks every network request, so the entry list never leaves the browser.
- `verify.mjs`: the same check from the command line (Node 20+, no dependencies). It runs the algorithm block from
  `index.html`, so the two cannot disagree.

```sh
node verify.mjs --vectors ../../docs/test-vectors-v1.json   # the published test vectors
node verify.mjs --check-offline                             # the page makes no network calls
node verify.mjs --commit '#draw <hash>' --seed <hex> --entries entries.txt --winners 3 --alternates 2 \
  --list-hash <hex> --picks amy,bob,cat
```

Hosting (plan decision 5): publish this folder, the specification and the test vectors from a public repository
with GitHub Pages, then set `giveaway.verifierUrl` in `gradle.properties` to the page's address so certificates
print it.
