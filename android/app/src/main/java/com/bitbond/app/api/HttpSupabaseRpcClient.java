package com.bitbond.app.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

public final class HttpSupabaseRpcClient implements SupabaseRpcClient {
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final Pattern RPC_FUNCTION_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final String baseUrl;
    private final String anonKey;
    private final Transport transport;

    public HttpSupabaseRpcClient(String baseUrl, String anonKey) {
        this(baseUrl, anonKey, new UrlConnectionTransport());
    }

    public HttpSupabaseRpcClient(String baseUrl, String anonKey, Transport transport) {
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl").trim());
        this.anonKey = Objects.requireNonNull(anonKey, "anonKey").trim();
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public ApiResult<JSONObject> rpc(String accessToken, String functionName, JSONObject payload) {
        String normalizedFunctionName = normalize(functionName);
        if (!isSafeFunctionName(normalizedFunctionName)) {
            return ApiResult.error(new ApiError("supabase_invalid_function_name", "RPC function name is invalid"));
        }

        String url = baseUrl + "/rest/v1/rpc/" + normalizedFunctionName;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("apikey", anonKey);
        headers.put("Authorization", "Bearer " + normalize(accessToken));
        headers.put("Content-Type", CONTENT_TYPE_JSON);
        headers.put("Accept", CONTENT_TYPE_JSON);

        String body = payloadBody(payload);

        try {
            TransportResponse response = transport.post(url, headers, body);
            if (response.statusCode < 200 || response.statusCode >= 300) {
                return ApiResult.error(new ApiError("supabase_http_" + response.statusCode, "RPC request failed"));
            }

            return ApiResult.success(parseBody(response.body));
        } catch (IOException exception) {
            return ApiResult.error(new ApiError("supabase_network_error", "RPC request failed"));
        } catch (JSONException exception) {
            return ApiResult.error(new ApiError("supabase_json_error", "RPC response could not be parsed"));
        }
    }

    private static JSONObject parseBody(String body) throws JSONException {
        String normalized = normalize(body);
        if (normalized.isEmpty()) {
            return new JSONObject();
        }

        return new JSONObject(normalized);
    }

    private static String payloadBody(JSONObject payload) {
        if (payload == null) {
            return "{}";
        }

        return payload.toString();
    }

    private static String stripTrailingSlash(String value) {
        String normalized = normalize(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isSafeFunctionName(String value) {
        return RPC_FUNCTION_NAME.matcher(value).matches();
    }

    private static final class UrlConnectionTransport implements Transport {
        @Override
        public TransportResponse post(String url, Map<String, String> headers, String body) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }

            byte[] requestBytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
            }

            int statusCode = connection.getResponseCode();
            InputStream responseStream = statusCode >= 200 && statusCode < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readBody(responseStream);
            connection.disconnect();
            return new TransportResponse(statusCode, responseBody);
        }

        private static String readBody(InputStream inputStream) throws IOException {
            if (inputStream == null) {
                return "";
            }

            try (InputStream stream = inputStream;
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        }
    }
}
