package com.bitbond.app.status;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.status.StatusModels.PartnerStatus;

public interface StatusGateway {
    ApiResult<PartnerStatus> getPartnerStatus(AuthSession session);
}
