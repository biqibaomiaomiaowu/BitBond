package com.bitbond.app.analytics;

import com.bitbond.app.analytics.AnalyticsModels.AnalyticsEventResult;
import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;

import org.json.JSONObject;

public interface AnalyticsGateway {
    ApiResult<AnalyticsEventResult> recordEvent(AuthSession session, String eventName, JSONObject properties);
}
