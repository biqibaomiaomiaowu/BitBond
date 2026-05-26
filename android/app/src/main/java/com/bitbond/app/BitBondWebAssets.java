package com.bitbond.app;

import java.net.URI;
import java.net.URISyntaxException;

public final class BitBondWebAssets {
    public static final String ORIGIN = "https://bitbond.local";
    public static final String INDEX_URL = ORIGIN + "/index.html";

    private static final String HOST = "bitbond.local";

    private BitBondWebAssets() {
    }

    public static String assetPathForUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | NullPointerException exception) {
            return null;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOST.equalsIgnoreCase(uri.getHost())) {
            return null;
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "index.html";
        }

        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.isEmpty() || hasUnsafeSegment(path)) {
            return null;
        }

        return path;
    }

    public static String mimeTypeForAssetPath(String assetPath) {
        String normalized = assetPath == null ? "" : assetPath.toLowerCase();
        if (normalized.endsWith(".html")) {
            return "text/html";
        }
        if (normalized.endsWith(".js") || normalized.endsWith(".mjs")) {
            return "application/javascript";
        }
        if (normalized.endsWith(".css")) {
            return "text/css";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        if (normalized.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (normalized.endsWith(".json")) {
            return "application/json";
        }
        return "application/octet-stream";
    }

    private static boolean hasUnsafeSegment(String path) {
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment) || segment.contains("\\")) {
                return true;
            }
        }
        return false;
    }
}
