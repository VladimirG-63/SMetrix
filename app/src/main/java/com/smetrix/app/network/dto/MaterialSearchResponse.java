// app/src/main/java/com/smetrix/app/network/dto/MaterialSearchResponse.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Ответ сервера на запрос поиска материалов из ФГИС ЦС.
 *
 * <p>Поддерживает постраничную навигацию через поля {@code limit}
 * и {@code offset}. Для загрузки следующей страницы нужно
 * передать {@code offset += limit} в следующем запросе.
 */
public class MaterialSearchResponse {

    /**
     * Список найденных материалов на текущей странице.
     * Максимальное количество элементов ограничено полем {@code limit}.
     */
    @SerializedName("items")
    public List<MaterialDto> items;

    /** Общее количество материалов, соответствующих запросу. */
    @SerializedName("total")
    public int total;

    /** Максимальное количество элементов на странице (запрошенное). */
    @SerializedName("limit")
    public int limit;

    /** Смещение от начала результатов (для постраничной навигации). */
    @SerializedName("offset")
    public int offset;
}
