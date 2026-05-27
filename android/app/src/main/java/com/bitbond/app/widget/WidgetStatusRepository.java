package com.bitbond.app.widget;

import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;

import java.util.Objects;

import org.json.JSONObject;

public final class WidgetStatusRepository implements WidgetStatusGateway {
    private static final String GET_WIDGET_STATUS_RPC = "get_widget_status";

    private final SupabaseRpcClient rpcClient;

    public WidgetStatusRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<WidgetStatusSnapshot> getWidgetStatus(AuthSession session) {
        Objects.requireNonNull(session, "session");

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    GET_WIDGET_STATUS_RPC,
                    new JSONObject());
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            return ApiResult.success(WidgetStatusSnapshot.fromWidgetStatusPayload(result.value()));
        } catch (RuntimeException exception) {
            return ApiResult.error(new ApiError("widget_status_json_error", "Widget status response could not be parsed"));
        }
    }
}
