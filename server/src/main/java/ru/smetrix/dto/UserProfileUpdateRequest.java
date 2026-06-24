package ru.smetrix.dto;

public class UserProfileUpdateRequest {
    private String name;
    private String email;

    public UserProfileUpdateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
