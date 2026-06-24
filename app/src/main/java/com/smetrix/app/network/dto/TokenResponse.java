// app/src/main/java/com/smetrix/app/network/dto/TokenResponse.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Ответ сервера при обновлении токенов аутентификации.
 *
 * <p>Используется в {@code AuthInterceptor} при автоматическом
 * обновлении токена после получения ответа 401 Unauthorized.
 * Оба токена сохраняются в {@code EncryptedSharedPreferences}.
 */
public class TokenResponse {

    /** Новый токен доступа (Bearer-токен для API-запросов). */
    @SerializedName("access_token")
    public String accessToken;

    /** Новый токен обновления (для следующего запроса refresh). */
    @SerializedName("refresh_token")
    public String refreshToken;

    /** Идентификатор авторизованного пользователя. */
    @SerializedName("user_id")
    public String userId;

    /** Имя пользователя из профиля. */
    @SerializedName("name")
    public String name;

    /** Email пользователя из профиля. */
    @SerializedName("email")
    public String email;

    /**
     * Срок действия токена доступа в секундах от момента выдачи.
     * Типовое значение: 3600 (1 час).
     */
    @SerializedName("access_token_expires_in")
    public long accessTokenExpiresIn;

    /**
     * Срок действия токена обновления в секундах от момента выдачи.
     * Типовое значение: 2592000 (30 дней).
     */
    @SerializedName("refresh_token_expires_in")
    public long refreshTokenExpiresIn;
}
