// app/src/main/java/com/smetrix/app/network/dto/RegisterResponse.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Ответ сервера при успешной регистрации.
 * Сервер сразу выдаёт токены, чтобы пользователь не делал отдельный вход.
 */
public class RegisterResponse {

    @SerializedName("user_id")
    public String userId;

    @SerializedName("name")
    public String name;

    @SerializedName("email")
    public String email;

    @SerializedName("access_token")
    public String accessToken;

    @SerializedName("refresh_token")
    public String refreshToken;

    @SerializedName("access_token_expires_in")
    public long accessTokenExpiresIn;
}
