# comment-picker

Local-first Android app for fair, verifiable Instagram comment giveaways. All giveaway data stays on the phone.

## Build

Requirements: JDK 17 or newer (Android Studio's bundled JBR works) and the Android SDK.

```sh
# If your default Java is older than 17, point JAVA_HOME at Android Studio's JDK first, e.g.
# export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug test
```

Missing SDK platforms are downloaded automatically when the SDK licenses are accepted.

Run the same checks as CI before pushing:

```sh
./gradlew assembleDebug assembleRelease lint detekt test koverVerify verifyRoborazziDebug --continue
```

Screenshot tests (Roborazzi) compare against golden images committed in each module's `src/test/screenshots`. After an intended visual change, re-record and review the images before committing:

```sh
./gradlew recordRoborazziDebug
```

## CI

- `.github/workflows/ci.yml` runs on every push: debug and release builds, Android lint (warnings are errors, config in `lint.xml`), detekt (`config/detekt/detekt.yml`), unit tests, screenshot comparison, and the 100% coverage gate on `core-draw`. It also runs the login helper's tests once `login-helper/` exists.
- `.github/workflows/dependency-scan.yml` runs OWASP dependency-check weekly and when build files change, scanning each module's shipped dependencies and failing on CVSS 7 or higher. Vulnerability data comes from the DependencyCheck project's daily NVD mirror, so no API key is needed. Reviewed false positives are suppressed in `config/dependency-check/suppressions.xml`.
- Dependabot proposes weekly updates for Gradle dependencies and GitHub Actions.

## Modules

| Module | Purpose |
|---|---|
| `app` | Single activity, navigation, Hilt graph |
| `core-designsystem` | Theme tokens, fonts, shared Compose components |
| `core-draw` | Pure Kotlin/JVM: commit-reveal, entry rules, deterministic selection |
| `core-data` | Room + SQLCipher, repositories, encrypted backup |
| `core-instagram` | Every Instagram API call |
| `core-media` | Draw recorder and certificate builder |
| `core-security` | Keystore keys, app lock, integrity checks |
| `feature-onboarding` | S1 Welcome, S2 Connect, S3 App lock |
| `feature-home` | S4 Home |
| `feature-create` | S6 to S10 (pick post, rules, lock in, import, review) |
| `feature-draw` | S11 to S15 (draw, recording, winners, certificate) |
| `feature-settings` | S5 Settings, backup and restore |

Shared build configuration lives in `build-logic/` (convention plugins `giveaway.*`). Versions are in `gradle/libs.versions.toml`.

Module dependency rules are enforced at configuration time: feature modules can't depend on each other, core modules can't depend on feature modules, nothing depends on `app`, and `core-draw` has no project dependencies.
