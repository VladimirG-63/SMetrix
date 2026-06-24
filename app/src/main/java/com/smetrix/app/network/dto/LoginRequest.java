// app/src/main/java/com/smetrix/app/network/dto/LoginRequest.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Тело запроса для входа в систему POST /auth/login.
 */
public class LoginRequest {

    @SerializedName("email")
    public final String email;

    @SerializedName("password")
    public final String password;

    public LoginRequest(String email, String password) {
        this.email    = email;
        this.password = password;
    }
}
