package com.pepea.siteview;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ZoomDiagnostics {
    private static final String TAG = "CableZoomDiagnostics";
    private static final int MAX_PENDING = 100;
    private static final String SNAPSHOT_SCRIPT = "(function(){try{var v=window.visualViewport,h=document.documentElement,b=document.body,m=document.querySelector('meta[name=viewport]');return JSON.stringify({url:location.origin+location.pathname,ready:document.readyState,innerWidth:innerWidth,innerHeight:innerHeight,outerWidth:outerWidth,screenWidth:screen.width,screenHeight:screen.height,dpr:devicePixelRatio,viewportScale:v?v.scale:null,viewportWidth:v?v.width:null,viewportHeight:v?v.height:null,viewportOffsetLeft:v?v.offsetLeft:null,documentWidth:h?h.clientWidth:null,scrollWidth:h?h.scrollWidth:null,htmlZoom:h?getComputedStyle(h).zoom:null,bodyZoom:b?getComputedStyle(b).zoom:null,meta:m?m.content:null,historyLength:history.length});}catch(e){return JSON.stringify({error:String(e)})}})()";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final String session = UUID.randomUUID().toString();
    private final String endpoint;
    private final String token;
    private boolean draining;
    private long sequence;
    private Process logcat;

    ZoomDiagnostics(String endpoint, String token) {
        this.endpoint = endpoint;
        this.token = token;
    }

    boolean enabled() {
        return !endpoint.isEmpty() && !token.isEmpty();
    }

    void startLogcat() {
        if (!enabled()) return;
        sender.execute(() -> {
            try {
                logcat = new ProcessBuilder("logcat", "-v", "threadtime", "--pid=" + android.os.Process.myPid())
                        .redirectErrorStream(true).start();
                Thread reader = new Thread(() -> {
                    try (BufferedReader input = new BufferedReader(new InputStreamReader(logcat.getInputStream()))) {
                        String line;
                        while ((line = input.readLine()) != null) {
                            // Each upload itself emits TrafficStats, so forwarding those
                            // lines would create an unbounded log-upload feedback loop.
                            if (line.contains(" D TrafficStats:")) continue;
                            JSONObject event = new JSONObject();
                            event.put("session", session);
                            event.put("timeMs", System.currentTimeMillis());
                            event.put("phase", "logcat");
                            event.put("line", line);
                            enqueue(event.toString());
                        }
                    } catch (Exception error) {
                        Log.w(TAG, "Logcat capture ended", error);
                    }
                }, "cable-logcat-reader");
                reader.setDaemon(true);
                reader.start();
            } catch (Exception error) {
                Log.w(TAG, "Logcat unavailable", error);
            }
        });
    }

    void event(String phase, WebView view, int selectedZoom) {
        if (!enabled()) return;
        try {
            JSONObject event = base(phase, view, selectedZoom);
            enqueue(event.toString());
        } catch (JSONException error) {
            Log.w(TAG, "Cannot encode zoom event", error);
        }
    }

    void snapshot(String phase, WebView view, int selectedZoom, long delayMs) {
        if (!enabled() || view == null) return;
        handler.postDelayed(() -> {
            if (view.getParent() == null) return;
            try {
                JSONObject event = base(phase, view, selectedZoom);
                event.put("delayMs", delayMs);
                view.evaluateJavascript(SNAPSHOT_SCRIPT, result -> {
                    try {
                        Object decoded = result == null ? null : new JSONTokener(result).nextValue();
                        event.put("js", decoded instanceof String ? new JSONObject((String) decoded) : JSONObject.NULL);
                    } catch (JSONException error) {
                        try { event.put("jsRaw", result); } catch (JSONException ignored) { return; }
                    }
                    enqueue(event.toString());
                });
            } catch (JSONException error) {
                Log.w(TAG, "Cannot encode zoom snapshot", error);
            }
        }, delayMs);
    }

    private JSONObject base(String phase, WebView view, int selectedZoom) throws JSONException {
        JSONObject event = new JSONObject();
        event.put("session", session);
        event.put("seq", ++sequence);
        event.put("timeMs", System.currentTimeMillis());
        event.put("uptimeMs", SystemClock.uptimeMillis());
        event.put("phase", phase);
        event.put("version", BuildConfig.VERSION_NAME);
        event.put("selectedZoom", selectedZoom);
        event.put("webViewScale", view == null ? JSONObject.NULL : view.getScale());
        event.put("webViewWidth", view == null ? JSONObject.NULL : view.getWidth());
        event.put("webViewHeight", view == null ? JSONObject.NULL : view.getHeight());
        event.put("surfaceScaleX", view == null ? JSONObject.NULL : view.getScaleX());
        event.put("surfaceScaleY", view == null ? JSONObject.NULL : view.getScaleY());
        event.put("displayWidth", view == null ? JSONObject.NULL : view.getWidth() * view.getScaleX());
        event.put("displayHeight", view == null ? JSONObject.NULL : view.getHeight() * view.getScaleY());
        event.put("parentWidth", view == null || view.getParent() == null ? JSONObject.NULL
                : ((android.view.View) view.getParent()).getWidth());
        event.put("parentHeight", view == null || view.getParent() == null ? JSONObject.NULL
                : ((android.view.View) view.getParent()).getHeight());
        event.put("density", view == null ? JSONObject.NULL : view.getResources().getDisplayMetrics().density);
        event.put("url", view == null ? "" : safeUrl(view.getUrl()));
        event.put("sdk", Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 26 && WebView.getCurrentWebViewPackage() != null) {
            event.put("webViewPackage", WebView.getCurrentWebViewPackage().versionName);
        }
        return event;
    }

    private String safeUrl(String value) {
        if (value == null) return "";
        android.net.Uri uri = android.net.Uri.parse(value);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
    }

    private synchronized void enqueue(String value) {
        if (pending.size() == MAX_PENDING) pending.removeFirst();
        pending.addLast(value);
        if (!draining) {
            draining = true;
            sender.execute(this::drain);
        }
    }

    private void drain() {
        while (true) {
            String value;
            synchronized (this) {
                value = pending.pollFirst();
                if (value == null) { draining = false; return; }
            }
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Diagnostic-Token", token);
                connection.setConnectTimeout(1500);
                connection.setReadTimeout(1500);
                connection.setDoOutput(true);
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                int status = connection.getResponseCode();
                connection.disconnect();
                // Do not log upload failures here: those logs would be uploaded too.
            } catch (Exception error) {
                // The bounded queue protects the app when the PC is offline.
            }
        }
    }

    void close() {
        if (logcat != null) logcat.destroy();
        sender.shutdown();
    }
}
