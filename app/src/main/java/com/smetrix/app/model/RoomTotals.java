// app/src/main/java/com/smetrix/app/model/RoomTotals.java
package com.smetrix.app.model;

import java.math.BigDecimal;

/**
 * Агрегированные итоги по помещению (Project Room) для отображения
 * в Sticky Bottom Bar на экране детализации комнаты.
 *
 * <p>Объект содержит три финансовых показателя, вычисляемых
 * в {@code RoomDetailViewModel.recalcTotals()} через объединение
 * двух источников LiveData — итогов по материалам и итогов по работам.
 *
 * <h3>Гарантии точности:</h3>
 * <ul>
 *   <li>Все поля имеют тип {@link BigDecimal} — безопасный тип для денежных расчётов.</li>
 *   <li>Scale: 2 знака после запятой, {@code RoundingMode.HALF_UP}.</li>
 *   <li>Ни одно поле не может быть {@code null} — используйте {@code BigDecimal.ZERO} по умолчанию.</li>
 * </ul>
 *
 * <h3>Вычисление полей:</h3>
 * <pre>
 *   materialsTotal = SUM(EstimateItem.totalPrice)  для данной комнаты
 *   salariesTotal  = SUM(WorkTask.totalPayment)     для данной комнаты
 *   roomTotal      = materialsTotal + salariesTotal
 * </pre>
 */
public class RoomTotals {

    /**
     * Суммарная стоимость всех материалов в комнате.
     * Вычисляется через {@code SELECT COALESCE(SUM(total_price), 0) FROM estimate_item WHERE project_room_id = :roomId}.
     * Scale: 2, RoundingMode.HALF_UP.
     */
    public final BigDecimal materialsTotal;

    /**
     * Суммарный фонд оплаты труда по всем рабочим задачам в комнате.
     * Вычисляется через {@code SELECT COALESCE(SUM(total_payment), 0) FROM work_task WHERE project_room_id = :roomId}.
     * Scale: 2, RoundingMode.HALF_UP.
     */
    public final BigDecimal salariesTotal;

    /**
     * Общий итог по комнате: стоимость материалов + оплата труда.
     * Формула: {@code roomTotal = materialsTotal.add(salariesTotal)}.
     * Scale: 2, RoundingMode.HALF_UP.
     */
    public final BigDecimal roomTotal;

    /**
     * Создаёт объект итогов по комнате с явным указанием всех трёх компонентов.
     *
     * <p>Ни один из параметров не должен быть {@code null}. Если реальные данные
     * ещё не загружены, передавайте {@code BigDecimal.ZERO}.
     *
     * <p>Вызывается в {@code RoomDetailViewModel.recalcTotals()}, когда
     * хотя бы один из источников ({@code materialsTotalLd} или {@code salariesTotalLd})
     * обновляет своё значение.
     *
     * @param materialsTotal суммарная стоимость материалов, не должна быть {@code null}.
     * @param salariesTotal  суммарный ФОТ, не должна быть {@code null}.
     * @param roomTotal      общий итог по комнате, не должна быть {@code null}.
     */
    public RoomTotals(BigDecimal materialsTotal, BigDecimal salariesTotal, BigDecimal roomTotal) {
        this.materialsTotal = materialsTotal;
        this.salariesTotal = salariesTotal;
        this.roomTotal = roomTotal;
    }
}
