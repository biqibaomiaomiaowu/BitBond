package com.bitbond.app.api;

import java.util.Objects;

public final class ApiResult<T> {
    private final T value;
    private final ApiError error;

    private ApiResult(T value, ApiError error) {
        this.value = value;
        this.error = error;
    }

    public static <T> ApiResult<T> success(T value) {
        return new ApiResult<>(Objects.requireNonNull(value, "value"), null);
    }

    public static <T> ApiResult<T> error(ApiError error) {
        return new ApiResult<>(null, Objects.requireNonNull(error, "error"));
    }

    public boolean isSuccess() {
        return error == null;
    }

    public T value() {
        return value;
    }

    public ApiError error() {
        return error;
    }
}
