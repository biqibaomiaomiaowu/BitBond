package com.bitbond.app.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;

public class BridgePayloadTest {
    @Test
    public void rpcPayloadMapsJsFieldNamesToSupabaseFieldNames() throws Exception {
        JSONObject rpcPayload = BridgePayload.from(new JSONObject()
                .put("code", "ABCD12")
                .put("avatarId", "avatar_cat"))
                .toRpcPayload();

        assertEquals("ABCD12", rpcPayload.getString("invite_code"));
        assertEquals("avatar_cat", rpcPayload.getString("next_avatar_id"));
        assertFalse(rpcPayload.has("code"));
        assertFalse(rpcPayload.has("avatarId"));
    }

    @Test
    public void blankInviteCodeIsRejectedWithoutLeakingSecrets() throws Exception {
        JSONObject rawPayload = new JSONObject()
                .put("code", "   ")
                .put("access_token", "secret-token-value");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BridgePayload.from(rawPayload));

        assertEquals("invite code is required", error.getMessage());
        assertFalse(error.getMessage().contains("secret-token-value"));
    }

    @Test
    public void inviteCodeAndAvatarIdMustBeStrings() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgePayload.from(new JSONObject()
                        .put("code", new JSONObject().put("access_token", "secret-token-value"))));

        assertThrows(
                IllegalArgumentException.class,
                () -> BridgePayload.from(new JSONObject()
                        .put("code", "ABCD12")
                        .put("avatarId", new JSONArray().put(new JSONObject().put("secret", "x")))));
    }

    @Test
    public void safeObjectRemovesSecretLikeFields() throws Exception {
        JSONObject safePayload = BridgePayload.safeObject(new JSONObject()
                .put("code", "ABCD12")
                .put("access_token", "secret-token-value"));

        assertEquals("ABCD12", safePayload.getString("code"));
        assertFalse(safePayload.has("access_token"));
        assertFalse(safePayload.toString().contains("secret-token-value"));
    }

    @Test
    public void safeObjectRemovesNestedSecretLikeFields() throws Exception {
        JSONObject safePayload = BridgePayload.safeObject(new JSONObject()
                .put("nested", new JSONObject()
                        .put("refresh_token", "nested-secret")
                        .put("visible", "ok"))
                .put("items", new JSONArray()
                        .put(new JSONObject()
                                .put("authorization", "nested-auth")
                                .put("label", "safe"))));

        assertEquals("ok", safePayload.getJSONObject("nested").getString("visible"));
        assertFalse(safePayload.getJSONObject("nested").has("refresh_token"));
        assertEquals("safe", safePayload.getJSONArray("items").getJSONObject(0).getString("label"));
        assertFalse(safePayload.getJSONArray("items").getJSONObject(0).has("authorization"));
        assertFalse(safePayload.toString().contains("nested-secret"));
        assertFalse(safePayload.toString().contains("nested-auth"));
    }

    @Test
    public void safeObjectKeepsOriginalPayloadUntouched() throws Exception {
        JSONObject rawPayload = new JSONObject()
                .put("code", "ABCD12")
                .put("access_token", "secret-token-value");

        BridgePayload.safeObject(rawPayload);

        assertTrue(rawPayload.has("access_token"));
    }
}
