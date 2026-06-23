
package com.smetrix.app.repository;

import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.dao.EstimateItemDao;
import com.smetrix.app.db.dao.OpeningDao;
import com.smetrix.app.db.dao.ProjectRoomDao;
import com.smetrix.app.db.dao.WorkTaskDao;
import com.smetrix.app.db.entity.EstimateItemEntity;
import com.smetrix.app.db.entity.OpeningEntity;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.db.entity.WorkTaskEntity;
import com.smetrix.app.model.SyncState;
import com.smetrix.app.model.UuidGenerator;
import com.smetrix.app.network.sync.SyncManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;















public class RoomRepository {





    private static final BigDecimal TWO  = new BigDecimal("2");
    private static final BigDecimal ZERO = BigDecimal.ZERO;





    private final ProjectRoomDao  projectRoomDao;
    private final EstimateItemDao estimateItemDao;
    private final WorkTaskDao     workTaskDao;
    private final OpeningDao      openingDao;
    private final SyncManager     syncManager;





    public RoomRepository(
            ProjectRoomDao projectRoomDao,
            EstimateItemDao estimateItemDao,
            WorkTaskDao workTaskDao,
            OpeningDao openingDao,
            SyncManager syncManager
    ) {
        this.projectRoomDao  = projectRoomDao;
        this.estimateItemDao = estimateItemDao;
        this.workTaskDao     = workTaskDao;
        this.openingDao      = openingDao;
        this.syncManager     = syncManager;
    }








    public void createRoom(final String projectId, final String name) {
        final long currentTimeMillis = System.currentTimeMillis();
        final String newRoomId = UuidGenerator.generate();
        AppExecutors.diskIO().execute(() -> {
            ProjectRoomEntity newRoom = new ProjectRoomEntity();
            newRoom.id              = newRoomId;
            newRoom.projectId       = projectId;
            newRoom.name            = name;
            newRoom.length          = null;
            newRoom.width           = null;
            newRoom.height          = null;
            newRoom.manualAreaOverride = null;
            newRoom.createdAt       = currentTimeMillis;
            newRoom.updatedAt       = currentTimeMillis;
            newRoom.version         = 0L;
            newRoom.syncState       = SyncState.PENDING_CREATE.name();
            projectRoomDao.insert(newRoom);
            syncManager.scheduleSync();
        });
    }











    public void updateDimensionsAndRecalculate(
            final android.content.Context context,
            final String roomId,
            final BigDecimal l,
            final BigDecimal w,
            final BigDecimal h
    ) {
        final long ts = System.currentTimeMillis();

        AppExecutors.diskIO().execute(() -> {
            AppDatabase database = AppDatabase.getInstance(context.getApplicationContext());

            database.runInTransaction(() -> {

                projectRoomDao.updateDimensions(
                        roomId, l, w, h, ts);


                ProjectRoomEntity room = projectRoomDao.getById(roomId);
                BigDecimal netArea = ZERO;
                if (room != null
                        && room.length != null
                        && room.width != null
                        && room.height != null) {
                    List<OpeningEntity> openingList = projectRoomDao.getOpeningsSync(roomId);
                    netArea = calcNetArea(room, openingList);
                }
                final BigDecimal effectiveArea = (room != null && room.manualAreaOverride != null)
                        ? room.manualAreaOverride : netArea;




                List<EstimateItemEntity> estimateItemList =
                        projectRoomDao.getEstimateItemsSync(roomId);
                for (EstimateItemEntity item : estimateItemList) {
                    if (item.consumptionRate == null) {
                        continue;
                    }
                    BigDecimal qty = effectiveArea
                            .multiply(item.consumptionRate)
                            .setScale(6, RoundingMode.HALF_UP);
                    BigDecimal total = item.finalPrice
                            .multiply(qty)
                            .setScale(2, RoundingMode.HALF_UP);
                    estimateItemDao.updateQuantityAndTotal(item.id, qty, total, ts);
                }



                List<WorkTaskEntity> pieceworkList =
                        projectRoomDao.getPieceworkTasksSync(roomId);
                for (WorkTaskEntity task : pieceworkList) {
                    BigDecimal payment = effectiveArea
                            .multiply(task.rateValue)
                            .setScale(2, RoundingMode.HALF_UP);
                    workTaskDao.updatePayment(task.id, payment, ts);
                }

            });

            syncManager.scheduleSync();
        });
    }











    public void setManualAreaOverride(
            final android.content.Context context,
            final String roomId,
            final BigDecimal area) {
        final long ts = System.currentTimeMillis();
        AppExecutors.diskIO().execute(() -> {
            AppDatabase database = AppDatabase.getInstance(context.getApplicationContext());
            database.runInTransaction(() -> {
                projectRoomDao.updateManualAreaOverride(roomId, area, ts);
                recalcAfterElementChange(context, roomId, ts);
            });
            syncManager.scheduleSync();
        });
    }







    public void clearManualAreaOverride(
            final android.content.Context context,
            final String roomId) {
        final long ts = System.currentTimeMillis();
        AppExecutors.diskIO().execute(() -> {
            AppDatabase database = AppDatabase.getInstance(context.getApplicationContext());
            database.runInTransaction(() -> {
                projectRoomDao.updateManualAreaOverride(roomId, null, ts);
                recalcAfterElementChange(context, roomId, ts);
            });
            syncManager.scheduleSync();
        });
    }





    public void deleteRoom(final String roomId) {
        final long now = System.currentTimeMillis();
        AppExecutors.diskIO().execute(() -> {
            ProjectRoomEntity room = projectRoomDao.getById(roomId);
            if (room != null && SyncState.PENDING_CREATE.name().equals(room.syncState)) {
                projectRoomDao.deleteById(roomId);
            } else {
                projectRoomDao.markDeleted(roomId, now);
            }
            syncManager.scheduleSync();
        });
    }




    public void addWorkTask(
            final String roomId,
            final String workerId,
            final String taskName,
            final String rateType,
            final BigDecimal rateValue) {
        final long now    = System.currentTimeMillis();
        final String newId = UuidGenerator.generate();
        AppExecutors.diskIO().execute(() -> {
            WorkTaskEntity task = new WorkTaskEntity();
            task.id            = newId;
            task.projectRoomId = roomId;
            task.workerId      = workerId;
            task.taskName      = taskName;
            task.rateType      = rateType;
            task.rateValue     = rateValue;
            task.totalPayment  = rateValue;
            task.createdAt     = now;
            task.updatedAt     = now;
            task.version       = 0L;
            task.syncState     = SyncState.PENDING_CREATE.name();
            workTaskDao.insert(task);
            syncManager.scheduleSync();
        });
    }




    public void updateRoomName(final String roomId, final String newName) {
        final long ts = System.currentTimeMillis();
        AppExecutors.diskIO().execute(() -> {
            projectRoomDao.updateRoomName(roomId, newName, ts);
            syncManager.scheduleSync();
        });
    }




    public void deleteWorkTask(final String taskId) {
        final long now = System.currentTimeMillis();
        AppExecutors.diskIO().execute(() -> {
            WorkTaskEntity task = workTaskDao.getById(taskId);
            if (task != null && SyncState.PENDING_CREATE.name().equals(task.syncState)) {
                workTaskDao.deleteById(taskId);
            } else {
                workTaskDao.markDeleted(taskId, now);
            }
            syncManager.scheduleSync();
        });
    }




    public void updateWorkTask(
            final String taskId,
            final String workerId,
            final String taskName,
            final String rateType,
            final BigDecimal rateValue) {
        final long now = System.currentTimeMillis();
        AppExecutors.diskIO().execute(() -> {
            workTaskDao.updateTaskFields(
                    taskId, workerId, taskName, rateType, rateValue, rateValue, now);
            syncManager.scheduleSync();
        });
    }















    private BigDecimal calcNetArea(
            @androidx.annotation.NonNull ProjectRoomEntity room,
            @androidx.annotation.NonNull List<OpeningEntity> elements) {

        BigDecimal area = TWO.multiply(room.length.add(room.width)).multiply(room.height);

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
        return area.max(ZERO);
    }












    public void addOpening(
            final android.content.Context context,
            final String roomId,
            final String type,
            final BigDecimal width,
            final BigDecimal height,
            @androidx.annotation.Nullable final BigDecimal depth,
            @androidx.annotation.Nullable final String placementType) {
        final long now    = System.currentTimeMillis();
        final String newId = UuidGenerator.generate();
        AppExecutors.diskIO().execute(() -> {

            OpeningEntity el = new OpeningEntity();
            el.id            = newId;
            el.projectRoomId = roomId;
            el.type          = type;
            el.width         = width;
            el.height        = height;
            el.depth         = depth;
            el.placementType = placementType;
            el.createdAt     = now;
            el.updatedAt     = now;
            el.version       = 0L;
            el.syncState     = SyncState.PENDING_CREATE.name();
            openingDao.insert(el);


            recalcAfterElementChange(context, roomId, now);
            syncManager.scheduleSync();
        });
    }








    public void deleteOpening(
            final android.content.Context context,
            final String openingId,
            final String roomId) {
        final long now = System.currentTimeMillis();
        AppExecutors.diskIO().execute(() -> {
            OpeningEntity existing = openingDao.getById(openingId);
            if (existing != null && SyncState.PENDING_CREATE.name().equals(existing.syncState)) {
                openingDao.delete(openingId);
            } else {
                openingDao.markDeleted(openingId, now);
            }
            recalcAfterElementChange(context, roomId, now);
            syncManager.scheduleSync();
        });
    }






    private void recalcAfterElementChange(
            final android.content.Context context,
            final String roomId,
            final long now) {
        AppDatabase database = AppDatabase.getInstance(context.getApplicationContext());
        ProjectRoomEntity room = projectRoomDao.getById(roomId);
        if (room == null || room.length == null || room.width == null || room.height == null) return;

        database.runInTransaction(() -> {
            List<OpeningEntity> elements = projectRoomDao.getOpeningsSync(roomId);
            BigDecimal netArea = calcNetArea(room, elements);
            final BigDecimal effectiveArea = (room.manualAreaOverride != null)
                    ? room.manualAreaOverride : netArea;

            java.math.RoundingMode rm = java.math.RoundingMode.HALF_UP;

            List<EstimateItemEntity> estimateItems = projectRoomDao.getEstimateItemsSync(roomId);
            for (EstimateItemEntity item : estimateItems) {
                if (item.consumptionRate == null) {
                    continue;
                }
                BigDecimal qty   = effectiveArea.multiply(item.consumptionRate).setScale(6, rm);
                BigDecimal total = item.finalPrice.multiply(qty).setScale(2, rm);
                estimateItemDao.updateQuantityAndTotal(item.id, qty, total, now);
            }

            List<WorkTaskEntity> pieceworkTasks = projectRoomDao.getPieceworkTasksSync(roomId);
            for (WorkTaskEntity task : pieceworkTasks) {
                BigDecimal payment = effectiveArea.multiply(task.rateValue).setScale(2, rm);
                workTaskDao.updatePayment(task.id, payment, now);
            }
        });
    }
}
