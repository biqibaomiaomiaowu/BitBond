package com.bitbond.app.pairing;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.pairing.PairingModels.CoupleState;
import com.bitbond.app.pairing.PairingModels.PairInvite;

public interface PairingGateway {
    ApiResult<PairInvite> createInvite(AuthSession session);

    ApiResult<CoupleState> acceptInvite(AuthSession session, String code);

    ApiResult<CoupleState> getCurrentCouple(AuthSession session);

    ApiResult<Boolean> unlink(AuthSession session);
}
