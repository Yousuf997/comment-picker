# MASVS L2 self-review (H-01)

Review of the app and the login helper against OWASP MASVS v2, level L2 (spec: Security and privacy). It prepares the external penetration test (H-05), which has the final word. Reviewed code: everything up to M-21 plus the fixes below, and the changes of plan X-1 to X-4 (redraw, editing, deleting).

## Control groups

| Group | Status | Basis |
|---|---|---|
| STORAGE | Met | SQLCipher database with a random 32-byte key, wrapped by a Keystore AES-GCM key in `noBackupFilesDir`. Token and seed each have their own key and associated data. `allowBackup=false`, and the extraction rules exclude every domain. No logs, no WebView, no SharedPreferences for data. Backup passwords never go into saved state. |
| CRYPTO | Met | Tink AEAD over Keystore AES-256-GCM, StrongBox when available. Non-exportable EC P-256 signing key. Argon2id for the PIN (32 MiB) and the backup key (64 MiB), with a cap on crafted header costs. Authenticated backup header. SecureRandom throughout. |
| AUTH | Met, with accepted risks | Sign-in `state` is 32 random bytes, single use, compared in constant time. The app lock composes nothing behind it, measures idle time on the monotonic clock, and backs off after 5 wrong PINs. See accepted risks 3 to 5. |
| NETWORK | Met in code; needs F-01 values | HTTPS only. `graph.instagram.com` is pinned with backup pins. The helper pin applies once `giveaway.authHostPins` is set, and a Play upload is blocked until it is. |
| PLATFORM | Met | Only the launcher and the App Link activity are exported. FileProvider isn't exported and shares only `certificates/`. Intents are explicit, PendingIntents immutable. FLAG_SECURE on sensitive screens when "Block screenshots" is on. No dialog or sheet can stay above the lock screen. |
| CODE | Met | R8 minify and shrink, Log calls stripped in release, detekt bans logging, dependency-check and Dependabot in CI, minSdk 26. Restore accepts only the database's real columns. |
| RESILIENCE | Partly, by design | Play Integrity is enforced at login (fails closed in production) and checked before a real draw. No on-device root or hook detection: the spec allows use on failing devices and only marks the certificate. |
| PRIVACY | Met | Generic, private-visibility notifications. Analytics and ad ID off. The helper keeps no logs. Crash reports carry no custom keys, user IDs or logs. |

## Findings and status

| # | Severity | Finding | Status |
|---|---|---|---|
| 1 | High | Dialogs and sheets (own windows) could stay usable above the lock screen, and touches could reach the app underneath. | **Fixed.** `AppLockGate` composes nothing of the app while locked; saved UI state and navigation come back on unlock. Test: `AppLockGateTest`. |
| 2 | Medium | The certificate's "device integrity verified" is the app's own claim; a repackaged app could set it. | **Accepted for 1.0.** What a certificate proves rests on the commitment and the open verifier, which a modified app can't defeat (finding 10 covers what the commitment no longer guarantees). A signed verdict from the helper (Ed25519, checked by the verifier) is the follow-up if the pentest asks for it. |
| 3 | Medium | Idle lock and PIN lockout used the wall clock. | **Idle lock fixed** (monotonic clock). **PIN lockout:** a clock moved backwards keeps it locked longer. Moving it forward needs the phone unlocked to reach its settings; accepted. |
| 4 | Low | No re-authentication before turning off app lock or exporting a backup. | **Accepted for 1.0.** Both need the app already unlocked; the app lock is a second layer behind the phone's own lock. Revisit after the pentest. |
| 5 | Low | Biometric unlock isn't bound to a Keystore key (`CryptoObject`). | **Accepted (plan A18).** Background work needs the keys without user presence; forcing the callback needs a rooted, hooked device. |
| 6 | Low | Restore took the idle time and screenshot setting from the file, and column names went into SQL unchecked. | **Fixed.** This phone's lock timing and screenshot setting are kept; columns not in `PRAGMA table_info` reject the file. Tests: `BackupRestoreTest`. |
| 7 | Low | Backup passwords were kept with `rememberSaveable`, so they went into saved instance state. | **Fixed.** Plain `remember`. |
| 8 | Info | The Instagram token is sent as a query parameter. | As Meta documents; nothing logs URLs. Check `Authorization: Bearer` support against the live API. |
| 9 | Info | Login doesn't require device integrity, only the genuine app. | Product decision; kept so more devices can sign in. |
| 10 | Info | Since plan A30–A33 the organizer can redraw, or change the rules, the entries or the post, after seeing the result, and nothing records it. This replaces the spec's "the result can never be changed or redrawn". | **Accepted (user decision, 2026-10-05).** Each certificate still verifies on its own. A redraw seals a new seed, so its code differs from the one posted before entries closed: anyone who saw the original code can tell, and the certificate says the code wasn't in the caption unless the organizer posts the new one. The certificate reports the caption when the draw ran, not at the deadline. The store listing and S8 no longer claim that results can't be changed. |
| 10 | Info | Below Android 13, FLAG_SECURE is set in `onPause`; some phones take the recents snapshot earlier. No `filterTouchesWhenObscured` (Android 8 to 11). | Known limit; Android 12+ blocks untrusted overlays. |
| 11 | Info | Crash reports have no in-app opt-out. | Declared in the Data safety form; an opt-out would be a new feature. |
| 12 | Info | The release configuration check runs for app bundles only. | Intended: CI builds release APKs before F-01 values exist. Only bundles go to Play. |

## Needs the F-01 accounts

- The auth host (`giveaway.authHost`) and its SPKI pins with a backup pin (`giveaway.authHostPins`), taken from the live certificate chain.
- `graph.instagram.com` pins rechecked against the live chain before release (the pin-set expires 2027-04-01).
- Play Integrity: Cloud project number, Play Console link, `GOOGLE_SA_JSON` (Play Integrity role only).
- Login helper secrets and variables: `IG_APP_SECRET`, Meta app ID, redirect URI, Play app signing and upload certificate fingerprints. Then check App Link verification on a device.
- Firebase `google-services.json`, the final package name, privacy policy URL and Data safety form.
