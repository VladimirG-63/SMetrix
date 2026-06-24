// app/src/main/java/com/smetrix/app/network/dto/ForgotPasswordRequest.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Тело запроса для сброса пароля POST /auth/forgot-password.
 */
public class ForgotPasswordRequest {

    @SerializedName("email")
    public final String email;

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }
}
