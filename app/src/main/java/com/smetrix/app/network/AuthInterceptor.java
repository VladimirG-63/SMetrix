// app/src/main/java/com/smetrix/app/network/AuthInterceptor.java
package com.smetrix.app.network;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.smetrix.app.network.dto.TokenResponse;
import com.smetrix.app.repository.AuthRepository;
import com.smetrix.app.utils.SecurePrefsHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * OkHttp-интерцептор для автоматического управления Bearer-токеном.
 *
 * <p>При каждом запросе:
 * <ol>
 *   <li>Добавляет заголовок {@code Authorization: Bearer {accessToken}}.</li>
 *   <li>Добавляет заголовки {@code Content-Type} и {@code Accept-Charset}.</li>
 *   <li>При ответе {@code 401} — выполняет автоматический refresh токена.</li>
 * </ol>
 *
 * <p><b>Потокобезопасность:</b> блок refresh защищён через {@code synchronized(this)},
 * что предотвращает параллельное обновление токена из нескольких потоков.
 *
 * <p><b>Broadcast при logout:</b> если refresh завершился неудачей
 * (ответ 401 или null), отправляет {@code LocalBroadcast} с action
 * {@link #ACTION_LOGOUT}, чтобы UI мог показать экран авторизации.
 */
public class AuthInterceptor implements Interceptor {

    private static final String TAG = "AuthInterceptor";

    /** Action для LocalBroadcast при необходимости выхода из аккаунта. */
    public static final String ACTION_LOGOUT = "com.smetrix.app.ACTION_LOGOUT";

    private final Context context;

    /**
     * Создаёт интерцептор с доступом к контексту приложения.
     *
     * @param context контекст приложения (используется для SharedPreferences
     *                и LocalBroadcastManager).
     */
    public AuthInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Перехватывает каждый HTTP-запрос: добавляет токен, обрабатывает 401.
     *
     * @param chain цепочка интерцепторов OkHttp.
     * @return HTTP-ответ (оригинальный или повторный после refresh).
     * @throws IOException при сетевой ошибке.
     */
    @Override
    public Response intercept(Chain chain) throws IOException {
        String accessToken = readAccessToken();

        Request originalRequest = chain.request();
        Request authenticatedRequest = buildAuthenticatedRequest(originalRequest, accessToken);

        Response response = chain.proceed(authenticatedRequest);

        if (response.code() != 401) {
            return response;
        }

        // Ответ 401 — пытаемся обновить токен (один поток за раз)
        return handleUnauthorized(chain, originalRequest, response);
    }

    /**
     * Обрабатывает ответ 401: выполняет refresh токена и повторяет запрос.
     *
     * <p>Метод синхронизирован, чтобы предотвратить параллельный refresh
     * из нескольких потоков (например, при одновременных запросах).
     *
     * @param chain           цепочка OkHttp.
     * @param originalRequest оригинальный запрос без нового токена.
     * @param staleResponse   ответ 401, который нужно закрыть перед повторным запросом.
     * @return повторный ответ с обновлённым токеном, либо исходный 401 при неудаче.
     * @throws IOException при сетевой ошибке.
     */
    private synchronized Response handleUnauthorized(
            Chain chain,
            Request originalRequest,
            Response staleResponse) throws IOException {

        // Проверяем, не обновил ли уже токен другой поток пока мы ждали лока
        String currentToken = readAccessToken();
        String requestToken  = staleResponse.request().header("Authorization");
        if (requestToken != null && !requestToken.equals("Bearer " + currentToken)) {
            // Другой поток уже обновил токен — повторяем запрос с актуальным токеном
            staleResponse.close();
            return chain.proceed(buildAuthenticatedRequest(originalRequest, currentToken));
        }

        staleResponse.close();

        // Выполняем refresh синхронно через отдельный OkHttpClient
        // (без AuthInterceptor, чтобы избежать рекурсии)
        RefreshAttempt refreshAttempt = performTokenRefresh();

        if (refreshAttempt.tokenResponse == null) {
            if (!refreshAttempt.revokeSession) {
                throw new IOException("Token refresh failed without session revocation.");
            }
            clearSession();
            broadcastLogout();
            // Возвращаем повторный 401, чтобы верхний код корректно его обработал
            return chain.proceed(buildAuthenticatedRequest(originalRequest, ""));
        }

        // Сохраняем новые токены
        saveTokens(refreshAttempt.tokenResponse);

        // Повторяем исходный запрос с новым токеном
        return chain.proceed(buildAuthenticatedRequest(originalRequest, refreshAttempt.tokenResponse.accessToken));
    }

    /**
     * Выполняет синхронный POST-запрос на {@code /auth/refresh}.
     *
     * <p>Использует отдельный {@code OkHttpClient} без {@link AuthInterceptor},
     * чтобы не вызвать рекурсию при ответе 401 на сам refresh-запрос.
     *
     * @return {@link TokenResponse} при успехе; {@code null} при ошибке или ответе 401.
     */
    private RefreshAttempt performTokenRefresh() {
        String refreshToken = readRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            Log.w(TAG, "performTokenRefresh: refresh_token отсутствует");
            return RefreshAttempt.revoke();
        }

        try {
            // Чистый клиент без AuthInterceptor для предотвращения рекурсии
            OkHttpClient refreshClient = new OkHttpClient.Builder().build();

            ApiService refreshService = new Retrofit.Builder()
                    .baseUrl(getBaseUrl())
                    .client(refreshClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);

            Map<String, String> body = new HashMap<>();
            body.put(AuthRepository.KEY_REFRESH_TOKEN, refreshToken);

            retrofit2.Response<TokenResponse> refreshResponse =
                    refreshService.refreshToken(body).execute();

            if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                return RefreshAttempt.success(refreshResponse.body());
            }

            Log.w(TAG, "performTokenRefresh: сервер вернул " + refreshResponse.code());
            if (refreshResponse.code() == 400
                    || refreshResponse.code() == 401
                    || refreshResponse.code() == 403) {
                return RefreshAttempt.revoke();
            }
            return RefreshAttempt.retryable();

        } catch (IOException e) {
            Log.e(TAG, "performTokenRefresh: сетевая ошибка при refresh токена", e);
            return RefreshAttempt.retryable();
        } catch (Exception e) {
            Log.e(TAG, "performTokenRefresh: неожиданная ошибка при refresh токена", e);
            return RefreshAttempt.retryable();
        }
    }

    /**
     * Отправляет {@code LocalBroadcast} для принудительного выхода из аккаунта.
     *
     * <p>UI-слой должен подписаться на {@link #ACTION_LOGOUT} и
     * перенаправить пользователя на экран авторизации.
     */
    private void broadcastLogout() {
        Log.i(TAG, "broadcastLogout: refresh провалился, отправляем ACTION_LOGOUT");
        Intent intent = new Intent(ACTION_LOGOUT);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * Создаёт запрос с добавленными заголовками аутентификации.
     *
     * @param original    оригинальный запрос.
     * @param accessToken текущий токен доступа.
     * @return новый запрос с заголовками {@code Authorization}, {@code Content-Type}, {@code Accept-Charset}.
     */
    private Request buildAuthenticatedRequest(Request original, String accessToken) {
        Request.Builder builder = original.newBuilder();

        if (accessToken != null && !accessToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        builder.header("Content-Type", "application/json; charset=utf-8");
        builder.header("Accept-Charset", "utf-8");

        return builder.build();
    }

    /**
     * Читает access-токен из {@code EncryptedSharedPreferences}.
     *
     * <p>Заглушка для Фазы 5: чтение напрямую из {@code EncryptedSharedPreferences}.
     * В Фазе 6 будет заменено на обращение к {@code AuthRepository}.
     *
     * @return access-токен или пустую строку, если токен не найден.
     */
    private String readAccessToken() {
        return readFromPrefs(AuthRepository.KEY_ACCESS_TOKEN);
    }

    /**
     * Читает refresh-токен из {@code EncryptedSharedPreferences}.
     *
     * @return refresh-токен или пустую строку, если токен не найден.
     */
    private String readRefreshToken() {
        return readFromPrefs(AuthRepository.KEY_REFRESH_TOKEN);
    }

    /**
     * Читает строковое значение по ключу из {@code EncryptedSharedPreferences}.
     *
     * @param key ключ настройки.
     * @return значение или пустую строку при ошибке.
     */
    private String readFromPrefs(String key) {
        try {
            return SecurePrefsHelper.get(context).getString(key, "");
        } catch (Exception e) {
            Log.e(TAG, "readFromPrefs: не удалось прочитать ключ '" + key + "'", e);
            return "";
        }
    }

    /**
     * Сохраняет новую пару токенов в {@code EncryptedSharedPreferences}.
     *
     * @param accessToken  новый токен доступа.
     * @param refreshToken новый токен обновления.
     */
    private void saveTokens(TokenResponse tokenResponse) {
        try {
            long expiresInSeconds = tokenResponse.accessTokenExpiresIn > 0
                    ? tokenResponse.accessTokenExpiresIn
                    : 900L;
            long expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L;

            SharedPreferences.Editor editor = SecurePrefsHelper.get(context)
                    .edit()
                    .putString(AuthRepository.KEY_ACCESS_TOKEN, tokenResponse.accessToken)
                    .putLong(AuthRepository.KEY_ACCESS_EXPIRES_AT, expiresAt);

            if (tokenResponse.refreshToken != null && !tokenResponse.refreshToken.isEmpty()) {
                editor.putString(AuthRepository.KEY_REFRESH_TOKEN, tokenResponse.refreshToken);
            }

            editor.apply();
            Log.d(TAG, "saveTokens: токены успешно обновлены");
        } catch (Exception e) {
            Log.e(TAG, "saveTokens: не удалось сохранить токены", e);
        }
    }

    private void clearSession() {
        SecurePrefsHelper.get(context)
                .edit()
                .remove(AuthRepository.KEY_ACCESS_TOKEN)
                .remove(AuthRepository.KEY_REFRESH_TOKEN)
                .remove(AuthRepository.KEY_USER_ID)
                .remove(AuthRepository.KEY_ACCESS_EXPIRES_AT)
                .apply();
    }

    /**
     * Возвращает базовый URL API из {@code BuildConfig}.
     *
     * <p>Метод необходим для построения отдельного {@code Retrofit}-экземпляра
     * в {@link #performTokenRefresh()} без создания циклической зависимости
     * с {@link ApiClient}.
     *
     * @return строка базового URL (например, {@code "https://api-dev.smetrix.ru/api/v1/"}).
     */
    private String getBaseUrl() {
        return com.smetrix.app.BuildConfig.API_BASE_URL;
    }

    private static final class RefreshAttempt {
        final TokenResponse tokenResponse;
        final boolean revokeSession;

        private RefreshAttempt(TokenResponse tokenResponse, boolean revokeSession) {
            this.tokenResponse = tokenResponse;
            this.revokeSession = revokeSession;
        }

        static RefreshAttempt success(TokenResponse tokenResponse) {
            return new RefreshAttempt(tokenResponse, false);
        }

        static RefreshAttempt revoke() {
            return new RefreshAttempt(null, true);
        }

        static RefreshAttempt retryable() {
            return new RefreshAttempt(null, false);
        }
    }
}
