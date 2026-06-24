// app/src/main/java/com/smetrix/app/model/EstimateItemDisplay.java
package com.smetrix.app.model;

import java.math.BigDecimal;

/**
 * Модель отображения (Display Model) для одной позиции сметы.
 *
 * <p>Этот класс является частью паттерна «UI Model» и служит прослойкой
 * между слоем базы данных ({@code EstimateItemEntity}) и слоем UI
 * (Fragment / RecyclerView.Adapter). UI-слой <b>никогда</b> не видит
 * {@code EstimateItemEntity} напрямую — только объекты этого класса.
 *
 * <p>Преобразование Entity → Display производится в {@code EstimateItemMapper}
 * и вызывается <b>исключительно</b> из ViewModel.
 *
 * <h3>Поля статуса:</h3>
 * <ul>
 *   <li>{@code status}    — логистический статус материала:
 *       {@code "NEED_TO_BUY"}, {@code "ORDERED"}, {@code "ON_SITE"},
 *       {@code "UNITS_MISMATCH"}.</li>
 *   <li>{@code syncState} — состояние синхронизации с сервером:
 *       {@code "SYNCED"}, {@code "PENDING_CREATE"}, {@code "PENDING_UPDATE"},
 *       {@code "PENDING_DELETE"}, {@code "FAILED"}, {@code "CONFLICT"}.</li>
 * </ul>
 *
 * <h3>Денежные поля (BigDecimal):</h3>
 * <ul>
 *   <li>{@code quantity}   — расчётное количество, scale = 6, RoundingMode.HALF_UP.</li>
 *   <li>{@code finalPrice} — итоговая цена за единицу, scale = 2, RoundingMode.HALF_UP.</li>
 *   <li>{@code totalPrice} — суммарная стоимость позиции, scale = 2, RoundingMode.HALF_UP.</li>
 * </ul>
 */
public class EstimateItemDisplay {

    /**
     * Уникальный идентификатор позиции сметы (UUID-строка).
     * Соответствует полю {@code id} в таблице {@code estimate_item}.
     */
    public final String id;

    /**
     * Человекочитаемое название материала или вида работы.
     * Пример: «Краска акриловая белая, матовая».
     */
    public final String name;

    /**
     * Единица измерения материала.
     * Пример: «кв.м», «п.м», «шт», «кг».
     */
    public final String unitMeasure;

    /**
     * Логистический статус материала.
     * Допустимые строковые значения:
     * {@code "NEED_TO_BUY"}, {@code "ORDERED"}, {@code "ON_SITE"}, {@code "UNITS_MISMATCH"}.
     *
     * @see ItemStatus
     */
    public final String status;

    /**
     * Состояние синхронизации этой записи с сервером.
     * Допустимые строковые значения:
     * {@code "SYNCED"}, {@code "PENDING_CREATE"}, {@code "PENDING_UPDATE"},
     * {@code "PENDING_DELETE"}, {@code "FAILED"}, {@code "CONFLICT"}.
     *
     * @see SyncState
     */
    public final String syncState;

    /**
     * Расчётное количество материала для данного помещения.
     * Вычисляется по формуле: {@code quantity = effectiveArea × consumptionRate}.
     * Масштаб: 6 знаков после запятой, округление HALF_UP.
     */
    public final BigDecimal quantity;

    /**
     * Итоговая цена за единицу материала.
     * Включает налоги, логистические наценки и ручные корректировки.
     * Масштаб: 2 знака после запятой, округление HALF_UP.
     */
    public final BigDecimal finalPrice;

    /**
     * Суммарная стоимость позиции сметы для данного помещения.
     * Вычисляется по формуле: {@code totalPrice = finalPrice × quantity}.
     * Масштаб: 2 знака после запятой, округление HALF_UP.
     */
    public final BigDecimal totalPrice;

    /**
     * Конструктор, принимающий все поля объекта.
     *
     * <p>Объекты этого класса создаются исключительно в {@link EstimateItemMapper}
     * и являются неизменяемыми (все поля — {@code final}). Это гарантирует
     * потокобезопасность при передаче объектов между потоками (из ViewModel в UI).
     *
     * @param id          идентификатор позиции сметы.
     * @param name        название материала или вида работы.
     * @param unitMeasure единица измерения.
     * @param status      логистический статус (строка).
     * @param syncState   состояние синхронизации (строка).
     * @param quantity    расчётное количество.
     * @param finalPrice  итоговая цена за единицу.
     * @param totalPrice  суммарная стоимость позиции.
     */
    public EstimateItemDisplay(
            String id,
            String name,
            String unitMeasure,
            String status,
            String syncState,
            BigDecimal quantity,
            BigDecimal finalPrice,
            BigDecimal totalPrice) {

        this.id = id;
        this.name = name;
        this.unitMeasure = unitMeasure;
        this.status = status;
        this.syncState = syncState;
        this.quantity = quantity;
        this.finalPrice = finalPrice;
        this.totalPrice = totalPrice;
    }
}
