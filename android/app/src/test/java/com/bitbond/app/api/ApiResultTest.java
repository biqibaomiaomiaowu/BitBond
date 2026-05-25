package com.bitbond.app.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ApiResultTest {
    @Test
    public void successResultExposesValueAndNoError() {
        JSONObject value = new JSONObject();

        ApiResult<JSONObject> result = ApiResult.success(value);

        assertTrue(result.isSuccess());
        assertSame(value, result.value());
        assertNull(result.error());
    }

    @Test
    public void errorResultExposesErrorAndNoValue() {
        ApiError error = new ApiError("supabase_http_401", "RPC request failed");

        ApiResult<JSONObject> result = ApiResult.error(error);

        assertFalse(result.isSuccess());
        assertNull(result.value());
        assertSame(error, result.error());
    }

    @Test
    public void errorResultDoesNotExposeResponseBodyByDefault() {
        ApiError error = new ApiError("supabase_http_401", "{\"message\":\"bad jwt\"}");

        ApiResult<JSONObject> result = ApiResult.error(error);

        assertEquals("supabase_http_401", result.error().code());
        assertFalse(result.error().message().contains("bad jwt"));
    }
}
