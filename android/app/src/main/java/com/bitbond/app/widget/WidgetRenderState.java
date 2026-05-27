package com.bitbond.app.widget;

public final class WidgetRenderState {
    private final boolean paired;
    private final String statusCode;
    private final String statusText;
    private final String updatedAt;
    private final boolean sharing;

    public WidgetRenderState(boolean paired, String statusCode, String statusText, String updatedAt, boolean sharing) {
        this.paired = paired;
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.updatedAt = updatedAt;
        this.sharing = sharing;
    }

    public boolean paired() {
        return paired;
    }

    public String statusCode() {
        return statusCode;
    }

    public String statusText() {
        return statusText;
    }

    public String updatedAt() {
        return updatedAt;
    }

    public boolean sharing() {
        return sharing;
    }
}
