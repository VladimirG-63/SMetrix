package com.smetrix.app.ui.room;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.smetrix.app.R;
import com.smetrix.app.network.dto.MaterialDto;
import com.smetrix.app.utils.quantity.QuantityCalculationEngine;
import com.smetrix.app.utils.quantity.QuantityCalculationMethod;
import com.smetrix.app.utils.quantity.QuantityMethodProvider;
import com.smetrix.app.utils.quantity.RoomGeometryCalculator;
import com.smetrix.app.utils.quantity.UnitGroup;
import com.smetrix.app.utils.quantity.UnitNormalizer;
import com.smetrix.app.viewmodel.RoomDetailViewModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class MaterialQuantityBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "MaterialQuantityBottomSheet";

    private MaterialDto material;
    private RoomDetailViewModel viewModel;
    
    private TextView tvMaterialName;
    private TextView tvMaterialCode;
    private TextView tvUnit;
    private TextView tvResult;
    private TextView tvWarning;
    private AutoCompleteTextView spinnerMethod;
    private TextInputLayout layoutMethod;
    private TextInputLayout layoutAreaType;
    private AutoCompleteTextView spinnerAreaType;
    private LinearLayout dynamicFieldsContainer;
    private Button btnSave;

    private List<QuantityCalculationMethod> availableMethods;
    private QuantityCalculationMethod selectedMethod;
    private UnitNormalizer.NormalizationResult normalizationResult;
    
    // Dynamic fields
    private TextInputEditText etManualQuantity;
    private TextInputEditText etWastePercent;
    private TextInputEditText etLayers;
    private TextInputEditText etThickness;
    private TextInputEditText etConsumption;
    private TextInputEditText etCoverage;
    private TextInputEditText etManualArea;

    private String selectedAreaType = "FLOOR";

    public static MaterialQuantityBottomSheet newInstance(MaterialDto material) {
        MaterialQuantityBottomSheet fragment = new MaterialQuantityBottomSheet();
        fragment.material = material;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        android.content.Context context = requireContext();
        int padding = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvMaterialName = new TextView(context);
        tvMaterialName.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Title);
        tvMaterialName.setText(material.name != null ? material.name : "Материал");
        root.addView(tvMaterialName);

        tvMaterialCode = new TextView(context);
        tvMaterialCode.setText(material.fgisCode);
        root.addView(tvMaterialCode);

        tvUnit = new TextView(context);
        tvUnit.setText("Единица измерения ФГИС: " + material.unitMeasure);
        root.addView(tvUnit);

        tvWarning = new TextView(context);
        tvWarning.setTextColor(0xFFD32F2F); // Red
        tvWarning.setVisibility(View.GONE);
        root.addView(tvWarning);
        
        TextView tvHint = new TextView(context);
        tvHint.setText("ФГИС ЦС содержит цену материала, но не содержит норму расхода. Выберите способ расчёта количества.");
        tvHint.setPadding(0, 16, 0, 16);
        root.addView(tvHint);

        layoutMethod = new TextInputLayout(context);
        layoutMethod.setHint("Способ расчёта");
        spinnerMethod = new AutoCompleteTextView(context);
        spinnerMethod.setInputType(InputType.TYPE_NULL);
        layoutMethod.addView(spinnerMethod);
        root.addView(layoutMethod);

        layoutAreaType = new TextInputLayout(context);
        layoutAreaType.setHint("Какую площадь использовать");
        layoutAreaType.setVisibility(View.GONE);
        spinnerAreaType = new AutoCompleteTextView(context);
        spinnerAreaType.setInputType(InputType.TYPE_NULL);
        layoutAreaType.addView(spinnerAreaType);
        
        LinearLayout.LayoutParams paramsAreaType = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsAreaType.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        root.addView(layoutAreaType, paramsAreaType);

        dynamicFieldsContainer = new LinearLayout(context);
        dynamicFieldsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams paramsDyn = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsDyn.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        root.addView(dynamicFieldsContainer, paramsDyn);

        tvResult = new TextView(context);
        tvResult.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Medium);
        tvResult.setPadding(0, 16, 0, 16);
        root.addView(tvResult);

        btnSave = new Button(context);
        btnSave.setText("Сохранить");
        btnSave.setOnClickListener(v -> saveItem());
        root.addView(btnSave);

        return new androidx.core.widget.NestedScrollView(context) {{ addView(root); }};
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(RoomDetailViewModel.class);

        normalizationResult = UnitNormalizer.normalize(material.unitMeasure);
        availableMethods = QuantityMethodProvider.getAvailableMethods(normalizationResult.group);

        if (normalizationResult.group == UnitGroup.MASS || normalizationResult.group == UnitGroup.LIQUID) {
            tvWarning.setText("ФГИС ЦС содержит цену материала, но не содержит норму расхода. Расход вводится пользователем.");
            tvWarning.setVisibility(View.VISIBLE);
        } else if (normalizationResult.group == UnitGroup.UNKNOWN) {
            tvWarning.setText("Единица измерения не распознана. Введите количество вручную.");
            tvWarning.setVisibility(View.VISIBLE);
        }

        String[] methodNames = new String[availableMethods.size()];
        for (int i = 0; i < availableMethods.size(); i++) {
            methodNames[i] = getMethodLabel(availableMethods.get(i));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, methodNames);
        spinnerMethod.setAdapter(adapter);
        
        spinnerMethod.setOnItemClickListener((parent, view1, position, id) -> {
            selectedMethod = availableMethods.get(position);
            updateDynamicFields();
            calculate();
        });

        setupAreaTypeSpinner();

        if (!availableMethods.isEmpty()) {
            spinnerMethod.setText(methodNames[0], false);
            selectedMethod = availableMethods.get(0);
            updateDynamicFields();
            calculate();
        }
    }

    private void setupAreaTypeSpinner() {
        String[] areaLabels = new String[]{"Пол", "Стены", "Потолок", "Все поверхности", "Ввести площадь вручную"};
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, areaLabels);
        spinnerAreaType.setAdapter(areaAdapter);
        spinnerAreaType.setText(areaLabels[0], false);
        spinnerAreaType.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0: selectedAreaType = "FLOOR"; break;
                case 1: selectedAreaType = "WALLS"; break;
                case 2: selectedAreaType = "CEILING"; break;
                case 3: selectedAreaType = "ALL"; break;
                case 4: selectedAreaType = "MANUAL"; break;
            }
            updateDynamicFields();
            calculate();
        });
    }

    private String getMethodLabel(QuantityCalculationMethod method) {
        switch (method) {
            case AREA_FLOOR: return "По площади пола";
            case AREA_WALLS: return "По площади стен";
            case AREA_CEILING: return "По площади потолка";
            case AREA_ALL_SURFACES: return "По всем поверхностям";
            case VOLUME_AREA_THICKNESS: return "По площади и толщине слоя";
            case VOLUME_ROOM: return "По объёму помещения";
            case LENGTH_PERIMETER: return "По периметру комнаты";
            case LENGTH_PERIMETER_MINUS_OPENINGS: return "По периметру без дверных проёмов";
            case CONSUMPTION_PER_M2: return "По расходу на 1 м²";
            case PIECES_BY_DOORS: return "По количеству дверей";
            case PIECES_BY_WINDOWS: return "По количеству окон";
            case PIECES_BY_AREA_COVERAGE: return "По площади, которую закрывает 1 штука";
            case PACKAGES_BY_COVERAGE: return "По площади, которую закрывает 1 упаковка";
            case MANUAL_QUANTITY: 
                if (normalizationResult.group == UnitGroup.LENGTH) return "Ввести длину вручную";
                if (normalizationResult.group == UnitGroup.PACKAGE) return "Ввести количество упаковок вручную";
                return "Ввести количество вручную";
            default: return "Ввести количество вручную";
        }
    }

    private String getDisplayUnitLabel(UnitNormalizer.NormalizationResult normalizationResult) {
        switch (normalizationResult.canonicalUnit) {
            case "M2": return "м²";
            case "M3": return "м³";
            case "M": return "м";
            case "KG": return "кг";
            case "L": return "л";
            case "PCS": return "шт";
            case "PKG": return "упак.";
            default: return material.unitMeasure;
        }
    }

    private String getQuantityInputLabel(UnitNormalizer.NormalizationResult normalizationResult) {
        switch (normalizationResult.canonicalUnit) {
            case "M2": return "Количество, м²";
            case "M3": return "Количество, м³";
            case "M": return "Количество, м";
            case "KG": return "Количество, кг";
            case "L": return "Количество, л";
            case "PCS": return "Количество, шт";
            case "PKG": return "Количество, упак.";
            default: return "Количество";
        }
    }

    private String getConsumptionInputLabel(UnitNormalizer.NormalizationResult normalizationResult) {
        switch (normalizationResult.canonicalUnit) {
            case "KG": return "Расход на 1 м², кг";
            case "L": return "Расход на 1 м², л";
            case "PCS": return "Расход на 1 м², шт";
            case "M2": return "Расход на 1 м², м²";
            default: return "Расход на 1 м²";
        }
    }

    private void updateDynamicFields() {
        dynamicFieldsContainer.removeAllViews();
        if (selectedMethod == null) return;

        android.content.Context context = requireContext();
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { calculate(); }
        };

        etManualQuantity = null; etWastePercent = null; etLayers = null; etThickness = null;
        etConsumption = null; etCoverage = null; etManualArea = null;

        // Area mapping overrides
        if (selectedMethod == QuantityCalculationMethod.AREA_FLOOR) selectedAreaType = "FLOOR";
        else if (selectedMethod == QuantityCalculationMethod.AREA_WALLS) selectedAreaType = "WALLS";
        else if (selectedMethod == QuantityCalculationMethod.AREA_CEILING) selectedAreaType = "CEILING";
        else if (selectedMethod == QuantityCalculationMethod.AREA_ALL_SURFACES) selectedAreaType = "ALL";

        // Show/Hide spinnerAreaType
        if (selectedMethod == QuantityCalculationMethod.VOLUME_AREA_THICKNESS ||
            selectedMethod == QuantityCalculationMethod.CONSUMPTION_PER_M2 ||
            selectedMethod == QuantityCalculationMethod.PIECES_BY_AREA_COVERAGE ||
            selectedMethod == QuantityCalculationMethod.PACKAGES_BY_COVERAGE) {
            layoutAreaType.setVisibility(View.VISIBLE);
            if ("MANUAL".equals(selectedAreaType)) {
                etManualArea = addField("Площадь вручную, м²", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, watcher);
            }
        } else {
            layoutAreaType.setVisibility(View.GONE);
        }

        if (selectedMethod == QuantityCalculationMethod.MANUAL_QUANTITY || selectedMethod.name().endsWith("_MANUAL")) {
            etManualQuantity = addField(getQuantityInputLabel(normalizationResult), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, watcher);
        }

        if (selectedMethod == QuantityCalculationMethod.VOLUME_AREA_THICKNESS) {
            etThickness = addField("Толщина слоя, мм", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, watcher);
        }

        if (selectedMethod == QuantityCalculationMethod.CONSUMPTION_PER_M2) {
            etConsumption = addField(getConsumptionInputLabel(normalizationResult), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, watcher);
            if (material.consumptionRate != null && !material.consumptionRate.isBlank()) {
                etConsumption.setText(material.consumptionRate);
            }
            etLayers = addField("Количество слоёв", InputType.TYPE_CLASS_NUMBER, watcher);
        }

        if (selectedMethod == QuantityCalculationMethod.PIECES_BY_AREA_COVERAGE || selectedMethod == QuantityCalculationMethod.PACKAGES_BY_COVERAGE) {
            etCoverage = addField(selectedMethod == QuantityCalculationMethod.PACKAGES_BY_COVERAGE ? "Площадь, которую закрывает 1 упаковка, м²" : "Площадь, которую закрывает 1 штука, м²", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, watcher);
        }

        if (selectedMethod != QuantityCalculationMethod.MANUAL_QUANTITY && selectedMethod != QuantityCalculationMethod.PIECES_BY_DOORS && selectedMethod != QuantityCalculationMethod.PIECES_BY_WINDOWS) {
            etWastePercent = addField("Запас, %", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, watcher);
        }
    }

    private TextInputEditText addField(String hint, int inputType, TextWatcher watcher) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText editText = new TextInputEditText(requireContext());
        editText.setInputType(inputType);
        if (watcher != null) editText.addTextChangedListener(watcher);
        layout.addView(editText);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        dynamicFieldsContainer.addView(layout, params);
        return editText;
    }

    private QuantityCalculationEngine.EngineParams buildParams() {
        QuantityCalculationEngine.EngineParams p = new QuantityCalculationEngine.EngineParams();
        p.unitMeasure = material.unitMeasure;
        p.normalizedUnit = normalizationResult.canonicalUnit;
        p.method = selectedMethod;
        p.geometry = viewModel.getGeometry();
        
        p.selectedAreaType = selectedAreaType;

        if (etManualQuantity != null && etManualQuantity.getText() != null) {
            try { p.manualQuantity = new BigDecimal(etManualQuantity.getText().toString()); } catch (Exception ignored) {}
        }
        if (etWastePercent != null && etWastePercent.getText() != null) {
            try { p.wastePercent = new BigDecimal(etWastePercent.getText().toString()); } catch (Exception ignored) {}
        }
        if (etConsumption != null && etConsumption.getText() != null) {
            try { p.consumptionRate = new BigDecimal(etConsumption.getText().toString()); } catch (Exception ignored) {}
        }
        if (etThickness != null && etThickness.getText() != null) {
            try { p.thicknessMeters = new BigDecimal(etThickness.getText().toString()).divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP); } catch (Exception ignored) {}
        }
        if (etLayers != null && etLayers.getText() != null) {
            try { p.layers = Integer.parseInt(etLayers.getText().toString()); } catch (Exception ignored) {}
        }
        if (etCoverage != null && etCoverage.getText() != null) {
            try { 
                if (selectedMethod == QuantityCalculationMethod.PACKAGES_BY_COVERAGE) {
                    p.coveragePerPackage = new BigDecimal(etCoverage.getText().toString());
                } else {
                    p.coveragePerPiece = new BigDecimal(etCoverage.getText().toString());
                }
            } catch (Exception ignored) {}
        }
        if (etManualArea != null && etManualArea.getText() != null) {
            try { p.manualArea = new BigDecimal(etManualArea.getText().toString()); } catch (Exception ignored) {}
        }
        
        return p;
    }

    private void calculate() {
        QuantityCalculationEngine.QuantityCalculationResult result = QuantityCalculationEngine.calculate(buildParams());
        if (result.validationError != null) {
            tvResult.setText(result.validationError);
            btnSave.setEnabled(false);
        } else {
            BigDecimal displayQuantity = result.quantity;
            BigDecimal fgisQuantity = result.quantity.divide(normalizationResult.factorToCanonical, 6, RoundingMode.HALF_UP);
            
            String displayUnit = getDisplayUnitLabel(normalizationResult);
            String text = "Количество: " + displayQuantity.toPlainString() + " " + displayUnit + "\nФормула: " + result.formulaDescription;
            
            if (normalizationResult.factorToCanonical.compareTo(BigDecimal.ONE) != 0) {
                text += "\nДля расчёта стоимости: " + fgisQuantity.toPlainString() + " × цена за " + material.unitMeasure;
                text += "\n" + displayQuantity.toPlainString() + " " + displayUnit + " = " + fgisQuantity.toPlainString() + " × " + material.unitMeasure;
            }
            
            if (result.warningMessage != null) text += "\nВнимание: " + result.warningMessage;
            if (result.isRounded) text += "\nКоличество округлено вверх до целого.";
            tvResult.setText(text);
            btnSave.setEnabled(true);
        }
    }

    private void saveItem() {
        QuantityCalculationEngine.QuantityCalculationResult result = QuantityCalculationEngine.calculate(buildParams());
        if (result.validationError != null) return;
        QuantityCalculationEngine.EngineParams p = buildParams();
        
        BigDecimal fgisQuantity = result.quantity.divide(normalizationResult.factorToCanonical, 6, RoundingMode.HALF_UP);
        
        String displayUnit = getDisplayUnitLabel(normalizationResult);
        String finalFormula = result.formulaDescription;
        if (normalizationResult.factorToCanonical.compareTo(BigDecimal.ONE) != 0) {
             finalFormula += " | " + result.quantity.toPlainString() + " " + displayUnit + " = " + fgisQuantity.toPlainString() + " × " + material.unitMeasure + " для расчёта стоимости";
        }

        viewModel.addAdvancedEstimateItem(
                material.fgisCode,
                material.name,
                material.unitMeasure,
                material.basePrice != null ? new BigDecimal(material.basePrice) : BigDecimal.ZERO,
                fgisQuantity, // Save fgisPriceQuantity to DB since totalPrice = quantity * basePrice
                p.method.name(),
                p.wastePercent,
                p.layers,
                p.thicknessMeters,
                p.manualQuantity,
                p.coveragePerPiece,
                p.coveragePerPackage,
                p.kgPerPackage != null ? p.kgPerPackage : p.volumePerPackage,
                finalFormula,
                p.consumptionRate
        );
        dismiss();
    }
}
