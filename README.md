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
- Android TV launcher support and D-pad/remote navigation inside web content
- Kiosk-style fullscreen display with no app header, footer, or navigation controls
- Screen remains awake while Skynet is in the foreground
- Tiny semi-transparent app-version badge in the upper-right corner
- Native cool-white Soft UI pointer: D-pad moves, OK clicks, held arrows accelerate
- Cursor and webpage hover treatment hide after five idle seconds and return on remote input
- Automatic edge scrolling plus injected hover and focus styling for web controls
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

After the Android SDK is installed and `local.properties` points to it:

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
