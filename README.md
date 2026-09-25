# Skynet

Skynet is a reusable Android and Android TV WebView shell with no preconfigured website.
Enter a website on first launch; plain domains are accepted and HTTPS is added automatically.
Version 2.1.0 clears legacy saved website settings once on upgrade and asks for a fresh choice.
After that, the selected site is saved normally. The setup prompt appears on each launch
until a website has been saved.

It supports phones, tablets, and Android/Google TV devices such as onn. streaming boxes.
The app shows the configured website in an immersive, edge-to-edge WebView.

## Included behavior

- Conservative host-based ad and tracker blocking, enabled by default
- Passive CSS cosmetic filtering for clearly identified ad containers, with no DOM observer
- HTTPS-only navigation and Android Safe Browsing
- First-party cookies and DOM storage for sign-in sessions
- File upload support
- Full-screen web media support
- Initial page scale is 90% in Skynet; Cable on TV has a saved 50%-110% zoom menu
- Android TV launcher support and D-pad/remote navigation inside web content
- Kiosk-style fullscreen display with no app header, footer, or navigation controls
- Screen remains awake while Skynet is in the foreground
- Tiny semi-transparent app-version badge in the upper-right corner
- Native matte-black arrow pointer: D-pad moves, OK clicks, held arrows accelerate
- Cursor hides after five idle seconds and returns on remote input
- Automatic edge scrolling without injecting hover or focus CSS into websites
- Remote Back navigates WebView history and never exits the app at the home page
- Frame-timed cursor animation with continuous acceleration and smooth edge scrolling
- Top-level navigation locked to the configured HTTPS origin; cross-origin video frames remain allowed
- Adult and retail redirect content is silently suppressed without displaying an interstitial block page
- Centered native loading spinner appears while a page is rendering
- Clearly identified ad overlays, popunders, and ad interstitials are suppressed without
  scanning page layout or modifying legitimate player overlays and modal APIs
- Websites retain their original styling; only the native Soft UI pointer and selection treatment are added

The built-in block list is intentionally conservative because aggressive generic filtering
can break authentication, payments, analytics-dependent dashboards, and CDNs.

## Build

Install the Android SDK once outside the repository. On this PC, the shared SDK
is at `/home/okonu/Android/Sdk`, and the ignored `local.properties` file contains
`sdk.dir=/home/okonu/Android/Sdk`. Other developers should point that file to
their own shared SDK location; do not copy the SDK into this project.

Then build with:

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Cable edition

The Cable product flavor is a separate Android TV app with the same simple native
matte-black arrow pointer. D-pad arrows move it, Enter activates the element below
it, and the loaded website keeps its original styling.
On TV, select the small version badge in the upper-right corner to choose 50%,
60%, 70%, 80%, 90%, 100%, or 110% zoom. The choice persists across launches.
Cable now keeps the browser's own zoom at 100%, lays out a larger WebView, and
scales that Android view to the selected size. Websites cannot change the outer
Android transform. The remote pointer's clicks, hover, and edge scrolling are
mapped into the larger WebView. Fullscreen video uses a separate unscaled view.
At low zoom levels the larger surface can use more memory and rendering work.

```sh
./gradlew assembleCableRelease
```

The Cable release APK is written to
`app/build/outputs/apk/cable/release/app-cable-release.apk`.

The permanent Cable download is
`https://github.com/okonnu/skynet-android-tv/releases/download/cable-current/Cable.apk`.
The TV-friendly link is `https://tinyurl.com/cable4tv` and points to that
permanent download. The earlier `https://tinyurl.com/22vgmkyf` still points to
Cable 1.2.2 and cannot be retargeted without access to its owning TinyURL
account and a paid TinyURL plan.

The `cable-current` prerelease keeps this URL stable without replacing numbered
release assets or changing which version the app's auto-updater considers latest.
After publishing a numbered `cable-v*` release with a `Cable.apk` asset, run
`bash scripts/update-cable-current.sh` to copy the highest numbered Cable APK to
the permanent download. A short link should point to this permanent URL, not
to a numbered release. GitHub Actions cannot run unattended on this account
while its billing lock is active, so this step is currently part of publishing.

## Temporary LAN zoom diagnostics

Cable 1.2.8 is an experimental diagnostic build for the TV zoom issue. It uses
an outer Android view transform instead of WebView page zoom. It records
the selected zoom, WebView scale changes, viewport measurements at short delays
around navigation, and logcat entries visible to its own app process. Android
does not grant an ordinary app access to the complete device-wide logcat.
The diagnostic sender is disabled in ordinary builds unless both environment
variables below are supplied at build time. Skynet does not include it.

Start the receiver on this PC's current private Wi-Fi IP:

```sh
python3 scripts/zoom-log-server.py --bind 192.168.1.224 --port 8765
```

It creates `.diagnostics/token` and appends events to
`.diagnostics/events.jsonl`. Both are ignored by git. Build the diagnostic APK
with `CABLE_DIAG_URL=http://192.168.1.224:8765/event` and
`CABLE_DIAG_TOKEN` set to the contents of `.diagnostics/token`. The IP must be
updated if this PC's Wi-Fi address changes. The published 1.2.8 APK was built
with these values. The receiver currently keeps only 1.2.8 sessions so an
older TV app cannot refill the cleared logs. Stop the receiver and remove the
temporary firewall rule
after the TV test, then remove this diagnostic instrumentation in the next
normal release.
