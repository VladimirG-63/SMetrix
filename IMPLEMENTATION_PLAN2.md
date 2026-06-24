## Фаза 4: ViewModel и Бизнес-логика

> Цель: реализовать все ViewModel-классы, геометрический движок пересчёта площади и агрегацию итогов для Sticky Bottom Bar.

### 4.1 Модель отображения (Display Model)

- [ ] **4.1.1** Создать `model/EstimateItemDisplay.java`:
  - Поля: `id`, `name`, `unitMeasure`, `syncState` (String).
  - BigDecimal: `quantity`, `finalPrice`, `totalPrice`.
  - `status` (String: `NEED_TO_BUY | ORDERED | ON_SITE | UNITS_MISMATCH`).

- [ ] **4.1.2** Создать `model/RoomTotals.java`:
  - Поля BigDecimal: `materialsTotal`, `salariesTotal`, `roomTotal`.
  - Конструктор `RoomTotals(BigDecimal mat, BigDecimal sal, BigDecimal total)`.

### 4.2 Mapper: Entity → Display

- [ ] **4.2.1** Создать `model/EstimateItemMapper.java` — статический метод:
  ```java
  public static EstimateItemDisplay fromEntity(EstimateItemEntity e) { ... }
  public static List<EstimateItemDisplay> fromList(List<EstimateItemEntity> list) { ... }
  ```
  Маппер вызывается **только** в ViewModel. UI-слой никогда не видит Entity напрямую.

### 4.3 SQL-запросы для агрегации итогов

- [ ] **4.3.1** Дополнить `db/dao/EstimateItemDao.java`:
  ```java
  @Query("SELECT COALESCE(SUM(total_price), 0) FROM estimate_item WHERE project_room_id = :roomId")
  LiveData<BigDecimal> getMaterialsTotal(String roomId);

  @Query("SELECT * FROM estimate_item WHERE sync_state IN (:states)")
  List<EstimateItemEntity> getByStates(List<String> states);
  ```

- [ ] **4.3.2** Дополнить `db/dao/WorkTaskDao.java`:
  ```java
  @Query("SELECT COALESCE(SUM(total_payment), 0) FROM work_task WHERE project_room_id = :roomId")
  LiveData<BigDecimal> getSalariesTotal(String roomId);
  ```

- [ ] **4.3.3** Добавить в `db/dao/ProjectRoomDao.java` полные SQL-запросы (заменить заглушки):
  ```java
  @Query("UPDATE project_room SET length=:l, width=:w, height=:h, updated_at=:ts, version=version+1, sync_state='PENDING_UPDATE' WHERE id=:id")
  void updateDimensions(String id, BigDecimal l, BigDecimal w, BigDecimal h, long ts);

  @Query("SELECT * FROM project_room WHERE id = :id")
  ProjectRoomEntity getById(String id);

  @Query("SELECT * FROM opening WHERE project_room_id = :roomId")
  List<OpeningEntity> getOpeningsSync(String roomId);

  @Query("SELECT * FROM estimate_item WHERE project_room_id = :roomId")
  List<EstimateItemEntity> getEstimateItemsSync(String roomId);

  @Query("SELECT * FROM work_task WHERE project_room_id = :roomId AND rate_type = 'PIECEWORK'")
  List<WorkTaskEntity> getPieceworkTasksSync(String roomId);
  ```

- [ ] **4.3.4** Добавить в `db/dao/EstimateItemDao.java`:
  ```java
  @Query("UPDATE estimate_item SET quantity=:q, total_price=:tp, updated_at=:ts, sync_state='PENDING_UPDATE' WHERE id=:id")
  void updateQuantityAndTotal(String id, BigDecimal q, BigDecimal tp, long ts);

  @Query("UPDATE estimate_item SET version=:v, updated_at=:ua, sync_state=:state WHERE id=:id")
  void updateAfterSync(String id, long v, long ua, String state);

  @Query("UPDATE estimate_item SET sync_state=:state WHERE id=:id")
  void updateSyncState(String id, String state);

  @Query("SELECT * FROM estimate_item WHERE project_room_id = :roomId")
  LiveData<List<EstimateItemEntity>> getByRoomId(String roomId);
  ```

- [ ] **4.3.5** Добавить в `db/dao/WorkTaskDao.java`:
  ```java
  @Query("UPDATE work_task SET total_payment=:payment, updated_at=:ts, sync_state='PENDING_UPDATE' WHERE id=:id")
  void updatePayment(String id, BigDecimal payment, long ts);

  @Query("UPDATE work_task SET sync_state=:state WHERE id=:id")
  void updateSyncState(String id, String state);

  @Query("SELECT * FROM work_task WHERE sync_state IN (:states)")
  List<WorkTaskEntity> getByStates(List<String> states);
  ```

- [ ] **4.3.6** Добавить в `db/dao/ProjectDao.java` полные SQL-запросы:
  ```java
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(ProjectEntity entity);

  @Update
  void update(ProjectEntity entity);

  @Query("SELECT * FROM project WHERE user_id=:userId AND deleted_at IS NULL")
  LiveData<List<ProjectEntity>> getActiveProjects(String userId);

  @Query("UPDATE project SET deleted_at=:deletedAt, sync_state='PENDING_DELETE' WHERE id=:id")
  void markDeleted(String id, long deletedAt);

  @Query("SELECT * FROM project WHERE sync_state IN (:states)")
  List<ProjectEntity> getByStates(List<String> states);
  ```

- [ ] **4.3.7** Добавить в `db/dao/MaterialsCacheDao.java`:
  ```java
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insertOrReplace(MaterialsCacheEntity entity);

  @Query("SELECT * FROM materials_cache WHERE name LIKE '%' || :q || '%' ORDER BY priority_score DESC LIMIT 30")
  LiveData<List<MaterialsCacheEntity>> searchLocal(String q);
  ```

- [ ] **4.3.8** Добавить в `db/dao/ConflictDao.java`:
  ```java
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(ConflictEntity entity);

  @Query("DELETE FROM conflict WHERE entity_id = :entityId")
  void delete(String entityId);

  @Query("SELECT * FROM conflict")
  LiveData<List<ConflictEntity>> getAll();
  ```

- [ ] **4.3.9** Добавить в `db/dao/WorkerDao.java`:
  ```java
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(WorkerEntity entity);

  @Update
  void update(WorkerEntity entity);

  @Query("SELECT * FROM worker WHERE user_id = :userId")
  LiveData<List<WorkerEntity>> getAll(String userId);

  @Query("SELECT * FROM worker WHERE sync_state IN (:states)")
  List<WorkerEntity> getByStates(List<String> states);
  ```

- [ ] **4.3.10** Добавить в `db/dao/OpeningDao.java`:
  ```java
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(OpeningEntity entity);

  @Query("DELETE FROM opening WHERE id = :id")
  void delete(String id);

  @Query("SELECT * FROM opening WHERE sync_state IN (:states)")
  List<OpeningEntity> getByStates(List<String> states);
  ```

- [ ] **4.3.11** Пересобрать проект — убедиться, что все DAO компилируются без ошибок.

### 4.4 SQL-запрос для глобального статуса синхронизации

- [ ] **4.4.1** Создать `db/dao/SyncStatusDao.java` — `@Dao`-интерфейс:
  ```java
  @Dao
  public interface SyncStatusDao {
      @Query("SELECT " +
             "  CASE " +
             "    WHEN COUNT(*) FILTER (WHERE sync_state = 'CONFLICT') > 0 THEN 'CONFLICT' " +
             "    WHEN COUNT(*) FILTER (WHERE sync_state = 'FAILED')   > 0 THEN 'FAILED' " +
             "    WHEN COUNT(*) FILTER (WHERE sync_state LIKE 'PENDING%') > 0 THEN 'PENDING' " +
             "    ELSE 'SYNCED' " +
             "  END AS globalStatus, " +
             "  COUNT(*) FILTER (WHERE sync_state = 'CONFLICT') AS conflictCount " +
             "FROM (" +
             "  SELECT sync_state FROM project UNION ALL " +
             "  SELECT sync_state FROM project_room UNION ALL " +
             "  SELECT sync_state FROM estimate_item UNION ALL " +
             "  SELECT sync_state FROM work_task" +
             ")")
      LiveData<SyncStatusResult> getGlobalSyncStatus();
  }
  ```

- [ ] **4.4.2** Создать `model/SyncStatusResult.java` — POJO с полями `String globalStatus` и `int conflictCount`. Аннотировать `@ColumnInfo`.

- [ ] **4.4.3** Добавить `SyncStatusDao` в `AppDatabase`. Пересобрать проект.

### 4.5 SyncManager (network/sync/)

- [ ] **4.5.1** Создать `network/sync/SyncManager.java`:
  - Конструктор принимает `WorkManager workManager`.
  - Метод `scheduleSync()` — `OneTimeWorkRequest` с `NetworkType.CONNECTED`, `BackoffPolicy.EXPONENTIAL` (30 сек), `ExistingWorkPolicy.KEEP`, тег `"SYNC_ONE_TIME"`.
  - Метод `scheduleSyncForce()` — то же самое, но `ExistingWorkPolicy.REPLACE` (для кнопки «Синхронизировать»).
  - Метод `schedulePeriodicSync()` — `PeriodicWorkRequest` 15 минут, `ExistingPeriodicWorkPolicy.KEEP`, тег `"SYNC_PERIODIC"`.

### 4.6 ProjectListViewModel (viewmodel/)

- [ ] **4.6.1** Создать `viewmodel/ProjectListViewModel.java` extends `AndroidViewModel`:
  - Поле `LiveData<List<ProjectEntity>> projects` — из `ProjectRepository.getActiveProjects(userId)`.
  - Метод `createProject(String name, String city, String regionCode, BigDecimal taxMultiplier, BigDecimal logisticsMarkup)` — делегирует в `ProjectRepository`.
  - Метод `deleteProject(String id)` — делегирует `ProjectRepository.softDelete(id)`.
  - `userId` получать из `EncryptedSharedPreferences` (ключ `"user_id"`).

### 4.7 RoomDetailViewModel (viewmodel/)

- [ ] **4.7.1** Создать `viewmodel/RoomDetailViewModel.java` extends `ViewModel`:
  - Поле `LiveData<List<EstimateItemDisplay>> estimateItems` — через `Transformations.map()` из `EstimateRepository.getItemsByRoom(roomId)` → `EstimateItemMapper.fromList()`.
  - Поле `MediatorLiveData<RoomTotals> roomTotals` — объединяет `materialsTotalLd` и `salariesTotalLd`.
  - Метод `recalcTotals()` — вызывается из обоих `addSource()`. Использует `BigDecimal.add().setScale(0, HALF_UP)`.
  - Метод `updateDimensions(String roomId, BigDecimal l, BigDecimal w, BigDecimal h)` — выполняет через `AppExecutors.diskIO()`, делегирует `RoomRepository.updateDimensionsAndRecalculate()`.
  - Метод `boolean hasEstimateItems()` — возвращает `estimateItems.getValue() != null && !estimateItems.getValue().isEmpty()`.

- [ ] **4.7.2** Добавить в `RoomDetailViewModel` метод `addEstimateItem(...)`:
  - Параметры: `roomId`, `fgisCode`, `name`, `unitMeasure`, `basePrice`, `finalPrice`, `consumptionRate`.
  - Получить `effectiveArea` из текущей комнаты (через `RoomRepository` или кэш ViewModel).
  - Делегирует `EstimateRepository.addEstimateItem(...)`.
  - Ловит `ValidationException` → выставляет `MutableLiveData<String> errorMessage`.

### 4.8 WorkerViewModel (viewmodel/)

- [ ] **4.8.1** Создать `viewmodel/WorkerViewModel.java` extends `ViewModel`:
  - Поле `LiveData<List<WorkerEntity>> workers` — из `WorkerRepository.getWorkers(userId)`.
  - Метод `saveWorker(String fullName, String phone, String specialty)` — делегирует репозиторию, ловит `ValidationException` и `DuplicateEntityException`, выставляет `errorMessage`.

### 4.9 SyncViewModel (viewmodel/)

- [ ] **4.9.1** Создать `viewmodel/SyncViewModel.java` extends `AndroidViewModel`:
  - Поле `LiveData<SyncStatusResult> syncStatus` — из `SyncStatusDao.getGlobalSyncStatus()`.
  - Метод `forceSyncNow()` — вызывает `SyncManager.scheduleSyncForce()`.
  - Поле `LiveData<List<ConflictEntity>> conflicts` — из `ConflictDao.getAll()`.

### 4.10 Финальная проверка Фазы 4

- [ ] **4.10.1** `Build → Make Project` — ошибок быть не должно.
- [ ] **4.10.2** Написать JUnit-тест `RoomDetailViewModelTest`:
  - Проверить, что `recalcTotals()` корректно суммирует `materialsTotal + salariesTotal`.
  - Использовать `InstantTaskExecutorRule` для LiveData.
- [ ] **4.10.3** Написать JUnit-тест на геометрический движок (через `RoomRepository`):
  - `roughArea = (3 + 2) × 2 × 2.5 = 25.0`
  - `openingsArea = 0.9 × 2.0 = 1.8`
  - `netArea = 25.0 − 1.8 = 23.2`
  - Проверить scale: `quantity.scale() == 6`, `totalPrice.scale() == 2`.

---

## Фаза 5: Сеть и Синхронизация

> Цель: реализовать Retrofit-клиент, DTO-классы, AuthInterceptor, SyncWorker и полную логику разрешения 409 Conflict.

### 5.1 DTO-классы (network/dto/)

- [ ] **5.1.1** Создать `network/dto/ProjectDto.java`:
  - Поля (snake_case через `@SerializedName`): `id`, `name`, `city`, `regionCode`, `taxMultiplier` (String), `logisticsMarkup` (String), `version` (long), `createdAt` (String), `updatedAt` (String), `deletedAt` (String nullable).
  - BigDecimal-поля передаются как **String** в JSON (сохраняет точность, см. RFC Часть 2 §2.1).

- [ ] **5.1.2** Создать `network/dto/ProjectRoomDto.java`:
  - Поля: `id`, `projectId`, `name`, `length`, `width`, `height`, `manualAreaOverride` (все BigDecimal как String), `version`, `createdAt`, `updatedAt`.

- [ ] **5.1.3** Создать `network/dto/EstimateItemDto.java`:
  - Поля: `id`, `projectRoomId`, `operation` (String: `"CREATE" | "UPDATE" | "DELETE"`), `version`, `fgisCode`, `name`, `unitMeasure`, `basePrice`, `finalPrice`, `consumptionRate`, `quantity`, `totalPrice` (BigDecimal как String), `status`, `createdAt`, `updatedAt`.

- [ ] **5.1.4** Создать `network/dto/SyncBatchRequest.java`:
  - Поля: `projectId` (String), `items` (`List<EstimateItemDto>`).

- [ ] **5.1.5** Создать `network/dto/SyncItemResult.java`:
  - Поля: `id` (String), `status` (int), `version` (long), `updatedAt` (String), `errorCode` (String nullable), `serverSnapshot` (String nullable — raw JSON).

- [ ] **5.1.6** Создать `network/dto/SyncBatchResponse.java`:
  - Поля: `results` (`List<SyncItemResult>`), `batchSize` (int), `processedAt` (String).

- [ ] **5.1.7** Создать `network/dto/TokenResponse.java`:
  - Поля: `accessToken`, `refreshToken` (String), `accessTokenExpiresIn`, `refreshTokenExpiresIn` (long).

- [ ] **5.1.8** Создать `network/dto/MaterialDto.java`:
  - Поля: `fgisCode`, `name`, `unitMeasure`, `basePrice` (String), `regionCode`, `quarter`.

- [ ] **5.1.9** Создать `network/dto/MaterialSearchResponse.java`:
  - Поля: `items` (`List<MaterialDto>`), `total` (int), `limit` (int), `offset` (int).

### 5.2 Retrofit API-интерфейс (network/)

- [ ] **5.2.1** Создать `network/ApiService.java` — `@interface`:
  ```java
  @POST("projects")
  Call<ProjectDto> createProject(@Body ProjectDto dto);

  @DELETE("projects/{id}")
  Call<Void> deleteProject(@Path("id") String id, @Body Map<String, Long> body);

  @POST("estimate-items/sync")
  Call<SyncBatchResponse> syncEstimateItems(@Body SyncBatchRequest request);

  @POST("project-rooms/sync")
  Call<SyncBatchResponse> syncProjectRooms(@Body SyncBatchRequest request);

  @GET("materials")
  Call<MaterialSearchResponse> searchMaterials(
      @Query("q") String query,
      @Query("region") String regionCode,
      @Query("limit") int limit,
      @Query("offset") int offset);

  @POST("auth/refresh")
  Call<TokenResponse> refreshToken(@Body Map<String, String> body);
  ```

### 5.3 AuthInterceptor (network/)

- [ ] **5.3.1** Создать `network/AuthInterceptor.java` implements `Interceptor`:
  - В методе `intercept(Chain chain)`:
    1. Добавить хедер `Authorization: Bearer {accessToken}` из `EncryptedSharedPreferences`.
    2. Добавить `Content-Type: application/json; charset=utf-8` и `Accept-Charset: utf-8`.
    3. Выполнить запрос.
    4. Если `response.code() == 401` — войти в `synchronized(this)` блок:
       - Вызвать `POST /auth/refresh` синхронно.
       - Если успех — сохранить новые токены в `EncryptedSharedPreferences`, повторить исходный запрос.
       - Если ошибка (null или 401) — вызвать `broadcastLogout()` через `LocalBroadcastManager`.
  - **Правило**: пустые `catch`-блоки запрещены. Ловить `IOException` отдельно от `Exception`.

### 5.4 ApiClient (network/)

- [ ] **5.4.1** Создать `network/ApiClient.java` — singleton-класс:
  - Собрать `OkHttpClient` с `AuthInterceptor` и `HttpLoggingInterceptor` (уровень `BODY` только для `debug`).
  - Собрать `Retrofit` с `GsonConverterFactory` и `baseUrl(BuildConfig.API_BASE_URL)`.
  - Метод `static ApiService getService()` — возвращает `ApiService` через `retrofit.create()`.
  - Хранить экземпляр в `private static volatile ApiClient INSTANCE` (double-checked locking).

### 5.5 SyncWorker — полная реализация (network/sync/)

- [ ] **5.5.1** Создать `network/sync/SyncWorker.java` extends `Worker`:
  - Конструктор: получать `AppDatabase` и `ApiService` через `WorkerParameters` (через `WorkerFactory` или статический доступ).
  - Метод `doWork()`:
    - Вызвать последовательно: `syncPendingProjects()`, `syncPendingRooms()`, `syncPendingEstimateItems()`, `syncPendingWorkTasks()`.
    - `catch (IOException e)` → `return Result.retry()` (сетевая ошибка — WorkManager повторит с backoff).
    - `catch (Exception e)` → логировать через `Log.e`, `return Result.failure()`.

- [ ] **5.5.2** Реализовать `syncPendingEstimateItems()` в `SyncWorker`:
  - Получить список с `estimateItemDao.getByStates(List.of("PENDING_CREATE","PENDING_UPDATE","PENDING_DELETE"))`.
  - Разбить на батчи по 50 через `Lists.partition(pending, 50)`.
  - Для каждого батча вызвать `syncBatch(batch)` внутри `try/catch`:
    - `catch` конфликта одной записи **не откатывает весь батч** — обрабатывать независимо.

- [ ] **5.5.3** Реализовать `syncBatch(List<EstimateItemEntity> batch)` в `SyncWorker`:
  - Маппить Entity → `EstimateItemDto` (поле `operation` из `syncState`: `PENDING_CREATE→"CREATE"` и т.д.).
  - Вызвать `apiService.syncEstimateItems(request).execute()`.
  - Итерировать `response.body().results`:
    - `status 200 | 201` → `estimateItemDao.updateAfterSync(id, version, updatedAt, "SYNCED")`.
    - `status 409` → `estimateItemDao.updateSyncState(id, "CONFLICT")` + `conflictDao.insert(new ConflictEntity(...))`.
    - `status >= 500` → `estimateItemDao.updateSyncState(id, "FAILED")`.

- [ ] **5.5.4** Реализовать `syncPendingProjects()` в `SyncWorker`:
  - Получить `projectDao.getByStates(List.of("PENDING_CREATE","PENDING_DELETE"))`.
  - `PENDING_CREATE` → `POST /projects` → при 201 обновить `version`, `syncState = SYNCED`.
  - `PENDING_DELETE` → `DELETE /projects/{id}` с телом `{"version": N}` → при 200 физически не удалять (soft delete), `syncState = SYNCED`.

- [ ] **5.5.5** Аналогично реализовать `syncPendingRooms()` и `syncPendingWorkTasks()` по той же схеме.

### 5.6 Логика разрешения 409 Conflict

- [ ] **5.6.1** Создать `repository/ConflictRepository.java`:
  - Конструктор: `ConflictDao conflictDao`, `EstimateItemDao estimateItemDao`, `SyncManager syncManager`.
  - Метод `LiveData<List<ConflictEntity>> getAllConflicts()` — делегирует `conflictDao.getAll()`.
  - Метод `void resolveKeepLocal(String entityId, long serverVersion)`:
    1. Получить локальный entity из `EstimateItemDao` по id.
    2. `estimateItemDao.bumpVersionAndMarkPending(entityId, serverVersion + 1)` — обновить `version` и `syncState = PENDING_UPDATE`.
    3. `conflictDao.delete(entityId)`.
    4. `syncManager.scheduleSync()`.
  - Метод `void resolveAcceptServer(String entityId, String serverSnapshotJson)`:
    1. Десериализовать `serverSnapshotJson` через `Gson` в `EstimateItemEntity`.
    2. `estimateItemDao` — перезаписать запись (`syncState = SYNCED`).
    3. `conflictDao.delete(entityId)`.

- [ ] **5.6.2** Добавить в `EstimateItemDao`:
  ```java
  @Query("UPDATE estimate_item SET version=:newVersion, sync_state='PENDING_UPDATE', updated_at=:ts WHERE id=:id")
  void bumpVersionAndMarkPending(String id, long newVersion, long ts);
  ```

### 5.7 MaterialRepository — сетевой поиск

- [ ] **5.7.1** Дополнить `repository/MaterialRepository.java`:
  - Конструктор добавить `ApiService apiService`.
  - Метод `void searchRemote(String query, String regionCode, Callback<MaterialSearchResponse> cb)`:
    - Вызвать `apiService.searchMaterials(query, regionCode, 20, 0).enqueue(cb)`.
    - В `onResponse` — маппить `MaterialDto` → `MaterialsCacheEntity`, вызвать `cacheFromServer()`.
    - В `onFailure` — логировать `Log.w`, не выбрасывать исключение наружу.

### 5.8 Финальная проверка Фазы 5

- [ ] **5.8.1** `Build → Make Project` — ошибок быть не должно.
- [ ] **5.8.2** Написать JUnit-тест на `SyncWorker` (мокировать `ApiService` через Mockito):
  - Сценарий 1: сервер возвращает `status 201` → `syncState` становится `SYNCED`.
  - Сценарий 2: сервер возвращает `status 409` → `syncState` становится `CONFLICT`, `ConflictEntity` записывается в DAO.
  - Сценарий 3: `IOException` → метод `doWork()` возвращает `Result.retry()`.
- [ ] **5.8.3** Проверить вручную: запустить приложение в debug, создать запись, убедиться, что `SyncWorker` запускается и `syncState` меняется через `WorkManager Inspector` в Android Studio.

---
