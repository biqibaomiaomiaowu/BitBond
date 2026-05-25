package com.bitbond.app.api;

import org.json.JSONObject;

public interface SupabaseRpcClient {
    ApiResult<JSONObject> rpc(String accessToken, String functionName, JSONObject payload);
}
