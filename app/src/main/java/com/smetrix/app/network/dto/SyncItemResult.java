// app/src/main/java/com/smetrix/app/network/dto/SyncItemResult.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Результат обработки одной позиции сметы в батч-синхронизации.
 *
 * <p>Сервер возвращает отдельный {@code SyncItemResult} для каждой
 * позиции из запроса. Коды статусов:
 * <ul>
 *   <li>{@code 200} или {@code 201} — операция успешна, обновить {@code syncState = SYNCED}.</li>
 *   <li>{@code 409} — конфликт версий, обновить {@code syncState = CONFLICT},
 *       записать {@code serverSnapshot} в {@code ConflictEntity}.</li>
 *   <li>{@code >= 500} — серверная ошибка, обновить {@code syncState = FAILED}.</li>
 * </ul>
 */
public class SyncItemResult {

    /** Идентификатор позиции сметы (UUID). */
    @SerializedName("id")
    public String id;

    /**
     * HTTP-код результата обработки этой конкретной позиции.
     * Возможные значения: 200, 201, 409, 5xx.
     */
    @SerializedName("status")
    public int status;

    /** Актуальная версия записи на сервере после обработки. */
    @SerializedName("version")
    public long version;

    /** Дата и время обновления на сервере (ISO-8601). */
    @SerializedName("updated_at")
    public String updatedAt;

    /**
     * Код ошибки в случае неуспешной обработки.
     * {@code null} при статусе 200/201.
     */
    @SerializedName("error_code")
    public String errorCode;

    /**
     * Снимок серверной версии записи при конфликте (raw JSON).
     * Заполняется только при статусе 409. Используется для
     * разрешения конфликта через {@code ConflictRepository}.
     */
    @SerializedName("server_snapshot")
    public String serverSnapshot;
}
