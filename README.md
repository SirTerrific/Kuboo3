# Kuboo3

A maintained fork of [Kuboo](https://github.com/sethchhim/Kuboo) by Seth Chhim, whose
development stopped in 2019. Kuboo3 adds support for Ubooquity 3 and brings the build
back to current Android tooling.

It is a separate application: it installs alongside the original rather than replacing
it, and is not published on Google Play. Builds are on the [releases page](../../releases).

Free, with no ads and no tracking. If it is useful to you,
[buy me a coffee](https://www.buymeacoffee.com/menelikIII) — entirely optional.

This lightweight Android application can load books for the [Ubooquity](https://vaemendis.net/ubooquity/) server software.
 * Powered by the love of reading.
 * Free and open source.
 * No Ads, No Special Permissions.
 * Tested against Ubooquity 3.1.0, and still works with Ubooquity 2.

This is <b>NOT</b> a stand-alone comic viewer.

This app <b>REQUIRES</b> the Ubooquity server software to function properly.

The Ubooquity server <b>MUST</b> enable OPDS feed.

Requires Android 7.0 or later.

When adding a server address you must follow one of these formats:
* Ubooquity 3: "http://<span>192.168.0.1:2202/opds/</span>"
* Ubooquity 2: "http://<span>192.168.0.1:2202/opds-comics/</span>"
* Ubooquity 2: "http://<span>192.168.0.1:2202/opds-books/</span>"

> This fork carries the Ubooquity 3 support and build modernisation described in the
> [releases](../../releases). Upstream development of Kuboo stopped in 2019.

## What this fork fixes

Everything here was found against a real Ubooquity 3.1.0 server and verified on Android 9
and Android 15.

**Reading without downloading.** Ubooquity 3 advertises its page stream at
`/opds/comicreader/{id}`, but that address answers with the catalogue feed instead of a page
image, which is why comics would not stream. The address its own web reader uses does serve
the image, and Kuboo3 uses it:

```
/pagereader/{id}?page={n}&width={w}
```

This works for PDFs too — Ubooquity streams their pages and advertises them the same way — so
covers and pages come from the page stream for both.

**Signing in.** That page endpoint ignores the HTTP basic authentication header entirely and
checks for a session cookie instead. Without one it answers an "Authentication error" image
with a 200 status, so on any server with user management enabled every page and cover came back
as that image and nothing looked like a failure. Kuboo3 now signs in the way the web login form
does — `hmac_sha256(hmac_sha256(password, serversalt), servertime)` — and keeps the session.

**Reading position.** Ubooquity 3's `user-api/bookmark` takes a plain text body, not the JSON
that Ubooquity 2 returned: a page number for comics, `spineIndex#percentage` for books. Sending
a form content type makes the servlet consume the body and fail.

**Android.** Runtime receivers are registered with an explicit export flag (Android 14),
pending intents are immutable (Android 12), the reader opts out of forced edge-to-edge
(Android 15), and the build ships arm64 so it installs on 64-bit-only devices such as a Pixel 8.

**Downloads** stream to disk rather than being held in memory, and the read timeout allows for
a slow server.

## Notes on the server

* A long list is paginated by the server. Raising **Items per page** in the Ubooquity admin
  page (up to 300) means fewer pages to walk through.
* Cover sharpness is not a server setting any more: covers are requested from the page stream at
  the size they are displayed at. The thumbnail size in the admin page only matters for titles
  with no page stream.

## Building

Debug and release builds both need JDK 21 — the JDK bundled with Android Studio works. Release
APKs on the releases page are signed with the standard Android **debug** key, not a private
release key, so Android will warn about an unknown source.

## Screenshots

From this build, against a Ubooquity 3.1.0 server. The reader is a test file; the rest is a
real library.

<img src="docs/screenshots/browse.png" width="24%"> <img src="docs/screenshots/reader.png" width="24%"> <img src="docs/screenshots/settings.png" width="24%">

<img src="docs/screenshots/about.png" width="24%"> <img src="docs/screenshots/developer.png" width="24%">

<br/>

*"Kuboo3" is an independent third party application not affiliated with Ubooquity in any manner.*

*"Kuboo3" provides unrestricted access to the internet and is not responsible for the availability or content of these external sources.*

*Licensed under Apache 2.0, as the original.*
