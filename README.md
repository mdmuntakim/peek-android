# Peek for Android

**Peek** is a professional search app for Android (arm64): every query gets a
synthesized **Peek AI** answer paragraph on top of ranked web results, a
Wikipedia knowledge panel, instant answers, related-search chips and an image
search tab — all key-free, straight from public endpoints.

The search engine is a faithful native Kotlin port of the
[Peek engine](https://github.com/mdmuntakim/peek) (`lib/*.js`):

| Node original        | Android port                                        |
| -------------------- | --------------------------------------------------- |
| `lib/search.js`      | `engine/Search.kt` (DDG HTML + lite parsing, IA, Wikipedia, suggestions) |
| `lib/rank.js`        | `engine/Rank.kt` (BM25-flavoured scorer, authority boost) |
| `lib/answer.js`      | `engine/Answer.kt` (intent-aware extractive synthesis) |
| `lib/images.js`      | `engine/Images.kt` (vqd token + image grid)         |
| `lib/util.js`        | `engine/Text.kt`, `engine/Http.kt`, `engine/Json.kt` |

Zero third-party engine dependencies: the JSON parser, HTTP layer and DuckDuckGo
scheduler are all in-tree, so the pipeline is testable on the plain JVM.

## UI

- **Material 3** (Jetpack Compose) — search field with live suggestions, Peek AI
  answer card, knowledge panel, result cards, image grid, bottom navigation,
  skeletons while providers answer.
- **Original theme** — the "Lagoon" palette (deep lagoon teal, burnt amber,
  plum) with light/dark/system modes; not derived from any other brand.
- **Original logo** — the "Lagoon aperture eye": a vesica eye inside a dashed
  lens ring with an offset pupil. Also used as the adaptive launcher icon
  (with Android 13 monochrome theming) and the splash mark.
- Splash screen via `core-splashscreen`, edge-to-edge layout.

## Build

Requirements: JDK 17+, Android SDK (platform 36, build-tools).

```bash
gradle :app:assembleRelease      # app/build/outputs/apk/release/app-arm64-release.apk
gradle :app:testDebugUnitTest    # JVM unit tests of the engine
```

The release build is an **arm64-v8a** APK, signed with the in-tree release
key (`app/peek-release.keystore`, store/key password `peek-android`, alias
`peek`). The key is public on purpose so anyone can reproduce the signed
build; do **not** use it to sign anything else.

## Engine endpoints (all key-free)

- DuckDuckGo HTML / lite results
- DuckDuckGo Instant Answer API
- Wikipedia search + summary API
- DuckDuckGo suggestions and image search

## License

MIT — same as the Peek engine.
