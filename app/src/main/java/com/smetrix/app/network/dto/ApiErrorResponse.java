package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/** Единый JSON-контракт ошибок backend API v1. */
public class ApiErrorResponse {
    @SerializedName("error")
    public ErrorBody error;

    public static class ErrorBody {
        @SerializedName("code")
        public String code;

        @SerializedName("message")
        public String message;

        @SerializedName("entity_id")
        public String entityId;
    }
}
