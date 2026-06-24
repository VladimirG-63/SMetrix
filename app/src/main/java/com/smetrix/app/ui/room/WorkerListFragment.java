// app/src/main/java/com/smetrix/app/ui/room/WorkerListFragment.java
package com.smetrix.app.ui.room;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.smetrix.app.R;
import com.smetrix.app.databinding.FragmentWorkerListBinding;
import com.smetrix.app.db.entity.WorkerEntity;
import com.smetrix.app.viewmodel.WorkerViewModel;

import java.util.List;

/**
 * Экран управления бригадой (списком рабочих).
 *
 * <p>Отображает список всех рабочих пользователя через {@link WorkerAdapter}.
 * Позволяет:
 * <ul>
 *   <li>Создать нового рабочего (FAB).</li>
 *   <li>Редактировать существующего рабочего (кнопка-карандаш в строке).</li>
 *   <li>Удалить рабочего (кнопка-корзина + диалог подтверждения).</li>
 * </ul>
 */
public class WorkerListFragment extends Fragment {

    /** Тег для Fragment Back Stack и поиска через FragmentManager. */
    public static final String TAG = "WorkerListFragment";

    // ─────────────────────────────────────────────────────────────────────────
    // ViewBinding
    // ─────────────────────────────────────────────────────────────────────────

    private FragmentWorkerListBinding binding;

    // ─────────────────────────────────────────────────────────────────────────
    // Зависимости
    // ─────────────────────────────────────────────────────────────────────────

    private WorkerViewModel viewModel;
    private WorkerAdapter adapter;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentWorkerListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(WorkerViewModel.class);

        setupRecyclerView();
        setupFab();
        observeData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Инициализация UI
    // ─────────────────────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new WorkerAdapter(new WorkerAdapter.OnWorkerActionListener() {
            @Override
            public void onEdit(@NonNull WorkerEntity worker) {
                showEditWorkerDialog(worker);
            }

            @Override
            public void onDelete(@NonNull String workerId, @NonNull String workerName) {
                showDeleteWorkerDialog(workerId, workerName);
            }
        });

        binding.rvWorkers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWorkers.setAdapter(adapter);
        binding.rvWorkers.setHasFixedSize(false);
    }

    private void setupFab() {
        binding.fabAddWorker.setOnClickListener(v -> showCreateWorkerDialog());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LiveData observers
    // ─────────────────────────────────────────────────────────────────────────

    private void observeData() {
        viewModel.getWorkers().observe(getViewLifecycleOwner(), workers -> {
            adapter.submitList(workers);
            updateEmptyState(workers);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Empty state
    // ─────────────────────────────────────────────────────────────────────────

    private void updateEmptyState(@Nullable List<WorkerEntity> workers) {
        if (binding == null) return;
        boolean isEmpty = workers == null || workers.isEmpty();
        binding.tvEmptyWorkers.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvWorkers.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Диалоги
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Диалог создания нового рабочего.
     */
    private void showCreateWorkerDialog() {
        showWorkerFormDialog(null);
    }

    /**
     * Диалог редактирования существующего рабочего (с предзаполненными данными).
     *
     * @param worker рабочий для редактирования.
     */
    private void showEditWorkerDialog(@NonNull WorkerEntity worker) {
        showWorkerFormDialog(worker);
    }

    /**
     * Универсальный диалог формы рабочего.
     * Если {@code existingWorker == null} — режим создания; иначе — редактирование.
     *
     * @param existingWorker рабочий для предзаполнения данных, или null.
     */
    private void showWorkerFormDialog(@Nullable WorkerEntity existingWorker) {
        boolean isEditMode = existingWorker != null;

        View formView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_worker, null);

        final com.google.android.material.textfield.TextInputLayout tilName =
                formView.findViewById(R.id.tilWorkerName);
        final com.google.android.material.textfield.TextInputEditText etName =
                formView.findViewById(R.id.etWorkerName);
        final com.google.android.material.textfield.TextInputEditText etPhone =
                formView.findViewById(R.id.etWorkerPhone);
        final com.google.android.material.textfield.TextInputEditText etSpecialty =
                formView.findViewById(R.id.etWorkerSpecialty);

        // Предзаполняем поля при редактировании
        if (isEditMode) {
            etName.setText(existingWorker.fullName);
            etPhone.setText(existingWorker.phone);
            etSpecialty.setText(existingWorker.specialty);
        }

        String positiveLabel = isEditMode
                ? getString(R.string.label_save)
                : getString(R.string.label_create);

        int titleRes = isEditMode
                ? R.string.title_edit_worker
                : R.string.title_create_worker;

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setView(formView)
                .setPositiveButton(positiveLabel, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText() != null
                        ? etName.getText().toString().trim() : "";

                if (name.isEmpty()) {
                    if (tilName != null) {
                        tilName.setError(getString(R.string.error_field_required));
                    }
                    return;
                }
                if (tilName != null) tilName.setError(null);

                String phone = etPhone.getText() != null
                        ? etPhone.getText().toString().trim() : "";
                String specialty = etSpecialty.getText() != null
                        ? etSpecialty.getText().toString().trim() : "";

                if (isEditMode) {
                    viewModel.updateWorker(existingWorker.id, name, phone, specialty);
                } else {
                    viewModel.saveWorker(name, phone, specialty);
                }
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    /**
     * Диалог подтверждения удаления рабочего.
     *
     * @param workerId   идентификатор рабочего.
     * @param workerName имя рабочего (для отображения в диалоге).
     */
    private void showDeleteWorkerDialog(@NonNull String workerId, @NonNull String workerName) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_worker_title)
                .setMessage(getString(R.string.dialog_delete_worker_message, workerName))
                .setPositiveButton(R.string.label_delete, (dialog, which) ->
                        viewModel.deleteWorker(workerId))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
