// app/src/main/java/com/smetrix/app/network/ApiErrorHandler.java
package com.smetrix.app.network;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.smetrix.app.network.dto.ApiErrorResponse;

import java.io.IOException;

import retrofit2.Response;

/**
 * Утилитарный класс для стандартизированной обработки ошибок Retrofit-ответов.
 *
 * <p><b>Назначение:</b><br>
 * Централизует логику интерпретации HTTP-кодов ошибок и исключений сети.
 * Вместо дублирования кода {@code if (response.code() == 401) ...} по всему
 * приложению — используйте методы этого класса.
 *
 * <p><b>Категории ошибок:</b>
 * <ul>
 *   <li>4xx Client Error — ошибки на стороне клиента (неверный запрос, нет прав).</li>
 *   <li>5xx Server Error — ошибки на стороне сервера (перегрузка, баги).</li>
 *   <li>{@link IOException} — сетевые ошибки (нет интернета, таймаут).</li>
 * </ul>
 *
 * <p><b>Использование в SyncWorker:</b>
 * <pre>
 *   Response&lt;SyncBatchResponse&gt; response = apiService.syncEstimateItems(request).execute();
 *   if (ApiErrorHandler.isSuccess(response)) {
 *       // обработать успешный ответ
 *   } else if (ApiErrorHandler.isConflict(response)) {
 *       // обработать конфликт 409
 *   } else if (ApiErrorHandler.isServerError(response)) {
 *       // обработать серверную ошибку 5xx
 *   }
 * </pre>
 */
public final class ApiErrorHandler {

    private static final String TAG = "ApiErrorHandler";

    /** Приватный конструктор — только статические методы. */
    private ApiErrorHandler() {
        // Не инстанцируется.
    }

    // ─── Методы проверки статуса ─────────────────────────────────────────────

    /**
     * Проверяет, является ли ответ успешным (код 2xx).
     *
     * @param response ответ Retrofit.
     * @return {@code true} при коде 200–299.
     */
    public static boolean isSuccess(@Nullable Response<?> response) {
        return response != null && response.isSuccessful();
    }

    /**
     * Проверяет, является ли ответ ошибкой авторизации (401 Unauthorized).
     *
     * <p>Используется в {@link AuthInterceptor} для запуска обновления токена.
     *
     * @param response ответ Retrofit.
     * @return {@code true} при коде 401.
     */
    public static boolean isUnauthorized(@Nullable Response<?> response) {
        return response != null && response.code() == 401;
    }

    /**
     * Проверяет, является ли ответ конфликтом версий (409 Conflict).
     *
     * <p>Используется в {@link com.smetrix.app.network.sync.SyncWorker}
     * для записи {@link com.smetrix.app.db.entity.ConflictEntity} в Room.
     *
     * @param response ответ Retrofit.
     * @return {@code true} при коде 409.
     */
    public static boolean isConflict(@Nullable Response<?> response) {
        return response != null && response.code() == 409;
    }

    /**
     * Проверяет, является ли ответ серверной ошибкой (5xx).
     *
     * <p>Серверные ошибки обычно временные — запись помечается как {@code FAILED}
     * для повторной попытки при следующей синхронизации.
     *
     * @param response ответ Retrofit.
     * @return {@code true} при коде 500 и выше.
     */
    public static boolean isServerError(@Nullable Response<?> response) {
        return response != null && response.code() >= 500;
    }

    /**
     * Проверяет, является ли ответ ошибкой клиента (4xx, кроме 401 и 409).
     *
     * @param response ответ Retrofit.
     * @return {@code true} при коде 400–499 (кроме 401 и 409).
     */
    public static boolean isClientError(@Nullable Response<?> response) {
        if (response == null) {
            return false;
        }
        int code = response.code();
        return code >= 400 && code < 500 && code != 401 && code != 409;
    }

    // ─── Методы логирования ───────────────────────────────────────────────────

    /**
     * Логирует детали HTTP-ошибки в стандартный Android Log.
     *
     * <p>Формат лога: {@code [TAG] HTTP ошибка: url={url}, code={code}, message={message}}.
     *
     * @param tag      тег для логирования (обычно название класса).
     * @param response ответ Retrofit с ошибкой.
     * @param context  контекстное описание операции (например, "синхронизация проектов").
     */
    public static void logHttpError(@NonNull String tag,
                                    @Nullable Response<?> response,
                                    @NonNull String context) {
        if (response == null) {
            Log.e(tag, "HTTP ошибка: response равен null. Контекст: " + context);
            return;
        }
        Log.e(tag, "HTTP ошибка [" + context + "]: code=" + response.code()
                + ", url=" + (response.raw().request().url()));
    }

    /**
     * Логирует сетевую ошибку ({@link IOException}) в стандартный Android Log.
     *
     * @param tag     тег для логирования.
     * @param error   исключение сети.
     * @param context контекстное описание операции.
     */
    public static void logNetworkError(@NonNull String tag,
                                       @NonNull IOException error,
                                       @NonNull String context) {
        Log.e(tag, "Сетевая ошибка [" + context + "]: " + error.getMessage(), error);
    }

    /**
     * Возвращает человекочитаемое сообщение об ошибке по HTTP-коду.
     *
     * <p>Используется для отображения в {@code Snackbar} или диалоге.
     *
     * @param response ответ Retrofit с ошибкой.
     * @return строка описания ошибки.
     */
    @NonNull
    public static String getErrorMessage(@Nullable Response<?> response) {
        if (response == null) {
            return "Нет ответа от сервера";
        }
        String serverMessage = readServerMessage(response);
        if (serverMessage != null) {
            return serverMessage;
        }
        int code = response.code();
        switch (code) {
            case 400:
                return "Некорректный запрос (400)";
            case 401:
                return "Ошибка авторизации (401). Попробуйте войти заново.";
            case 403:
                return "Доступ запрещён (403)";
            case 404:
                return "Ресурс не найден (404)";
            case 409:
                return "Конфликт версий (409). Требуется разрешение конфликта.";
            case 429:
                return "Слишком много запросов (429). Попробуйте позже.";
            case 500:
                return "Внутренняя ошибка сервера (500)";
            case 503:
                return "Сервер временно недоступен (503)";
            default:
                if (code >= 500) {
                    return "Серверная ошибка (" + code + ")";
                } else if (code >= 400) {
                    return "Ошибка клиента (" + code + ")";
                }
                return "Неожиданный код ответа: " + code;
        }
    }

    @Nullable
    private static String readServerMessage(@NonNull Response<?> response) {
        if (response.errorBody() == null) {
            return null;
        }
        try {
            String json = response.errorBody().string();
            ApiErrorResponse parsed = new Gson().fromJson(json, ApiErrorResponse.class);
            if (parsed != null && parsed.error != null
                    && parsed.error.message != null && !parsed.error.message.isBlank()) {
                return parsed.error.message;
            }
        } catch (IOException | JsonSyntaxException error) {
            Log.w(TAG, "Не удалось разобрать JSON ошибки API", error);
        }
        return null;
    }
}
