package com.bitbond.app.config;

import com.bitbond.app.BuildConfig;

import java.net.URI;
import java.net.URISyntaxException;

public final class SupabaseConfig {
    private static final Values CURRENT = fromValues(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY);

    private SupabaseConfig() {
    }

    public static Values current() {
        return CURRENT;
    }

    public static Values fromValues(String url, String anonKey) {
        return new Values(url, anonKey);
    }

    public static boolean isConfigured() {
        return current().isConfigured();
    }

    public static String url() {
        return current().url();
    }

    public static String anonKey() {
        return current().anonKey();
    }

    public static String host() {
        return current().host();
    }

    public static final class Values {
        private final String url;
        private final String anonKey;
        private final String host;
        private final boolean validUrl;

        private Values(String url, String anonKey) {
            this.url = normalize(url);
            this.anonKey = normalize(anonKey);
            URI uri = parseUri(this.url);
            this.host = parseHost(uri);
            this.validUrl = uri != null
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && !this.host.isEmpty();
        }

        public boolean isConfigured() {
            return validUrl && !anonKey.isEmpty();
        }

        public String url() {
            return url;
        }

        public String anonKey() {
            return anonKey;
        }

        public String host() {
            return host;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static URI parseUri(String url) {
        if (url.isEmpty()) {
            return null;
        }

        try {
            return new URI(url);
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static String parseHost(URI uri) {
        if (uri == null) {
            return "";
        }

        String host = uri.getHost();
        return host == null ? "" : host;
    }
}
