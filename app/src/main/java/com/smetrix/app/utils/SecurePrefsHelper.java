// app/src/main/java/com/smetrix/app/utils/SecurePrefsHelper.java
package com.smetrix.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Централизованный хелпер для доступа к зашифрованному хранилищу данных.
 *
 * <p>Все компоненты приложения (AuthRepository, ProfileViewModel, AuthInterceptor и др.)
 * должны получать SharedPreferences только через этот класс, чтобы гарантировать
 * использование одного и того же файла и одного и того же механизма шифрования.
 *
 * <p>При повреждении старого файла выполняется одна безопасная переинициализация.
 * Переход на обычный SharedPreferences запрещён: токены не должны сохраняться
 * открытым текстом даже при сбое Android Keystore.
 */
public final class SecurePrefsHelper {

    private static final String TAG = "SecurePrefsHelper";

    /** Имя файла SharedPreferences, единое для всего приложения. */
    public static final String PREFS_NAME = "smetrix_auth_prefs";

    private SecurePrefsHelper() {}

    /**
     * Возвращает экземпляр SharedPreferences.
     *
     * <p>Пытается открыть EncryptedSharedPreferences. Если файл был создан старой
     * версией приложения в открытом виде или повреждён, удаляет только этот файл
     * сессии и повторяет безопасную инициализацию один раз.
     *
     * @param context контекст приложения.
     * @return готовый к использованию SharedPreferences.
     */
    public static SharedPreferences get(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            return createEncryptedPreferences(appContext);
        } catch (Exception firstFailure) {
            Log.e(TAG, "Защищённое хранилище повреждено; локальная сессия будет сброшена.",
                    firstFailure);
            appContext.deleteSharedPreferences(PREFS_NAME);
            try {
                return createEncryptedPreferences(appContext);
            } catch (Exception secondFailure) {
                throw new IllegalStateException(
                        "Не удалось инициализировать защищённое хранилище токенов",
                        secondFailure
                );
            }
        }
    }

    // AndroidX Security Crypto 1.1.0 помечает весь стабильный API deprecated,
    // но он остаётся используемым здесь до миграции существующего зашифрованного
    // файла без потери пользовательских сессий.
    @SuppressWarnings("deprecation")
    private static SharedPreferences createEncryptedPreferences(Context context)
            throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }
}
