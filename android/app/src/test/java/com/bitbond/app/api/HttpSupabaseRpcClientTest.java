package com.bitbond.app.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Test;

public class HttpSupabaseRpcClientTest {
    @Test
    public void rpcPostsToFunctionEndpointWithSafeHeaders() throws Exception {
        RecordingTransport transport = new RecordingTransport(200, "{\"value\":1}");
        HttpSupabaseRpcClient client = new HttpSupabaseRpcClient("https://api.example.test", "anon-key", transport);

        ApiResult<JSONObject> result = client.rpc("user-token", "ensure_user_profile", new JSONObject().put("x", 1));

        assertTrue(result.isSuccess());
        assertEquals("https://api.example.test/rest/v1/rpc/ensure_user_profile", transport.url);
        assertEquals("anon-key", transport.headers.get("apikey"));
        assertEquals("Bearer user-token", transport.headers.get("Authorization"));
        assertEquals("application/json", transport.headers.get("Content-Type"));
        assertEquals("{\"x\":1}", transport.body);
        assertEquals(1, result.value().getInt("value"));
    }

    @Test
    public void rpcMapsNon2xxToApiErrorWithoutLeakingBody() throws Exception {
        RecordingTransport transport = new RecordingTransport(401, "{\"message\":\"bad jwt\"}");
        HttpSupabaseRpcClient client = new HttpSupabaseRpcClient("https://api.example.test", "anon-key", transport);

        ApiResult<JSONObject> result = client.rpc("user-token", "x", new JSONObject());

        assertFalse(result.isSuccess());
        assertEquals("supabase_http_401", result.error().code());
        assertFalse(result.error().message().contains("bad jwt"));
    }

    @Test
    public void rpcMapsInvalidJsonResponseToApiError() throws Exception {
        RecordingTransport transport = new RecordingTransport(200, "not json");
        HttpSupabaseRpcClient client = new HttpSupabaseRpcClient("https://api.example.test", "anon-key", transport);

        ApiResult<JSONObject> result = client.rpc("user-token", "x", new JSONObject());

        assertFalse(result.isSuccess());
        assertEquals("supabase_json_error", result.error().code());
    }

    @Test
    public void rpcMapsNetworkFailureToApiError() {
        HttpSupabaseRpcClient client = new HttpSupabaseRpcClient(
                "https://api.example.test",
                "anon-key",
                new NetworkFailureTransport());

        ApiResult<JSONObject> result = client.rpc("user-token", "x", new JSONObject());

        assertFalse(result.isSuccess());
        assertEquals("supabase_network_error", result.error().code());
    }

    @Test
    public void rpcUsesEmptyPayloadForNullPayloadAndStripsTrailingSlash() throws Exception {
        RecordingTransport transport = new RecordingTransport(200, "{}");
        HttpSupabaseRpcClient client = new HttpSupabaseRpcClient("https://api.example.test/", "anon-key", transport);

        ApiResult<JSONObject> result = client.rpc("user-token", "x", null);

        assertTrue(result.isSuccess());
        assertEquals("https://api.example.test/rest/v1/rpc/x", transport.url);
        assertEquals("{}", transport.body);
    }

    @Test
    public void rpcRejectsUnsafeFunctionNameWithoutPosting() {
        RecordingTransport transport = new RecordingTransport(200, "{}");
        HttpSupabaseRpcClient client = new HttpSupabaseRpcClient("https://api.example.test", "anon-key", transport);

        ApiResult<JSONObject> result = client.rpc("user-token", "x/../users?select=*", new JSONObject());

        assertFalse(result.isSuccess());
        assertEquals("supabase_invalid_function_name", result.error().code());
        assertFalse(transport.wasCalled());
    }

    @Test
    public void defaultUrlConnectionTransportDisconnectsWhenRequestWriteFails() throws Exception {
        RecordingHttpURLConnection connection = new RecordingHttpURLConnection(new URL("https://api.example.test/rest/v1/rpc/x"));
        Transport transport = HttpSupabaseRpcClient.urlConnectionTransport(url -> connection);

        try {
            transport.post(
                    "https://api.example.test/rest/v1/rpc/x",
                    Collections.singletonMap("Content-Type", "application/json"),
                    "{}");
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("write failed", expected.getMessage());
        }

        assertTrue(connection.disconnected);
    }

    private static final class RecordingTransport implements Transport {
        private final int statusCode;
        private final String responseBody;
        private String url;
        private Map<String, String> headers;
        private String body;

        private RecordingTransport(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public TransportResponse post(String url, Map<String, String> headers, String body) throws IOException {
            this.url = url;
            this.headers = headers;
            this.body = body;
            return new TransportResponse(statusCode, responseBody);
        }

        private boolean wasCalled() {
            return url != null;
        }
    }

    private static final class NetworkFailureTransport implements Transport {
        @Override
        public TransportResponse post(String url, Map<String, String> headers, String body) throws IOException {
            throw new IOException("network unavailable");
        }
    }

    private static final class RecordingHttpURLConnection extends HttpURLConnection {
        private boolean disconnected;

        private RecordingHttpURLConnection(URL url) {
            super(url);
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("write failed");
        }
    }
}
