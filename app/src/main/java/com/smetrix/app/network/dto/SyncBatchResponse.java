// app/src/main/java/com/smetrix/app/network/dto/SyncBatchResponse.java
package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Ответ сервера на батч-синхронизацию позиций сметы.
 *
 * <p>Содержит результаты обработки каждой позиции батча.
 * Порядок элементов в {@code results} соответствует порядку
 * в исходном запросе {@link SyncBatchRequest}.
 */
public class SyncBatchResponse {

    /**
     * Список результатов для каждой позиции батча.
     * Каждый элемент содержит HTTP-статус и данные для обновления локальной записи.
     */
    @SerializedName("results")
    public List<SyncItemResult> results;

    /** Фактическое количество обработанных позиций в батче. */
    @SerializedName("batch_size")
    public int batchSize;

    /** Дата и время обработки батча на сервере (ISO-8601). */
    @SerializedName("processed_at")
    public String processedAt;
}
