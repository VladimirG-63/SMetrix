// app/src/main/java/com/smetrix/app/ui/room/EstimateDiffCallback.java
package com.smetrix.app.ui.room;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import com.smetrix.app.model.EstimateItemDisplay;

import java.util.List;

/**
 * Стратегия сравнения позиций сметы для {@link DiffUtil}.
 *
 * <p>Используется в {@link EstimateAdapter#submitList(List)}.
 * Обеспечивает анимированные частичные обновления RecyclerView:
 * только изменившиеся строки будут перерисованы.
 *
 * <p><b>Частичные обновления (getChangePayload):</b><br>
 * Если элементы идентичны по id, но отличаются по отдельным полям,
 * {@link #getChangePayload} возвращает {@link Bundle} с перечнем изменившихся
 * ключей. Это позволяет адаптеру обновить только нужные View, не перебиндивая
 * весь ViewHolder (избегаем лишних анимаций мигания всей строки).
 *
 * <h3>Ключи в Bundle (payload):</h3>
 * <ul>
 *   <li>{@code "quantity"}    — изменилось количество материала.</li>
 *   <li>{@code "total_price"} — изменилась итоговая стоимость.</li>
 *   <li>{@code "status"}      — изменился логистический статус (цвет точки).</li>
 * </ul>
 *
 * @see EstimateAdapter
 * @see EstimateItemDisplay
 */
public class EstimateDiffCallback extends DiffUtil.Callback {

    /** Ключ payload: количество изменилось. */
    static final String KEY_QUANTITY = "quantity";
    /** Ключ payload: итоговая цена изменилась. */
    static final String KEY_TOTAL_PRICE = "total_price";
    /** Ключ payload: статус изменился. */
    static final String KEY_STATUS = "status";

    private final List<EstimateItemDisplay> oldList;
    private final List<EstimateItemDisplay> newList;

    /**
     * @param oldList предыдущий список позиций (не должен быть null).
     * @param newList новый список позиций (не должен быть null).
     */
    public EstimateDiffCallback(
            @NonNull List<EstimateItemDisplay> oldList,
            @NonNull List<EstimateItemDisplay> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    /**
     * Это та же запись? Сравниваем по первичному ключу {@code id}.
     * Если id совпали — RecyclerView считает, что элемент тот же самый
     * (пусть и с изменёнными полями), и вызовет {@link #areContentsTheSame}.
     */
    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        EstimateItemDisplay oldItem = oldList.get(oldItemPosition);
        EstimateItemDisplay newItem = newList.get(newItemPosition);
        // Сравниваем UUID-строки через .equals()
        return oldItem.id != null && oldItem.id.equals(newItem.id);
    }

    /**
     * Совпадает ли всё видимое содержимое строки?
     *
     * <p>Сравниваем все поля, отображаемые в {@code item_estimate.xml}:
     * <ul>
     *   <li>{@code name} — текст названия (equals).</li>
     *   <li>{@code quantity} — через {@code compareTo() == 0} (игнорирует лишние нули scale).</li>
     *   <li>{@code totalPrice} — через {@code compareTo() == 0}.</li>
     *   <li>{@code status} — логистический статус (цвет dot).</li>
     *   <li>{@code syncState} — состояние синхронизации.</li>
     * </ul>
     */
    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        EstimateItemDisplay oldItem = oldList.get(oldItemPosition);
        EstimateItemDisplay newItem = newList.get(newItemPosition);

        boolean sameName = oldItem.name != null && oldItem.name.equals(newItem.name);
        boolean sameQuantity = oldItem.quantity != null
                && oldItem.quantity.compareTo(newItem.quantity) == 0;
        boolean sameTotalPrice = oldItem.totalPrice != null
                && oldItem.totalPrice.compareTo(newItem.totalPrice) == 0;
        boolean sameStatus = oldItem.status != null && oldItem.status.equals(newItem.status);
        boolean sameSyncState = oldItem.syncState != null
                && oldItem.syncState.equals(newItem.syncState);

        return sameName && sameQuantity && sameTotalPrice && sameStatus && sameSyncState;
    }

    /**
     * Возвращает {@link Bundle} с ключами только тех полей, которые изменились.
     *
     * <p>Если элементы отличаются (areContentsTheSame = false), DiffUtil вызывает
     * этот метод для получения «частичного» payload. Адаптер в
     * {@code onBindViewHolder(holder, position, payloads)} использует payload
     * для обновления лишь конкретных View внутри ViewHolder.
     *
     * <p>Если Bundle пуст (ни одно из отслеживаемых полей не изменилось) —
     * возвращаем {@code null}, что заставит адаптер выполнить полное перебиндивание.
     *
     * @return {@link Bundle} с изменившимися ключами или {@code null}.
     */
    @Nullable
    @Override
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        EstimateItemDisplay oldItem = oldList.get(oldItemPosition);
        EstimateItemDisplay newItem = newList.get(newItemPosition);

        Bundle payload = new Bundle();

        // Количество изменилось?
        if (oldItem.quantity == null || oldItem.quantity.compareTo(newItem.quantity) != 0) {
            payload.putString(KEY_QUANTITY,
                    newItem.quantity != null ? newItem.quantity.toPlainString() : "0");
        }

        // Итоговая стоимость изменилась?
        if (oldItem.totalPrice == null || oldItem.totalPrice.compareTo(newItem.totalPrice) != 0) {
            payload.putString(KEY_TOTAL_PRICE,
                    newItem.totalPrice != null ? newItem.totalPrice.toPlainString() : "0");
        }

        // Статус изменился?
        if (oldItem.status == null || !oldItem.status.equals(newItem.status)) {
            payload.putString(KEY_STATUS, newItem.status);
        }

        // Если ни одно из отслеживаемых полей не изменилось — полное обновление
        if (payload.isEmpty()) {
            return null;
        }

        return payload;
    }
}
