// app/src/main/java/com/smetrix/app/network/dto/MaterialDto.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO для материала из федерального реестра ФГИС ЦС.
 *
 * <p>Используется при поиске материалов через API и при кэшировании
 * результатов поиска в локальной базе данных ({@code MaterialsCacheEntity}).
 *
 * <p>Поле {@code basePrice} передаётся как {@code String} для
 * сохранения точности {@code BigDecimal}.
 */
public class MaterialDto {

    /** Код материала по ФГИС ЦС (федеральный идентификатор). */
    @SerializedName("fgis_code")
    public String fgisCode;

    /** Полное наименование материала. */
    @SerializedName("name")
    public String name;

    /** Единица измерения (например, «м²», «шт», «т», «п.м»). */
    @SerializedName("unit_measure")
    public String unitMeasure;

    /**
     * Базовая цена материала из реестра ФГИС ЦС.
     * Передаётся как {@code String} для сохранения точности BigDecimal.
     */
    @SerializedName("base_price")
    public String basePrice;

    /** Код региона, для которого актуальна данная цена (ОКТМО). */
    @SerializedName("region_code")
    public String regionCode;

    /**
     * Квартал публикации цены в формате {@code "YYYY-QN"}
     * (например, {@code "2024-Q2"}).
     */
    @SerializedName("quarter")
    public String quarter;

    @SerializedName("priority_score")
    public int priorityScore;

    @SerializedName("consumption_rate")
    public String consumptionRate;
}
