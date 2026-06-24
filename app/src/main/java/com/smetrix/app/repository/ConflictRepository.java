// app/src/main/java/com/smetrix/app/repository/ConflictRepository.java
package com.smetrix.app.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.dao.ConflictDao;
import com.smetrix.app.db.dao.EstimateItemDao;
import com.smetrix.app.db.dao.OpeningDao;
import com.smetrix.app.db.dao.ProjectDao;
import com.smetrix.app.db.dao.ProjectRoomDao;
import com.smetrix.app.db.dao.WorkerDao;
import com.smetrix.app.db.dao.WorkTaskDao;
import com.smetrix.app.db.entity.ConflictEntity;
import com.smetrix.app.db.entity.EstimateItemEntity;
import com.smetrix.app.db.entity.OpeningEntity;
import com.smetrix.app.db.entity.ProjectEntity;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.db.entity.WorkerEntity;
import com.smetrix.app.db.entity.WorkTaskEntity;
import com.smetrix.app.network.sync.SyncManager;

import java.util.List;

/**
 * Репозиторий для управления конфликтами синхронизации.
 *
 * <p><b>Архитектурная роль (Clean Architecture):</b><br>
 * {@code ConflictRepository} является посредником между DAO-слоем
 * ({@link ConflictDao}, {@link EstimateItemDao}) и ViewModel.
 * Инкапсулирует бизнес-логику разрешения конфликтов, скрывая детали
 * работы с базой данных и сетью от вышестоящих слоёв.
 *
 * <p><b>Сценарии возникновения конфликта:</b><br>
 * Конфликт возникает, когда одна и та же запись была изменена одновременно
 * на клиенте (offline) и на сервере другим устройством. При синхронизации
 * {@link com.smetrix.app.network.sync.SyncWorker} получает ответ HTTP 409
 * и записывает конфликт в таблицу {@code conflict} через {@link ConflictDao}.
 *
 * <p><b>Два варианта разрешения конфликта:</b>
 * <ol>
 *   <li>{@link #resolveKeepLocal} — оставить локальную версию, отправить её
 *       на сервер повторно с bumped-версией.</li>
 *   <li>{@link #resolveAcceptServer} — принять серверную версию, перезаписать
 *       локальную запись данными из {@code serverSnapshotJson}.</li>
 * </ol>
 *
 * <p><b>Важно:</b> все операции с БД выполняются синхронно. Вызывающий код
 * (ViewModel/Activity) обязан запускать эти методы через
 * {@link AppExecutors#diskIO()}, а не на Main Thread.
 */
public class ConflictRepository {

    private static final String TAG = "ConflictRepository";

    // ─── Зависимости ─────────────────────────────────────────────────────────

    /** DAO для таблицы «conflict». Используется для чтения и удаления конфликтов. */
    private final ConflictDao conflictDao;
    private final AppDatabase database;

    /** DAO для таблицы «estimate_item». Используется при разрешении конфликтов. */
    private final EstimateItemDao estimateItemDao;
    private final ProjectDao projectDao;
    private final ProjectRoomDao projectRoomDao;
    private final OpeningDao openingDao;
    private final WorkerDao workerDao;
    private final WorkTaskDao workTaskDao;

    /** Менеджер синхронизации. Запускает новую задачу после разрешения конфликта. */
    private final SyncManager syncManager;

    /** Gson для десериализации serverSnapshotJson → EstimateItemEntity. */
    private final Gson gson;

    // ─── Конструктор ─────────────────────────────────────────────────────────

    /**
     * Создаёт репозиторий конфликтов.
     *
     * @param conflictDao     DAO таблицы «conflict».
     * @param estimateItemDao DAO таблицы «estimate_item».
     * @param syncManager     менеджер синхронизации WorkManager.
     */
    public ConflictRepository(AppDatabase database,
                              ConflictDao conflictDao,
                              EstimateItemDao estimateItemDao,
                              ProjectDao projectDao,
                              ProjectRoomDao projectRoomDao,
                              OpeningDao openingDao,
                              WorkerDao workerDao,
                              WorkTaskDao workTaskDao,
                              SyncManager syncManager) {
        this.database        = database;
        this.conflictDao     = conflictDao;
        this.estimateItemDao = estimateItemDao;
        this.projectDao      = projectDao;
        this.projectRoomDao  = projectRoomDao;
        this.openingDao      = openingDao;
        this.workerDao       = workerDao;
        this.workTaskDao     = workTaskDao;
        this.syncManager     = syncManager;
        this.gson            = new Gson();
    }

    // ─── Публичные методы ────────────────────────────────────────────────────

    /**
     * Возвращает реактивный список всех неразрешённых конфликтов.
     *
     * <p>UI подписывается на этот {@code LiveData} и обновляется автоматически
     * при появлении новых конфликтов или разрешении существующих.
     *
     * @return {@code LiveData} со списком всех записей таблицы «conflict».
     */
    public LiveData<List<ConflictEntity>> getAllConflicts() {
        return conflictDao.getAll();
    }

    /**
     * Разрешает конфликт, оставляя локальную версию записи.
     *
     * <p><b>Алгоритм:</b>
     * <ol>
     *   <li>Устанавливает версию записи = {@code serverVersion + 1}, чтобы
     *       сервер принял нашу версию как более новую при повторной синхронизации.</li>
     *   <li>Помечает запись как {@code PENDING_UPDATE} — чтобы SyncWorker
     *       отправил её на сервер при следующей синхронизации.</li>
     *   <li>Удаляет запись из таблицы «conflict».</li>
     *   <li>Планирует немедленную синхронизацию через WorkManager.</li>
     * </ol>
     *
     * <p><b>Важно:</b> вызывать только из фонового потока ({@link AppExecutors#diskIO()}).
     *
     * @param entityId      id конфликтующей записи (первичный ключ таблицы «conflict»).
     * @param serverVersion версия записи на сервере, полученная из {@link ConflictEntity#serverSnapshot}.
     */
    public void resolveKeepLocal(String entityId, long serverVersion) {
        ConflictEntity conflict = requireConflict(entityId);
        if (conflict == null) {
            return;
        }
        long newVersion = serverVersion + 1;
        long now        = System.currentTimeMillis();

        final boolean[] resolved = {false};
        database.runInTransaction(() -> {
            resolved[0] = markLocalPending(conflict.entityType, entityId, newVersion, now);
            if (resolved[0]) {
                conflictDao.delete(entityId);
            }
        });
        if (!resolved[0]) return;

        // Запускаем синхронизацию — SyncWorker заберёт PENDING_UPDATE и отправит.
        syncManager.scheduleSync();

        Log.d(TAG, "Конфликт разрешён (локальная версия): entityId=" + entityId
                + ", newVersion=" + newVersion);
    }

    /**
     * Разрешает конфликт, принимая серверную версию записи.
     *
     * <p><b>Алгоритм:</b>
     * <ol>
     *   <li>Десериализует {@code serverSnapshotJson} в {@link EstimateItemEntity}
     *       через Gson.</li>
     *   <li>Устанавливает {@code syncState = SYNCED} в десериализованной сущности.</li>
     *   <li>Сохраняет сущность через {@code insert(REPLACE)} — полностью перезаписывает
     *       локальную запись серверными данными.</li>
     *   <li>Удаляет запись из таблицы «conflict».</li>
     * </ol>
     *
     * <p><b>Важно:</b> вызывать только из фонового потока ({@link AppExecutors#diskIO()}).
     *
     * @param entityId           id конфликтующей записи.
     * @param serverSnapshotJson raw JSON серверной версии из {@link ConflictEntity#serverSnapshot}.
     */
    public void resolveAcceptServer(String entityId, String serverSnapshotJson) {
        if (serverSnapshotJson == null || serverSnapshotJson.isEmpty()) {
            Log.e(TAG, "resolveAcceptServer: serverSnapshotJson пустой для entityId=" + entityId);
            return;
        }

        try {
            ConflictEntity conflict = requireConflict(entityId);
            if (conflict == null) {
                return;
            }

            final boolean[] resolved = {false};
            database.runInTransaction(() -> {
                resolved[0] = applyServerSnapshot(
                        conflict.entityType, entityId, serverSnapshotJson);
                if (resolved[0]) {
                    conflictDao.delete(entityId);
                }
            });
            if (!resolved[0]) return;

            Log.d(TAG, "Конфликт разрешён (серверная версия принята): entityId=" + entityId);

        } catch (JsonSyntaxException jsonException) {
            Log.e(TAG, "resolveAcceptServer: ошибка парсинга JSON для entityId=" + entityId,
                    jsonException);
        }
    }

    private ConflictEntity requireConflict(String entityId) {
        ConflictEntity conflict = conflictDao.getById(entityId);
        if (conflict == null || conflict.entityType == null) {
            Log.e(TAG, "Конфликт не найден или не содержит тип: entityId=" + entityId);
            return null;
        }
        return conflict;
    }

    private boolean markLocalPending(String entityType, String entityId,
                                     long version, long updatedAt) {
        switch (entityType) {
            case "PROJECT":
                projectDao.bumpVersionAndMarkPending(entityId, version, updatedAt);
                return true;
            case "PROJECT_ROOM":
                projectRoomDao.bumpVersionAndMarkPending(entityId, version, updatedAt);
                return true;
            case "OPENING":
                openingDao.bumpVersionAndMarkPending(entityId, version, updatedAt);
                return true;
            case "ESTIMATE_ITEM":
                estimateItemDao.bumpVersionAndMarkPending(entityId, version, updatedAt);
                return true;
            case "WORKER":
                workerDao.bumpVersionAndMarkPending(entityId, version, updatedAt);
                return true;
            case "WORK_TASK":
                workTaskDao.bumpVersionAndMarkPending(entityId, version, updatedAt);
                return true;
            default:
                Log.e(TAG, "Неизвестный тип конфликта: " + entityType);
                return false;
        }
    }

    private boolean applyServerSnapshot(String entityType, String entityId, String json) {
        switch (entityType) {
            case "PROJECT": {
                ProjectEntity entity = gson.fromJson(json, ProjectEntity.class);
                if (!isExpectedId(entityId, entity != null ? entity.id : null)) return false;
                entity.syncState = "SYNCED";
                projectDao.upsertFromSync(entity);
                return true;
            }
            case "PROJECT_ROOM": {
                ProjectRoomEntity entity = gson.fromJson(json, ProjectRoomEntity.class);
                if (!isExpectedId(entityId, entity != null ? entity.id : null)) return false;
                entity.syncState = "SYNCED";
                projectRoomDao.upsertFromSync(entity);
                return true;
            }
            case "OPENING": {
                OpeningEntity entity = gson.fromJson(json, OpeningEntity.class);
                if (!isExpectedId(entityId, entity != null ? entity.id : null)) return false;
                entity.syncState = "SYNCED";
                openingDao.upsertFromSync(entity);
                return true;
            }
            case "ESTIMATE_ITEM": {
                EstimateItemEntity entity = gson.fromJson(json, EstimateItemEntity.class);
                if (!isExpectedId(entityId, entity != null ? entity.id : null)) return false;
                entity.syncState = "SYNCED";
                estimateItemDao.insert(entity);
                return true;
            }
            case "WORKER": {
                WorkerEntity entity = gson.fromJson(json, WorkerEntity.class);
                if (!isExpectedId(entityId, entity != null ? entity.id : null)) return false;
                entity.syncState = "SYNCED";
                workerDao.upsertFromSync(entity);
                return true;
            }
            case "WORK_TASK": {
                WorkTaskEntity entity = gson.fromJson(json, WorkTaskEntity.class);
                if (!isExpectedId(entityId, entity != null ? entity.id : null)) return false;
                entity.syncState = "SYNCED";
                workTaskDao.upsertFromSync(entity);
                return true;
            }
            default:
                Log.e(TAG, "Неподдерживаемый тип серверного снимка: " + entityType);
                return false;
        }
    }

    private boolean isExpectedId(String expected, String actual) {
        boolean matches = expected != null && expected.equals(actual);
        if (!matches) {
            Log.e(TAG, "ID серверного снимка не совпадает с конфликтом: expected="
                    + expected + ", actual=" + actual);
        }
        return matches;
    }
}
