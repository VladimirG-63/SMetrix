
package com.smetrix.app.repository;

import androidx.lifecycle.LiveData;

import com.smetrix.app.db.dao.EstimateItemDao;
import com.smetrix.app.db.entity.EstimateItemEntity;
import com.smetrix.app.model.ItemStatus;
import com.smetrix.app.model.SyncState;
import com.smetrix.app.model.UuidGenerator;
import com.smetrix.app.network.sync.SyncManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;





































public class EstimateRepository {
















    public static class ValidationException extends RuntimeException {






        public ValidationException(String message) {
            super(message);
        }
    }









    private final EstimateItemDao estimateItemDao;





    private final SyncManager syncManager;

















    public EstimateRepository(EstimateItemDao estimateItemDao, SyncManager syncManager) {
        this.estimateItemDao = estimateItemDao;
        this.syncManager = syncManager;
    }





















    public LiveData<List<EstimateItemEntity>> getItemsByRoom(String roomId) {

        return estimateItemDao.getByRoomId(roomId);
    }
































































    public void addEstimateItem(
            final String roomId,
            final String fgisCode,
            final String name,
            final String unitMeasure,
            final BigDecimal basePrice,
            final BigDecimal finalPrice,
            final BigDecimal consumptionRate,
            final BigDecimal effectiveArea,
            final String initialStatus
    ) {





        if (roomId == null || roomId.isBlank()) {
            throw new ValidationException("Не указана комната для позиции сметы.");
        }
        if (name == null || name.trim().isEmpty() || name.length() > 300) {
            throw new ValidationException("Название позиции должно содержать от 1 до 300 символов.");
        }
        if (unitMeasure == null || unitMeasure.trim().isEmpty() || unitMeasure.length() > 30) {
            throw new ValidationException("Некорректная единица измерения.");
        }
        if (basePrice == null || finalPrice == null || consumptionRate == null
                || effectiveArea == null) {
            throw new ValidationException("Расчётные значения позиции не могут быть пустыми.");
        }


        if (basePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    "Базовая цена (basePrice) не может быть отрицательной. "
                    + "Передано значение: " + basePrice.toPlainString()
            );
        }

        if (finalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    "Итоговая цена (finalPrice) не может быть отрицательной. "
                    + "Передано значение: " + finalPrice.toPlainString()
            );
        }

        if (consumptionRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    "Норма расхода (consumptionRate) не может быть отрицательной. "
                    + "Передано значение: " + consumptionRate.toPlainString()
            );
        }

        if (effectiveArea.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Площадь не может быть отрицательной.");
        }








        final BigDecimal quantity = effectiveArea
                .multiply(consumptionRate)
                .setScale(6, RoundingMode.HALF_UP);








        final BigDecimal totalPrice = finalPrice
                .multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);


        final long currentTimeMillis = System.currentTimeMillis();
        final String newItemId = UuidGenerator.generate();



        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {

                EstimateItemEntity newItem = new EstimateItemEntity();


                newItem.id = newItemId;


                newItem.projectRoomId = roomId;


                newItem.fgisCode = fgisCode;


                newItem.name = name;


                newItem.unitMeasure = unitMeasure;


                newItem.basePrice = basePrice;
                newItem.finalPrice = finalPrice;


                newItem.consumptionRate = consumptionRate;


                newItem.quantity = quantity;
                newItem.totalPrice = totalPrice;



                newItem.status = initialStatus != null
                        ? initialStatus : ItemStatus.NEED_TO_BUY.name();


                newItem.createdAt = currentTimeMillis;
                newItem.updatedAt = currentTimeMillis;



                newItem.version = 0L;



                newItem.syncState = SyncState.PENDING_CREATE.name();




                estimateItemDao.insert(newItem);




                syncManager.scheduleSync();
            }
        });
    }

    public void addAdvancedEstimateItem(
            String roomId, String fgisCode, String name, String unitMeasure,
            BigDecimal basePrice, BigDecimal finalPrice, BigDecimal quantity, String status,
            String calculationMethod, BigDecimal wastePercent, Integer layers, BigDecimal thicknessMeters,
            BigDecimal manualQuantity, BigDecimal coveragePerPiece, BigDecimal coveragePerPackage, BigDecimal packageSize,
            String formulaDescription, BigDecimal consumptionRate
    ) throws ValidationException {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new ValidationException("ID комнаты не может быть пустым.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Название материала обязательно.");
        }
        if (basePrice == null || basePrice.signum() < 0) {
            throw new ValidationException("Базовая цена не может быть отрицательной.");
        }
        if (finalPrice == null || finalPrice.signum() < 0) {
            throw new ValidationException("Итоговая цена не может быть отрицательной.");
        }
        if (quantity == null || quantity.signum() < 0) {
            throw new ValidationException("Количество не может быть отрицательным.");
        }

        final long currentTimeMillis = System.currentTimeMillis();
        final String newItemId = UuidGenerator.generate();
        final BigDecimal totalPrice = finalPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                EstimateItemEntity entity = new EstimateItemEntity();
                entity.id = newItemId;
                entity.projectRoomId = roomId;
                entity.fgisCode = fgisCode;
                entity.name = name.trim();
                entity.unitMeasure = unitMeasure != null ? unitMeasure.trim() : "";
                entity.basePrice = basePrice;
                entity.finalPrice = finalPrice;
                entity.quantity = quantity;
                entity.totalPrice = totalPrice;
                entity.status = status;

                entity.calculationMethod = calculationMethod;
                entity.wastePercent = wastePercent;
                entity.layers = layers;
                entity.thicknessMeters = thicknessMeters;
                entity.manualQuantity = manualQuantity;
                entity.coveragePerPiece = coveragePerPiece;
                entity.coveragePerPackage = coveragePerPackage;
                entity.packageSize = packageSize;
                entity.formulaDescription = formulaDescription;
                entity.consumptionRate = consumptionRate;

                entity.createdAt = currentTimeMillis;
                entity.updatedAt = currentTimeMillis;
                entity.version = 0L;
                entity.syncState = "PENDING_CREATE";

                estimateItemDao.insert(entity);
                syncManager.scheduleSync();
            }
        });
    }






    public void addEstimateItemWithQuantity(
            final String roomId,
            final String fgisCode,
            final String name,
            final String unitMeasure,
            final BigDecimal basePrice,
            final BigDecimal finalPrice,
            final BigDecimal quantity,
            final String initialStatus
    ) {
        if (roomId == null || roomId.isBlank()) {
            throw new ValidationException("Не указана комната для позиции сметы.");
        }
        if (name == null || name.trim().isEmpty() || name.length() > 4000) {
            throw new ValidationException("Некорректное название материала.");
        }
        if (unitMeasure == null || unitMeasure.trim().isEmpty() || unitMeasure.length() > 30) {
            throw new ValidationException("Некорректная единица измерения.");
        }
        if (basePrice == null || finalPrice == null || quantity == null
                || basePrice.signum() < 0 || finalPrice.signum() < 0 || quantity.signum() <= 0) {
            throw new ValidationException("Количество должно быть больше нуля, а цены не могут быть отрицательными.");
        }

        final BigDecimal normalizedQuantity = quantity.setScale(6, RoundingMode.HALF_UP);
        final BigDecimal totalPrice = finalPrice.multiply(normalizedQuantity)
                .setScale(2, RoundingMode.HALF_UP);
        final long now = System.currentTimeMillis();
        final String itemId = UuidGenerator.generate();

        AppExecutors.diskIO().execute(() -> {
            EstimateItemEntity item = new EstimateItemEntity();
            item.id = itemId;
            item.projectRoomId = roomId;
            item.fgisCode = fgisCode;
            item.name = name;
            item.unitMeasure = unitMeasure;
            item.basePrice = basePrice;
            item.finalPrice = finalPrice;
            item.consumptionRate = null;
            item.quantity = normalizedQuantity;
            item.totalPrice = totalPrice;
            item.status = initialStatus != null ? initialStatus : ItemStatus.NEED_TO_BUY.name();
            item.createdAt = now;
            item.updatedAt = now;
            item.version = 0L;
            item.syncState = SyncState.PENDING_CREATE.name();
            estimateItemDao.insert(item);
            syncManager.scheduleSync();
        });
    }

    public void updateManualQuantity(final String itemId, final BigDecimal quantity) {
        if (itemId == null || itemId.isBlank() || quantity == null || quantity.signum() <= 0) {
            throw new ValidationException("Количество должно быть больше нуля.");
        }
        final BigDecimal normalizedQuantity = quantity.setScale(6, RoundingMode.HALF_UP);
        AppExecutors.diskIO().execute(() -> {
            EstimateItemEntity item = estimateItemDao.getById(itemId);
            if (item == null || item.finalPrice == null) {
                return;
            }
            BigDecimal total = item.finalPrice.multiply(normalizedQuantity)
                    .setScale(2, RoundingMode.HALF_UP);
            estimateItemDao.updateManualQuantity(
                    itemId, normalizedQuantity, total, System.currentTimeMillis());
            syncManager.scheduleSync();
        });
    }
























    public void updateSyncResult(
            final String id,
            final long serverVersion,
            final long serverUpdatedAt
    ) {

        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {


                estimateItemDao.updateAfterSync(id, serverVersion, serverUpdatedAt, "SYNCED");
            }
        });
    }














    public void deleteEstimateItem(final String itemId) {
        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                estimateItemDao.deleteById(itemId);
                syncManager.scheduleSync();
            }
        });
    }










    public void updateEstimateItemStatus(final String itemId, final String newStatus) {
        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                estimateItemDao.updateStatus(itemId, newStatus);
            }
        });
    }
}
