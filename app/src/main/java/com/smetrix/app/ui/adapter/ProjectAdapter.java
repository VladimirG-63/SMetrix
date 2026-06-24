// app/src/main/java/com/smetrix/app/ui/adapter/ProjectAdapter.java
package com.smetrix.app.ui.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.smetrix.app.databinding.ItemProjectBinding;
import com.smetrix.app.db.entity.ProjectEntity;
import com.smetrix.app.model.SyncState;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер для отображения списка проектов в RecyclerView на экране ProjectListFragment.
 *
 * <p><b>DiffUtil:</b> обновления списка выполняются через внутренний
 * {@link ProjectDiffCallback}, что обеспечивает анимированные изменения
 * без полной перерисовки списка.
 *
 * <p><b>ViewBinding:</b> каждый ViewHolder работает через {@link ItemProjectBinding},
 * никакого {@code findViewById} в коде нет.
 *
 * <p><b>Интерфейс:</b> {@link OnProjectClickListener} — единственный публичный
 * callback-интерфейс. Содержит {@code onProjectClick} и {@code onProjectLongClick}.
 */
public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    // Интерфейс событий
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback-интерфейс для обработки нажатий на карточку проекта.
     * Fragment реализует этот интерфейс (или передаёт анонимный класс).
     */
    public interface OnProjectClickListener {
        /**
         * Вызывается при коротком нажатии на карточку (переход к комнатам).
         *
         * @param project сущность проекта, на которую нажали.
         */
        void onProjectClick(@NonNull ProjectEntity project);

        /**
         * Вызывается при долгом нажатии на карточку (диалог удаления).
         *
         * @param project сущность проекта, на которую нажали долго.
         */
        void onProjectLongClick(@NonNull ProjectEntity project);

        /**
         * Вызывается при нажатии на иконку редактирования (карандаш).
         *
         * @param project сущность проекта для редактирования.
         */
        void onProjectEditClick(@NonNull ProjectEntity project);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Поля
    // ─────────────────────────────────────────────────────────────────────────

    /** Текущий список проектов. Никогда не бывает null — инициализируется как пустой список. */
    private List<ProjectEntity> items = new ArrayList<>();

    /** Единственный слушатель событий. Устанавливается через конструктор. */
    private final OnProjectClickListener listener;

    /** Форматтер для отображения даты последнего обновления проекта. */
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param listener обработчик нажатий (короткое и долгое). Не должен быть null.
     */
    public ProjectAdapter(@NonNull OnProjectClickListener listener) {
        this.listener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Обновление данных
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Обновляет список проектов с анимацией через DiffUtil.
     *
     * <p>Сравнивает текущий {@link #items} с {@code newList} с помощью
     * {@link ProjectDiffCallback} и диспатчит минимальный набор изменений.
     * Вызывать из UI-потока.
     *
     * @param newList новый список проектов. Если null — используется пустой список.
     */
    public void submitList(@Nullable List<ProjectEntity> newList) {
        if (newList == null) {
            newList = new ArrayList<>();
        }

        // Вычисляем минимальный diff между старым и новым списком
        DiffUtil.DiffResult diffResult =
                DiffUtil.calculateDiff(new ProjectDiffCallback(items, newList));

        // Обновляем поле items (делаем копию, чтобы избежать мутации снаружи)
        items = new ArrayList<>(newList);

        // Диспатчим изменения в RecyclerView — он сам запустит нужные анимации
        diffResult.dispatchUpdatesTo(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Инфлейтим через ViewBinding — никакого inflate(R.layout...) напрямую
        ItemProjectBinding binding = ItemProjectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProjectEntity project = items.get(position);
        holder.bind(project);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ViewHolder для карточки проекта.
     * Все поля — ссылки из {@link ItemProjectBinding}.
     */
    public class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemProjectBinding binding;

        public ViewHolder(@NonNull ItemProjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Заполняет карточку данными конкретного проекта.
         *
         * @param project данные проекта из Room.
         */
        public void bind(@NonNull ProjectEntity project) {
            // ── Текстовые поля ───────────────────────────────────────────────
            binding.tvProjectName.setText(project.name);
            binding.tvProjectCity.setText(project.city);

            // Форматируем timestamp в читаемую дату
            String formattedDate = dateFormat.format(new Date(project.updatedAt));
            binding.tvProjectUpdatedAt.setText(
                    binding.getRoot().getContext()
                            .getString(com.smetrix.app.R.string.label_updated_at, formattedDate)
            );

            // ── Индикатор синхронизации ───────────────────────────────────────
            updateSyncDot(project.syncState);

            // ── Короткое нажатие → переход к комнатам ────────────────────────
            binding.getRoot().setOnClickListener(v -> listener.onProjectClick(project));

            // ── Долгое нажатие → диалог удаления ─────────────────────────────
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onProjectLongClick(project);
                return true;
            });

            // ── Нажатие на иконку карандаша → диалог редактирования ───────────
            binding.ivEditProject.setOnClickListener(v -> listener.onProjectEditClick(project));
        }

        /**
         * Обновляет цвет точки-индикатора синхронизации в зависимости от syncState.
         *
         * @param syncStateStr строковое имя значения {@link SyncState}.
         */
        public void updateSyncDot(@Nullable String syncStateStr) {
            final int color;

            if (SyncState.SYNCED.name().equals(syncStateStr)) {
                color = 0xFF4CAF50; // Зелёный — синхронизировано
            } else if (SyncState.PENDING_CREATE.name().equals(syncStateStr)
                    || SyncState.PENDING_UPDATE.name().equals(syncStateStr)
                    || SyncState.PENDING_DELETE.name().equals(syncStateStr)) {
                color = 0xFFFF9800; // Оранжевый — ожидает отправки
            } else if (SyncState.CONFLICT.name().equals(syncStateStr)) {
                color = 0xFFFFC107; // Жёлтый — конфликт
            } else if (SyncState.FAILED.name().equals(syncStateStr)) {
                color = 0xFFF44336; // Красный — ошибка
            } else {
                color = 0xFF9E9E9E; // Серый — неизвестное состояние
            }

            binding.ivSyncDot.setImageTintList(ColorStateList.valueOf(color));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DiffUtil.Callback
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Стратегия сравнения для DiffUtil.
     *
     * <p>{@code areItemsTheSame} — сравниваем по первичному ключу {@code id}.
     * {@code areContentsTheSame} — сравниваем поля, которые отображаются в карточке.
     */
    private static final class ProjectDiffCallback extends DiffUtil.Callback {

        private final List<ProjectEntity> oldList;
        private final List<ProjectEntity> newList;

        ProjectDiffCallback(
                @NonNull List<ProjectEntity> oldList,
                @NonNull List<ProjectEntity> newList) {
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
         * Это одна и та же запись? Сравниваем по первичному ключу (id).
         */
        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            ProjectEntity oldItem = oldList.get(oldItemPosition);
            ProjectEntity newItem = newList.get(newItemPosition);
            return oldItem.id != null && oldItem.id.equals(newItem.id);
        }

        /**
         * Совпадает ли видимое содержимое карточки?
         * Сравниваем только те поля, которые отображаются в item_project.xml.
         */
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            ProjectEntity oldItem = oldList.get(oldItemPosition);
            ProjectEntity newItem = newList.get(newItemPosition);

            boolean sameName = oldItem.name != null && oldItem.name.equals(newItem.name);
            boolean sameUpdatedAt = oldItem.updatedAt == newItem.updatedAt;
            boolean sameSyncState = oldItem.syncState != null
                    && oldItem.syncState.equals(newItem.syncState);

            return sameName && sameUpdatedAt && sameSyncState;
        }
    }
}
