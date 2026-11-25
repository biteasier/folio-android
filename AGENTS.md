# Folio for Android

- Read-only Markdown and text reader. Java, plain Android views and a WebView. Package `com.biteasier.folio`.
- Keep it small: no AndroidX, no Kotlin, no permissions. The only libraries are commonmark-java and its extensions.
- `Renderer`, `TextDecoder` and `Paths` use no Android classes, so test them with `./gradlew testDebugUnitTest`.
- Documents are untrusted. Keep the page policy in `ReaderActivity` (scripts, styles and network blocked) and the tag filter in `Renderer`.
- Commit as `biteasier`. Never commit signing keys. See `docs/PLAN.md` and `docs/DECISIONS.md`.
