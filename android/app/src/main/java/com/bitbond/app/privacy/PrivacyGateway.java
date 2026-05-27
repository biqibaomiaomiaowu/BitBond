package com.bitbond.app.privacy;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;
import com.bitbond.app.privacy.PrivacyModels.PrivacySettings;

import java.util.List;

public interface PrivacyGateway {
    ApiResult<PrivacySettings> getSettings(AuthSession session);

    ApiResult<PrivacySettings> updateSettings(AuthSession session, List<String> allowedStatuses);
}
