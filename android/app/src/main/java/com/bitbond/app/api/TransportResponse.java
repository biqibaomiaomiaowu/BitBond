package com.bitbond.app.api;

public final class TransportResponse {
    public final int statusCode;
    public final String body;

    public TransportResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }
}
