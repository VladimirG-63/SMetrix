package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

public class UserProfileRequest {

    @SerializedName("name")
    public String name;

    @SerializedName("email")
    public String email;

    public UserProfileRequest(String name, String email) {
        this.name = name;
        this.email = email;
    }
}
