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
- Initial page scale is 90% on most sites; user zoom controls and gestures are disabled
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
On TV, Cable gives pantyflix.com a 1200 CSS-pixel layout viewport so its desktop
navigation appears instead of the floating mobile menu. Other sites are unchanged.

```sh
./gradlew assembleCableRelease
```

The Cable release APK is written to
`app/build/outputs/apk/cable/release/app-cable-release.apk`.
