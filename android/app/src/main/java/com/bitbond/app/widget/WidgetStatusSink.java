package com.bitbond.app.widget;

import com.bitbond.app.status.StatusModels.PartnerStatus;

public interface WidgetStatusSink {
    void cachePartnerStatus(PartnerStatus partnerStatus);

    static WidgetStatusSink noop() {
        return partnerStatus -> {
        };
    }
}
