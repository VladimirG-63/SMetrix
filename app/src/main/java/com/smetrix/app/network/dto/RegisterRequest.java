// app/src/main/java/com/smetrix/app/network/dto/RegisterRequest.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Тело запроса для регистрации нового пользователя POST /auth/register.
 */
public class RegisterRequest {

    @SerializedName("name")
    public final String name;

    @SerializedName("email")
    public final String email;

    @SerializedName("password")
    public final String password;

    public RegisterRequest(String name, String email, String password) {
        this.name     = name;
        this.email    = email;
        this.password = password;
    }
}
