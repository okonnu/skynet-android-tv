# Cable TV zoom regression

- Cable 1.2.8 uses an outer Android View transform for TV zoom. Keep WebView's
  internal initial scale at 100% and do not reintroduce viewport-meta zoom or
  native `zoomBy()` retries; both raced with late page layout on pantyflix.com.
- The WebView is laid out at root size divided by selected zoom, then the
  Android View is scaled to fill the root. Keep pointer hover, click, and edge
  scroll coordinates mapped between root and WebView space.
- Fullscreen video is a separate unscaled view on the root.
- Before publishing a zoom change, test a cold launch and page navigation at
  60%. Check the outer surface dimensions and pointer alignment, not only the
  badge or JavaScript viewport scale.
- Publishing Cable requires refreshing the permanent download with
  `bash scripts/update-cable-current.sh` after creating the numbered release.
