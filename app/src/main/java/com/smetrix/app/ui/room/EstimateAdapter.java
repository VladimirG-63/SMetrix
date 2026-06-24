// app/src/main/java/com/smetrix/app/ui/room/EstimateAdapter.java
package com.smetrix.app.ui.room;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.smetrix.app.databinding.ItemEstimateBinding;
import com.smetrix.app.model.EstimateItemDisplay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер RecyclerView для списка позиций сметы на экране детализации комнаты.
 *
 * <p><b>Ключевые особенности:</b>
 * <ul>
 *   <li><b>DiffUtil + Payload:</b> обновление через {@link EstimateDiffCallback}.
 *       Если изменились только quantity/total_price/status — полный перебинд НЕ выполняется,
 *       только нужные View обновляются. Это убирает нежелательное мигание строк.</li>
 *   <li><b>ViewBinding:</b> все обращения к View — через {@link ItemEstimateBinding}.</li>
 *   <li><b>Индикатор статуса:</b> {@code statusDot} окрашивается в цвет логистического
 *       статуса через {@link ColorStateList}.</li>
 *   <li><b>Sync-состояние:</b> визуально отображается через tint иконки синхронизации
 *       рядом с суммой (если потребуется расширить позже — через payload ключ sync_state).</li>
 * </ul>
 *
 * @see EstimateDiffCallback
 * @see EstimateItemDisplay
 */
public class EstimateAdapter extends RecyclerView.Adapter<EstimateAdapter.ViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    // Интерфейс событий
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback-интерфейс для обработки действий пользователя над позицией сметы.
     * Fragment реализует этот интерфейс и передаёт в адаптер.
     */
    public interface OnItemActionListener {
        /**
         * Вызывается при долгом нажатии на строку сметы.
         *
         * @param itemId UUID позиции сметы.
         */
        void onLongClick(@NonNull EstimateItemDisplay item);

        /**
         * Вызывается при ручном изменении логистического статуса материала.
         *
         * @param itemId    UUID позиции сметы.
         * @param newStatus новый статус (строка из {@link com.smetrix.app.model.ItemStatus}).
         */
        void onStatusChange(@NonNull String itemId, @NonNull String newStatus);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Поля
    // ─────────────────────────────────────────────────────────────────────────

    /** Текущий список позиций сметы. Никогда не null — инициализируется как пустой список. */
    private List<EstimateItemDisplay> items = new ArrayList<>();

    /** Слушатель событий пользователя. Устанавливается через конструктор. */
    private final OnItemActionListener listener;

    /**
     * Форматировщик для отображения цен в российских рублях.
     * Используем Locale для корректного форматирования (разделители, символ валюты).
     */
    private final NumberFormat currencyFormat =
            NumberFormat.getCurrencyInstance(new Locale("ru", "RU"));

    // ─────────────────────────────────────────────────────────────────────────
    // Константы цветов статусов (ARGB)
    // ─────────────────────────────────────────────────────────────────────────

    /** ORDERED — материал заказан, ожидаем. Жёлтый. */
    private static final int COLOR_ORDERED = 0xFFFF9800;
    /** ON_SITE — материал доставлен. Зелёный. */
    private static final int COLOR_ON_SITE = 0xFF4CAF50;
    /** UNITS_MISMATCH — несоответствие единиц. Красный. */
    private static final int COLOR_UNITS_MISMATCH = 0xFFF44336;
    /** NEED_TO_BUY / default — нужно купить или неизвестно. Серый. */
    private static final int COLOR_DEFAULT = 0xFF9E9E9E;

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param listener обработчик событий долгого нажатия и смены статуса. Не должен быть null.
     */
    public EstimateAdapter(@NonNull OnItemActionListener listener) {
        this.listener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Обновление данных
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Обновляет список позиций сметы с анимацией и поддержкой частичных payload.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>Вычисляем diff между старым и новым списком через {@link EstimateDiffCallback}.</li>
     *   <li>Заменяем {@link #items} на копию нового списка.</li>
     *   <li>Диспатчим изменения — RecyclerView сам запускает анимации.</li>
     * </ol>
     *
     * @param newList новый список позиций. Если null — используется пустой список.
     */
    public void submitList(@Nullable List<EstimateItemDisplay> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }

        DiffUtil.DiffResult result =
                DiffUtil.calculateDiff(new EstimateDiffCallback(items, newList));

        items = new ArrayList<>(newList);

        result.dispatchUpdatesTo(this);
    }

    @NonNull
    public List<EstimateItemDisplay> getItemsSnapshot() {
        return new ArrayList<>(items);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView.Adapter — обязательные методы
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEstimateBinding binding = ItemEstimateBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    /**
     * Полное перебиндивание строки — вызывается когда payloads пуст
     * или при первом отображении элемента.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    /**
     * Частичное обновление — вызывается DiffUtil, когда getChangePayload вернул не null.
     *
     * <p>Если payloads непуст — обновляем только изменившиеся View через Bundle.
     * Возвращаем сразу после частичного обновления, НЕ вызываем полный bind.
     */
    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position,
            @NonNull List<Object> payloads) {

        if (payloads.isEmpty()) {
            // Нет payload — полный перебинд
            onBindViewHolder(holder, position);
            return;
        }

        // Есть payload — частичное обновление
        for (Object payloadObj : payloads) {
            if (!(payloadObj instanceof Bundle)) {
                continue;
            }
            Bundle bundle = (Bundle) payloadObj;

            // Quantity изменилось → обновляем только tvItemQuantity
            if (bundle.containsKey(EstimateDiffCallback.KEY_QUANTITY)) {
                String rawQuantity = bundle.getString(EstimateDiffCallback.KEY_QUANTITY, "0");
                BigDecimal quantity = new BigDecimal(rawQuantity)
                        .setScale(2, RoundingMode.HALF_UP);
                holder.binding.tvItemQuantity.setText(quantity.toPlainString());
            }

            // Total price изменилось → обновляем только tvItemTotal
            if (bundle.containsKey(EstimateDiffCallback.KEY_TOTAL_PRICE)) {
                String rawTotal = bundle.getString(EstimateDiffCallback.KEY_TOTAL_PRICE, "0");
                BigDecimal totalPrice = new BigDecimal(rawTotal);
                holder.binding.tvItemTotal.setText(formatPrice(totalPrice));
            }

            // Статус изменился → обновляем только statusDot
            if (bundle.containsKey(EstimateDiffCallback.KEY_STATUS)) {
                String newStatus = bundle.getString(EstimateDiffCallback.KEY_STATUS);
                holder.updateStatusIndicator(newStatus);
            }
        }
        // Намеренно НЕ вызываем super.onBindViewHolder() — частичное обновление завершено
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ViewHolder для строки позиции сметы.
     *
     * <p>Все View-поля доступны через {@link ItemEstimateBinding}.
     * Метод {@link #updateStatusIndicator(String)} позволяет обновить
     * только точку-индикатор без полного перебинда строки.
     */
    public class ViewHolder extends RecyclerView.ViewHolder {

        /** ViewBinding для item_estimate.xml. Пакетная область — для доступа из onBindViewHolder. */
        final ItemEstimateBinding binding;

        public ViewHolder(@NonNull ItemEstimateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Полное заполнение строки данными позиции сметы.
         *
         * @param item данные позиции сметы из ViewModel.
         */
        public void bind(@NonNull EstimateItemDisplay item) {
            // ── Текстовые поля ───────────────────────────────────────────────
            binding.tvItemName.setText(item.name);
            binding.tvItemUnit.setText(item.unitMeasure);
            binding.tvItemQuantity.setText(
                    item.quantity.setScale(2, RoundingMode.HALF_UP).toPlainString()
            );
            binding.tvItemPrice.setText(formatPrice(item.finalPrice));
            binding.tvItemTotal.setText(formatPrice(item.totalPrice));

            // ── Индикатор логистического статуса ─────────────────────────────
            updateStatusIndicator(item.status);

            // ── Долгое нажатие → callback в Fragment ─────────────────────────
            itemView.setOnLongClickListener(v -> {
                listener.onLongClick(item);
                return true;
            });
        }

        /**
         * Обновляет цвет {@code statusDot} в зависимости от логистического статуса материала.
         *
         * <p>Цвета соответствуют требованиям плана:
         * <ul>
         *   <li>{@code ORDERED}        → {@code #FF9800} (жёлтый).</li>
         *   <li>{@code ON_SITE}         → {@code #4CAF50} (зелёный).</li>
         *   <li>{@code UNITS_MISMATCH}  → {@code #F44336} (красный).</li>
         *   <li>{@code default}         → {@code #9E9E9E} (серый).</li>
         * </ul>
         *
         * @param status строковое значение статуса из {@link com.smetrix.app.model.ItemStatus}.
         */
        public void updateStatusIndicator(@Nullable String status) {
            final int color;

            if (status == null) {
                color = COLOR_DEFAULT;
            } else {
                switch (status) {
                    case "ORDERED":
                        color = COLOR_ORDERED;
                        break;
                    case "ON_SITE":
                        color = COLOR_ON_SITE;
                        break;
                    case "UNITS_MISMATCH":
                        color = COLOR_UNITS_MISMATCH;
                        break;
                    default:
                        // NEED_TO_BUY или неизвестный статус — серый
                        color = COLOR_DEFAULT;
                        break;
                }
            }

            binding.statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Форматирует денежное значение в строку с символом рубля.
     *
     * <p>Использует {@link NumberFormat#getCurrencyInstance(Locale)} для Locale("ru", "RU").
     * Это корректно отображает разделитель тысяч, дробную часть и символ ₽
     * в соответствии с российскими стандартами. Символ рубля не хардкодится в коде.
     *
     * @param value денежное значение (BigDecimal). Если null — возвращает «0 ₽».
     * @return строка с форматированной суммой, например «12 500,00 ₽».
     */
    private String formatPrice(@Nullable BigDecimal value) {
        if (value == null) {
            return currencyFormat.format(BigDecimal.ZERO);
        }
        return currencyFormat.format(value);
    }
}
