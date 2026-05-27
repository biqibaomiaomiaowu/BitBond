package com.bitbond.app.account;

import com.bitbond.app.account.AccountModels.DeleteAccountResult;
import com.bitbond.app.api.ApiError;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.api.SupabaseRpcClient;
import com.bitbond.app.auth.AuthSession;

import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

public final class AccountRepository implements AccountGateway {
    private static final String DELETE_ACCOUNT_DATA_RPC = "delete_account_data";

    private final SupabaseRpcClient rpcClient;

    public AccountRepository(SupabaseRpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    @Override
    public ApiResult<DeleteAccountResult> deleteAccount(AuthSession session) {
        Objects.requireNonNull(session, "session");

        try {
            ApiResult<JSONObject> result = rpcClient.rpc(
                    session.accessToken(),
                    DELETE_ACCOUNT_DATA_RPC,
                    new JSONObject());
            if (!result.isSuccess()) {
                return ApiResult.error(result.error());
            }

            boolean deleted = result.value().getBoolean("deleted");
            return ApiResult.success(new DeleteAccountResult(
                    deleted,
                    new JSONObject().put("deleted", deleted).toString()));
        } catch (JSONException | IllegalArgumentException exception) {
            return ApiResult.error(new ApiError("account_json_error", "Account response could not be parsed"));
        }
    }
}
