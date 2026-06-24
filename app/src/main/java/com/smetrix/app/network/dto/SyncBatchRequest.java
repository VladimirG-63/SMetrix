// app/src/main/java/com/smetrix/app/network/dto/SyncBatchRequest.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Тело запроса для батч-синхронизации позиций сметы.
 *
 * <p>Один батч содержит до 50 позиций сметы. Если данных больше —
 * создаётся несколько запросов. Серверная сторона обрабатывает
 * каждую позицию независимо: сбой одной не откатывает весь батч.
 */
public class SyncBatchRequest {

    /** Идентификатор проекта, к которому относятся все позиции батча. */
    @SerializedName("project_id")
    public String projectId;

    /**
     * Список позиций сметы для синхронизации.
     * Максимальный рекомендованный размер — 50 элементов.
     */
    @SerializedName("items")
    public List<Object> items;

    /**
     * Создаёт запрос батч-синхронизации.
     *
     * @param projectId идентификатор проекта.
     * @param items     список DTO позиций сметы.
     */
    public SyncBatchRequest(String projectId, List<?> items) {
        this.projectId = projectId;
        this.items = new ArrayList<>(items);
    }
}
