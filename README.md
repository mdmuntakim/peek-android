# Peek for Android

**Peek** is a search engine that lives entirely inside its own Android app
(arm64). It has its own crawler (**PeekBot**), its own inverted index with
BM25 ranking, its own answer synthesizer (**Peek AI**) and its own built-in
browser — and it never contacts any third-party search engine. No Google, no
Bing, no DuckDuckGo, no wrapper around someone else's API.

## The engine is Peek's own

Every stage of the pipeline runs on-device, in this repository:

| Stage        | Source                       | What it does                                                       |
| ------------ | ---------------------------- | ------------------------------------------------------------------ |
| Crawler      | `engine/Crawler.kt`          | Breadth-first crawl from the seed ring; robots.txt-aware, polite per-host delays, page-size caps |
| Reader       | `engine/HtmlDoc.kt`          | Extracts title, headings, readable text, links and images from fetched pages |
| Index        | `engine/Index.kt`            | Inverted index with field-weighted term frequencies + BM25 scorer, query language (`site:`, `-term`, `"phrase"`) |
| Ranker       | `engine/Rank.kt`             | De-duplication and authority nudges on top of BM25 scores          |
| Answers      | `engine/Answer.kt`           | Deterministic extractive synthesis — the "Peek AI" paragraph with sources |
| Storage      | `engine/Store.kt`            | JSON snapshot persistence (atomic write), history, bookmarks, seeds, image gallery |
| Browser      | `ui/BrowserScreen.kt`        | In-app WebView browser; pages you read are indexed ("browse to build your engine") |

The index is the only universe Peek searches. When a query finds nothing,
Peek says so honestly and grows the index in the background from the seed
ring instead of quietly asking another engine.

## UI

- **Material 3** (Jetpack Compose) — Search, Browser, Images and Settings tabs.
- **"Abyss" palette** — deep-sea ink, signal teal, beacon amber and dusk
  violet, with an aurora-gradient hero on the home screen; light/dark/system
  modes. Not derived from any other brand.
- **Original logo** — the "Peek aperture eye": a vesica eye inside a
  four-segment aperture ring with an offset pupil. Used as the in-app mark,
  the adaptive launcher icon and the splash.
- Live index status chip, highlighted snippets, related-search chips,
  knowledge panel from the index, skeleton loading states.

## Features

- Full-text search of the crawled corpus with BM25 ranking and highlighted snippets
- Peek AI answer paragraph with confidence and source chips
- Knowledge panel for entity queries, built from the index
- Query language: `site:example.org`, `-excluded`, `"exact phrase"`
- Suggestions from your own search history and indexed page titles
- Related searches mined from the index's salient terms
- Image gallery collected by PeekBot while crawling
- Built-in browser with back/forward, bookmarks and one-tap "Index page"
- Seed-ring manager, crawl budget (12/24/48/96 pages), crawl progress and cancel
- History, bookmarks, theme modes, `peek://search?q=` deep links

## Build

Requirements: JDK 17+, Android SDK (platform 37, build-tools).

```bash
gradle :app:assembleRelease      # app/build/outputs/apk/release/app-arm64-v8a-release.apk
gradle :app:testDebugUnitTest    # 32 JVM unit tests of the engine
```

The release build is an **arm64-v8a** APK, signed with the in-tree release
key (`app/peek-release.keystore`, store/key password `peek-android`, alias
`peek`). The key is public on purpose so anyone can reproduce the signed
build; do **not** use it to sign anything else.

## Honest limitations

A one-app crawler cannot index the whole web. Peek's results are only as
broad as what PeekBot has crawled and what you browsed; the Settings tab
shows exactly how large your universe is (pages, sites, terms). That is the
trade of a search engine that is truly yours — private, transparent and
independent, at personal scale.

## License

MIT — same as the Peek engine.
