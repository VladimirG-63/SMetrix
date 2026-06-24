// app/src/main/java/com/smetrix/app/ui/room/WorkerAdapter.java
package com.smetrix.app.ui.room;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.smetrix.app.databinding.ItemWorkerBinding;
import com.smetrix.app.db.entity.WorkerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер для отображения списка рабочих в {@link WorkerListFragment}.
 *
 * <p>Каждый элемент показывает: имя, специализацию, телефон и
 * кнопки «Редактировать» / «Удалить».
 */
public class WorkerAdapter extends RecyclerView.Adapter<WorkerAdapter.ViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    // Интерфейс событий
    // ─────────────────────────────────────────────────────────────────────────

    /** Callback-интерфейс для редактирования и удаления рабочего. */
    public interface OnWorkerActionListener {
        /** Вызывается при нажатии «Редактировать». */
        void onEdit(@NonNull WorkerEntity worker);

        /** Вызывается при нажатии «Удалить». */
        void onDelete(@NonNull String workerId, @NonNull String workerName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Поля
    // ─────────────────────────────────────────────────────────────────────────

    private List<WorkerEntity> items = new ArrayList<>();
    private final OnWorkerActionListener listener;

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    public WorkerAdapter(@NonNull OnWorkerActionListener listener) {
        this.listener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Обновление данных
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Обновляет список рабочих с анимацией через DiffUtil.
     *
     * @param newList новый список рабочих.
     */
    public void submitList(@Nullable List<WorkerEntity> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(
                new WorkerDiffCallback(items, newList));
        items = new ArrayList<>(newList);
        result.dispatchUpdatesTo(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWorkerBinding binding = ItemWorkerBinding.inflate(
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

        private final ItemWorkerBinding binding;

        public ViewHolder(@NonNull ItemWorkerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(@NonNull WorkerEntity worker) {
            // Имя
            binding.tvWorkerName.setText(worker.fullName != null ? worker.fullName : "");

            // Специальность
            String specialty = worker.specialty != null ? worker.specialty : "";
            binding.tvWorkerSpecialty.setText(specialty.isEmpty() ? "—" : specialty);

            // Телефон
            String phone = worker.phone != null ? worker.phone : "";
            binding.tvWorkerPhone.setText(phone.isEmpty() ? "—" : phone);

            // Кнопка «Редактировать»
            binding.ibEditWorker.setOnClickListener(v -> listener.onEdit(worker));

            // Кнопка «Удалить»
            binding.ibDeleteWorker.setOnClickListener(v ->
                    listener.onDelete(worker.id,
                            worker.fullName != null ? worker.fullName : ""));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DiffUtil.Callback
    // ─────────────────────────────────────────────────────────────────────────

    private static final class WorkerDiffCallback extends DiffUtil.Callback {

        private final List<WorkerEntity> oldList;
        private final List<WorkerEntity> newList;

        WorkerDiffCallback(@NonNull List<WorkerEntity> oldList,
                           @NonNull List<WorkerEntity> newList) {
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
            WorkerEntity o = oldList.get(oldPos);
            WorkerEntity n = newList.get(newPos);
            return o.id != null && o.id.equals(n.id);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            WorkerEntity o = oldList.get(oldPos);
            WorkerEntity n = newList.get(newPos);
            boolean sameName = o.fullName != null && o.fullName.equals(n.fullName);
            boolean samePhone = (o.phone == null && n.phone == null)
                    || (o.phone != null && o.phone.equals(n.phone));
            return sameName && samePhone;
        }
    }
}
