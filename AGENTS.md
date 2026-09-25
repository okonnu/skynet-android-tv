# Cable TV zoom regression

- Keep WebView's native initial scale equal to Cable's saved zoom percentage.
  Do not replace it with `setInitialScale(0)` or rely only on viewport metadata.
- Regression observed on pantyflix.com: after a cold launch, the badge and
  viewport metadata still said 60%, but `visualViewport.scale` was 1.0.
- Before publishing a zoom change, test a cold launch and page navigation at
  60%. Check the actual visual scale, not only the badge or viewport tag.
- Publishing Cable requires refreshing the permanent download with
  `bash scripts/update-cable-current.sh` after creating the numbered release.
