
package com.smetrix.app.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.entity.EstimateItemEntity;
import com.smetrix.app.db.entity.OpeningEntity;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.db.entity.ProjectEntity;
import com.smetrix.app.db.entity.UnitConversionEntity;
import com.smetrix.app.db.entity.WorkTaskEntity;
import com.smetrix.app.model.EstimateItemDisplay;
import com.smetrix.app.model.EstimateItemMapper;
import com.smetrix.app.model.RoomTotals;
import com.smetrix.app.network.sync.SyncManager;
import com.smetrix.app.repository.AppExecutors;
import com.smetrix.app.repository.EstimateRepository;
import com.smetrix.app.repository.RoomRepository;
import com.smetrix.app.utils.UnitConversionEngine;

import androidx.work.WorkManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;







































public class RoomDetailViewModel extends AndroidViewModel {






    private static final String TAG = "RoomDetailViewModel";






    private static final int SCALE_TOTALS = 0;




    private static final RoundingMode ROUNDING_TOTALS = RoundingMode.HALF_UP;






    private final RoomRepository roomRepository;


    private final EstimateRepository estimateRepository;


    private final String roomId;


    private final LiveData<ProjectRoomEntity> room;
    private final LiveData<ProjectEntity> project;
    private final LiveData<List<UnitConversionEntity>> conversionRules;















    private final LiveData<List<EstimateItemDisplay>> estimateItems;










    private final LiveData<BigDecimal> materialsTotalLd;










    private final LiveData<BigDecimal> salariesTotalLd;















    private final MediatorLiveData<RoomTotals> roomTotals;







    private final LiveData<List<WorkTaskEntity>> workTasks;







    private final LiveData<List<OpeningEntity>> openings;











    private final MutableLiveData<String> errorMessage;





















    public RoomDetailViewModel(@NonNull Application application, @NonNull String roomId) {
        super(application);
        this.roomId = roomId;

        Log.d(TAG, "Инициализация RoomDetailViewModel для roomId=" + roomId);


        AppDatabase database = AppDatabase.getInstance(application);
        WorkManager workManager = WorkManager.getInstance(application);
        SyncManager syncManager = new SyncManager(workManager);


        this.roomRepository = new RoomRepository(
                database.projectRoomDao(),
                database.estimateItemDao(),
                database.workTaskDao(),
                database.openingDao(),
                syncManager
        );

        this.estimateRepository = new EstimateRepository(
                database.estimateItemDao(),
                syncManager
        );


        this.errorMessage = new MutableLiveData<String>();


        this.room = database.projectRoomDao().getByIdLiveData(roomId);
        this.project = Transformations.switchMap(
                this.room,
                currentRoom -> currentRoom == null
                        ? new MutableLiveData<>(null)
                        : database.projectDao().getByIdLiveData(currentRoom.projectId)
        );
        this.conversionRules = database.unitConversionDao().observeAll();



        LiveData<List<EstimateItemEntity>> rawItems = estimateRepository.getItemsByRoom(roomId);





        this.estimateItems = Transformations.map(
                rawItems,
                input -> EstimateItemMapper.fromList(input)
        );


        this.materialsTotalLd = Transformations.map(
                database.estimateItemDao().getMaterialPrices(roomId),
                prices -> {
                    BigDecimal total = BigDecimal.ZERO;
                    if (prices != null) {
                        for (String priceStr : prices) {
                            if (priceStr != null && !priceStr.isEmpty()) {
                                total = total.add(new BigDecimal(priceStr));
                            }
                        }
                    }
                    return total;
                }
        );
        this.salariesTotalLd  = database.workTaskDao().getSalariesTotal(roomId);


        this.workTasks = database.workTaskDao().getByRoom(roomId);


        this.openings = database.projectRoomDao().getOpeningsLiveData(roomId);


        this.roomTotals = new MediatorLiveData<RoomTotals>();



        this.roomTotals.addSource(
                materialsTotalLd,
                new Observer<BigDecimal>() {
                    @Override
                    public void onChanged(@Nullable BigDecimal newMaterialsTotal) {
                        Log.d(TAG, "materialsTotalLd обновился: " + newMaterialsTotal);
                        recalcTotals();
                    }
                }
        );



        this.roomTotals.addSource(
                salariesTotalLd,
                new Observer<BigDecimal>() {
                    @Override
                    public void onChanged(@Nullable BigDecimal newSalariesTotal) {
                        Log.d(TAG, "salariesTotalLd обновился: " + newSalariesTotal);
                        recalcTotals();
                    }
                }
        );

        Log.d(TAG, "RoomDetailViewModel успешно инициализирован.");
    }













    public LiveData<List<EstimateItemDisplay>> getEstimateItems() {
        return estimateItems;
    }

    public LiveData<ProjectRoomEntity> getRoom() {
        return room;
    }

    public LiveData<ProjectEntity> getProject() {
        return project;
    }











    public LiveData<RoomTotals> getRoomTotals() {
        return roomTotals;
    }








    public LiveData<List<WorkTaskEntity>> getWorkTasks() {
        return workTasks;
    }









    public LiveData<List<OpeningEntity>> getOpenings() {
        return openings;
    }









    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }














    public void updateDimensions(
            @Nullable final BigDecimal l,
            @Nullable final BigDecimal w,
            @Nullable final BigDecimal h
    ) {
        Log.d(TAG, "updateDimensions: roomId=" + roomId
                + ", l=" + l + ", w=" + w + ", h=" + h);

        try {
            if ((l != null && l.compareTo(BigDecimal.ZERO) < 0)
                    || (w != null && w.compareTo(BigDecimal.ZERO) < 0)
                    || (h != null && h.compareTo(BigDecimal.ZERO) < 0)) {
                postError("Размеры комнаты не могут быть отрицательными.");
                return;
            }

            roomRepository.updateDimensionsAndRecalculate(
                    getApplication(),
                    roomId,
                    l, w, h
            );

        } catch (EstimateRepository.ValidationException validationException) {
            Log.w(TAG, "updateDimensions: ошибка валидации — " + validationException.getMessage());
            postError(validationException.getMessage());
        }
    }























    public void addEstimateItem(
            final String fgisCode,
            final String name,
            final String unitMeasure,
            final BigDecimal basePrice,
            final BigDecimal consumptionRate,
            final BigDecimal effectiveArea
    ) {
        Log.d(TAG, "addEstimateItem: добавление позиции сметы.");

        try {
            ProjectEntity currentProject = project.getValue();
            if (currentProject == null) {
                postError("Настройки проекта ещё не загружены. Повторите добавление.");
                return;
            }

            BigDecimal normalizedBasePrice = basePrice;
            String normalizedUnit = unitMeasure;
            String initialStatus = "NEED_TO_BUY";
            List<UnitConversionEntity> rules = conversionRules.getValue();
            UnitConversionEntity matchedRule = findConversionRule(unitMeasure, rules);

            if (matchedRule != null) {
                normalizedUnit = matchedRule.appUnit;
                normalizedBasePrice = UnitConversionEngine.convert(
                        basePrice, unitMeasure, matchedRule.appUnit, rules)
                        .setScale(4, RoundingMode.HALF_UP);
            } else if (fgisCode != null && !fgisCode.isBlank()) {
                initialStatus = "UNITS_MISMATCH";
            }

            BigDecimal taxMultiplier = currentProject.taxMultiplier != null
                    ? currentProject.taxMultiplier : BigDecimal.ONE;
            BigDecimal logisticsPercent = currentProject.logisticsMarkup != null
                    ? currentProject.logisticsMarkup : BigDecimal.ZERO;
            BigDecimal logisticsFactor = BigDecimal.ONE.add(
                    logisticsPercent.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP));
            BigDecimal calculatedFinalPrice = normalizedBasePrice
                    .multiply(taxMultiplier)
                    .multiply(logisticsFactor)
                    .setScale(4, RoundingMode.HALF_UP);



            estimateRepository.addEstimateItem(
                    roomId,
                    fgisCode,
                    name,
                    normalizedUnit,
                    normalizedBasePrice,
                    calculatedFinalPrice,
                    consumptionRate,
                    effectiveArea,
                    initialStatus
            );
        } catch (EstimateRepository.ValidationException validationException) {

            Log.w(TAG, "addEstimateItem: ошибка валидации — "
                    + validationException.getMessage());
            postError(validationException.getMessage());
        }
    }

    public void addEstimateItemWithQuantity(
            final String fgisCode,
            final String name,
            final String unitMeasure,
            final BigDecimal basePrice,
            final BigDecimal quantity
    ) {
        try {
            ProjectEntity currentProject = project.getValue();
            if (currentProject == null) {
                postError("Настройки проекта ещё не загружены. Повторите добавление.");
                return;
            }
            BigDecimal taxMultiplier = currentProject.taxMultiplier != null
                    ? currentProject.taxMultiplier : BigDecimal.ONE;
            BigDecimal logisticsPercent = currentProject.logisticsMarkup != null
                    ? currentProject.logisticsMarkup : BigDecimal.ZERO;
            BigDecimal logisticsFactor = BigDecimal.ONE.add(
                    logisticsPercent.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP));
            BigDecimal finalPrice = basePrice
                    .multiply(taxMultiplier)
                    .multiply(logisticsFactor)
                    .setScale(4, RoundingMode.HALF_UP);

            estimateRepository.addEstimateItemWithQuantity(
                    roomId, fgisCode, name, unitMeasure, basePrice, finalPrice,
                    quantity, "NEED_TO_BUY");
        } catch (EstimateRepository.ValidationException validationException) {
            postError(validationException.getMessage());
        }
    }

    public void updateManualQuantity(final String itemId, final BigDecimal quantity) {
        try {
            estimateRepository.updateManualQuantity(itemId, quantity);
        } catch (EstimateRepository.ValidationException validationException) {
            postError(validationException.getMessage());
        }
    }

    @Nullable
    private UnitConversionEntity findConversionRule(
            @Nullable String unit,
            @Nullable List<UnitConversionEntity> rules) {
        if (unit == null || rules == null) return null;
        for (UnitConversionEntity rule : rules) {
            if (rule != null && rule.fgisUnit != null
                    && rule.fgisUnit.trim().equalsIgnoreCase(unit.trim())
                    && rule.appUnit != null && rule.conversionFactor != null
                    && rule.conversionFactor.compareTo(BigDecimal.ZERO) > 0) {
                return rule;
            }
        }
        return null;
    }











    public boolean hasEstimateItems() {
        List<EstimateItemDisplay> currentValue = estimateItems.getValue();
        return currentValue != null && !currentValue.isEmpty();
    }














    @NonNull
    public BigDecimal getCurrentEffectiveArea() {
        return getGeometry().wallsArea;
    }

    @NonNull
    public com.smetrix.app.utils.quantity.RoomGeometryCalculator.GeometryResult getGeometry() {
        ProjectRoomEntity currentRoom = room.getValue();
        List<OpeningEntity> currentOpenings = openings.getValue();
        if (currentRoom == null) {
            return new com.smetrix.app.utils.quantity.RoomGeometryCalculator.GeometryResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }
        return com.smetrix.app.utils.quantity.RoomGeometryCalculator.calculate(
                currentRoom.length, currentRoom.width, currentRoom.height, currentRoom.manualAreaOverride, currentOpenings);
    }

    public void addAdvancedEstimateItem(
            String fgisCode, String name, String unitMeasure, BigDecimal basePrice, BigDecimal quantity,
            String calculationMethod, BigDecimal wastePercent, Integer layers, BigDecimal thicknessMeters,
            BigDecimal manualQuantity, BigDecimal coveragePerPiece, BigDecimal coveragePerPackage, BigDecimal packageSize,
            String formulaDescription, BigDecimal consumptionRate
    ) {
        try {
            ProjectEntity currentProject = project.getValue();
            if (currentProject == null) {
                postError("Настройки проекта ещё не загружены.");
                return;
            }
            BigDecimal taxMultiplier = currentProject.taxMultiplier != null
                    ? currentProject.taxMultiplier : BigDecimal.ONE;
            BigDecimal logisticsPercent = currentProject.logisticsMarkup != null
                    ? currentProject.logisticsMarkup : BigDecimal.ZERO;
            BigDecimal logisticsFactor = BigDecimal.ONE.add(
                    logisticsPercent.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP));
            BigDecimal finalPrice = basePrice
                    .multiply(taxMultiplier)
                    .multiply(logisticsFactor)
                    .setScale(4, RoundingMode.HALF_UP);

            estimateRepository.addAdvancedEstimateItem(
                    roomId, fgisCode, name, unitMeasure, basePrice, finalPrice, quantity, "NEED_TO_BUY",
                    calculationMethod, wastePercent, layers, thicknessMeters, manualQuantity, coveragePerPiece,
                    coveragePerPackage, packageSize, formulaDescription, consumptionRate
            );
        } catch (EstimateRepository.ValidationException validationException) {
            postError(validationException.getMessage());
        }
    }










    public void deleteEstimateItem(final String itemId) {
        Log.d(TAG, "deleteEstimateItem: itemId=" + itemId);
        estimateRepository.deleteEstimateItem(itemId);
    }










    public void updateEstimateItemStatus(final String itemId, final String newStatus) {
        Log.d(TAG, "updateEstimateItemStatus: itemId=" + itemId + ", status=" + newStatus);
        estimateRepository.updateEstimateItemStatus(itemId, newStatus);
    }








    public void clearError() {
        errorMessage.setValue(null);
    }












    public void addWorkTask(
            final String workerId,
            final String taskName,
            final String rateType,
            final BigDecimal rateValue) {
        Log.d(TAG, "addWorkTask: добавление рабочей задачи.");
        if (taskName == null || taskName.trim().isEmpty()) {
            postError("Укажите название вида работ.");
            return;
        }
        if (rateValue == null || rateValue.compareTo(BigDecimal.ZERO) < 0) {
            postError("Ставка должна быть больше или равна нулю.");
            return;
        }
        roomRepository.addWorkTask(roomId, workerId, taskName.trim(), rateType, rateValue);
    }






    public void deleteWorkTask(final String taskId) {
        Log.d(TAG, "deleteWorkTask: taskId=" + taskId);
        roomRepository.deleteWorkTask(taskId);
    }

















    public void addOpening(
            final String type,
            final java.math.BigDecimal width,
            final java.math.BigDecimal height,
            @Nullable final java.math.BigDecimal depth,
            @Nullable final String placementType) {
        Log.d(TAG, "addOpening: roomId=" + roomId + ", type=" + type
                + ", w=" + width + ", h=" + height + ", d=" + depth);
        if (width == null || width.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            postError("Ширина должна быть больше нуля.");
            return;
        }
        if (height == null || height.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            postError("Высота должна быть больше нуля.");
            return;
        }
        if ("COLUMN".equals(type)
                && (depth == null || depth.compareTo(java.math.BigDecimal.ZERO) <= 0)) {
            postError("Для колонны необходимо указать глубину больше нуля.");
            return;
        }
        roomRepository.addOpening(getApplication(), roomId, type, width, height, depth, placementType);
    }









    public void setManualAreaOverride(@NonNull final BigDecimal area) {
        if (area.compareTo(BigDecimal.ZERO) <= 0) {
            postError("Ручная площадь должна быть больше нуля.");
            return;
        }
        Log.d(TAG, "setManualAreaOverride: roomId=" + roomId + ", area=" + area);
        roomRepository.setManualAreaOverride(getApplication(), roomId, area);
    }




    public void clearManualAreaOverride() {
        Log.d(TAG, "clearManualAreaOverride: roomId=" + roomId);
        roomRepository.clearManualAreaOverride(getApplication(), roomId);
    }






    public void deleteOpening(final String openingId) {
        Log.d(TAG, "deleteOpening: openingId=" + openingId);
        roomRepository.deleteOpening(getApplication(), openingId, roomId);
    }










    public void updateWorkTask(
            final String taskId,
            final String workerId,
            final String taskName,
            final String rateType,
            final BigDecimal rateValue) {
        Log.d(TAG, "updateWorkTask: обновление рабочей задачи.");
        if (taskName == null || taskName.trim().isEmpty()) {
            postError("Укажите название вида работ.");
            return;
        }
        if (rateValue == null || rateValue.compareTo(BigDecimal.ZERO) < 0) {
            postError("Ставка должна быть больше или равна нулю.");
            return;
        }
        roomRepository.updateWorkTask(taskId, workerId, taskName.trim(), rateType, rateValue);
    }


























    private void recalcTotals() {

        BigDecimal rawMaterials = materialsTotalLd.getValue();
        final BigDecimal materialsTotal;
        if (rawMaterials == null) {

            materialsTotal = BigDecimal.ZERO.setScale(SCALE_TOTALS, ROUNDING_TOTALS);
        } else {
            materialsTotal = rawMaterials.setScale(SCALE_TOTALS, ROUNDING_TOTALS);
        }


        BigDecimal rawSalaries = salariesTotalLd.getValue();
        final BigDecimal salariesTotal;
        if (rawSalaries == null) {
            salariesTotal = BigDecimal.ZERO.setScale(SCALE_TOTALS, ROUNDING_TOTALS);
        } else {
            salariesTotal = rawSalaries.setScale(SCALE_TOTALS, ROUNDING_TOTALS);
        }



        final BigDecimal roomTotal = materialsTotal
                .add(salariesTotal)
                .setScale(SCALE_TOTALS, ROUNDING_TOTALS);



        RoomTotals newTotals = new RoomTotals(materialsTotal, salariesTotal, roomTotal);
        roomTotals.setValue(newTotals);

        Log.d(TAG, "recalcTotals: materialsTotal=" + materialsTotal
                + ", salariesTotal=" + salariesTotal
                + ", roomTotal=" + roomTotal);
    }









    private void postError(@NonNull String message) {
        Log.w(TAG, "postError: " + message);

        errorMessage.postValue(message);
    }
}
