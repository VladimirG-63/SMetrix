
package com.smetrix.app.ui.room;

import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListPopupWindow;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.smetrix.app.R;
import com.smetrix.app.databinding.FragmentRoomDetailBinding;
import com.smetrix.app.db.entity.OpeningEntity;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.db.entity.WorkTaskEntity;
import com.smetrix.app.db.entity.WorkerEntity;
import com.smetrix.app.model.EstimateItemDisplay;
import com.smetrix.app.model.RoomTotals;
import com.smetrix.app.network.dto.MaterialDto;
import com.smetrix.app.viewmodel.RoomDetailViewModel;
import com.smetrix.app.viewmodel.WorkerViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;




public class RoomDetailFragment extends Fragment {

    public static final String TAG = "RoomDetailFragment";
    private static final String ARG_ROOM_ID = "room_id";

    private FragmentRoomDetailBinding binding;
    private RoomDetailViewModel viewModel;
    private EstimateAdapter estimateAdapter;
    private WorkTaskAdapter workTaskAdapter;
    private boolean suppressDimensionTextChanges;
    private String currentRegionCode = "";

    private final NumberFormat currencyFormat =
            NumberFormat.getCurrencyInstance(new Locale("ru", "RU"));

    @NonNull
    public static RoomDetailFragment newInstance(@NonNull String roomId) {
        RoomDetailFragment fragment = new RoomDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRoomDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String roomId = (args != null) ? args.getString(ARG_ROOM_ID) : null;
        if (roomId == null || roomId.isEmpty()) {
            throw new IllegalStateException(
                    "RoomDetailFragment требует аргумент ARG_ROOM_ID. "
                    + "Используйте RoomDetailFragment.newInstance(roomId).");
        }

        viewModel = new ViewModelProvider(
                this,
                new RoomDetailViewModelFactory(requireActivity().getApplication(), roomId)
        ).get(RoomDetailViewModel.class);

        setupRecyclerView();
        setupDimensionListeners();
        setupManualAreaControls();
        setupAddMaterialButton();
        setupAddWorkTaskButton();
        setupAddOpeningButton();
        observeData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onPause() {
        applyDimensions();
        super.onPause();
    }





    private void setupRecyclerView() {
        estimateAdapter = new EstimateAdapter(new EstimateAdapter.OnItemActionListener() {
            @Override
            public void onLongClick(@NonNull EstimateItemDisplay item) {
                showEstimateItemActions(item);
            }

            @Override
            public void onStatusChange(@NonNull String itemId, @NonNull String newStatus) {

                viewModel.updateEstimateItemStatus(itemId, newStatus);
            }
        });

        binding.rvEstimateItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvEstimateItems.setAdapter(estimateAdapter);
        binding.rvEstimateItems.setHasFixedSize(false);


        workTaskAdapter = new WorkTaskAdapter(new WorkTaskAdapter.OnWorkTaskActionListener() {
            @Override
            public void onEdit(@NonNull WorkTaskEntity task) {
                showAddWorkTaskDialog(task);
            }

            @Override
            public void onDelete(@NonNull String taskId, @NonNull String taskName) {
                showDeleteTaskDialog(taskId, taskName);
            }
        });
        binding.rvWorkTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWorkTasks.setAdapter(workTaskAdapter);
        binding.rvWorkTasks.setHasFixedSize(false);
    }

    private void setupDimensionListeners() {
        View.OnFocusChangeListener listener = buildFocusListenerWithWarning();
        binding.blockRoomDimensions.etLength.setOnFocusChangeListener(listener);
        binding.blockRoomDimensions.etWidth.setOnFocusChangeListener(listener);
        binding.blockRoomDimensions.etHeight.setOnFocusChangeListener(listener);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressDimensionTextChanges) return;
                updateAreaDisplayFromInputs();
                applyDimensions();
            }
        };
        binding.blockRoomDimensions.etLength.addTextChangedListener(watcher);
        binding.blockRoomDimensions.etWidth.addTextChangedListener(watcher);
        binding.blockRoomDimensions.etHeight.addTextChangedListener(watcher);
    }

    private void setupAddMaterialButton() {
        binding.btnAddMaterial.setOnClickListener(v -> showMaterialSearchBottomSheet());
    }







    private void setupManualAreaControls() {
        final View rowManualArea      = binding.blockRoomDimensions.rowManualArea;
        final com.google.android.material.button.MaterialButton btnToggle =
                binding.blockRoomDimensions.btnToggleManualArea;
        final com.google.android.material.textfield.TextInputEditText etManual =
                binding.blockRoomDimensions.etManualArea;
        final com.google.android.material.button.MaterialButton btnApply =
                binding.blockRoomDimensions.btnApplyManualArea;


        btnToggle.setOnClickListener(v -> {
            boolean isManual = rowManualArea.getVisibility() == View.VISIBLE;
            if (isManual) {
                rowManualArea.setVisibility(View.GONE);
                btnToggle.setText(getString(R.string.btn_manual_area));
                viewModel.clearManualAreaOverride();
                updateAreaDisplayFromInputs();
            } else {
                BigDecimal currentArea = viewModel.getCurrentEffectiveArea();
                rowManualArea.setVisibility(View.VISIBLE);
                btnToggle.setText(getString(R.string.btn_auto_area));
                if (currentArea.compareTo(BigDecimal.ZERO) > 0) {
                    etManual.setText(currentArea.stripTrailingZeros().toPlainString());
                }
                updateAreaDisplayFromInputs();
            }
        });


        btnApply.setOnClickListener(v -> {
            String raw = etManual.getText() != null
                    ? etManual.getText().toString().trim() : "";
            if (raw.isEmpty()) {
                binding.blockRoomDimensions.tilManualArea.setError(
                        getString(R.string.error_field_required));
                return;
            }
            try {
                BigDecimal area = new BigDecimal(raw);
                binding.blockRoomDimensions.tilManualArea.setError(null);
                viewModel.setManualAreaOverride(area);
                updateAreaDisplayFromInputs();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                requireContext().getSystemService(
                                        android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etManual.getWindowToken(), 0);
                etManual.clearFocus();
            } catch (NumberFormatException e) {
                binding.blockRoomDimensions.tilManualArea.setError("Некорректное число");
            }
        });


        etManual.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (suppressDimensionTextChanges) return;
                updateAreaDisplayFromInputs();
                try {
                    String raw = s.toString().trim();
                    if (!raw.isEmpty()) {
                        BigDecimal area = new BigDecimal(raw);
                        if (area.compareTo(BigDecimal.ZERO) > 0) {
                            binding.blockRoomDimensions.tilManualArea.setError(null);
                            viewModel.setManualAreaOverride(area);
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        });


        viewModel.getRoom().observe(getViewLifecycleOwner(), room -> {
            if (room != null && room.manualAreaOverride != null) {
                rowManualArea.setVisibility(View.VISIBLE);
                btnToggle.setText(getString(R.string.btn_auto_area));
                suppressDimensionTextChanges = true;
                try {
                    setTextIfNotFocused(etManual, room.manualAreaOverride.stripTrailingZeros().toPlainString());
                } finally {
                    suppressDimensionTextChanges = false;
                }
                updateAreaDisplayFromInputs();
            }
        });
    }


    private void setupAddWorkTaskButton() {
        binding.btnAddWorkTask.setOnClickListener(v -> showAddWorkTaskDialog(null));
    }

    private void setupAddOpeningButton() {
        binding.blockOpenings.btnAddOpening.setOnClickListener(v -> showAddOpeningDialog());
    }







    private void showAddOpeningDialog() {
        View formView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_opening, null);

        final android.widget.RadioGroup rgType      = formView.findViewById(R.id.rgOpeningType);
        final android.widget.RadioGroup rgPlacement = formView.findViewById(R.id.rgPlacementType);
        final TextInputLayout tilWidth  = formView.findViewById(R.id.tilOpeningWidth);
        final TextInputLayout tilHeight = formView.findViewById(R.id.tilOpeningHeight);
        final TextInputLayout tilDepth  = formView.findViewById(R.id.tilOpeningDepth);
        final com.google.android.material.textfield.TextInputEditText etWidth =
                formView.findViewById(R.id.etOpeningWidth);
        final com.google.android.material.textfield.TextInputEditText etHeight =
                formView.findViewById(R.id.etOpeningHeight);
        final com.google.android.material.textfield.TextInputEditText etDepth =
                formView.findViewById(R.id.etOpeningDepth);


        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isColumn = (checkedId == R.id.rbColumn);
            tilDepth.setVisibility(isColumn ? View.VISIBLE : View.GONE);
            rgPlacement.setVisibility(isColumn ? View.VISIBLE : View.GONE);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_add_opening)
                .setView(formView)
                .setPositiveButton(R.string.label_add, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String widthStr  = etWidth.getText() != null
                        ? etWidth.getText().toString().trim() : "";
                String heightStr = etHeight.getText() != null
                        ? etHeight.getText().toString().trim() : "";
                String depthStr  = etDepth.getText() != null
                        ? etDepth.getText().toString().trim() : "";

                boolean hasError = false;
                tilWidth.setError(null);
                tilHeight.setError(null);
                tilDepth.setError(null);

                if (widthStr.isEmpty()) {
                    tilWidth.setError(getString(R.string.error_field_required));
                    hasError = true;
                }
                if (heightStr.isEmpty()) {
                    tilHeight.setError(getString(R.string.error_field_required));
                    hasError = true;
                }

                boolean isColumn = (rgType.getCheckedRadioButtonId() == R.id.rbColumn);
                if (isColumn && depthStr.isEmpty()) {
                    tilDepth.setError(getString(R.string.error_field_required));
                    hasError = true;
                }
                if (hasError) return;

                java.math.BigDecimal width, height, depth = null;
                try {
                    width  = new java.math.BigDecimal(widthStr);
                    height = new java.math.BigDecimal(heightStr);
                    if (isColumn && !depthStr.isEmpty()) {
                        depth = new java.math.BigDecimal(depthStr);
                    }
                } catch (NumberFormatException e) {
                    tilWidth.setError("Некорректное число");
                    return;
                }

                String type;
                String placement = null;
                int checked = rgType.getCheckedRadioButtonId();
                if (checked == R.id.rbDoor)        type = "DOOR";
                else if (checked == R.id.rbVent)   type = "VENT";
                else if (checked == R.id.rbColumn) {
                    type = "COLUMN";
                    placement = (rgPlacement.getCheckedRadioButtonId() == R.id.rbWallAdjacent)
                            ? "WALL_ADJACENT" : "FREESTANDING";
                } else                             type = "WINDOW";

                viewModel.addOpening(type, width, height, depth, placement);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    @NonNull
    private View.OnFocusChangeListener buildFocusListenerWithWarning() {
        return (v, hasFocus) -> {
            if (hasFocus && viewModel.hasEstimateItems()) {
                showRecalcWarningDialog(
                        () -> {                             },
                        () -> v.clearFocus()
                );
            } else if (!hasFocus) {
                applyDimensions();
            }
        };
    }





    private void observeData() {
        viewModel.getRoom().observe(getViewLifecycleOwner(), this::bindRoomDimensions);
        viewModel.getProject().observe(getViewLifecycleOwner(), project -> {
            if (project != null && project.regionCode != null) {
                currentRegionCode = project.regionCode;
            }
        });

        viewModel.getEstimateItems().observe(getViewLifecycleOwner(), items -> {
            estimateAdapter.submitList(items);
            updateEstimateEmptyState(items);
        });

        viewModel.getWorkTasks().observe(getViewLifecycleOwner(), tasks -> {
            workTaskAdapter.submitList(tasks);
            updateWorkTasksEmptyState(tasks);
        });


        viewModel.getOpenings().observe(getViewLifecycleOwner(), openingList -> {
            rebuildOpeningChips(openingList);

            ProjectRoomEntity currentRoom = viewModel.getRoom().getValue();
            if (binding != null && currentRoom != null) {
                updateAreaDisplayFromInputs();
            }
        });

        viewModel.getRoomTotals().observe(getViewLifecycleOwner(), totals -> {
            if (totals != null) updateBottomBar(totals);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Snackbar.make(binding.getRoot(), errorMsg, Snackbar.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });
    }





    private void updateEstimateEmptyState(@Nullable List<EstimateItemDisplay> items) {
        if (binding == null) return;
        boolean isEmpty = items == null || items.isEmpty();
        binding.tvEmptyEstimate.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvEstimateItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateWorkTasksEmptyState(@Nullable List<WorkTaskEntity> tasks) {
        if (binding == null) return;
        boolean isEmpty = tasks == null || tasks.isEmpty();
        binding.tvEmptyWorkTasks.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvWorkTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
















    private void rebuildOpeningChips(@Nullable List<OpeningEntity> openingList) {
        if (binding == null) return;

        ChipGroup chipGroup = binding.blockOpenings.chipGroupOpenings;
        chipGroup.removeAllViews();

        if (openingList == null || openingList.isEmpty()) return;

        for (OpeningEntity el : openingList) {
            String label;
            if ("COLUMN".equals(el.type) && el.depth != null) {
                boolean isWall = "WALL_ADJACENT".equals(el.placementType);
                label = isWall
                        ? getString(R.string.chip_column_wall_fmt,
                                el.width.toPlainString(),
                                el.depth.toPlainString(),
                                el.height.toPlainString())
                        : getString(R.string.chip_column_free_fmt,
                                el.width.toPlainString(),
                                el.depth.toPlainString(),
                                el.height.toPlainString());
            } else if ("VENT".equals(el.type)) {
                label = getString(R.string.chip_vent_fmt,
                        el.width.toPlainString(), el.height.toPlainString());
            } else if ("DOOR".equals(el.type)) {
                label = getString(R.string.chip_door_fmt,
                        el.width.toPlainString(), el.height.toPlainString());
            } else {
                label = getString(R.string.chip_window_fmt,
                        el.width.toPlainString(), el.height.toPlainString());
            }

            Chip chip = new Chip(requireContext());
            chip.setText(label);
            chip.setCloseIconVisible(true);
            final String elId = el.id;
            chip.setOnCloseIconClickListener(v -> viewModel.deleteOpening(elId));
            chipGroup.addView(chip);
        }
    }





    private void updateBottomBar(@NonNull RoomTotals totals) {
        binding.tvMaterialsTotal.setText(
                getString(R.string.materials_total_fmt, formatRub(totals.materialsTotal)));
        binding.tvSalariesTotal.setText(
                getString(R.string.salaries_total_fmt, formatRub(totals.salariesTotal)));
        binding.tvRoomTotal.setText(
                getString(R.string.room_total_fmt, formatRub(totals.roomTotal)));
    }

    private void bindRoomDimensions(@Nullable ProjectRoomEntity room) {
        if (binding == null || room == null) return;

        suppressDimensionTextChanges = true;
        try {
            setTextIfNotFocused(binding.blockRoomDimensions.etLength, toPlain(room.length));
            setTextIfNotFocused(binding.blockRoomDimensions.etWidth, toPlain(room.width));
            setTextIfNotFocused(binding.blockRoomDimensions.etHeight, toPlain(room.height));
        } finally {
            suppressDimensionTextChanges = false;
        }

        updateAreaDisplayFromInputs();
    }

    private void applyDimensions() {
        if (binding == null) return;
        try {
            viewModel.updateDimensions(
                    parseDimensionOrNull(binding.blockRoomDimensions.etLength),
                    parseDimensionOrNull(binding.blockRoomDimensions.etWidth),
                    parseDimensionOrNull(binding.blockRoomDimensions.etHeight)
            );
        } catch (NumberFormatException nfe) {

        }
    }


    @NonNull
    private String getText(@NonNull android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setTextIfNotFocused(@NonNull android.widget.EditText editText,
                                     @NonNull String value) {
        if (editText.hasFocus()) return;
        String current = getText(editText);
        if (!current.equals(value)) {
            editText.setText(value);
        }
    }

    @NonNull
    private String toPlain(@Nullable BigDecimal value) {
        return value != null ? value.stripTrailingZeros().toPlainString() : "";
    }

    @NonNull
    private BigDecimal calculateNetArea(@NonNull ProjectRoomEntity room) {
        return calculateNetArea(room.length, room.width, room.height);
    }

    @NonNull
    private BigDecimal calculateNetArea(@Nullable BigDecimal length,
                                        @Nullable BigDecimal width,
                                        @Nullable BigDecimal height) {
        if (length == null || width == null || height == null) {
            return BigDecimal.ZERO;
        }
        final BigDecimal TWO = new BigDecimal("2");
        BigDecimal area = TWO.multiply(length.add(width)).multiply(height);

        List<OpeningEntity> elements = viewModel.getOpenings().getValue();
        if (elements != null) {
            for (OpeningEntity el : elements) {
                if (el.width == null || el.height == null) continue;
                if ("COLUMN".equals(el.type)) {
                    if (el.depth == null) continue;
                    if ("WALL_ADJACENT".equals(el.placementType)) {

                        area = area.add(
                                el.width.add(el.depth.multiply(TWO)).multiply(el.height));
                    } else {

                        area = area.add(
                                TWO.multiply(el.width.add(el.depth)).multiply(el.height));
                    }
                } else {
                    area = area.subtract(el.width.multiply(el.height));
                }
            }
        }
        return area.max(BigDecimal.ZERO);
    }

    private void updateAreaDisplayFromInputs() {
        if (binding == null) return;


        if (binding.blockRoomDimensions.rowManualArea.getVisibility() == View.VISIBLE) {
            BigDecimal manual = parseDimensionOrNull(binding.blockRoomDimensions.etManualArea);
            if (manual != null) {
                binding.blockRoomDimensions.tvAreaDisplay.setText(
                        getString(R.string.label_area, formatArea(manual)));
                return;
            }
        }

        try {
            BigDecimal length = parseDimensionOrNull(binding.blockRoomDimensions.etLength);
            BigDecimal width = parseDimensionOrNull(binding.blockRoomDimensions.etWidth);
            BigDecimal height = parseDimensionOrNull(binding.blockRoomDimensions.etHeight);
            binding.blockRoomDimensions.tvAreaDisplay.setText(
                    getString(R.string.label_area, formatArea(calculateNetArea(length, width, height))));
        } catch (NumberFormatException ignored) {
            binding.blockRoomDimensions.tvAreaDisplay.setText(
                    getString(R.string.label_area, formatArea(BigDecimal.ZERO)));
        }
    }

    @Nullable
    private BigDecimal parseDimensionOrNull(@NonNull android.widget.EditText et) {
        String value = getText(et);
        return value.isEmpty() ? null : new BigDecimal(value);
    }

    @NonNull
    private String formatArea(@Nullable BigDecimal value) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        return safeValue.setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

















    private void showAddWorkTaskDialog(@Nullable WorkTaskEntity editTask) {
        boolean isEditMode = editTask != null;

        View formView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_work_task, null);


        final View layoutStateA = formView.findViewById(R.id.layoutWorkerStateA);
        final View layoutStateB = formView.findViewById(R.id.layoutWorkerStateB);
        final android.widget.Button btnCreateNewWorker =
                formView.findViewById(R.id.btnCreateNewWorker);
        final View btnPickWorker = formView.findViewById(R.id.btnPickWorker);
        final android.widget.TextView tvSelectedWorkerName =
                formView.findViewById(R.id.tvSelectedWorkerName);
        final View btnClearWorker = formView.findViewById(R.id.btnClearWorker);


        final com.google.android.material.textfield.TextInputLayout tilTaskName =
                formView.findViewById(R.id.tilTaskName);
        final com.google.android.material.textfield.TextInputEditText etTaskName =
                formView.findViewById(R.id.etTaskName);
        final android.widget.RadioGroup rgRateType =
                formView.findViewById(R.id.rgRateType);
        final com.google.android.material.textfield.TextInputLayout tilRateValue =
                formView.findViewById(R.id.tilRateValue);
        final com.google.android.material.textfield.TextInputEditText etRateValue =
                formView.findViewById(R.id.etRateValue);


        final WorkerViewModel workerViewModel =
                new ViewModelProvider(this).get(WorkerViewModel.class);


        final List<WorkerEntity> workerList = new ArrayList<>();


        final String[] selectedWorkerId   = {isEditMode ? editTask.workerId : null};
        final String[] selectedWorkerName = {null};



        Runnable showStateA = () -> {
            layoutStateA.setVisibility(View.VISIBLE);
            layoutStateB.setVisibility(View.GONE);
        };


        Runnable showStateB = () -> {
            layoutStateA.setVisibility(View.GONE);
            layoutStateB.setVisibility(View.VISIBLE);
        };


        if (isEditMode && editTask.workerId != null) {


            tvSelectedWorkerName.setText(editTask.workerId);
            showStateB.run();
        } else {
            showStateA.run();
        }


        if (isEditMode) {
            etTaskName.setText(editTask.taskName);
            if (editTask.rateValue != null) {
                etRateValue.setText(editTask.rateValue.toPlainString());
            }
            if ("FIXED".equals(editTask.rateType)) {
                rgRateType.check(R.id.rbFixed);
            } else {
                rgRateType.check(R.id.rbPiecework);
            }
        }


        workerViewModel.getWorkers().observe(getViewLifecycleOwner(), workers -> {
            workerList.clear();
            if (workers != null) {
                workerList.addAll(workers);
            }


            if (isEditMode && editTask.workerId != null) {
                for (WorkerEntity w : workerList) {
                    if (editTask.workerId.equals(w.id)) {
                        selectedWorkerName[0] = w.fullName;
                        tvSelectedWorkerName.setText(w.fullName != null ? w.fullName : "");
                        break;
                    }
                }
            }
        });


        btnCreateNewWorker.setOnClickListener(v ->
                showCreateWorkerDialog(workerViewModel));


        btnPickWorker.setOnClickListener(v -> {
            if (workerList.isEmpty()) {
                Toast.makeText(requireContext(), "Рабочих нет. Создайте нового.",
                        Toast.LENGTH_SHORT).show();
                return;
            }


            List<String> names = new ArrayList<>();
            for (WorkerEntity w : workerList) {
                names.add(w.fullName != null ? w.fullName : "");
            }

            android.widget.ArrayAdapter<String> popupAdapter =
                    new android.widget.ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            names);

            ListPopupWindow popupWindow = new ListPopupWindow(requireContext());
            popupWindow.setAdapter(popupAdapter);


            popupWindow.setAnchorView(layoutStateA);
            popupWindow.setWidth(ListPopupWindow.MATCH_PARENT);
            popupWindow.setHeight(ListPopupWindow.WRAP_CONTENT);
            popupWindow.setModal(true);

            popupWindow.setOnItemClickListener((parent, row, position, id) -> {
                WorkerEntity chosen = workerList.get(position);
                selectedWorkerId[0]   = chosen.id;
                selectedWorkerName[0] = chosen.fullName;
                tvSelectedWorkerName.setText(chosen.fullName != null ? chosen.fullName : "");
                showStateB.run();
                popupWindow.dismiss();
            });

            popupWindow.show();
        });


        btnClearWorker.setOnClickListener(v -> {
            selectedWorkerId[0]   = null;
            selectedWorkerName[0] = null;
            showStateA.run();
        });


        int titleRes = isEditMode ? R.string.title_edit_task : R.string.title_add_work_task;
        String positiveLabel = isEditMode
                ? getString(R.string.label_save_task)
                : getString(R.string.label_add_task);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setView(formView)
                .setPositiveButton(positiveLabel, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String taskName = etTaskName.getText() != null
                        ? etTaskName.getText().toString().trim() : "";
                String rateStr  = etRateValue.getText() != null
                        ? etRateValue.getText().toString().trim() : "";

                boolean hasError = false;

                if (taskName.isEmpty()) {
                    if (tilTaskName != null) {
                        tilTaskName.setError(getString(R.string.error_field_required));
                    }
                    hasError = true;
                } else if (tilTaskName != null) {
                    tilTaskName.setError(null);
                }

                if (rateStr.isEmpty()) {
                    if (tilRateValue != null) {
                        tilRateValue.setError(getString(R.string.error_field_required));
                    }
                    hasError = true;
                } else if (tilRateValue != null) {
                    tilRateValue.setError(null);
                }

                if (hasError) return;

                BigDecimal rateValue;
                try {
                    rateValue = new BigDecimal(rateStr);
                } catch (NumberFormatException e) {
                    if (tilRateValue != null) tilRateValue.setError("Некорректное число");
                    return;
                }

                String rateType = (rgRateType.getCheckedRadioButtonId() == R.id.rbFixed)
                        ? "FIXED" : "PIECEWORK";

                if (isEditMode) {
                    viewModel.updateWorkTask(
                            editTask.id,
                            selectedWorkerId[0],
                            taskName,
                            rateType,
                            rateValue);
                } else {
                    viewModel.addWorkTask(
                            selectedWorkerId[0],
                            taskName,
                            rateType,
                            rateValue);
                }
                dialog.dismiss();
            });
        });

        dialog.show();
    }





    private void showDeleteTaskDialog(@NonNull String taskId, @NonNull String taskName) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_task_title)
                .setMessage(getString(R.string.dialog_delete_task_message, taskName))
                .setPositiveButton(R.string.label_delete, (dialog, which) ->
                        viewModel.deleteWorkTask(taskId))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }





    private void showCreateWorkerDialog(@NonNull WorkerViewModel workerViewModel) {
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

        AlertDialog workerDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_create_worker)
                .setView(formView)
                .setPositiveButton(R.string.label_create, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        workerDialog.setOnShowListener(wd -> {
            workerDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText() != null
                        ? etName.getText().toString().trim() : "";

                if (name.isEmpty()) {
                    if (tilName != null) tilName.setError(getString(R.string.error_field_required));
                    return;
                }
                if (tilName != null) tilName.setError(null);

                String phone     = etPhone.getText()     != null
                        ? etPhone.getText().toString().trim()     : "";
                String specialty = etSpecialty.getText() != null
                        ? etSpecialty.getText().toString().trim() : "";

                workerViewModel.saveWorker(name, phone, specialty);
                workerDialog.dismiss();
            });
        });

        workerDialog.show();
    }





    private void showMaterialSearchBottomSheet() {
        if (currentRegionCode == null || currentRegionCode.trim().isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.error_region_not_loaded,
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        MaterialSearchBottomSheet bottomSheet =
                MaterialSearchBottomSheet.newInstance(currentRegionCode,
                        new MaterialSearchBottomSheet.OnMaterialPickedListener() {
                    @Override
                    public void onMaterialPicked(@NonNull MaterialDto material) {
                        MaterialQuantityBottomSheet quantitySheet = MaterialQuantityBottomSheet.newInstance(material);
                        quantitySheet.show(getChildFragmentManager(), MaterialQuantityBottomSheet.TAG);
                    }
                });
        bottomSheet.show(getChildFragmentManager(), MaterialSearchBottomSheet.TAG);
    }

    private void showMaterialQuantityDialog(@NonNull MaterialDto material) {
        BigDecimal basePrice = material.basePrice != null
                ? parseSafe(material.basePrice) : BigDecimal.ZERO;
        String unit = material.unitMeasure != null && !material.unitMeasure.isBlank()
                ? material.unitMeasure : "шт";
        String name = material.name != null ? material.name : "Материал";

        BigDecimal area = viewModel.getCurrentEffectiveArea();
        BigDecimal consumption = material.consumptionRate != null ? parseSafe(material.consumptionRate) : BigDecimal.ZERO;
        BigDecimal initialQuantity = consumption.signum() > 0 && area.signum() > 0
                ? area.multiply(consumption) : BigDecimal.ONE;

        showQuantityInputDialog(
                "Количество материала",
                name,
                unit,
                initialQuantity,
                quantity -> viewModel.addEstimateItemWithQuantity(
                        material.fgisCode, name, unit, basePrice, quantity)
        );
    }

    private void showEstimateItemActions(@NonNull EstimateItemDisplay item) {
        String[] actions = {"Изменить количество", getString(R.string.material_batch_action), "Удалить"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        BigDecimal current = item.quantity != null && item.quantity.signum() > 0
                                ? item.quantity : BigDecimal.ONE;
                        showQuantityInputDialog(
                                "Изменить количество",
                                item.name,
                                item.unitMeasure != null ? item.unitMeasure : "шт",
                                current,
                                quantity -> viewModel.updateManualQuantity(item.id, quantity)
                        );
                    } else if (which == 1) {
                        showBatchStatusDialog(item);
                    } else {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.dialog_delete_estimate_title)
                                .setMessage(R.string.dialog_delete_estimate_message)
                                .setPositiveButton(R.string.label_delete, (confirmDialog, ignored) ->
                                        viewModel.deleteEstimateItem(item.id))
                                .setNegativeButton(R.string.btn_cancel, null)
                                .show();
                    }
                })
                .show();
    }

    private void showBatchStatusDialog(@NonNull EstimateItemDisplay initiallySelected) {
        List<EstimateItemDisplay> items = estimateAdapter.getItemsSnapshot();
        if (items.isEmpty()) return;
        String[] labels = new String[items.size()];
        boolean[] checked = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            EstimateItemDisplay item = items.get(i);
            labels[i] = item.name;
            checked[i] = item.id.equals(initiallySelected.id);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.material_batch_title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.label_continue, (dialog, ignored) -> {
                    List<EstimateItemDisplay> selected = new ArrayList<>();
                    for (int i = 0; i < items.size(); i++) if (checked[i]) selected.add(items.get(i));
                    if (!selected.isEmpty()) showBatchStatusChoice(selected);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showBatchStatusChoice(@NonNull List<EstimateItemDisplay> selected) {
        String[] labels = {"Нужно купить", "Заказано", "На объекте"};
        String[] values = {"NEED_TO_BUY", "ORDERED", "ON_SITE"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.material_batch_status_title)
                .setItems(labels, (dialog, which) -> {
                    for (EstimateItemDisplay item : selected) {
                        viewModel.updateEstimateItemStatus(item.id, values[which]);
                    }
                })
                .show();
    }

    private void showQuantityInputDialog(
            @NonNull String title,
            @NonNull String materialName,
            @NonNull String unit,
            @NonNull BigDecimal initialQuantity,
            @NonNull Consumer<BigDecimal> onConfirm
    ) {
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, 0, padding, 0);

        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setHint("Количество (" + unit + ")");
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);
        input.setText(initialQuantity.stripTrailingZeros().toPlainString());
        input.selectAll();
        inputLayout.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(inputLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(materialName)
                .setView(container)
                .setPositiveButton("Сохранить", null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String raw = input.getText() != null
                            ? input.getText().toString().trim().replace(',', '.') : "";
                    try {
                        BigDecimal quantity = new BigDecimal(raw);
                        if (quantity.signum() <= 0) {
                            throw new NumberFormatException();
                        }
                        inputLayout.setError(null);
                        onConfirm.accept(quantity);
                        dialog.dismiss();
                    } catch (NumberFormatException exception) {
                        inputLayout.setError("Введите количество больше нуля");
                    }
                }));
        dialog.show();
    }





    private void showRecalcWarningDialog(@NonNull Runnable onConfirm, @NonNull Runnable onCancel) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.warning_recalc_title)
                .setMessage(R.string.warning_recalc_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> onConfirm.run())
                .setNegativeButton(R.string.btn_cancel, (dialog, which) -> onCancel.run())
                .show();
    }





    @NonNull
    private String formatRub(@Nullable BigDecimal value) {
        return currencyFormat.format(value != null ? value : BigDecimal.ZERO);
    }

    @NonNull
    private BigDecimal parseSafe(@Nullable String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
