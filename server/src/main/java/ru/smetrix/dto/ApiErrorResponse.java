package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Единый контракт ошибок API v1. */
public class ApiErrorResponse {

    @JsonProperty("error")
    public final ErrorBody error;

    public ApiErrorResponse(ErrorBody error) {
        this.error = error;
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ErrorBody(code, message, null));
    }

    public static ApiErrorResponse of(String code, String message, String entityId) {
        return new ApiErrorResponse(new ErrorBody(code, message, entityId));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorBody {
        public final String code;
        public final String message;

        @JsonProperty("entity_id")
        public final String entityId;

        public ErrorBody(String code, String message, String entityId) {
            this.code = code;
            this.message = message;
            this.entityId = entityId;
        }
    }
}
