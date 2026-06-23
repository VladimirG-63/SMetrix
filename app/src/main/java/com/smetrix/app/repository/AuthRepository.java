
package com.smetrix.app.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.smetrix.app.network.ApiService;
import com.smetrix.app.network.dto.TokenResponse;
import com.smetrix.app.utils.SecurePrefsHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;





























public class AuthRepository {

    private static final String TAG = "AuthRepository";


    public static final String PREFS_NAME = "smetrix_auth_prefs";


    public static final String KEY_ACCESS_TOKEN = "access_token";


    public static final String KEY_REFRESH_TOKEN = "refresh_token";


    public static final String KEY_USER_ID = "user_id";


    public static final String KEY_ACCESS_EXPIRES_AT = "access_token_expires_at";


    public static final String KEY_GUEST_MODE = "guest_mode";


    public static final String GUEST_USER_ID = "00000000-0000-7000-8000-000000000001";




    private final SharedPreferences prefs;


    private final ApiService apiService;









    public AuthRepository(Context context, ApiService apiService) {
        this.prefs      = SecurePrefsHelper.get(context.getApplicationContext());
        this.apiService = apiService;
    }








    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }






    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }






    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }









    public boolean isLoggedIn() {
        String accessToken  = getAccessToken();
        String refreshToken = getRefreshToken();
        String userId       = getUserId();
        return accessToken != null && !accessToken.isEmpty()
                && refreshToken != null && !refreshToken.isEmpty()
                && userId != null && !userId.isEmpty();
    }

    public boolean isGuest() {
        return prefs.getBoolean(KEY_GUEST_MODE, false)
                && GUEST_USER_ID.equals(getUserId());
    }


    public boolean hasActiveSession() {
        return isLoggedIn() || isGuest();
    }


    public void enterGuestMode() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_ACCESS_EXPIRES_AT)
                .putString(KEY_USER_ID, GUEST_USER_ID)
                .putBoolean(KEY_GUEST_MODE, true)
                .apply();
    }









    public boolean isAccessTokenValid() {
        long expiresAt = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L);
        long bufferMs  = 60_000L;
        return System.currentTimeMillis() < (expiresAt - bufferMs);
    }











    public void saveTokens(TokenResponse tokenResponse) {
        if (tokenResponse == null) {
            Log.w(TAG, "saveTokens: tokenResponse равен null, токены не сохранены.");
            return;
        }
        long expiresInSeconds = tokenResponse.accessTokenExpiresIn > 0
                ? tokenResponse.accessTokenExpiresIn
                : 900L;
        long expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L;

        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
                .putLong(KEY_ACCESS_EXPIRES_AT, expiresAt)
                .remove(KEY_GUEST_MODE);

        if (tokenResponse.refreshToken != null && !tokenResponse.refreshToken.isEmpty()) {
            editor.putString(KEY_REFRESH_TOKEN, tokenResponse.refreshToken);
        }
        if (tokenResponse.userId != null && !tokenResponse.userId.isEmpty()) {
            editor.putString(KEY_USER_ID, tokenResponse.userId);
        }

        editor.apply();

        Log.d(TAG, "Токены сохранены. Access-токен истекает через "
                + expiresInSeconds + " сек.");
    }






    public void saveUserId(String userId) {
        prefs.edit().putString(KEY_USER_ID, userId).apply();
        Log.d(TAG, "Идентификатор пользователя сохранён.");
    }








    public void clearSession() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_ACCESS_EXPIRES_AT)
                .remove(KEY_GUEST_MODE)
                .apply();
        Log.d(TAG, "Сессия очищена. Пользователь разлогинен.");
    }
















    public boolean refreshTokenSync() throws IOException {
        String currentRefreshToken = getRefreshToken();
        if (currentRefreshToken == null || currentRefreshToken.isEmpty()) {
            Log.w(TAG, "refreshTokenSync: refresh-токен отсутствует, обновление невозможно.");
            return false;
        }

        Map<String, String> body = new HashMap<>();
        body.put("refresh_token", currentRefreshToken);

        Response<TokenResponse> response = apiService.refreshToken(body).execute();

        if (response.isSuccessful() && response.body() != null) {
            saveTokens(response.body());
            Log.d(TAG, "Access-токен успешно обновлён через refresh.");
            return true;
        } else {
            Log.w(TAG, "refreshTokenSync: сервер отклонил обновление токена, code="
                    + response.code());
            return false;
        }
    }
}
