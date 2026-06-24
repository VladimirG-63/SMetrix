// app/src/main/java/com/smetrix/app/ui/room/RoomAdapter.java
package com.smetrix.app.ui.room;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.smetrix.app.databinding.ItemRoomBinding;
import com.smetrix.app.db.entity.ProjectRoomEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер для списка комнат проекта в {@link RoomListFragment}.
 *
 * <p><b>ViewBinding:</b> использует {@link ItemRoomBinding}, никакого {@code findViewById}.
 * <p><b>DiffUtil:</b> обновления через {@link #submitList(List)} с {@link RoomDiffCallback}.
 * <p><b>Клики:</b> короткое нажатие — переход к деталям комнаты через {@link OnRoomActionListener}.
 */
public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.ViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    // Интерфейс событий
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback-интерфейс для обработки действий пользователя над карточкой комнаты.
     */
    public interface OnRoomActionListener {
        /** Вызывается при нажатии на карточку — открывает детали комнаты. */
        void onClick(ProjectRoomEntity room);

        /** Вызывается при долгом нажатии — показывает диалог удаления. */
        void onLongClick(ProjectRoomEntity room);

        /** Вызывается при нажатии на иконку редактирования — диалог переименования. */
        void onEditClick(ProjectRoomEntity room);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Поля
    // ─────────────────────────────────────────────────────────────────────────

    /** Текущий список комнат. Никогда не null. */
    private List<ProjectRoomEntity> items = new ArrayList<>();

    /** Слушатель событий. */
    private final OnRoomActionListener listener;

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    public RoomAdapter(@NonNull OnRoomActionListener listener) {
        this.listener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Обновление данных
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Обновляет список комнат с анимацией через DiffUtil.
     *
     * @param newList новый список комнат. Если null — используется пустой список.
     */
    public void submitList(List<ProjectRoomEntity> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new RoomDiffCallback(items, newList));
        items = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRoomBinding binding = ItemRoomBinding.inflate(
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

    /**
     * ViewHolder для карточки комнаты.
     */
    public class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemRoomBinding binding;

        public ViewHolder(@NonNull ItemRoomBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(@NonNull ProjectRoomEntity room) {
            // ── Название комнаты ─────────────────────────────────────────────
            binding.tvRoomName.setText(room.name);

            // ── Площадь (совпадает с полем «Площадь» внутри комнаты) ────────
            if (room.length != null && room.width != null && room.height != null) {
                BigDecimal displayArea;
                if (room.manualAreaOverride != null) {
                    // Если задана ручная площадь — показываем её
                    displayArea = room.manualAreaOverride;
                } else {
                    // Иначе: 2*(L+W)*H (площадь стен, как в деталях комнаты)
                    BigDecimal TWO = new BigDecimal("2");
                    displayArea = TWO
                            .multiply(room.length.add(room.width))
                            .multiply(room.height);
                }
                displayArea = displayArea.setScale(1, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                binding.tvRoomArea.setText(displayArea.toPlainString() + " м²");
                binding.tvRoomArea.setVisibility(android.view.View.VISIBLE);
            } else if (room.manualAreaOverride != null) {
                // Ручная площадь задана даже без размеров
                BigDecimal displayArea = room.manualAreaOverride
                        .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
                binding.tvRoomArea.setText(displayArea.toPlainString() + " м²");
                binding.tvRoomArea.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.tvRoomArea.setVisibility(android.view.View.GONE);
            }

            // ── Клик → переход к деталям комнаты ────────────────────────────
            binding.getRoot().setOnClickListener(v -> listener.onClick(room));

            // ── Долгий клик → диалог удаления ───────────────────────────────
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onLongClick(room);
                return true;
            });

            // ── Нажатие на карандаш → диалог переименования ─────────────────
            binding.ivEditRoom.setOnClickListener(v -> listener.onEditClick(room));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DiffUtil.Callback
    // ─────────────────────────────────────────────────────────────────────────

    private static final class RoomDiffCallback extends DiffUtil.Callback {

        private final List<ProjectRoomEntity> oldList;
        private final List<ProjectRoomEntity> newList;

        RoomDiffCallback(@NonNull List<ProjectRoomEntity> oldList,
                         @NonNull List<ProjectRoomEntity> newList) {
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
            ProjectRoomEntity o = oldList.get(oldPos);
            ProjectRoomEntity n = newList.get(newPos);
            return o.id != null && o.id.equals(n.id);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            ProjectRoomEntity o = oldList.get(oldPos);
            ProjectRoomEntity n = newList.get(newPos);
            boolean sameName = o.name != null && o.name.equals(n.name);
            boolean sameUpdatedAt = o.updatedAt == n.updatedAt;
            return sameName && sameUpdatedAt;
        }
    }
}
