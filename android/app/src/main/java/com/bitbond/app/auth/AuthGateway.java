package com.bitbond.app.auth;

import com.bitbond.app.api.ApiResult;

public interface AuthGateway {
    ApiResult<AuthSession> ensureSession();
}
