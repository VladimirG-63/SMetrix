// app/src/main/java/com/smetrix/app/ui/room/WorkTaskAdapter.java
package com.smetrix.app.ui.room;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.smetrix.app.databinding.ItemWorkTaskBinding;
import com.smetrix.app.db.entity.WorkTaskEntity;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер для отображения списка рабочих задач (WorkTask) в {@link RoomDetailFragment}.
 *
 * <p>Шаг 6.9: отображает задачи с именем рабочего, описанием, типом оплаты и суммой.
 * Каждая задача имеет кнопки «Редактировать» и «Удалить».
 *
 * <p><b>ViewBinding:</b> использует {@link ItemWorkTaskBinding}.
 * <p><b>DiffUtil:</b> обновления через {@link #submitList(List)}.
 */
public class WorkTaskAdapter extends RecyclerView.Adapter<WorkTaskAdapter.ViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    // Интерфейс событий
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback-интерфейс для действий с задачей (редактирование, удаление).
     */
    public interface OnWorkTaskActionListener {
        /** Вызывается при нажатии «Редактировать». */
        void onEdit(@NonNull WorkTaskEntity task);

        /** Вызывается при нажатии «Удалить». */
        void onDelete(@NonNull String taskId, @NonNull String taskName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Поля
    // ─────────────────────────────────────────────────────────────────────────

    /** Текущий список задач. */
    private List<WorkTaskEntity> items = new ArrayList<>();

    /** Слушатель событий. */
    private final OnWorkTaskActionListener listener;

    /** Форматировщик валюты. */
    private final NumberFormat currencyFormat =
            NumberFormat.getCurrencyInstance(new Locale("ru", "RU"));

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    public WorkTaskAdapter(@NonNull OnWorkTaskActionListener listener) {
        this.listener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Обновление данных
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Обновляет список задач с анимацией через DiffUtil.
     *
     * @param newList новый список {@link WorkTaskEntity}. Если null — пустой список.
     */
    public void submitList(@Nullable List<WorkTaskEntity> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new WorkTaskDiffCallback(items, newList));
        items = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWorkTaskBinding binding = ItemWorkTaskBinding.inflate(
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

        private final ItemWorkTaskBinding binding;

        public ViewHolder(@NonNull ItemWorkTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(@NonNull WorkTaskEntity task) {
            // Название задачи
            binding.tvTaskName.setText(task.taskName != null ? task.taskName : "");

            // Рабочий (workerId — только ID, имя нужно отдельным запросом)
            if (task.workerId != null && !task.workerId.isEmpty()) {
                binding.tvWorkerName.setText(task.workerId);
            } else {
                binding.tvWorkerName.setText("Не назначен");
            }

            // Итоговая сумма оплаты
            if (task.totalPayment != null) {
                binding.tvTaskPayment.setText(currencyFormat.format(task.totalPayment));
            } else {
                binding.tvTaskPayment.setText("—");
            }

            // Тип оплаты
            if ("PIECEWORK".equals(task.rateType)) {
                binding.tvRateType.setText("Сдельно");
            } else if ("FIXED".equals(task.rateType)) {
                binding.tvRateType.setText("Фиксировано");
            } else {
                binding.tvRateType.setText("");
            }

            // Кнопка «Редактировать»
            binding.ibEditTask.setOnClickListener(v -> listener.onEdit(task));

            // Кнопка «Удалить»
            binding.ibDeleteTask.setOnClickListener(v ->
                    listener.onDelete(task.id, task.taskName != null ? task.taskName : ""));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DiffUtil.Callback
    // ─────────────────────────────────────────────────────────────────────────

    private static final class WorkTaskDiffCallback extends DiffUtil.Callback {

        private final List<WorkTaskEntity> oldList;
        private final List<WorkTaskEntity> newList;

        WorkTaskDiffCallback(@NonNull List<WorkTaskEntity> oldList,
                             @NonNull List<WorkTaskEntity> newList) {
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
            WorkTaskEntity o = oldList.get(oldPos);
            WorkTaskEntity n = newList.get(newPos);
            return o.id != null && o.id.equals(n.id);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            WorkTaskEntity o = oldList.get(oldPos);
            WorkTaskEntity n = newList.get(newPos);
            boolean sameName = o.taskName != null && o.taskName.equals(n.taskName);
            boolean samePayment = o.totalPayment != null
                    && n.totalPayment != null
                    && o.totalPayment.compareTo(n.totalPayment) == 0;
            return sameName && samePayment;
        }
    }
}
