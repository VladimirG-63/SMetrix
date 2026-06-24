package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType = "Bearer";

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    @JsonProperty("access_token_expires_in")
    private long accessTokenExpiresIn;

    @JsonProperty("refresh_token_expires_in")
    private long refreshTokenExpiresIn;

    public AuthResponse() {}

    public AuthResponse(String accessToken) {
        this.accessToken = accessToken;
        this.refreshToken = accessToken;
    }

    public AuthResponse(String accessToken, String userId, String name, String email) {
        this.accessToken = accessToken;
        this.refreshToken = accessToken;
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public AuthResponse(String accessToken, String refreshToken,
                        long accessTokenExpiresIn, long refreshTokenExpiresIn,
                        String userId, String name, String email) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public long getAccessTokenExpiresIn() { return accessTokenExpiresIn; }
    public void setAccessTokenExpiresIn(long accessTokenExpiresIn) { this.accessTokenExpiresIn = accessTokenExpiresIn; }

    public long getRefreshTokenExpiresIn() { return refreshTokenExpiresIn; }
    public void setRefreshTokenExpiresIn(long refreshTokenExpiresIn) { this.refreshTokenExpiresIn = refreshTokenExpiresIn; }
}
