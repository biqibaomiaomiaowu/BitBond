package com.bitbond.app.account;

import com.bitbond.app.account.AccountModels.DeleteAccountResult;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;

public interface AccountGateway {
    ApiResult<DeleteAccountResult> deleteAccount(AuthSession session);
}
