// app/src/main/java/com/smetrix/app/network/dto/ProjectRoomDto.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO для передачи данных помещения (комнаты) проекта.
 *
 * <p>Все геометрические размеры и площадь переопределения передаются
 * как {@code String} для сохранения точности {@code BigDecimal}.
 */
public class ProjectRoomDto {

    /** Уникальный идентификатор помещения (UUID). */
    @SerializedName("id")
    public String id;

    /** Идентификатор проекта, к которому относится помещение. */
    @SerializedName("project_id")
    public String projectId;

    /** Операция синхронизации: CREATE, UPDATE или DELETE. */
    @SerializedName("operation")
    public String operation;

    /** Название помещения (например, «Гостиная», «Кухня»). */
    @SerializedName("name")
    public String name;

    /**
     * Длина помещения в метрах.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("length")
    public String length;

    /**
     * Ширина помещения в метрах.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("width")
    public String width;

    /**
     * Высота потолков в метрах.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("height")
    public String height;

    /**
     * Переопределение площади вручную.
     * Если задано, геометрический расчёт игнорируется.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("manual_area_override")
    public String manualAreaOverride;



    /** Версия записи (для optimistic concurrency control). */
    @SerializedName("version")
    public long version;

    /** Дата и время создания (ISO-8601). */
    @SerializedName("created_at")
    public String createdAt;

    /** Дата и время последнего обновления (ISO-8601). */
    @SerializedName("updated_at")
    public String updatedAt;
}
