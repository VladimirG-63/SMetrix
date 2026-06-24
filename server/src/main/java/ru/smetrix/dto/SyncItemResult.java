package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SyncItemResult {
    public String id;
    public int status;
    public long version;

    @JsonProperty("updated_at")
    public String updatedAt;

    @JsonProperty("error_code")
    public String errorCode;

    @JsonProperty("server_snapshot")
    public String serverSnapshot;

    public SyncItemResult(String id, int status, long version, String updatedAt,
                          String errorCode, String serverSnapshot) {
        this.id = id;
        this.status = status;
        this.version = version;
        this.updatedAt = updatedAt;
        this.errorCode = errorCode;
        this.serverSnapshot = serverSnapshot;
    }
}
