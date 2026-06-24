// app/src/main/java/com/smetrix/app/network/dto/ProjectDto.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO для передачи данных проекта между клиентом и сервером.
 *
 * <p>Все поля типа {@code BigDecimal} представлены как {@code String},
 * чтобы избежать потери точности при передаче JSON (RFC §2.1).
 * Сервер принимает и возвращает числовые значения в текстовом виде.
 */
public class ProjectDto {

    /** Уникальный идентификатор проекта (UUID). */
    @SerializedName("id")
    public String id;

    /** Название проекта. */
    @SerializedName("name")
    public String name;

    /** Город, в котором выполняется проект. */
    @SerializedName("city")
    public String city;

    /** Код региона (ФИАС/ОКТМО). */
    @SerializedName("region_code")
    public String regionCode;

    /**
     * Налоговый мультипликатор (НДС/УСН).
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("tax_multiplier")
    public String taxMultiplier;

    /**
     * Наценка на логистику.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("logistics_markup")
    public String logisticsMarkup;

    /** Версия записи (для optimistic concurrency control). */
    @SerializedName("version")
    public long version;

    /** Дата и время создания (ISO-8601). */
    @SerializedName("created_at")
    public String createdAt;

    /** Дата и время последнего обновления (ISO-8601). */
    @SerializedName("updated_at")
    public String updatedAt;

    /**
     * Дата и время мягкого удаления (ISO-8601).
     * {@code null}, если запись не удалена.
     */
    @SerializedName("deleted_at")
    public String deletedAt;
}
