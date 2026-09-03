package com.pepea.siteview;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AdBlocker {
    private static final byte[] EMPTY_BODY = new byte[0];
    private static final Map<String, String> EMPTY_HEADERS = Collections.emptyMap();
    private final Set<String> blockedHosts;

    AdBlocker(Context context) {
        Set<String> hosts = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("blocklist.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String host = line.trim().toLowerCase(Locale.US);
                if (!host.isEmpty() && !host.startsWith("#")) {
                    hosts.add(host);
                }
            }
        } catch (Exception ignored) {
            // A missing list should never stop the browser itself from working.
        }
        blockedHosts = Collections.unmodifiableSet(hosts);
    }

    WebResourceResponse intercept(WebResourceRequest request, boolean enabled) {
        if (!enabled || request.isForMainFrame()) {
            return null;
        }

        Uri uri = request.getUrl();
        String host = uri.getHost();
        if (host == null || !isBlocked(host)) {
            return null;
        }

        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                204,
                "No Content",
                EMPTY_HEADERS,
                new ByteArrayInputStream(EMPTY_BODY)
        );
    }

    private boolean isBlocked(String rawHost) {
        String host = rawHost.toLowerCase(Locale.US);
        if (blockedHosts.contains(host)) {
            return true;
        }
        int dot = host.indexOf('.');
        while (dot >= 0 && dot + 1 < host.length()) {
            host = host.substring(dot + 1);
            if (blockedHosts.contains(host)) {
                return true;
            }
            dot = host.indexOf('.');
        }
        return false;
    }
}
