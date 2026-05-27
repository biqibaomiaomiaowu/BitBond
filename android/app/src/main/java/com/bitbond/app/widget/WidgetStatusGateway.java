package com.bitbond.app.widget;

import com.bitbond.app.api.ApiResult;
import com.bitbond.app.auth.AuthSession;

public interface WidgetStatusGateway {
    ApiResult<WidgetStatusSnapshot> getWidgetStatus(AuthSession session);
}
