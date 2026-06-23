
package com.smetrix.app.network.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.dao.ConflictDao;
import com.smetrix.app.db.dao.EstimateItemDao;
import com.smetrix.app.db.dao.OpeningDao;
import com.smetrix.app.db.dao.ProjectDao;
import com.smetrix.app.db.dao.ProjectRoomDao;
import com.smetrix.app.db.dao.WorkTaskDao;
import com.smetrix.app.db.dao.WorkerDao;
import com.smetrix.app.db.entity.ConflictEntity;
import com.smetrix.app.db.entity.EstimateItemEntity;
import com.smetrix.app.db.entity.OpeningEntity;
import com.smetrix.app.db.entity.ProjectEntity;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.db.entity.WorkTaskEntity;
import com.smetrix.app.db.entity.WorkerEntity;
import com.smetrix.app.network.ApiClient;
import com.smetrix.app.network.ApiService;
import com.smetrix.app.network.dto.EstimateItemDto;
import com.smetrix.app.network.dto.OpeningDto;
import com.smetrix.app.network.dto.ProjectDto;
import com.smetrix.app.network.dto.ProjectRoomDto;
import com.smetrix.app.network.dto.SyncBatchRequest;
import com.smetrix.app.network.dto.SyncBatchResponse;
import com.smetrix.app.network.dto.SyncItemResult;
import com.smetrix.app.network.dto.SyncPullResponse;
import com.smetrix.app.network.dto.WorkTaskDto;
import com.smetrix.app.network.dto.WorkerDto;
import com.smetrix.app.repository.AuthRepository;
import com.smetrix.app.utils.SecurePrefsHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";


    private static final int BATCH_SIZE = 50;


    private static final List<String> PENDING_STATES = Arrays.asList(
            "PENDING_CREATE", "PENDING_UPDATE", "PENDING_DELETE"
    );


    private ProjectDao projectDao;
    private ProjectRoomDao projectRoomDao;
    private EstimateItemDao estimateItemDao;
    private OpeningDao openingDao;
    private WorkTaskDao workTaskDao;
    private WorkerDao workerDao;
    private ConflictDao conflictDao;
    private ApiService apiService;
    private AppDatabase database;
    private String currentUserId;
    private final Gson gson = new Gson();

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
        super(context, workerParameters);
    }

    @NonNull
    @Override
    public Result doWork() {

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        database        = db;
        projectDao      = db.projectDao();
        projectRoomDao  = db.projectRoomDao();
        estimateItemDao = db.estimateItemDao();
        openingDao      = db.openingDao();
        workTaskDao     = db.workTaskDao();
        workerDao       = db.workerDao();
        conflictDao     = db.conflictDao();
        apiService      = ApiClient.getService(getApplicationContext());
        AuthRepository authRepository = new AuthRepository(getApplicationContext(), apiService);
        if (!authRepository.isLoggedIn()) {
            Log.d(TAG, "Синхронизация пропущена: активного аккаунта нет.");
            return Result.success();
        }
        currentUserId = authRepository.getUserId();

        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.w(TAG, "Синхронизация остановлена: нет userId текущего аккаунта.");
            return Result.success();
        }

        try {
            syncPendingProjects();
            syncPendingRooms();
            syncPendingOpenings();
            syncPendingEstimateItems();
            syncPendingWorkers();
            syncPendingWorkTasks();
            pullServerChanges();
            return Result.success();
        } catch (IOException networkError) {

            Log.w(TAG, "Сетевая ошибка при синхронизации. Запланирован повтор.", networkError);
            return Result.retry();
        } catch (Exception unexpectedException) {

            Log.e(TAG, "Неисправимая ошибка при синхронизации.", unexpectedException);
            return Result.failure();
        }
    }

    private void pullServerChanges() throws IOException {
        String checkpointKey = "last_pull_at_" + currentUserId;
        long since = SecurePrefsHelper.get(getApplicationContext())
                .getLong(checkpointKey, 0L);

        Response<SyncPullResponse> response = apiService.pullChanges(since).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("Pull sync failed: HTTP " + response.code());
        }

        SyncPullResponse pull = response.body();
        database.runInTransaction(() -> applyPulledChanges(pull));
        SecurePrefsHelper.get(getApplicationContext()).edit()
                .putLong(checkpointKey, pull.serverTime)
                .apply();
    }

    private void applyPulledChanges(SyncPullResponse pull) {
        if (pull.projects != null) {
            for (SyncPullResponse.ProjectChange change : pull.projects) {
                ProjectEntity local = projectDao.getById(change.id);
                if (!canApplyRemote(local != null ? local.syncState : null,
                        local != null ? local.version : -1L, change.version)) continue;
                ProjectEntity entity = new ProjectEntity();
                entity.id = change.id;
                entity.userId = currentUserId;
                entity.name = change.name;
                entity.city = change.city;
                entity.regionCode = change.regionCode;
                entity.taxMultiplier = decimal(change.taxMultiplier);
                entity.logisticsMarkup = decimal(change.logisticsMarkup);
                entity.deletedAt = change.deletedAt;
                entity.lastSyncedAt = value(change.updatedAt);
                entity.createdAt = value(change.createdAt);
                entity.updatedAt = value(change.updatedAt);
                entity.version = change.version;
                entity.syncState = "SYNCED";
                projectDao.upsertFromSync(entity);
            }
        }

        if (pull.rooms != null) {
            for (SyncPullResponse.RoomChange change : pull.rooms) {
                ProjectRoomEntity local = projectRoomDao.getById(change.id);
                if (!canApplyRemote(local != null ? local.syncState : null,
                        local != null ? local.version : -1L, change.version)) continue;
                if (change.deletedAt != null) {
                    projectRoomDao.deleteById(change.id);
                    continue;
                }
                ProjectRoomEntity entity = new ProjectRoomEntity();
                entity.id = change.id;
                entity.projectId = change.projectId;
                entity.name = change.name;
                entity.length = decimal(change.length);
                entity.width = decimal(change.width);
                entity.height = decimal(change.height);
                entity.manualAreaOverride = decimal(change.manualAreaOverride);
                entity.createdAt = value(change.createdAt);
                entity.updatedAt = value(change.updatedAt);
                entity.version = change.version;
                entity.syncState = "SYNCED";
                projectRoomDao.upsertFromSync(entity);
            }
        }

        if (pull.workers != null) {
            for (SyncPullResponse.WorkerChange change : pull.workers) {
                WorkerEntity local = workerDao.getById(change.id);
                if (!canApplyRemote(local != null ? local.syncState : null,
                        local != null ? local.version : -1L, change.version)) continue;
                if (change.deletedAt != null) {
                    workerDao.deleteById(change.id);
                    continue;
                }
                WorkerEntity entity = new WorkerEntity();
                entity.id = change.id;
                entity.userId = currentUserId;
                entity.fullName = change.fullName;
                entity.phone = change.phone;
                entity.specialty = change.specialty;
                entity.createdAt = value(change.createdAt);
                entity.updatedAt = value(change.updatedAt);
                entity.version = change.version;
                entity.syncState = "SYNCED";
                workerDao.upsertFromSync(entity);
            }
        }

        if (pull.openings != null) {
            for (SyncPullResponse.OpeningChange change : pull.openings) {
                OpeningEntity local = openingDao.getById(change.id);
                if (!canApplyRemote(local != null ? local.syncState : null,
                        local != null ? local.version : -1L, change.version)) continue;
                if (change.deletedAt != null) {
                    openingDao.delete(change.id);
                    continue;
                }
                OpeningEntity entity = new OpeningEntity();
                entity.id = change.id;
                entity.projectRoomId = change.projectRoomId;
                entity.type = change.type;
                entity.width = decimal(change.width);
                entity.height = decimal(change.height);
                entity.depth = decimal(change.depth);
                entity.placementType = change.placementType;
                entity.createdAt = value(change.createdAt);
                entity.updatedAt = value(change.updatedAt);
                entity.version = change.version;
                entity.syncState = "SYNCED";
                openingDao.upsertFromSync(entity);
            }
        }

        if (pull.estimateItems != null) {
            for (SyncPullResponse.EstimateChange change : pull.estimateItems) {
                EstimateItemEntity local = estimateItemDao.getById(change.id);
                if (!canApplyRemote(local != null ? local.syncState : null,
                        local != null ? local.version : -1L, change.version)) continue;
                if (change.deletedAt != null) {
                    estimateItemDao.deleteById(change.id);
                    continue;
                }
                EstimateItemEntity entity = new EstimateItemEntity();
                entity.id = change.id;
                entity.projectRoomId = change.projectRoomId;
                entity.fgisCode = change.fgisCode;
                entity.name = change.name;
                entity.unitMeasure = change.unitMeasure;
                entity.basePrice = decimal(change.basePrice);
                entity.finalPrice = decimal(change.finalPrice);
                entity.consumptionRate = decimal(change.consumptionRate);
                entity.quantity = decimal(change.quantity);
                entity.totalPrice = decimal(change.totalPrice);
                entity.status = change.status;
                entity.calculationMethod = change.calculationMethod;
                entity.wastePercent = decimal(change.wastePercent);
                entity.layers = change.layers;
                entity.thicknessMeters = decimal(change.thicknessMeters);
                entity.manualQuantity = decimal(change.manualQuantity);
                entity.coveragePerPiece = decimal(change.coveragePerPiece);
                entity.coveragePerPackage = decimal(change.coveragePerPackage);
                entity.packageSize = decimal(change.packageSize);
                entity.formulaDescription = change.formulaDescription;
                entity.createdAt = value(change.createdAt);
                entity.updatedAt = value(change.updatedAt);
                entity.version = change.version;
                entity.syncState = "SYNCED";
                estimateItemDao.upsertFromSync(entity);
            }
        }

        if (pull.workTasks != null) {
            for (SyncPullResponse.WorkTaskChange change : pull.workTasks) {
                WorkTaskEntity local = workTaskDao.getById(change.id);
                if (!canApplyRemote(local != null ? local.syncState : null,
                        local != null ? local.version : -1L, change.version)) continue;
                if (change.deletedAt != null) {
                    workTaskDao.deleteById(change.id);
                    continue;
                }
                WorkTaskEntity entity = new WorkTaskEntity();
                entity.id = change.id;
                entity.projectRoomId = change.projectRoomId;
                entity.workerId = change.workerId;
                entity.taskName = change.taskName;
                entity.rateType = change.rateType;
                entity.rateValue = decimal(change.rateValue);
                entity.totalPayment = decimal(change.totalPayment);
                entity.createdAt = value(change.createdAt);
                entity.updatedAt = value(change.updatedAt);
                entity.version = change.version;
                entity.syncState = "SYNCED";
                workTaskDao.upsertFromSync(entity);
            }
        }
    }

    private boolean canApplyRemote(String localState, long localVersion, long remoteVersion) {
        if (localState != null && !"SYNCED".equals(localState)) {
            return false;
        }
        return remoteVersion >= localVersion;
    }

    private long value(Long timestamp) {
        return timestamp != null ? timestamp : 0L;
    }

    private BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }















    private void syncPendingProjects() throws IOException {
        List<ProjectEntity> pending = projectDao.getByStatesForUser(currentUserId, PENDING_STATES);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (ProjectEntity project : pending) {
            if ("PENDING_CREATE".equals(project.syncState)
                    || "PENDING_UPDATE".equals(project.syncState)) {
                syncUpsertProject(project);
            } else if ("PENDING_DELETE".equals(project.syncState)) {
                syncDeleteProject(project);
            }
        }
    }







    private void syncUpsertProject(ProjectEntity project) throws IOException {
        ProjectDto dto = entityToProjectDto(project);
        Response<ProjectDto> response = apiService.createProject(dto).execute();

        if (response.isSuccessful() && response.body() != null) {
            ProjectDto serverDto = response.body();
            long now = System.currentTimeMillis();
            projectDao.updateAfterSync(project.id, serverDto.version,
                    parseServerTimestamp(serverDto.updatedAt), now, "SYNCED");
            Log.d(TAG, "Проект создан на сервере: id=" + project.id);
        } else {
            int code = response.code();
            if (code == 409) {
                projectDao.updateSyncState(project.id, "CONFLICT");
                ConflictEntity conflict = new ConflictEntity();
                conflict.entityId = project.id;
                conflict.entityType = "PROJECT";
                conflict.localSnapshot = gson.toJson(project);
                conflict.serverSnapshot = response.errorBody() != null
                        ? response.errorBody().string() : null;
                conflict.detectedAt = System.currentTimeMillis();
                conflictDao.insert(conflict);
                return;
            }
            if (code >= 500) {
                markProjectFailed(project.id);
                Log.e(TAG, "Серверная ошибка при создании проекта id=" + project.id
                        + ", code=" + code);
            } else {
                Log.w(TAG, "Ошибка создания проекта id=" + project.id + ", code=" + code);
            }
        }
    }







    private void syncDeleteProject(ProjectEntity project) throws IOException {
        Map<String, Long> body = new HashMap<>();
        body.put("version", project.version);
        Response<Void> response = apiService.deleteProject(project.id, body).execute();

        if (response.isSuccessful()) {
            long now = System.currentTimeMillis();
            projectDao.updateAfterSync(project.id, project.version + 1, now, now, "SYNCED");
            Log.d(TAG, "Проект помечен удалённым на сервере: id=" + project.id);
        } else {
            int code = response.code();
            if (code == 409) {
                projectDao.updateSyncState(project.id, "CONFLICT");
                ConflictEntity conflict = new ConflictEntity();
                conflict.entityId = project.id;
                conflict.entityType = "PROJECT";
                conflict.localSnapshot = gson.toJson(project);
                conflict.serverSnapshot = response.errorBody() != null
                        ? response.errorBody().string() : null;
                conflict.detectedAt = System.currentTimeMillis();
                conflictDao.insert(conflict);
                return;
            }
            if (code >= 500) {
                markProjectFailed(project.id);
            }
            Log.w(TAG, "Ошибка удаления проекта id=" + project.id + ", code=" + code);
        }
    }


    private void markProjectFailed(String projectId) {
        projectDao.updateSyncState(projectId, "FAILED");
    }













    private void syncPendingRooms() throws IOException {
        List<ProjectRoomEntity> pending = filterRoomsForCurrentUser(
                projectRoomDao.getByStates(PENDING_STATES));
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (List<ProjectRoomEntity> batch : partition(pending, BATCH_SIZE)) {
            if (batch.isEmpty()) {
                continue;
            }
            String projectId = batch.get(0).projectId;
            List<ProjectRoomDto> dtos = new ArrayList<>(batch.size());
            Map<String, ProjectRoomEntity> entityIndex = new HashMap<>();
            for (ProjectRoomEntity room : batch) {
                dtos.add(entityToProjectRoomDto(room));
                entityIndex.put(room.id, room);
            }

            Response<SyncBatchResponse> response =
                    apiService.syncProjectRooms(new SyncBatchRequest(projectId, dtos)).execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Сервер вернул ошибку синхронизации комнат: "
                        + response.code());
            }

            if (response.body().results == null) {
                continue;
            }
            for (SyncItemResult result : response.body().results) {
                handleProjectRoomResult(result, entityIndex.get(result.id));
            }
        }
    }

    private void syncPendingWorkers() throws IOException {
        List<WorkerEntity> pending = filterWorkersForCurrentUser(
                workerDao.getByStates(PENDING_STATES));
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (List<WorkerEntity> batch : partition(pending, BATCH_SIZE)) {
            List<WorkerDto> dtos = new ArrayList<>(batch.size());
            Map<String, WorkerEntity> entityIndex = new HashMap<>();
            for (WorkerEntity worker : batch) {
                dtos.add(entityToWorkerDto(worker));
                entityIndex.put(worker.id, worker);
            }

            Response<SyncBatchResponse> response =
                    apiService.syncWorkers(new SyncBatchRequest("", dtos)).execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Сервер вернул ошибку синхронизации рабочих: "
                        + response.code());
            }

            if (response.body().results == null) {
                continue;
            }
            for (SyncItemResult result : response.body().results) {
                handleWorkerResult(result, entityIndex.get(result.id));
            }
        }
    }

    private void syncPendingOpenings() throws IOException {
        List<OpeningEntity> pending = filterOpeningsForCurrentUser(
                openingDao.getByStates(PENDING_STATES));
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (List<OpeningEntity> batch : partition(pending, BATCH_SIZE)) {
            if (batch.isEmpty()) {
                continue;
            }
            String roomId = batch.get(0).projectRoomId;
            ProjectRoomEntity room = projectRoomDao.getById(roomId);
            String projectId = room != null ? room.projectId : "";

            List<OpeningDto> dtos = new ArrayList<>(batch.size());
            Map<String, OpeningEntity> entityIndex = new HashMap<>();
            for (OpeningEntity opening : batch) {
                dtos.add(entityToOpeningDto(opening));
                entityIndex.put(opening.id, opening);
            }

            Response<SyncBatchResponse> response =
                    apiService.syncOpenings(new SyncBatchRequest(projectId, dtos)).execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Сервер вернул ошибку синхронизации проёмов: "
                        + response.code());
            }

            if (response.body().results == null) {
                continue;
            }
            for (SyncItemResult result : response.body().results) {
                handleOpeningResult(result, entityIndex.get(result.id));
            }
        }
    }















    private void syncPendingEstimateItems() throws IOException {
        List<EstimateItemEntity> pending = filterEstimateItemsForCurrentUser(
                estimateItemDao.getByStates(PENDING_STATES));
        if (pending == null || pending.isEmpty()) {
            return;
        }



        List<List<EstimateItemEntity>> batches = partition(pending, BATCH_SIZE);

        for (List<EstimateItemEntity> batch : batches) {
            if (batch.isEmpty()) {
                continue;
            }

            String roomId = batch.get(0).projectRoomId;
            ProjectRoomEntity room = projectRoomDao.getById(roomId);
            String projectId = (room != null) ? room.projectId : "";
            syncEstimateBatch(projectId, batch);
        }
    }

















    private void syncEstimateBatch(String projectId,
                                   List<EstimateItemEntity> batch) throws IOException {

        List<EstimateItemDto> dtos = new ArrayList<>(batch.size());
        for (EstimateItemEntity entity : batch) {
            dtos.add(entityToEstimateItemDto(entity));
        }

        SyncBatchRequest request = new SyncBatchRequest(projectId, dtos);
        Response<SyncBatchResponse> response = apiService.syncEstimateItems(request).execute();

        if (!response.isSuccessful() || response.body() == null) {

            throw new IOException("Сервер вернул ошибку батч-синхронизации: "
                    + response.code());
        }

        SyncBatchResponse batchResponse = response.body();
        if (batchResponse.results == null) {
            return;
        }


        Map<String, EstimateItemEntity> entityIndex = new HashMap<>();
        for (EstimateItemEntity e : batch) {
            entityIndex.put(e.id, e);
        }

        for (SyncItemResult result : batchResponse.results) {
            if (result == null || result.id == null) {
                continue;
            }
            handleEstimateItemResult(result, entityIndex.get(result.id));
        }
    }







    private void handleEstimateItemResult(SyncItemResult result,
                                          EstimateItemEntity localEntity) {
        int status = result.status;

        if (status == 200 || status == 201) {

            long serverUpdatedAt = parseServerTimestamp(result.updatedAt);
            estimateItemDao.updateAfterSync(result.id, result.version,
                    serverUpdatedAt, "SYNCED");
            Log.d(TAG, "EstimateItem синхронизирован: id=" + result.id);

        } else if (status == 409) {

            estimateItemDao.updateSyncState(result.id, "CONFLICT");

            ConflictEntity conflict = new ConflictEntity();
            conflict.entityId     = result.id;
            conflict.entityType   = "ESTIMATE_ITEM";
            conflict.serverSnapshot = result.serverSnapshot;
            conflict.localSnapshot  = (localEntity != null) ? gson.toJson(localEntity) : null;
            conflict.detectedAt   = System.currentTimeMillis();
            conflictDao.insert(conflict);

            Log.w(TAG, "Конфликт при синхронизации EstimateItem: id=" + result.id);

        } else if (status >= 500) {

            estimateItemDao.updateSyncState(result.id, "FAILED");
            Log.e(TAG, "Серверная ошибка для EstimateItem id=" + result.id
                    + ", status=" + status);
        } else {
            Log.w(TAG, "Неожиданный статус=" + status + " для EstimateItem id=" + result.id);
        }
    }














    private void syncPendingWorkTasks() throws IOException {
        List<WorkTaskEntity> pending = filterWorkTasksForCurrentUser(
                workTaskDao.getByStates(PENDING_STATES));
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (List<WorkTaskEntity> batch : partition(pending, BATCH_SIZE)) {
            if (batch.isEmpty()) {
                continue;
            }
            String roomId = batch.get(0).projectRoomId;
            ProjectRoomEntity room = projectRoomDao.getById(roomId);
            String projectId = room != null ? room.projectId : "";

            List<WorkTaskDto> dtos = new ArrayList<>(batch.size());
            Map<String, WorkTaskEntity> entityIndex = new HashMap<>();
            for (WorkTaskEntity task : batch) {
                dtos.add(entityToWorkTaskDto(task));
                entityIndex.put(task.id, task);
            }

            Response<SyncBatchResponse> response =
                    apiService.syncWorkTasks(new SyncBatchRequest(projectId, dtos)).execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Сервер вернул ошибку синхронизации работ: "
                        + response.code());
            }

            if (response.body().results == null) {
                continue;
            }
            for (SyncItemResult result : response.body().results) {
                handleWorkTaskResult(result, entityIndex.get(result.id));
            }
        }
    }











    private ProjectDto entityToProjectDto(ProjectEntity entity) {
        ProjectDto dto = new ProjectDto();
        dto.id              = entity.id;
        dto.name            = entity.name;
        dto.city            = entity.city;
        dto.regionCode      = entity.regionCode;
        dto.taxMultiplier   = entity.taxMultiplier != null
                ? entity.taxMultiplier.toPlainString() : null;
        dto.logisticsMarkup = entity.logisticsMarkup != null
                ? entity.logisticsMarkup.toPlainString() : null;
        dto.version         = entity.version;
        return dto;
    }














    private EstimateItemDto entityToEstimateItemDto(EstimateItemEntity entity) {
        EstimateItemDto dto = new EstimateItemDto();
        dto.id              = entity.id;
        dto.projectRoomId   = entity.projectRoomId;
        dto.operation       = syncStateToOperation(entity.syncState);
        dto.version         = entity.version;
        dto.fgisCode        = entity.fgisCode;
        dto.name            = entity.name;
        dto.unitMeasure     = entity.unitMeasure;
        dto.basePrice       = entity.basePrice != null
                ? entity.basePrice.toPlainString() : null;
        dto.finalPrice      = entity.finalPrice != null
                ? entity.finalPrice.toPlainString() : null;
        dto.consumptionRate = entity.consumptionRate != null
                ? entity.consumptionRate.toPlainString() : null;
        dto.quantity        = entity.quantity != null
                ? entity.quantity.toPlainString() : null;
        dto.totalPrice      = entity.totalPrice != null
                ? entity.totalPrice.toPlainString() : null;
        dto.status          = entity.status;
        dto.calculationMethod = entity.calculationMethod;
        dto.wastePercent = entity.wastePercent != null ? entity.wastePercent.toPlainString() : null;
        dto.layers = entity.layers;
        dto.thicknessMeters = entity.thicknessMeters != null ? entity.thicknessMeters.toPlainString() : null;
        dto.manualQuantity = entity.manualQuantity != null ? entity.manualQuantity.toPlainString() : null;
        dto.coveragePerPiece = entity.coveragePerPiece != null ? entity.coveragePerPiece.toPlainString() : null;
        dto.coveragePerPackage = entity.coveragePerPackage != null ? entity.coveragePerPackage.toPlainString() : null;
        dto.packageSize = entity.packageSize != null ? entity.packageSize.toPlainString() : null;
        dto.formulaDescription = entity.formulaDescription;
        return dto;
    }

    private ProjectRoomDto entityToProjectRoomDto(ProjectRoomEntity entity) {
        ProjectRoomDto dto = new ProjectRoomDto();
        dto.id = entity.id;
        dto.projectId = entity.projectId;
        dto.operation = syncStateToOperation(entity.syncState);
        dto.name = entity.name;
        dto.length = entity.length != null ? entity.length.toPlainString() : null;
        dto.width = entity.width != null ? entity.width.toPlainString() : null;
        dto.height = entity.height != null ? entity.height.toPlainString() : null;
        dto.manualAreaOverride = entity.manualAreaOverride != null
                ? entity.manualAreaOverride.toPlainString() : null;
        dto.version = entity.version;
        return dto;
    }

    private OpeningDto entityToOpeningDto(OpeningEntity entity) {
        OpeningDto dto = new OpeningDto();
        dto.id = entity.id;
        dto.projectRoomId = entity.projectRoomId;
        dto.operation = syncStateToOperation(entity.syncState);
        dto.type = entity.type;
        dto.width = entity.width != null ? entity.width.toPlainString() : null;
        dto.height = entity.height != null ? entity.height.toPlainString() : null;
        dto.depth = entity.depth != null ? entity.depth.toPlainString() : null;
        dto.placementType = entity.placementType;
        dto.version = entity.version;
        return dto;
    }

    private WorkTaskDto entityToWorkTaskDto(WorkTaskEntity entity) {
        WorkTaskDto dto = new WorkTaskDto();
        dto.id = entity.id;
        dto.projectRoomId = entity.projectRoomId;
        dto.workerId = entity.workerId;
        dto.operation = syncStateToOperation(entity.syncState);
        dto.taskName = entity.taskName;
        dto.rateType = entity.rateType;
        dto.rateValue = entity.rateValue != null ? entity.rateValue.toPlainString() : null;
        dto.totalPayment = entity.totalPayment != null ? entity.totalPayment.toPlainString() : null;
        dto.version = entity.version;
        return dto;
    }

    private WorkerDto entityToWorkerDto(WorkerEntity entity) {
        WorkerDto dto = new WorkerDto();
        dto.id = entity.id;
        dto.userId = entity.userId;
        dto.operation = syncStateToOperation(entity.syncState);
        dto.fullName = entity.fullName;
        dto.phone = entity.phone;
        dto.specialty = entity.specialty;
        dto.version = entity.version;
        return dto;
    }

    private void handleWorkerResult(SyncItemResult result, WorkerEntity localEntity) {
        if (result == null || result.id == null) {
            return;
        }
        if (result.status == 200 || result.status == 201) {
            if (localEntity != null && "PENDING_DELETE".equals(localEntity.syncState)) {
                workerDao.deleteById(result.id);
            } else {
                workerDao.updateAfterSync(result.id, result.version,
                        parseServerTimestamp(result.updatedAt), "SYNCED");
            }
        } else if (result.status == 409) {
            workerDao.updateSyncState(result.id, "CONFLICT");
            ConflictEntity conflict = new ConflictEntity();
            conflict.entityId = result.id;
            conflict.entityType = "WORKER";
            conflict.serverSnapshot = result.serverSnapshot;
            conflict.localSnapshot = localEntity != null ? gson.toJson(localEntity) : null;
            conflict.detectedAt = System.currentTimeMillis();
            conflictDao.insert(conflict);
        } else if (result.status >= 500) {
            workerDao.updateSyncState(result.id, "FAILED");
        }
    }

    private void handleOpeningResult(SyncItemResult result, OpeningEntity localEntity) {
        if (result == null || result.id == null) {
            return;
        }
        if (result.status == 200 || result.status == 201) {
            if (localEntity != null && "PENDING_DELETE".equals(localEntity.syncState)) {
                openingDao.delete(result.id);
            } else {
                openingDao.updateAfterSync(result.id, result.version,
                        parseServerTimestamp(result.updatedAt), "SYNCED");
            }
        } else if (result.status == 409) {
            openingDao.updateSyncState(result.id, "CONFLICT");
            ConflictEntity conflict = new ConflictEntity();
            conflict.entityId = result.id;
            conflict.entityType = "OPENING";
            conflict.serverSnapshot = result.serverSnapshot;
            conflict.localSnapshot = localEntity != null ? gson.toJson(localEntity) : null;
            conflict.detectedAt = System.currentTimeMillis();
            conflictDao.insert(conflict);
        } else if (result.status >= 500) {
            openingDao.updateSyncState(result.id, "FAILED");
        }
    }

    private void handleProjectRoomResult(SyncItemResult result, ProjectRoomEntity localEntity) {
        if (result == null || result.id == null) {
            return;
        }
        if (result.status == 200 || result.status == 201) {
            if (localEntity != null && "PENDING_DELETE".equals(localEntity.syncState)) {
                projectRoomDao.deleteById(result.id);
            } else {
                projectRoomDao.updateAfterSync(result.id, result.version,
                        parseServerTimestamp(result.updatedAt), "SYNCED");
            }
        } else if (result.status == 409) {
            projectRoomDao.updateSyncState(result.id, "CONFLICT");
            ConflictEntity conflict = new ConflictEntity();
            conflict.entityId = result.id;
            conflict.entityType = "PROJECT_ROOM";
            conflict.serverSnapshot = result.serverSnapshot;
            conflict.localSnapshot = localEntity != null ? gson.toJson(localEntity) : null;
            conflict.detectedAt = System.currentTimeMillis();
            conflictDao.insert(conflict);
        } else if (result.status >= 500) {
            projectRoomDao.updateSyncState(result.id, "FAILED");
        }
    }

    private void handleWorkTaskResult(SyncItemResult result, WorkTaskEntity localEntity) {
        if (result == null || result.id == null) {
            return;
        }
        if (result.status == 200 || result.status == 201) {
            if (localEntity != null && "PENDING_DELETE".equals(localEntity.syncState)) {
                workTaskDao.deleteById(result.id);
            } else {
                workTaskDao.updateAfterSync(result.id, result.version,
                        parseServerTimestamp(result.updatedAt), "SYNCED");
            }
        } else if (result.status == 409) {
            workTaskDao.updateSyncState(result.id, "CONFLICT");
            ConflictEntity conflict = new ConflictEntity();
            conflict.entityId = result.id;
            conflict.entityType = "WORK_TASK";
            conflict.serverSnapshot = result.serverSnapshot;
            conflict.localSnapshot = localEntity != null ? gson.toJson(localEntity) : null;
            conflict.detectedAt = System.currentTimeMillis();
            conflictDao.insert(conflict);
        } else if (result.status >= 500) {
            workTaskDao.updateSyncState(result.id, "FAILED");
        }
    }







    private String syncStateToOperation(String syncState) {
        if ("PENDING_CREATE".equals(syncState)) {
            return "CREATE";
        } else if ("PENDING_DELETE".equals(syncState)) {
            return "DELETE";
        } else {
            return "UPDATE";
        }
    }











    @android.annotation.SuppressLint("NewApi")
    private long parseServerTimestamp(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

                java.time.Instant instant = java.time.Instant.parse(isoTimestamp);
                return instant.toEpochMilli();
            } else {

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date date = sdf.parse(isoTimestamp);
                return (date != null) ? date.getTime() : System.currentTimeMillis();
            }
        } catch (Exception parseException) {
            Log.w(TAG, "Не удалось распарсить timestamp: " + isoTimestamp, parseException);
            return System.currentTimeMillis();
        }
    }













    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            result.add(new ArrayList<>(list.subList(i, end)));
        }
        return result;
    }

    private List<ProjectRoomEntity> filterRoomsForCurrentUser(List<ProjectRoomEntity> rooms) {
        List<ProjectRoomEntity> result = new ArrayList<>();
        if (rooms == null) {
            return result;
        }
        for (ProjectRoomEntity room : rooms) {
            if (room != null && ownsProject(room.projectId)) {
                result.add(room);
            }
        }
        return result;
    }

    private List<EstimateItemEntity> filterEstimateItemsForCurrentUser(List<EstimateItemEntity> items) {
        List<EstimateItemEntity> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (EstimateItemEntity item : items) {
            if (item != null && ownsRoom(item.projectRoomId)) {
                result.add(item);
            }
        }
        return result;
    }

    private List<OpeningEntity> filterOpeningsForCurrentUser(List<OpeningEntity> openings) {
        List<OpeningEntity> result = new ArrayList<>();
        if (openings == null) {
            return result;
        }
        for (OpeningEntity opening : openings) {
            if (opening != null && ownsRoom(opening.projectRoomId)) {
                result.add(opening);
            }
        }
        return result;
    }

    private List<WorkTaskEntity> filterWorkTasksForCurrentUser(List<WorkTaskEntity> tasks) {
        List<WorkTaskEntity> result = new ArrayList<>();
        if (tasks == null) {
            return result;
        }
        for (WorkTaskEntity task : tasks) {
            if (task != null && ownsRoom(task.projectRoomId)) {
                result.add(task);
            }
        }
        return result;
    }

    private List<WorkerEntity> filterWorkersForCurrentUser(List<WorkerEntity> workers) {
        List<WorkerEntity> result = new ArrayList<>();
        if (workers == null) {
            return result;
        }
        for (WorkerEntity worker : workers) {
            if (worker != null && currentUserId.equals(worker.userId)) {
                result.add(worker);
            }
        }
        return result;
    }

    private boolean ownsRoom(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return false;
        }
        ProjectRoomEntity room = projectRoomDao.getById(roomId);
        return room != null && ownsProject(room.projectId);
    }

    private boolean ownsProject(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return false;
        }
        ProjectEntity project = projectDao.getById(projectId);
        return project != null && currentUserId.equals(project.userId);
    }
}
