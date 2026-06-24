// app/src/main/java/com/smetrix/app/ui/room/MaterialSearchAdapter.java
package com.smetrix.app.ui.room;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.smetrix.app.databinding.ItemMaterialSearchBinding;
import com.smetrix.app.network.dto.MaterialDto;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер для отображения результатов поиска материалов в {@link MaterialSearchBottomSheet}.
 *
 * <p>Шаг 6.8: Адаптер для поиска материалов.
 * <ul>
 *   <li>Данные — список {@link MaterialDto} (DTO из сети).</li>
 *   <li>Обновление через {@link #submitList(List)} с DiffUtil.</li>
 *   <li>По нажатию на элемент — {@link OnMaterialSelectedListener} с выбранным MaterialDto.</li>
 * </ul>
 *
 * <p><b>ViewBinding:</b> использует {@link ItemMaterialSearchBinding}.
 */
public class MaterialSearchAdapter extends RecyclerView.Adapter<MaterialSearchAdapter.ViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    // Интерфейс событий
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback-интерфейс: вызывается при нажатии на материал в списке результатов.
     */
    public interface OnMaterialSelectedListener {
        /**
         * Вызывается при нажатии на элемент результата поиска.
         *
         * @param material выбранный материал из ФГИС ЦС.
         */
        void onSelected(@NonNull MaterialDto material);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Поля
    // ─────────────────────────────────────────────────────────────────────────

    /** Текущий список результатов поиска. */
    private List<MaterialDto> items = new ArrayList<>();

    /** Слушатель выбора материала. */
    private final OnMaterialSelectedListener listener;

    /** Форматировщик для отображения цены. */
    private final NumberFormat currencyFormat =
            NumberFormat.getCurrencyInstance(new Locale("ru", "RU"));

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    public MaterialSearchAdapter(@NonNull OnMaterialSelectedListener listener) {
        this.listener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Обновление данных
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Обновляет список результатов поиска с анимацией через DiffUtil.
     *
     * @param newList новый список {@link MaterialDto}. Если null — пустой список.
     */
    public void submitList(List<MaterialDto> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new MaterialDiffCallback(items, newList));
        items = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMaterialSearchBinding binding = ItemMaterialSearchBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemMaterialSearchBinding binding;

        public ViewHolder(@NonNull ItemMaterialSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(@NonNull MaterialDto material) {
            // Название материала
            binding.tvMaterialName.setText(material.name != null ? material.name : "");

            // Код ФГИС ЦС
            if (material.fgisCode != null && !material.fgisCode.isEmpty()) {
                binding.tvMaterialCode.setText(
                        binding.getRoot().getContext().getString(
                                com.smetrix.app.R.string.col_name) + ": " + material.fgisCode);
            } else {
                binding.tvMaterialCode.setText("Код: —");
            }

            // Цена (basePrice приходит как String)
            if (material.basePrice != null && !material.basePrice.isEmpty()) {
                try {
                    BigDecimal price = new BigDecimal(material.basePrice);
                    binding.tvMaterialPrice.setText(currencyFormat.format(price));
                } catch (NumberFormatException ignored) {
                    binding.tvMaterialPrice.setText(material.basePrice);
                }
            } else {
                binding.tvMaterialPrice.setText("—");
            }

            // Единица измерения
            binding.tvMaterialUnit.setText(
                    material.unitMeasure != null ? material.unitMeasure : "");

            // Клик — передаём выбранный материал
            binding.getRoot().setOnClickListener(v -> listener.onSelected(material));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DiffUtil.Callback
    // ─────────────────────────────────────────────────────────────────────────

    private static final class MaterialDiffCallback extends DiffUtil.Callback {

        private final List<MaterialDto> oldList;
        private final List<MaterialDto> newList;

        MaterialDiffCallback(@NonNull List<MaterialDto> oldList,
                             @NonNull List<MaterialDto> newList) {
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

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            MaterialDto o = oldList.get(oldPos);
            MaterialDto n = newList.get(newPos);
            return o.fgisCode != null && o.fgisCode.equals(n.fgisCode);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            MaterialDto o = oldList.get(oldPos);
            MaterialDto n = newList.get(newPos);
            boolean sameName = o.name != null && o.name.equals(n.name);
            boolean samePrice = o.basePrice != null && o.basePrice.equals(n.basePrice);
            return sameName && samePrice;
        }
    }
}
