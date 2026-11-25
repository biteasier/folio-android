# Decisions

- **Java and plain views instead of Kotlin and Compose:** the app stays around 160 KB.
- **commonmark-java for parsing, WebView for display:** correct Markdown, and WebView gives text selection, find and
  printing without extra code. The page is served from memory, so there is no internet permission.
- **JavaScript is on, but the page policy blocks every script in a document.** Only Folio's own calls run.
- **No "all files" permission.** Folders the user adds give access to linked files and images.
- **Saved copies:** some apps let Folio read a file only once. Files up to 2 MB are then copied to private storage, so
  they can be reopened from Recent. They are deleted when removed from the list.
- **Limits:** 10 MB of text, formatting up to 2 million characters, 60 levels of nesting.
