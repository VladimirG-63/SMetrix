// app/src/main/java/com/smetrix/app/ui/room/MaterialSearchBottomSheet.java
package com.smetrix.app.ui.room;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.InputType;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.smetrix.app.R;
import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.entity.MaterialsCacheEntity;
import com.smetrix.app.databinding.BottomSheetMaterialSearchBinding;
import com.smetrix.app.network.ApiClient;
import com.smetrix.app.network.dto.MaterialSearchResponse;
import com.smetrix.app.network.dto.MaterialDto;
import com.smetrix.app.repository.MaterialRepository;
import com.smetrix.app.repository.AuthRepository;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * BottomSheetDialogFragment для живого поиска материалов из ФГИС ЦС (Шаг 6.8).
 *
 * <p><b>Функционал:</b>
 * <ul>
 *   <li>{@code etSearch} — EditText с {@link TextWatcher} для живого поиска.</li>
 *   <li>{@code rvMaterialResults} — RecyclerView с {@link MaterialSearchAdapter}.</li>
 *   <li>{@code tvEmptySearch} — заглушка пустого/начального состояния.</li>
 * </ul>
 *
 * <p><b>Взаимодействие с родительским фрагментом:</b><br>
 * При выборе материала пользователем вызывается {@link OnMaterialPickedListener#onMaterialPicked}.
 * Родительский фрагмент реализует этот интерфейс.
 */
public class MaterialSearchBottomSheet extends BottomSheetDialogFragment {

    /** Тег для поиска через FragmentManager. */
    public static final String TAG = "MaterialSearchBottomSheet";

    // ─────────────────────────────────────────────────────────────────────────
    // Интерфейс событий
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback-интерфейс: вызывается при выборе материала пользователем.
     */
    public interface OnMaterialPickedListener {
        /**
         * Вызывается, когда пользователь выбрал материал из результатов поиска.
         *
         * @param material выбранный материал из ФГИС ЦС.
         */
        void onMaterialPicked(@NonNull MaterialDto material);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Поля
    // ─────────────────────────────────────────────────────────────────────────

    private BottomSheetMaterialSearchBinding binding;
    private MaterialSearchAdapter adapter;

    /** Слушатель выбора материала (устанавливается родительским фрагментом). */
    private OnMaterialPickedListener pickedListener;

    private MaterialRepository materialRepository;
    private LiveData<List<MaterialsCacheEntity>> localSearchLiveData;
    private static final String ARG_REGION_CODE = "region_code";
    private String regionCode;
    private boolean remoteSearchEnabled;

    /** Задержка живого поиска (мс). Предотвращает запрос на каждое нажатие клавиши. */
    private static final long DEBOUNCE_MS = 400L;

    /** Handler на Main Thread — безопасен для UI, не привязан к View. */
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    /** Runnable для откладывания поиска (debounce). */
    private final Runnable searchRunnable = this::performSearch;

    // ─────────────────────────────────────────────────────────────────────────
    // Фабричный метод
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Создаёт новый экземпляр с слушателем выбора материала.
     *
     * @param listener callback для передачи выбранного материала.
     * @return новый экземпляр {@link MaterialSearchBottomSheet}.
     */
    @NonNull
    public static MaterialSearchBottomSheet newInstance(@NonNull String regionCode,
                                                         @NonNull OnMaterialPickedListener listener) {
        MaterialSearchBottomSheet sheet = new MaterialSearchBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_REGION_CODE, regionCode);
        sheet.setArguments(args);
        sheet.pickedListener = listener;
        return sheet;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetMaterialSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppDatabase database = AppDatabase.getInstance(requireContext().getApplicationContext());
        materialRepository = new MaterialRepository(
                database.materialsCacheDao(),
                ApiClient.getService(requireContext().getApplicationContext()));
        Bundle args = getArguments();
        regionCode = args != null ? args.getString(ARG_REGION_CODE, "") : "";
        remoteSearchEnabled = new AuthRepository(
                requireContext(), ApiClient.getService(requireContext())).isLoggedIn();

        setupAdapter();
        setupSearchField();
        binding.btnManualMaterial.setOnClickListener(v -> showManualMaterialDialog());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Отменяем отложенный поиск: Handler не привязан к View,
        // поэтому removeCallbacks достаточно для защиты от NPE после уничтожения View.
        searchHandler.removeCallbacks(searchRunnable);
        binding = null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Инициализация UI
    // ─────────────────────────────────────────────────────────────────────────

    private void setupAdapter() {
        adapter = new MaterialSearchAdapter(material -> {
            if (material.fgisCode != null) {
                materialRepository.markMaterialUsed(material.fgisCode, regionCode);
            }
            // Передаём выбранный материал родительскому фрагменту
            if (pickedListener != null) {
                pickedListener.onMaterialPicked(material);
            }
            dismiss();
        });

        binding.rvMaterialResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMaterialResults.setAdapter(adapter);
    }

    /**
     * Настраивает живой поиск через TextWatcher с debounce 300 мс.
     *
     * <p>При каждом изменении текста отменяем предыдущий отложенный запрос
     * и планируем новый. Это предотвращает избыточные запросы при быстром вводе.
     */
    private void setupSearchField() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Не используется
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Отменяем предыдущий отложенный поиск и планируем новый:
                // Handler.removeCallbacks удаляет ещё не сработавший Runnable из очереди,
                // Handler.postDelayed добавляет новый запрос с задержкой DEBOUNCE_MS.
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Не используется
            }
        });
    }

    private void showManualMaterialDialog() {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.material_manual_title)
                .setPositiveButton(R.string.label_add, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        android.content.Context themedContext = dialog.getContext();
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(themedContext);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, 0, padding, 0);
        TextInputEditText name = addField(form, R.string.material_name_hint, InputType.TYPE_CLASS_TEXT, themedContext);
        TextInputEditText unit = addField(form, R.string.material_unit_hint, InputType.TYPE_CLASS_TEXT, themedContext);
        TextInputEditText price = addField(form, R.string.material_price_hint,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, themedContext);
        
        TextInputLayout consumptionLayout = new TextInputLayout(themedContext);
        consumptionLayout.setHint("Норма расхода (необязательно)");
        consumptionLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText consumption = new TextInputEditText(themedContext);
        consumption.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        consumptionLayout.addView(consumption, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        form.addView(consumptionLayout, params);

        dialog.setView(form);

        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String materialName = text(name);
                    String materialUnit = text(unit);
                    BigDecimal materialPrice;
                    try { materialPrice = new BigDecimal(text(price)); }
                    catch (NumberFormatException e) { price.setError(getString(R.string.material_invalid_price)); return; }
                    
                    String consumptionText = text(consumption);
                    BigDecimal materialConsumption = null;
                    if (!consumptionText.isEmpty()) {
                        try { materialConsumption = new BigDecimal(consumptionText); }
                        catch (NumberFormatException e) { consumption.setError("Неверный формат"); return; }
                    }

                    if (materialName.isBlank()) { name.setError(getString(R.string.error_field_required)); return; }
                    if (materialUnit.isBlank()) { unit.setError(getString(R.string.error_field_required)); return; }
                    if (materialPrice.signum() < 0) { price.setError(getString(R.string.material_invalid_price)); return; }
                    
                    MaterialDto dto = new MaterialDto();
                    dto.fgisCode = "MANUAL-" + UUID.randomUUID();
                    dto.name = materialName;
                    dto.unitMeasure = materialUnit;
                    dto.regionCode = regionCode;
                    dto.basePrice = materialPrice.toPlainString();
                    if (materialConsumption != null && materialConsumption.signum() >= 0) {
                        dto.consumptionRate = materialConsumption.toPlainString();
                    }
                    materialRepository.cacheManual(dto, regionCode);
                    if (pickedListener != null) pickedListener.onMaterialPicked(dto);
                    dialog.dismiss();
                    dismiss();
                }));
        dialog.show();
    }

    private TextInputEditText addField(LinearLayout form, int hint, int inputType, android.content.Context context) {
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(getString(hint));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText editText = new TextInputEditText(context);
        editText.setInputType(inputType);
        layout.addView(editText, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        form.addView(layout, params);
        return editText;
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Поиск
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Выполняет offline-first поиск: сначала локальный Room-кэш, затем сервер.
     */
    private void performSearch() {
        if (binding == null) {
            return;
        }

        String query = binding.etSearch.getText() != null
                ? binding.etSearch.getText().toString().trim() : "";

        if (query.isEmpty()) {
            showEmptyState(getString(R.string.empty_search_hint));
            return;
        }

        observeLocalCache(query);
        if (remoteSearchEnabled) {
            searchRemote(query);
        }
    }

    private void observeLocalCache(@NonNull String query) {
        if (localSearchLiveData != null) {
            localSearchLiveData.removeObservers(getViewLifecycleOwner());
        }
        localSearchLiveData = materialRepository.searchLocal(query, regionCode);
        localSearchLiveData.observe(getViewLifecycleOwner(), cachedItems -> {
            if (binding == null || cachedItems == null) {
                return;
            }
            showResults(mapCachedMaterials(cachedItems));
        });
    }

    private void searchRemote(@NonNull String query) {
        materialRepository.searchRemote(query, regionCode, new Callback<MaterialSearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<MaterialSearchResponse> call,
                                   @NonNull Response<MaterialSearchResponse> response) {
                if (binding == null || !response.isSuccessful() || response.body() == null
                        || response.body().items == null) {
                    return;
                }
                showResults(response.body().items);
            }

            @Override
            public void onFailure(@NonNull Call<MaterialSearchResponse> call,
                                  @NonNull Throwable throwable) {
                // Локальный кэш уже показан через observeLocalCache; сетевую ошибку не перекрываем.
            }
        });
    }

    private List<MaterialDto> mapCachedMaterials(@NonNull List<MaterialsCacheEntity> cachedItems) {
        List<MaterialDto> result = new ArrayList<>(cachedItems.size());
        for (MaterialsCacheEntity cached : cachedItems) {
            if (cached == null) {
                continue;
            }
            MaterialDto dto = new MaterialDto();
            dto.fgisCode = cached.fgisCode;
            dto.name = cached.name;
            dto.unitMeasure = cached.unitMeasure;
            dto.regionCode = cached.regionCode;
            dto.basePrice = cached.basePrice != null ? cached.basePrice.toPlainString() : null;
            dto.consumptionRate = cached.consumptionRate != null ? cached.consumptionRate.toPlainString() : null;
            dto.priorityScore = cached.priorityScore;
            result.add(dto);
        }
        return result;
    }

    /**
     * Показывает результаты поиска в RecyclerView.
     *
     * @param results список {@link MaterialDto}.
     */
    private void showResults(@NonNull List<MaterialDto> results) {
        if (results.isEmpty()) {
            showEmptyState(getString(R.string.empty_search_results));
        } else {
            binding.tvEmptySearch.setVisibility(View.GONE);
            binding.rvMaterialResults.setVisibility(View.VISIBLE);
            adapter.submitList(results);
        }
    }

    /**
     * Показывает заглушку пустого состояния с заданным текстом.
     *
     * @param message текст заглушки.
     */
    private void showEmptyState(@NonNull String message) {
        adapter.submitList(new ArrayList<>());
        binding.rvMaterialResults.setVisibility(View.GONE);
        binding.tvEmptySearch.setVisibility(View.VISIBLE);
        binding.tvEmptySearch.setText(message);
    }
}
