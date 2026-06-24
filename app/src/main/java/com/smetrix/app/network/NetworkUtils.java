// app/src/main/java/com/smetrix/app/network/NetworkUtils.java
package com.smetrix.app.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

/**
 * Утилитарный класс для проверки состояния сетевого подключения.
 *
 * <p>Предоставляет статические методы для определения доступности сети
 * до выполнения сетевых запросов. Используется в ViewModel и Repository,
 * чтобы принять решение о запуске {@link com.smetrix.app.network.sync.SyncManager}
 * или немедленном использовании локального кэша.
 *
 * <p><b>API уровень:</b> реализация корректна для API 23+.
 * Методы с {@link NetworkCapabilities} доступны начиная с API 23,
 * устаревший {@code NetworkInfo} не используется.
 *
 * <p><b>Использование в ViewModel:</b>
 * <pre>
 *   if (NetworkUtils.isConnected(getApplication())) {
 *       materialRepository.searchRemote(query, regionCode, callback);
 *   } else {
 *       // Отображаем данные из кэша через searchLocal(query)
 *   }
 * </pre>
 *
 * <p><b>Важно:</b> проверка наличия сети — необходимое, но недостаточное
 * условие успешности запроса. Сервер может быть недоступен даже при
 * наличии интернета. Всегда обрабатывайте {@link java.io.IOException}
 * в Retrofit-колбэках.
 */
public final class NetworkUtils {

    /**
     * Приватный конструктор запрещает создание экземпляров.
     * Класс содержит только статические утилитарные методы.
     */
    private NetworkUtils() {
        // Не инстанцируется.
    }

    /**
     * Проверяет наличие активного сетевого подключения (включая локальные сети).
     *
     * <p>Метод использует {@link NetworkCapabilities} (API 23+) для проверки
     * наличия следующих транспортов:
     * <ul>
     *   <li>{@code TRANSPORT_WIFI} — Wi-Fi (в том числе локальная сеть без интернета).</li>
     *   <li>{@code TRANSPORT_CELLULAR} — мобильные данные.</li>
     *   <li>{@code TRANSPORT_ETHERNET} — проводная сеть.</li>
     * </ul>
     *
     * <p><b>Важно:</b> этот метод НЕ проверяет {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED},
     * т.к. этот флаг выставляется Android только при наличии глобального выхода в интернет
     * (пинг до серверов Google). В локальной Wi-Fi сети, где телефон общается только
     * с локальным сервером (например, SMetrix-Server по IP 192.168.x.x), этот флаг
     * может быть {@code false}, хотя соединение с сервером работает отлично.
     *
     * <p>Для проверки именно глобального интернета используйте
     * {@link #isValidatedInternet(Context)}.
     *
     * @param context контекст приложения.
     * @return {@code true} если есть активное сетевое подключение любого типа.
     */
    public static boolean isConnected(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            return false;
        }

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        if (caps == null) {
            return false;
        }

        // Проверяем тип транспорта — Wi-Fi, мобильная сеть или Ethernet.
        boolean hasTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);

        // Проверяем NET_CAPABILITY_INTERNET (способность отправлять IP-пакеты),
        // но НЕ NET_CAPABILITY_VALIDATED (глобальный интернет) — иначе локальный
        // Wi-Fi без выхода в интернет будет ошибочно считаться «нет сети».
        boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        return hasTransport && hasInternet;
    }

    /**
     * Проверяет наличие <b>валидированного</b> глобального доступа в интернет.
     *
     * <p>В отличие от {@link #isConnected(Context)}, этот метод дополнительно
     * проверяет {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} — т.е.
     * Android уже убедился, что через данную сеть достижимы внешние серверы.
     *
     * <p>Используйте этот метод только если вам действительно нужен глобальный
     * интернет (например, для загрузки обновлений с внешнего сервера).
     * Для работы с локальным SMetrix-Server достаточно {@link #isConnected(Context)}.
     *
     * @param context контекст приложения.
     * @return {@code true} если есть валидированный глобальный доступ в интернет.
     */
    public static boolean isValidatedInternet(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            return false;
        }

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        if (caps == null) {
            return false;
        }

        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * Проверяет, подключено ли устройство через Wi-Fi.
     *
     * <p>Используется для решения о запуске «тяжёлых» фоновых операций
     * (например, массовое обновление кэша материалов), которые желательно
     * выполнять только по Wi-Fi, чтобы не тратить мобильный трафик.
     *
     * @param context контекст приложения.
     * @return {@code true} если активное подключение — Wi-Fi.
     */
    public static boolean isOnWifi(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            return false;
        }

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /**
     * Проверяет, подключено ли устройство через мобильные данные.
     *
     * @param context контекст приложения.
     * @return {@code true} если активное подключение — мобильная сеть.
     */
    public static boolean isOnCellular(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            return false;
        }

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }
}
