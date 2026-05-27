package com.bitbond.app.sharing;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.sharing.SharingModels.SharingState;

public interface SharingGateway {
    ApiResult<SharingState> getSharingState(AuthSession session);

    ApiResult<SharingState> setSharingPaused(AuthSession session, boolean paused);
}
