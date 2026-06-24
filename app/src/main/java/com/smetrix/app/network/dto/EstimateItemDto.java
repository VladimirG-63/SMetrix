// app/src/main/java/com/smetrix/app/network/dto/EstimateItemDto.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO для позиции сметы, используемой в батч-синхронизации.
 *
 * <p>Поле {@code operation} определяет тип операции для сервера:
 * <ul>
 *   <li>{@code "CREATE"} — создать новую запись.</li>
 *   <li>{@code "UPDATE"} — обновить существующую запись.</li>
 *   <li>{@code "DELETE"} — удалить запись.</li>
 * </ul>
 *
 * <p>Все поля типа {@code BigDecimal} передаются как {@code String}
 * для сохранения точности (RFC §2.1).
 */
public class EstimateItemDto {

    /** Уникальный идентификатор позиции сметы (UUID). */
    @SerializedName("id")
    public String id;

    /** Идентификатор помещения, которому принадлежит позиция. */
    @SerializedName("project_room_id")
    public String projectRoomId;

    /**
     * Тип операции для сервера: {@code "CREATE"}, {@code "UPDATE"}, {@code "DELETE"}.
     * Маппится из {@code syncState} сущности.
     */
    @SerializedName("operation")
    public String operation;

    /** Версия записи (для optimistic concurrency control). */
    @SerializedName("version")
    public long version;

    /** Код по ФГИС ЦС (федеральный классификатор материалов). */
    @SerializedName("fgis_code")
    public String fgisCode;

    /** Наименование материала или работы. */
    @SerializedName("name")
    public String name;

    /** Единица измерения (например, «м²», «шт», «п.м»). */
    @SerializedName("unit_measure")
    public String unitMeasure;

    /**
     * Базовая цена из ФГИС ЦС.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("base_price")
    public String basePrice;

    /**
     * Итоговая цена с учётом региональных коэффициентов и наценок.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("final_price")
    public String finalPrice;

    /**
     * Норма расхода материала на единицу площади.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("consumption_rate")
    public String consumptionRate;

    /**
     * Количество единиц материала (рассчитывается автоматически).
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("quantity")
    public String quantity;

    /**
     * Общая стоимость позиции (quantity × finalPrice).
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("total_price")
    public String totalPrice;

    /**
     * Статус позиции: {@code "NEED_TO_BUY"}, {@code "ORDERED"},
     * {@code "ON_SITE"}, {@code "UNITS_MISMATCH"}.
     */
    @SerializedName("status")
    public String status;

    @SerializedName("calculation_method")
    public String calculationMethod;

    @SerializedName("waste_percent")
    public String wastePercent;

    @SerializedName("layers")
    public Integer layers;

    @SerializedName("thickness_meters")
    public String thicknessMeters;

    @SerializedName("manual_quantity")
    public String manualQuantity;

    @SerializedName("coverage_per_piece")
    public String coveragePerPiece;

    @SerializedName("coverage_per_package")
    public String coveragePerPackage;

    @SerializedName("package_size")
    public String packageSize;

    @SerializedName("formula_description")
    public String formulaDescription;

    /** Дата и время создания (ISO-8601). */
    @SerializedName("created_at")
    public String createdAt;

    /** Дата и время последнего обновления (ISO-8601). */
    @SerializedName("updated_at")
    public String updatedAt;
}
