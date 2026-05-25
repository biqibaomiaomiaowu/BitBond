package com.bitbond.app.status;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.status.StatusModels.CurrentStatusResult;

import java.time.Instant;

public interface StatusUploader {
    ApiResult<CurrentStatusResult> uploadCurrentStatus(
            AuthSession session,
            String statusCode,
            Instant statusUpdatedAt);
}
