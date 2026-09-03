package com.pepea.siteview;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {
    private static final long CURSOR_IDLE_TIMEOUT_MS = 5_000L;
    private static final String PREFS = "siteview_preferences";
    private static final String KEY_HOME_URL = "home_url";
    private static final String KEY_SITE_SETUP_SCHEMA = "site_setup_schema";
    private static final int SITE_SETUP_SCHEMA = 1;
    private static final String KEY_AD_BLOCKING = "ad_blocking";
    private static final int FILE_CHOOSER_REQUEST = 41;
    private static final boolean POINTER_ENABLED = BuildConfig.POINTER_ENABLED;
    /*
     * Cable uses this only after a D-pad press.  It deliberately has no mutation
     * observer, timer, or animation loop: a short-lived geometry cache makes held
     * arrows responsive without adding background work to the loaded website.
     */
    private static final String SPATIAL_NAVIGATION_INSTALLER = """
            (function() {
              if (window.siteviewSpatialNavigation) return;

              var selector = 'a[href],area[href],button,input:not([type="hidden"]),select,textarea,' +
                  'iframe,video[controls],[role="button"],[role="link"],[role="menuitem"],' +
                  '[role="tab"],[role="option"],[role="checkbox"],[role="radio"],' +
                  '[contenteditable="true"],[tabindex]:not([tabindex="-1"])';
              var cache = { at: 0, x: NaN, y: NaN, candidates: [] };
              var cacheLifetimeMs = 140;

              function visibleCandidate(element) {
                if (!(element instanceof HTMLElement) || element.disabled ||
                    element.getAttribute('aria-hidden') === 'true' || element.closest('[inert]')) {
                  return null;
                }
                var style = window.getComputedStyle(element);
                if (style.display === 'none' || style.visibility !== 'visible' ||
                    Number(style.opacity) === 0 || style.pointerEvents === 'none') {
                  return null;
                }
                var rect = element.getBoundingClientRect();
                if (rect.width < 4 || rect.height < 4 || rect.right < 0 || rect.bottom < 0 ||
                    rect.left > window.innerWidth || rect.top > window.innerHeight) {
                  return null;
                }
                return {
                  element: element,
                  left: rect.left,
                  right: rect.right,
                  top: rect.top,
                  bottom: rect.bottom,
                  cx: (rect.left + rect.right) / 2,
                  cy: (rect.top + rect.bottom) / 2
                };
              }

              function candidates() {
                var now = performance.now();
                if (now - cache.at < cacheLifetimeMs && cache.x === window.scrollX && cache.y === window.scrollY) {
                  return cache.candidates;
                }
                var nodes = document.querySelectorAll(selector);
                var next = [];
                for (var i = 0; i < nodes.length; i++) {
                  var candidate = visibleCandidate(nodes[i]);
                  if (candidate) next.push(candidate);
                }
                cache = { at: now, x: window.scrollX, y: window.scrollY, candidates: next };
                return next;
              }

              function currentCandidate() {
                var active = document.activeElement;
                if (!active || active === document.body || active === document.documentElement) return null;
                if (!active.matches(selector)) active = active.closest(selector);
                return active ? visibleCandidate(active) : null;
              }

              function edgeAnchor(direction) {
                var width = window.innerWidth;
                var height = window.innerHeight;
                if (direction === 'up') return { left: width / 2, right: width / 2, top: height, bottom: height, cx: width / 2, cy: height };
                if (direction === 'left') return { left: width, right: width, top: height / 2, bottom: height / 2, cx: width, cy: height / 2 };
                if (direction === 'right') return { left: 0, right: 0, top: height / 2, bottom: height / 2, cx: 0, cy: height / 2 };
                return { left: width / 2, right: width / 2, top: 0, bottom: 0, cx: width / 2, cy: 0 };
              }

              function measures(anchor, candidate, direction) {
                if (direction === 'up') {
                  return { forward: anchor.cy - candidate.cy, lateral: Math.abs(candidate.cx - anchor.cx),
                    overlaps: candidate.right >= anchor.left && candidate.left <= anchor.right };
                }
                if (direction === 'left') {
                  return { forward: anchor.cx - candidate.cx, lateral: Math.abs(candidate.cy - anchor.cy),
                    overlaps: candidate.bottom >= anchor.top && candidate.top <= anchor.bottom };
                }
                if (direction === 'right') {
                  return { forward: candidate.cx - anchor.cx, lateral: Math.abs(candidate.cy - anchor.cy),
                    overlaps: candidate.bottom >= anchor.top && candidate.top <= anchor.bottom };
                }
                return { forward: candidate.cy - anchor.cy, lateral: Math.abs(candidate.cx - anchor.cx),
                  overlaps: candidate.right >= anchor.left && candidate.left <= anchor.right };
              }

              function bestCandidate(direction) {
                var current = currentCandidate();
                var anchor = current || edgeAnchor(direction);
                var items = candidates();
                var aligned = null;
                var directional = null;
                var alignedScore = Infinity;
                var directionalScore = Infinity;
                for (var i = 0; i < items.length; i++) {
                  var item = items[i];
                  if (current && item.element === current.element) continue;
                  var distance = measures(anchor, item, direction);
                  if (distance.forward <= 3) continue;
                  if (distance.overlaps) {
                    var directScore = distance.forward + distance.lateral * 0.18;
                    if (directScore < alignedScore) {
                      aligned = item;
                      alignedScore = directScore;
                    }
                  } else if (distance.forward >= distance.lateral * 0.55) {
                    var coneScore = distance.forward + distance.lateral * 0.50;
                    if (coneScore < directionalScore) {
                      directional = item;
                      directionalScore = coneScore;
                    }
                  }
                }
                return aligned || directional;
              }

              function scroll(direction) {
                var amount = Math.max(120, Math.round((direction === 'up' || direction === 'down' ? window.innerHeight : window.innerWidth) * 0.68));
                if (direction === 'up') window.scrollBy({ top: -amount, behavior: 'auto' });
                else if (direction === 'left') window.scrollBy({ left: -amount, behavior: 'auto' });
                else if (direction === 'right') window.scrollBy({ left: amount, behavior: 'auto' });
                else window.scrollBy({ top: amount, behavior: 'auto' });
              }

              window.siteviewSpatialNavigation = {
                move: function(direction) {
                  var next = bestCandidate(direction);
                  if (!next) {
                    scroll(direction);
                    return 'scrolled';
                  }
                  try {
                    next.element.focus({ preventScroll: true });
                  } catch (ignored) {
                    next.element.focus();
                  }
                  next.element.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'auto' });
                  cache.at = 0;
                  return 'focused';
                }
              };
            })();
            """;

    private SharedPreferences preferences;
    private boolean adBlockingEnabled;
    private WebView webView;
    private AdBlocker adBlocker;
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout root;
    private ProgressBar loadingSpinner;
    private TextView versionBadge;
    private boolean isTvDevice;
    private TvCursorView cursorView;
    private float cursorX;
    private float cursorY;
    private boolean cursorEnabled;
    private boolean urlDialogVisible;
    private boolean cursorUpHeld;
    private boolean cursorDownHeld;
    private boolean cursorLeftHeld;
    private boolean cursorRightHeld;
    private boolean cursorFramePosted;
    private long previousCursorFrameNanos;
    private long cursorMovementStartedNanos;
    private long previousHoverDispatchNanos;
    private String lastAllowedUrl;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Runnable hideCursor = () -> {
        if (cursorView != null && cursorView.getVisibility() == View.VISIBLE) {
            dispatchHoverExit();
            cursorView.setVisibility(View.GONE);
        }
    };
    private final Choreographer.FrameCallback cursorFrameCallback = this::animateCursorFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        adBlockingEnabled = preferences.getBoolean(KEY_AD_BLOCKING, true);
        boolean resetSavedSite = preferences.getInt(KEY_SITE_SETUP_SCHEMA, 0) < SITE_SETUP_SCHEMA;
        if (resetSavedSite) {
            // One-time reset of legacy configuration; subsequent user choices are preserved.
            preferences.edit()
                    .remove(KEY_HOME_URL)
                    .remove("default_migration_version")
                    .putInt(KEY_SITE_SETUP_SCHEMA, SITE_SETUP_SCHEMA)
                    .apply();
        }
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        isTvDevice = uiModeManager != null &&
                uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        adBlocker = new AdBlocker(this);
        buildInterface();
        enableImmersiveMode();
        configureWebView();

        String homeUrl = preferences.getString(KEY_HOME_URL, "");
        if (homeUrl == null || homeUrl.isEmpty()) {
            showUrlDialog(false);
        } else {
            lastAllowedUrl = homeUrl;
            if (savedInstanceState == null || resetSavedSite
                    || webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(homeUrl);
            }
        }
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        versionBadge = new TextView(this);
        versionBadge.setText(getVersionLabel());
        versionBadge.setTextColor(Color.argb(210, 255, 255, 255));
        versionBadge.setTextSize(9);
        versionBadge.setIncludeFontPadding(false);
        versionBadge.setPadding(dp(5), dp(3), dp(5), dp(3));
        versionBadge.setClickable(false);
        versionBadge.setFocusable(false);
        versionBadge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        versionBadge.setElevation(dp(24));
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(Color.argb(120, 0, 0, 0));
        badgeBackground.setCornerRadius(dpFloat(5));
        versionBadge.setBackground(badgeBackground);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        badgeParams.setMargins(dp(6), dp(6), dp(6), 0);
        root.addView(versionBadge, badgeParams);

        loadingSpinner = new ProgressBar(this);
        loadingSpinner.setIndeterminate(true);
        loadingSpinner.setIndeterminateTintList(ColorStateList.valueOf(Color.rgb(255, 126, 24)));
        loadingSpinner.setVisibility(View.GONE);
        loadingSpinner.setElevation(dp(12));
        FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(dp(64), dp(64));
        spinnerParams.gravity = android.view.Gravity.CENTER;
        root.addView(loadingSpinner, spinnerParams);

        if (POINTER_ENABLED) {
            cursorView = new TvCursorView(this);
            FrameLayout.LayoutParams cursorParams = new FrameLayout.LayoutParams(dp(56), dp(56));
            root.addView(cursorView, cursorParams);
            cursorEnabled = isTvDevice;
            cursorView.setVisibility(cursorEnabled ? View.VISIBLE : View.GONE);
        }

        setContentView(root);
        root.post(() -> {
            if (POINTER_ENABLED) {
                cursorX = root.getWidth() / 2f;
                cursorY = root.getHeight() / 2f;
                updateCursorPosition();
                if (cursorEnabled) {
                    showCursorForInput();
                    scheduleCursorHide();
                }
            } else {
                webView.requestFocus();
            }
        });
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSafeBrowsingEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new SiteViewClient());
        webView.setWebChromeClient(new SiteChromeClient());
    }

    private final class SiteViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return adBlocker.intercept(request, adBlockingEnabled);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) {
                return false;
            }
            return !isAllowedTopLevelUrl(request.getUrl());
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (isAllowedTopLevelUrl(Uri.parse(url))) {
                showLoadingSpinner();
                return;
            }

            view.stopLoading();
            if (lastAllowedUrl != null && !lastAllowedUrl.isEmpty()) {
                view.loadUrl(lastAllowedUrl);
            }
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            super.onPageCommitVisible(view, url);
            if (isAllowedTopLevelUrl(Uri.parse(url))) {
                injectNavigationStyling(view);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (!isAllowedTopLevelUrl(Uri.parse(url))) {
                return;
            }
            lastAllowedUrl = url;
            hideLoadingSpinner();
            injectCosmeticFiltering(view);
            injectNavigationStyling(view);
            if (cursorEnabled) {
                dispatchHoverEvent();
            } else {
                view.requestFocus();
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                hideLoadingSpinner();
                Toast.makeText(MainActivity.this, "Unable to load the website", Toast.LENGTH_LONG).show();
            }
        }

    }

    private final class SiteChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (newProgress >= 100) {
                hideLoadingSpinner();
            } else if (isAllowedTopLevelUrl(Uri.parse(view.getUrl() == null ? "" : view.getUrl()))) {
                showLoadingSpinner();
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      android.os.Message resultMsg) {
            return false;
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
            }
            fileCallback = callback;
            Intent chooser;
            try {
                chooser = params.createIntent();
            } catch (Exception exception) {
                chooser = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                chooser.setType("*/*");
                chooser.addCategory(Intent.CATEGORY_OPENABLE);
            }
            try {
                startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException exception) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "No file picker is available", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            versionBadge.bringToFront();
            webView.setVisibility(View.GONE);
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }
    }

    private void injectCosmeticFiltering(WebView view) {
        if (!adBlockingEnabled) {
            return;
        }
        String script = "(function(){if(document.getElementById('siteview-ad-style'))return;" +
                "var s=document.createElement('style');s.id='siteview-ad-style';" +
                "s.textContent='ins.adsbygoogle,.adsbygoogle,[id^=google_ads]," +
                "iframe[src*=doubleclick],iframe[src*=googlesyndication]," +
                "a[href*=\"pornhub.com\"],a[href*=\"xvideos.com\"]," +
                "a[href*=\"xnxx.com\"],a[href*=\"xhamster.com\"]," +
                "a[href*=\"chaturbate.com\"],a[href*=\"onlyfans.com\"]," +
                "a[href*=\"amazon.com\"],a[href*=\"ebay.com\"]," +
                "a[href*=\"walmart.com\"],a[href*=\"aliexpress.com\"]," +
                "a[href*=\"temu.com\"],a[href*=\"shein.com\"]," +
                "a[href*=\"etsy.com\"]{display:none!important}';" +
                "(document.head||document.documentElement).appendChild(s);})();";
        view.evaluateJavascript(script, null);
        injectOverlayFiltering(view);
    }

    private void injectOverlayFiltering(WebView view) {
        String script = "(function(){" +
                "if(document.getElementById('siteview-overlay-style'))return;" +
                "var s=document.createElement('style');s.id='siteview-overlay-style';" +
                "s.textContent='[class*=\\\"ad-overlay\\\" i],[id*=\\\"ad-overlay\\\" i]," +
                "[class*=\\\"ad-popup\\\" i],[id*=\\\"ad-popup\\\" i]," +
                "[class*=\\\"ad-modal\\\" i],[id*=\\\"ad-modal\\\" i]," +
                "[class*=\\\"advert-overlay\\\" i],[id*=\\\"advert-overlay\\\" i]," +
                "[class*=\\\"popunder\\\" i],[id*=\\\"popunder\\\" i]," +
                "[class*=\\\"interstitial-ad\\\" i],[id*=\\\"interstitial-ad\\\" i]," +
                "[aria-label*=\\\"advertisement\\\" i]{display:none!important;" +
                "visibility:hidden!important;pointer-events:none!important}';" +
                "(document.head||document.documentElement).appendChild(s);" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    private void injectCursorStyling(WebView view) {
        String script = "(function(){" +
                "if(!document.getElementById('siteview-cursor-style')){" +
                "var s=document.createElement('style');s.id='siteview-cursor-style';" +
                "s.textContent='a[href]:hover,button:hover,input:hover,select:hover,textarea:hover," +
                "[role=button]:hover,[role=link]:hover,[role=menuitem]:hover,[role=tab]:hover," +
                "[tabindex]:hover{outline:3px solid #ff7e18!important;outline-offset:3px!important;" +
                "box-shadow:-4px -4px 9px rgba(255,255,255,.9)," +
                "4px 4px 9px rgba(154,174,195,.38)!important;border-radius:12px!important;}" +
                "a[href]:focus-visible,button:focus-visible,input:focus-visible,select:focus-visible," +
                "textarea:focus-visible,[role=button]:focus-visible,[role=link]:focus-visible," +
                "[tabindex]:focus-visible{outline:3px solid #ff7e18!important;outline-offset:3px!important;" +
                "box-shadow:inset 3px 3px 7px rgba(154,174,195,.32)," +
                "inset -3px -3px 7px rgba(255,255,255,.84)!important;}" +
                "a[href]:active,button:active,[role=button]:active,[role=link]:active{" +
                "filter:brightness(.88)!important;}';" +
                "(document.head||document.documentElement).appendChild(s);}" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    private void injectFocusStyling(WebView view) {
        String script = "(function(){" +
                "if(document.getElementById('siteview-focus-style'))return;" +
                "var s=document.createElement('style');s.id='siteview-focus-style';" +
                "s.textContent='a[href]:focus,button:focus,input:focus,select:focus,textarea:focus," +
                "[role=button]:focus,[role=link]:focus,[role=menuitem]:focus,[role=tab]:focus," +
                "[tabindex]:focus{outline:1px solid rgba(255,184,112,.58)!important;" +
                "outline-offset:2px!important;box-shadow:0 4px 12px rgba(0,0,0,.30)," +
                "0 0 0 2px rgba(255,126,24,.16)!important;border-radius:8px!important}';" +
                "(document.head||document.documentElement).appendChild(s);})();";
        view.evaluateJavascript(script, null);
    }

    private void injectNavigationStyling(WebView view) {
        if (POINTER_ENABLED) {
            injectCursorStyling(view);
        } else {
            injectFocusStyling(view);
            injectSpatialNavigation(view);
        }
    }

    private void injectSpatialNavigation(WebView view) {
        view.evaluateJavascript(SPATIAL_NAVIGATION_INSTALLER, null);
    }

    private void showUrlDialog(boolean cancelable) {
        urlDialogVisible = true;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        container.setPadding(padding, dp(8), padding, 0);

        TextView explanation = new TextView(this);
        explanation.setText(R.string.url_explanation);
        explanation.setTextSize(16);
        container.addView(explanation);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_URI);
        String savedUrl = preferences.getString(KEY_HOME_URL, "");
        input.setText(savedUrl);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(cancelable ? "Change website" : "Choose your website")
                .setView(container)
                .setCancelable(cancelable)
                .setNegativeButton(cancelable ? "Cancel" : null, null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String normalized = normalizeHttpsUrl(input.getText().toString());
                    if (normalized == null) {
                        input.setError("Enter a valid website, such as google.com");
                        return;
                    }
                    preferences.edit().putString(KEY_HOME_URL, normalized).apply();
                    lastAllowedUrl = normalized;
                    dialog.dismiss();
                    webView.loadUrl(normalized);
                }));
        dialog.setOnDismissListener(ignored -> {
            urlDialogVisible = false;
            enableImmersiveMode();
        });
        dialog.show();
        if (isTvDevice) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
        } else {
            input.requestFocus();
        }
    }

    static String normalizeHttpsUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains("://")) {
            value = "https://" + value;
        }
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) ||
                uri.getHost() == null ||
                uri.getHost().trim().isEmpty() || uri.getUserInfo() != null) {
            return null;
        }
        return uri.buildUpon().scheme("https").build().toString();
    }

    private boolean isAllowedTopLevelUrl(Uri destination) {
        if (destination == null || !"https".equalsIgnoreCase(destination.getScheme())) {
            return false;
        }
        String homeUrl = preferences.getString(KEY_HOME_URL, "");
        if (homeUrl.isEmpty()) {
            return false;
        }
        Uri home = Uri.parse(homeUrl);
        String homeHost = home.getHost();
        String destinationHost = destination.getHost();
        if (homeHost == null || destinationHost == null
                || !homeHost.equalsIgnoreCase(destinationHost)) {
            return false;
        }
        return effectivePort(home) == effectivePort(destination);
    }

    private static int effectivePort(Uri uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    private void showLoadingSpinner() {
        if (loadingSpinner != null && loadingSpinner.getVisibility() != View.VISIBLE) {
            loadingSpinner.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingSpinner() {
        if (loadingSpinner != null && loadingSpinner.getVisibility() != View.GONE) {
            loadingSpinner.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (urlDialogVisible || customView != null) {
            return super.dispatchKeyEvent(event);
        }

        int keyCode = event.getKeyCode();
        boolean directional = keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean click = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;

        if (!POINTER_ENABLED) {
            if (directional) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    moveSpatialFocus(keyCode);
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (directional) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                ensureCursorVisible();
                boolean changed = setDirectionHeld(keyCode, true);
                if (changed) {
                    startCursorAnimation();
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                setDirectionHeld(keyCode, false);
                if (!isAnyDirectionHeld()) {
                    scheduleCursorHide();
                }
            }
            return true;
        }

        if (click && cursorEnabled) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                boolean wasHidden = ensureCursorVisible();
                if (!wasHidden) {
                    clickAtCursor();
                }
            }
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void moveSpatialFocus(int keyCode) {
        String direction;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            direction = "up";
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            direction = "left";
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            direction = "right";
        } else {
            direction = "down";
        }
        webView.evaluateJavascript(
                "window.siteviewSpatialNavigation&&window.siteviewSpatialNavigation.move('" + direction + "');",
                null);
    }

    private boolean ensureCursorVisible() {
        boolean wasHidden = cursorView.getVisibility() != View.VISIBLE;
        if (!cursorEnabled) {
            cursorEnabled = true;
            if (cursorX == 0f && cursorY == 0f) {
                cursorX = root.getWidth() / 2f;
                cursorY = root.getHeight() / 2f;
            }
            updateCursorPosition();
        }
        showCursorForInput();
        if (wasHidden) {
            dispatchHoverEvent();
        }
        return wasHidden;
    }

    private void showCursorForInput() {
        cursorView.removeCallbacks(hideCursor);
        if (cursorView.getVisibility() != View.VISIBLE) {
            cursorView.setVisibility(View.VISIBLE);
        }
    }

    private void scheduleCursorHide() {
        cursorView.removeCallbacks(hideCursor);
        cursorView.postDelayed(hideCursor, CURSOR_IDLE_TIMEOUT_MS);
    }

    private boolean setDirectionHeld(int keyCode, boolean held) {
        boolean changed;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            changed = cursorUpHeld != held;
            cursorUpHeld = held;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            changed = cursorDownHeld != held;
            cursorDownHeld = held;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            changed = cursorLeftHeld != held;
            cursorLeftHeld = held;
        } else {
            changed = cursorRightHeld != held;
            cursorRightHeld = held;
        }
        return changed;
    }

    private boolean isAnyDirectionHeld() {
        return cursorUpHeld || cursorDownHeld || cursorLeftHeld || cursorRightHeld;
    }

    private void startCursorAnimation() {
        showCursorForInput();
        if (!cursorFramePosted) {
            cursorFramePosted = true;
            previousCursorFrameNanos = 0L;
            cursorMovementStartedNanos = 0L;
            choreographer.postFrameCallback(cursorFrameCallback);
        }
    }

    private void animateCursorFrame(long frameTimeNanos) {
        if (!isAnyDirectionHeld()) {
            cursorFramePosted = false;
            previousCursorFrameNanos = 0L;
            cursorMovementStartedNanos = 0L;
            return;
        }

        if (cursorMovementStartedNanos == 0L) {
            cursorMovementStartedNanos = frameTimeNanos;
        }
        float deltaSeconds = previousCursorFrameNanos == 0L
                ? 1f / 60f
                : Math.min((frameTimeNanos - previousCursorFrameNanos) / 1_000_000_000f, 0.05f);
        previousCursorFrameNanos = frameTimeNanos;

        float heldSeconds = (frameTimeNanos - cursorMovementStartedNanos) / 1_000_000_000f;
        float acceleration = Math.min(heldSeconds / 1.4f, 1f);
        float speedPerSecond = dpFloat(320f + 530f * acceleration);
        float horizontal = (cursorRightHeld ? 1f : 0f) - (cursorLeftHeld ? 1f : 0f);
        float vertical = (cursorDownHeld ? 1f : 0f) - (cursorUpHeld ? 1f : 0f);
        if (horizontal != 0f && vertical != 0f) {
            horizontal *= 0.7071f;
            vertical *= 0.7071f;
        }
        cursorX += horizontal * speedPerSecond * deltaSeconds;
        cursorY += vertical * speedPerSecond * deltaSeconds;

        float edge = dp(18);
        cursorX = Math.max(edge, Math.min(webView.getWidth() - edge, cursorX));
        cursorY = Math.max(edge, Math.min(webView.getHeight() - edge, cursorY));

        float scrollPerFrame = dpFloat(560f) * deltaSeconds;
        int scrollX = 0;
        int scrollY = 0;
        if (cursorUpHeld && cursorY <= edge) {
            scrollY = -Math.max(1, Math.round(scrollPerFrame));
        } else if (cursorDownHeld && cursorY >= webView.getHeight() - edge) {
            scrollY = Math.max(1, Math.round(scrollPerFrame));
        }
        if (cursorLeftHeld && cursorX <= edge) {
            scrollX = -Math.max(1, Math.round(scrollPerFrame));
        } else if (cursorRightHeld && cursorX >= webView.getWidth() - edge) {
            scrollX = Math.max(1, Math.round(scrollPerFrame));
        }
        if (scrollX != 0 || scrollY != 0) {
            webView.scrollBy(scrollX, scrollY);
        }

        updateCursorPosition();
        if (frameTimeNanos - previousHoverDispatchNanos >= 33_000_000L) {
            dispatchHoverEvent();
            previousHoverDispatchNanos = frameTimeNanos;
        }
        choreographer.postFrameCallback(cursorFrameCallback);
    }

    private void updateCursorPosition() {
        if (cursorView != null) {
            cursorView.setCursorPosition(cursorX, cursorY);
        }
    }

    private void dispatchHoverEvent() {
        if (webView.getWidth() == 0 || webView.getHeight() == 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent hover = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_HOVER_MOVE, cursorX, cursorY, 0);
        hover.setSource(InputDevice.SOURCE_MOUSE);
        webView.dispatchGenericMotionEvent(hover);
        hover.recycle();
    }

    private void dispatchHoverExit() {
        if (webView == null || webView.getWidth() == 0 || webView.getHeight() == 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent exit = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_HOVER_EXIT, cursorX, cursorY, 0);
        exit.setSource(InputDevice.SOURCE_MOUSE);
        webView.dispatchGenericMotionEvent(exit);
        exit.recycle();
    }

    private void clickAtCursor() {
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        webView.dispatchTouchEvent(down);
        down.recycle();

        MotionEvent up = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, cursorX, cursorY, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        webView.dispatchTouchEvent(up);
        up.recycle();
        cursorView.pulse();
        dispatchHoverEvent();
        scheduleCursorHide();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveMode();
        } else {
            stopCursorAnimation();
        }
    }

    @Override
    protected void onPause() {
        stopCursorAnimation();
        super.onPause();
    }

    private void stopCursorAnimation() {
        cursorUpHeld = false;
        cursorDownHeld = false;
        cursorLeftHeld = false;
        cursorRightHeld = false;
        if (cursorFramePosted) {
            choreographer.removeFrameCallback(cursorFrameCallback);
            cursorFramePosted = false;
        }
        previousCursorFrameNanos = 0L;
        cursorMovementStartedNanos = 0L;
        if (cursorView != null && cursorView.getVisibility() == View.VISIBLE) {
            scheduleCursorHide();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        stopCursorAnimation();
        if (cursorView != null) {
            cursorView.removeCallbacks(hideCursor);
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String getVersionLabel() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            return versionName == null || versionName.isEmpty() ? "" : "v" + versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return "";
        }
    }

    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
