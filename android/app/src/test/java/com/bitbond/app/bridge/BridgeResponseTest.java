package com.bitbond.app.bridge;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public class BridgeResponseTest {
    @Test
    public void successJsonWrapsDataWithOkTrue() throws Exception {
        String json = BridgeResponse.success(new JSONObject().put("x", 1)).toJson();

        assertEquals("{\"ok\":true,\"data\":{\"x\":1}}", json);
    }

    @Test
    public void errorJsonWrapsCodeAndMessage() {
        String json = BridgeResponse.error("invalid_payload", "Invite code is required").toJson();

        assertEquals(
                "{\"ok\":false,\"error\":{\"code\":\"invalid_payload\",\"message\":\"Invite code is required\"}}",
                json);
    }
}
