# Release checklist (H-10)

Everything that must be true before the staged rollout (spec: Release checklist, page 21–22; plan H-10). The code side is done; what's left needs the accounts from F-01 or people outside the codebase. Tick each box when it's done.

## 1. Configuration (`gradle.properties`)

A Play upload (`bundleRelease`) fails until every value below is real.

| Property | What to put | Status |
|---|---|---|
| `giveaway.igAppId` | Instagram app ID (Meta app, Instagram API with Instagram Login) | ☐ |
| `giveaway.metaAppId` | Meta (Facebook) app ID, sent with Share to Story | ☐ |
| `giveaway.authHost` | The login helper's domain, e.g. `auth.<domain>` | ☐ |
| `giveaway.authHostPins` | Two or more `sha256/...` SPKI pins for the helper's CA chain, including a backup pin | ☐ |
| `giveaway.cloudProjectNumber` | Google Cloud project linked to the app in Play Console (Play Integrity) | ☐ |
| `giveaway.privacyPolicyUrl` | The public privacy policy page | ☐ |
| `giveaway.verifierUrl` | The published verifier page (GitHub Pages) | ☐ |

Also:

- ☐ Final app name and icon; replace `[APP NAME]` and the placeholder package `app.giveaway` (`applicationId`, helper `ANDROID_PACKAGE`). Open decision 1.
- ☐ `app/google-services.json` from the Firebase project (turns crash reporting on in release builds).
- ☐ Release signing (upload key) in `keystore.properties`, never committed; Play app signing enabled.
- ☐ `graph.instagram.com` pins rechecked against the live chain (`app/src/main/res/xml/network_security_config.xml`); the pin-set expires 2027-04-01, and `SecurityConfigTest` fails a month before.

## 2. Login helper (`login-helper/`)

- ☐ Every `REPLACE_WITH_...` in `wrangler.toml` filled in (Meta app ID, hosts, Play app signing and upload certificate SHA-256).
- ☐ Secrets set for production: `IG_APP_SECRET`, `GOOGLE_SA_JSON` (service account with only the Play Integrity role).
- ☐ `INTEGRITY_ENFORCE = "true"` in production (already the default).
- ☐ Deployed: `npm run deploy:production`; logs, traces and Logpush stay off.
- ☐ OAuth redirect URI `https://<auth host>/ig/callback` added in the Meta app.
- ☐ App Link verified on a device: `adb shell pm verify-app-links --re-verify <package>`.

## 3. Proof and fairness

- ☐ Verifier published: `tools/verifier/`, `docs/draw-spec-v1.md` and `docs/test-vectors-v1.json` on a public repository with GitHub Pages; the page loads offline.
- ☐ G3: 20 real draws, each re-run in the published verifier from certificate data, 20 of 20 match (M-18).
- ☐ Copy checked against plan A30–A34: nothing in the app, the store listing or the privacy policy says a result can't be changed or redrawn (see `docs/security-review.md`, finding 10).

## 4. Meta and Google Play

- ☐ Privacy policy published and linked in the app, the Play listing and the Meta app.
- ☐ Meta app review approved for `instagram_business_basic` and `instagram_business_manage_comments` (M-09).
- ☐ Play Console: store listing in English and Arabic (`docs/store-listing.md`), screenshots, content rating, target audience 18+.
- ☐ Data safety form completed as in `docs/store-listing.md` (crash logs and diagnostics only).
- ☐ Closed testing: at least 12 testers for at least 14 days (M-20).

## 5. Quality gates

- ☐ CI green on `main`: build, lint, detekt, unit and screenshot tests, coverage gate, release APK check, device tests, verifier, login helper, dependency scan.
- ☐ Baseline Profile generated on a device and committed: `./gradlew :app:generateBaselineProfile`.
- ☐ Macrobenchmarks on the mid-range phone: cold start under 2 s (`:benchmark:connectedBenchmarkReleaseAndroidTest`); draw animation at 55 fps or more while recording (H-06, checked by hand with the GPU profiler or FrameTimingMetric).
- ☐ Manual matrix on a low-end, a mid-range and a recent phone, with posts of 0, 50, 5,000 and 20,000 comments (H-07).
- ☐ TalkBack pass on every screen, and the Arabic copy reviewed by a native speaker (H-03, M-19).
- ☐ External penetration test done, no high or critical findings open (H-05, H-08); see `docs/security-review.md`.
- ☐ Crash-free sessions above 99.5% in the closed beta (H-09).

## 6. Rollout

- ☐ `versionCode` and `versionName` raised; release notes in English and Arabic.
- ☐ `./gradlew bundleRelease` passes the configuration check and is uploaded.
- ☐ Staged rollout: 10%, watch crashes and reviews for 2 to 3 days, then 100%.
