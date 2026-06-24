# IMPLEMENTATION_PLAN.md — Smetrix Android

> Основан на RFC: `docs/RFC_Part1_Data_And_Packages.md`, `RFC_Part2_Sync_And_API.md`,
> `RFC_Part3_UI_And_Recalc.md`, `RFC_Part4_Security_And_Final.md`
>
> Язык: **Java only**. Layouts: **XML only**. Архитектура: **Clean Architecture (UI / ViewModel / Repository / DAO)**.

---

## Фаза 1: Setup

> Цель: подготовить `build.gradle` со всеми зависимостями и базовую конфигурацию проекта.

- [ ] **1.1** Открыть `build.gradle (app)`. Добавить версии зависимостей в блок `dependencies`:
  - `androidx.room:room-runtime` + `androidx.room:room-compiler` (annotationProcessor)
  - `androidx.room:room-testing` (для тестов)

- [ ] **1.2** Добавить в `dependencies` Retrofit-стек:
  - `com.squareup.retrofit2:retrofit`
  - `com.squareup.retrofit2:converter-gson`
  - `com.squareup.okhttp3:okhttp`
  - `com.squareup.okhttp3:logging-interceptor`

- [ ] **1.3** Добавить в `dependencies` WorkManager:
  - `androidx.work:work-runtime`

- [ ] **1.4** Добавить в `dependencies` Security-Crypto (EncryptedSharedPreferences):
  - `androidx.security:security-crypto`

- [ ] **1.5** Добавить в `dependencies` вспомогательные библиотеки:
  - `com.google.guava:guava` (для `Lists.partition()` в `SyncWorker`)
  - `androidx.lifecycle:lifecycle-livedata` + `lifecycle-viewmodel`

- [ ] **1.6** В блоке `android { buildTypes { ... } }` прописать `buildConfigField` для `API_BASE_URL`:
  - `debug` → `"https://api-dev.smetrix.ru/api/v1/"`
  - `release` → `"https://api.smetrix.ru/api/v1/"`
  - Убедиться, что `buildFeatures { buildConfig = true }` включён.

- [ ] **1.7** Включить `annotationProcessor` для Room в `build.gradle`:
  ```groovy
  annotationProcessor "androidx.room:room-compiler:<version>"
  ```

- [ ] **1.8** Синхронизировать проект (`Sync Now`) и убедиться, что сборка проходит без ошибок.

- [ ] **1.9** Создать структуру пакетов вручную (пустые папки) согласно RFC Часть 1, §2.1:
  - `com.smetrix.app.db.converter`
  - `com.smetrix.app.db.entity`
  - `com.smetrix.app.db.dao`
  - `com.smetrix.app.model`
  - `com.smetrix.app.repository`
  - `com.smetrix.app.network.dto`
  - `com.smetrix.app.network.sync`
  - `com.smetrix.app.viewmodel`
  - `com.smetrix.app.ui.project`, `.ui.room`, `.ui.worker`, `.ui.common`

---

## Фаза 2: База данных (Room)

> Цель: создать все Entity-классы, TypeConverter-ы и ENUM-ы. На выходе — компилирующийся граф таблиц без полных DAO и AppDatabase.

### 2.1 ENUM-ы (model/)

- [ ] **2.1.1** Создать `model/SyncState.java`:
  ```
  SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, FAILED, CONFLICT
  ```

- [ ] **2.1.2** Создать `model/ItemStatus.java`:
  ```
  NEED_TO_BUY, ORDERED, ON_SITE, UNITS_MISMATCH
  ```

### 2.2 TypeConverter (db/converter/)

- [ ] **2.2.1** Создать `db/converter/BigDecimalConverter.java`:
  - Метод `fromBigDecimal(BigDecimal) → String` через `toPlainString()`, null-safe.
  - Метод `toBigDecimal(String) → BigDecimal` через `new BigDecimal(value)`, null-safe.
  - Аннотировать оба метода `@TypeConverter`.

### 2.3 Entity: Project (db/entity/)

- [ ] **2.3.1** Создать `db/entity/ProjectEntity.java`.
  - Аннотация `@Entity(tableName = "project")` с тремя индексами: `user_id`, `deleted_at`, `sync_state`.
  - Поля: `id` (String, `@PrimaryKey @NonNull`), `userId`, `name`, `city`, `regionCode`.
  - BigDecimal: `taxMultiplier`, `logisticsMarkup`.
  - Sync-поля: `deletedAt` (Long, nullable), `lastSyncedAt` (Long), `createdAt` (long), `updatedAt` (long), `version` (long), `syncState` (String).

### 2.4 Entity: ProjectRoom

- [ ] **2.4.1** Создать `db/entity/ProjectRoomEntity.java`.
  - `@Entity(tableName = "project_room")` с `@ForeignKey` → `ProjectEntity`, `onDelete = CASCADE`.
  - `@Index` на `project_id`.
  - Поля: `id`, `projectId`, `name`.
  - BigDecimal: `length`, `width`, `height`, `manualAreaOverride` (nullable).
  - Sync-поля: `createdAt`, `updatedAt`, `version`, `syncState`.

### 2.5 Entity: Opening

- [ ] **2.5.1** Создать `db/entity/OpeningEntity.java`.
  - `@Entity(tableName = "opening")` с `@ForeignKey` → `ProjectRoomEntity`, `onDelete = CASCADE`.
  - `@Index` на `project_room_id`.
  - Поля: `id`, `projectRoomId`, `type` (String: "DOOR" | "WINDOW").
  - BigDecimal: `width`, `height`.
  - Sync-поля: `createdAt`, `updatedAt`, `version`, `syncState`.

### 2.6 Entity: EstimateItem

- [ ] **2.6.1** Создать `db/entity/EstimateItemEntity.java`.
  - `@Entity(tableName = "estimate_item")` с `@ForeignKey` → `ProjectRoomEntity`, `onDelete = CASCADE`.
  - `@Index` на `project_room_id`.
  - Поля: `id`, `projectRoomId`, `fgisCode` (nullable), `name`, `unitMeasure`.
  - BigDecimal (все ≥ 0): `basePrice`, `finalPrice`, `consumptionRate`, `quantity`, `totalPrice`.
  - `status` (String, значения из `ItemStatus`).
  - Sync-поля: `createdAt`, `updatedAt`, `version`, `syncState`.

### 2.7 Entity: WorkTask

- [ ] **2.7.1** Создать `db/entity/WorkTaskEntity.java`.
  - `@Entity(tableName = "work_task")` с двумя `@ForeignKey`:
    - → `ProjectRoomEntity` (`onDelete = CASCADE`)
    - → `WorkerEntity` (`onDelete = SET_NULL`)
  - `@Index` на `project_room_id` и `worker_id`.
  - Поля: `id`, `projectRoomId`, `workerId` (nullable), `taskName`.
  - `rateType` (String: "PIECEWORK" | "FIXED").
  - BigDecimal: `rateValue`, `totalPayment`.
  - Sync-поля: `createdAt`, `updatedAt`, `version`, `syncState`.

### 2.8 Entity: Worker

- [ ] **2.8.1** Создать `db/entity/WorkerEntity.java`.
  - `@Entity(tableName = "worker")` с `@Index` на `user_id`.
  - Поля: `id`, `userId`, `fullName`, `phone`, `specialty`.
  - Sync-поля: `createdAt`, `updatedAt`, `version`, `syncState`.

### 2.9 Entity: MaterialsCache

- [ ] **2.9.1** Создать `db/entity/MaterialsCacheEntity.java`.
  - `@Entity(tableName = "materials_cache")`.
  - Индексы: `fgis_code` (unique), `region_code`, `name`.
  - `@PrimaryKey` — `fgisCode` (String).
  - Поля: `name`, `unitMeasure`, `regionCode`, `source` (String: "SERVER" | "MANUAL"), `cachedAt` (long), `priorityScore` (int).
  - BigDecimal: `basePrice`.

### 2.10 Entity: UnitConversion

- [ ] **2.10.1** Создать `db/entity/UnitConversionEntity.java`.
  - `@Entity(tableName = "unit_conversion")` с уникальным `@Index` на `fgis_unit`.
  - Поля: `id` (`@PrimaryKey`), `fgisUnit`, `appUnit`.
  - BigDecimal: `conversionFactor`.

### 2.11 Entity: Conflict

- [ ] **2.11.1** Создать `db/entity/ConflictEntity.java`.
  - `@Entity(tableName = "conflict")`.
  - `@PrimaryKey` — `entityId` (String).
  - Поля: `entityType` (String), `localSnapshot` (String — JSON), `serverSnapshot` (String — JSON), `detectedAt` (long).

### 2.12 Компиляционная проверка Entity-слоя

- [ ] **2.12.1** Создать заглушку `db/AppDatabase.java` (без DAO, без `getInstance()`):
  ```java
  @Database(
      entities = {
          ProjectEntity.class, ProjectRoomEntity.class, OpeningEntity.class,
          EstimateItemEntity.class, WorkTaskEntity.class, WorkerEntity.class,
          MaterialsCacheEntity.class, UnitConversionEntity.class, ConflictEntity.class
      },
      version = 1
  )
  @TypeConverters({BigDecimalConverter.class})
  public abstract class AppDatabase extends RoomDatabase { }
  ```
- [ ] **2.12.2** Собрать проект (`Build → Make Project`). Убедиться, что Room генерирует схему без ошибок и все FK-ссылки разрешены.

---

## Фаза 3: Repository Layer

> Цель: создать все репозитории, реализующие бизнес-логику записи и чтения через DAO. DAO на этом этапе — минимальные интерфейсы-заглушки.

### 3.1 Минимальные DAO-заглушки (db/dao/)

> Цель: дать Repository точку компиляции. Полные SQL-запросы — в Фазе 4.

- [ ] **3.1.1** Создать `db/dao/ProjectDao.java` — `@Dao`-интерфейс с методами:
  - `void insert(ProjectEntity entity)`
  - `void update(ProjectEntity entity)`
  - `LiveData<List<ProjectEntity>> getActiveProjects(String userId)`
  - `void markDeleted(String id, long deletedAt)`

- [ ] **3.1.2** Создать `db/dao/ProjectRoomDao.java` — `@Dao` с:
  - `void insert(ProjectRoomEntity entity)`
  - `void updateDimensions(String id, BigDecimal l, BigDecimal w, BigDecimal h, long ts)`
  - `ProjectRoomEntity getById(String id)`
  - `List<OpeningEntity> getOpeningsSync(String roomId)`
  - `List<EstimateItemEntity> getEstimateItemsSync(String roomId)`
  - `List<WorkTaskEntity> getPieceworkTasksSync(String roomId)`

- [ ] **3.1.3** Создать `db/dao/EstimateItemDao.java`:
  - `void insert(EstimateItemEntity entity)`
  - `void updateQuantityAndTotal(String id, BigDecimal q, BigDecimal tp, long ts)`
  - `void updateAfterSync(String id, long version, long updatedAt, String syncState)`
  - `void updateSyncState(String id, String syncState)`
  - `List<EstimateItemEntity> getByStates(List<String> states)`
  - `LiveData<List<EstimateItemEntity>> getByRoomId(String roomId)`

- [ ] **3.1.4** Создать `db/dao/WorkTaskDao.java`:
  - `void insert(WorkTaskEntity entity)`
  - `void updatePayment(String id, BigDecimal payment, long ts)`
  - `void updateSyncState(String id, String syncState)`
  - `List<WorkTaskEntity> getByStates(List<String> states)`

- [ ] **3.1.5** Создать `db/dao/WorkerDao.java`:
  - `void insert(WorkerEntity entity)`
  - `void update(WorkerEntity entity)`
  - `LiveData<List<WorkerEntity>> getAll(String userId)`

- [ ] **3.1.6** Создать `db/dao/MaterialsCacheDao.java`:
  - `void insertOrReplace(MaterialsCacheEntity entity)`
  - `LiveData<List<MaterialsCacheEntity>> searchLocal(String query)`

- [ ] **3.1.7** Создать `db/dao/OpeningDao.java`:
  - `void insert(OpeningEntity entity)`
  - `void delete(String id)`

- [ ] **3.1.8** Создать `db/dao/ConflictDao.java`:
  - `void insert(ConflictEntity entity)`
  - `void delete(String entityId)`
  - `LiveData<List<ConflictEntity>> getAll()`

- [ ] **3.1.9** Добавить все DAO в `AppDatabase` как `public abstract` методы:
  ```java
  public abstract ProjectDao projectDao();
  public abstract ProjectRoomDao projectRoomDao();
  // ... и т.д.
  ```
  Пересобрать проект — ошибок быть не должно.

### 3.2 Utility: UuidGenerator

- [ ] **3.2.1** Создать `model/UuidGenerator.java` с методом `generateV7()`:
  - Можно использовать `UUID.randomUUID().toString()` как временный placeholder.
  - Оставить `TODO: replace with UUID v7 library` в комментарии.

### 3.3 Utility: AppExecutors

- [ ] **3.3.1** Создать `repository/AppExecutors.java` (singleton):
  ```java
  public class AppExecutors {
      private static final ExecutorService diskIO =
          Executors.newSingleThreadExecutor();
      public static ExecutorService diskIO() { return diskIO; }
  }
  ```
  Все Repository используют `AppExecutors.diskIO().execute(() -> { ... })`.

### 3.4 ProjectRepository (repository/)

- [ ] **3.4.1** Создать `repository/ProjectRepository.java`.
  - Конструктор принимает `ProjectDao projectDao` и `SyncManager syncManager`.
  - Метод `LiveData<List<ProjectEntity>> getActiveProjects(String userId)`:
    - делегирует `projectDao.getActiveProjects(userId)`.
  - Метод `void createProject(String name, String city, String regionCode, BigDecimal taxMultiplier, BigDecimal logisticsMarkup)`:
    - Создаёт `ProjectEntity` с UUID v7, `syncState = PENDING_CREATE`, `createdAt = updatedAt = now`, `version = 0`.
    - Вызывает `projectDao.insert(entity)` через `AppExecutors.diskIO()`.
    - После вставки вызывает `syncManager.scheduleSync()`.
  - Метод `void softDelete(String id)`:
    - Вызывает `projectDao.markDeleted(id, System.currentTimeMillis())` через `AppExecutors.diskIO()`.
    - Вызывает `syncManager.scheduleSync()`.

### 3.5 RoomRepository (repository/)

- [ ] **3.5.1** Создать `repository/RoomRepository.java`.
  - Конструктор: `ProjectRoomDao`, `EstimateItemDao`, `WorkTaskDao`, `SyncManager`.
  - Метод `void createRoom(String projectId, String name)`:
    - Создаёт `ProjectRoomEntity`, `syncState = PENDING_CREATE`, все размеры `null`.
    - Вставляет через `AppExecutors.diskIO()`, затем `scheduleSync()`.
  - Метод `void updateDimensionsAndRecalculate(String roomId, BigDecimal l, BigDecimal w, BigDecimal h)`:
    - Выполняется целиком через `AppExecutors.diskIO()`.
    - Внутри — `@Transaction` (аннотация на уровне DAO или через `runInTransaction`):
      1. `projectRoomDao.updateDimensions(roomId, l, w, h, now)`
      2. `roughArea = (l + w).multiply(TWO).multiply(h)`
      3. `openingsArea = SUM(opening.width × opening.height)` по `projectRoomDao.getOpeningsSync(roomId)`
      4. `netArea = max(roughArea − openingsArea, ZERO)`
      5. `effectiveArea = room.manualAreaOverride != null ? room.manualAreaOverride : netArea`
      6. Для каждого `EstimateItemEntity`: `quantity = effectiveArea × consumptionRate` (scale 6, HALF_UP), `totalPrice = finalPrice × quantity` (scale 2, HALF_UP) → `estimateItemDao.updateQuantityAndTotal()`
      7. Для каждого `WorkTaskEntity` с `rateType = "PIECEWORK"`: `totalPayment = effectiveArea × rateValue` (scale 2, HALF_UP) → `workTaskDao.updatePayment()`
      8. `syncManager.scheduleSync()`

### 3.6 EstimateRepository (repository/)

- [ ] **3.6.1** Создать `repository/EstimateRepository.java`.
  - Конструктор: `EstimateItemDao`, `SyncManager`.
  - Метод `LiveData<List<EstimateItemEntity>> getItemsByRoom(String roomId)`:
    - делегирует `estimateItemDao.getByRoomId(roomId)`.
  - Метод `void addEstimateItem(String roomId, String fgisCode, String name, String unitMeasure, BigDecimal basePrice, BigDecimal finalPrice, BigDecimal consumptionRate, BigDecimal effectiveArea)`:
    - Валидация: `basePrice`, `finalPrice`, `consumptionRate` не отрицательны — иначе `ValidationException`.
    - `quantity = effectiveArea × consumptionRate` (scale 6, HALF_UP).
    - `totalPrice = finalPrice × quantity` (scale 2, HALF_UP).
    - Создаёт `EstimateItemEntity` с `syncState = PENDING_CREATE`, `status = ItemStatus.NEED_TO_BUY.name()`.
    - Вставляет через `AppExecutors.diskIO()`.
    - `scheduleSync()`.
  - Метод `void updateSyncResult(String id, long serverVersion, long serverUpdatedAt)`:
    - Вызывает `estimateItemDao.updateAfterSync(id, serverVersion, serverUpdatedAt, "SYNCED")` через `AppExecutors.diskIO()`.

### 3.7 WorkerRepository (repository/)

- [ ] **3.7.1** Создать `repository/WorkerRepository.java`.
  - Конструктор: `WorkerDao`, `SyncManager`.
  - Метод `LiveData<List<WorkerEntity>> getWorkers(String userId)`:
    - делегирует `workerDao.getAll(userId)`.
  - Метод `void saveWorker(String fullName, String phone, String specialty)`:
    - `fullName = fullName.trim()` → длина 1–100, иначе `ValidationException`.
    - `phone = phone.trim().replaceAll("[^\\d+]", "")` → длина ≤ 20, иначе `ValidationException`.
    - `specialty = specialty.trim()`.
    - Создаёт `WorkerEntity` (`syncState = PENDING_CREATE`, `version = 0`).
    - Вставляет через `AppExecutors.diskIO()`.
    - Внутри `try/catch(SQLiteConstraintException e)` → бросить `DuplicateEntityException`.
    - `scheduleSync()`.

### 3.8 MaterialRepository (repository/)

- [ ] **3.8.1** Создать `repository/MaterialRepository.java`.
  - Конструктор: `MaterialsCacheDao`.
  - Метод `LiveData<List<MaterialsCacheEntity>> searchLocal(String query)`:
    - Делегирует `materialsCacheDao.searchLocal(query)`.
  - Метод `void cacheFromServer(List<MaterialsCacheEntity> serverItems)`:
    - Для каждого элемента вызывает `materialsCacheDao.insertOrReplace()` через `AppExecutors.diskIO()`.

### 3.9 Финальная проверка Фазы 3

- [ ] **3.9.1** Убедиться, что все Repository компилируются без неразрешённых зависимостей.
- [ ] **3.9.2** `Build → Make Project` — ошибок быть не должно.
- [ ] **3.9.3** Написать 1 минимальный JUnit-тест на `EstimateRepository.addEstimateItem()`: при отрицательном `basePrice` должна выбрасываться `ValidationException` (мокировать `EstimateItemDao` через Mockito).

---
