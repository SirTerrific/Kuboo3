# epublibdroid-kotlin

Epub reader view used by the app.

## Bundled jar

`libs/epublib-core-latest.jar` is **nl.siegmann.epublib:epublib-core 3.1** (built 2012-10-28 with
JDK 1.6, from the `META-INF/maven/.../pom.properties` inside the jar).
SHA-256: `6101d6d1c467dd64c307884ee193be723a302a3e80ec1e7eaf93e3ca1e4aa9a2`

The library is no longer maintained, so there is no newer release to move to.
It is kept as a file rather than a Maven dependency because the published pom pulls an old
slf4j binding that collides with the slf4j-api 2.x used by the app (see `build.gradle`).

Epub resources are written under the cache folder only after a path check
(`resolveInside` in `EpubReaderViewImpl1_Content.kt`), since the library does not clean
`../` in an epub's hrefs.
