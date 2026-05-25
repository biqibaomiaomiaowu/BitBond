package com.bitbond.app.status;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;

public interface StatusUploadTrigger {
    ApiResult<String> uploadDetectedStatus(AuthSession session);
}
