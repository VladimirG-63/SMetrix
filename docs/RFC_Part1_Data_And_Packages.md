# RFC — Smetrix: Часть 1. Данные и Пакеты

---

## 1. Схема Room (Entity-классы)

### 1.1 Общие соглашения

| Правило | Детали |
|---|---|
| **ID** | `String` (UUID v7), генерируется на клиенте |
| **Финансы / площади** | `BigDecimal` (через `@ColumnInfo` + `BigDecimalConverter`) |
| **Запрещено** | `float`, `double` для любых вычисляемых значений |
| **Soft Delete** | Только `Project` — поле `deletedAt` (`deleted_at`) |
| **Sync-поля** | `createdAt`, `updatedAt`, `version` (long), `syncState` (ENUM-строка) |
| **Транзакции** | Все каскадные операции — через `@Transaction` |

---

### 1.2 TypeConverter — BigDecimal

```java
// db/converter/BigDecimalConverter.java
public class BigDecimalConverter {

    @TypeConverter
    public static String fromBigDecimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    @TypeConverter
    public static BigDecimal toBigDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
```

> [!IMPORTANT]
> Room не умеет хранить `BigDecimal` нативно. Храним как `TEXT` с полной точностью через `toPlainString()`. Конвертер регистрируется в `@Database(... typeConverters = {BigDecimalConverter.class})`.

---

### 1.3 SyncState — ENUM

```java
// model/SyncState.java
public enum SyncState {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    FAILED,
    CONFLICT
}
```

---

### 1.4 Entity: Project

```java
// db/entity/ProjectEntity.java
@Entity(
    tableName = "project",
    indices = {
        @Index(value = {"user_id"}),          // часто фильтруем по владельцу
        @Index(value = {"deleted_at"}),        // фильтр корзины
        @Index(value = {"sync_state"})         // очередь синхронизации
    }
)
public class ProjectEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;                          // UUID v7

    @ColumnInfo(name = "user_id")
    public String userId;                      // nullable — локальный режим без авторизации

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "city")
    public String city;

    @ColumnInfo(name = "region_code")
    public String regionCode;

    @ColumnInfo(name = "tax_multiplier")
    public BigDecimal taxMultiplier;           // конвертер

    @ColumnInfo(name = "logistics_markup")
    public BigDecimal logisticsMarkup;         // конвертер

    /** Soft Delete: null = активен, timestamp = удалён */
    @ColumnInfo(name = "deleted_at")
    public Long deletedAt;                     // Unix timestamp ms, nullable

    @ColumnInfo(name = "last_synced_at")
    public Long lastSyncedAt;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @ColumnInfo(name = "version")
    public long version;

    @ColumnInfo(name = "sync_state")
    public String syncState;                   // SyncState.name()
}
```

**Soft Delete логика:**
- `deletedAt == null` → проект активен, отображается в списке
- `deletedAt != null` → проект в корзине, скрыт из UI
- Через 30 дней → физическое удаление по Cron (сервер) / WorkManager (клиент)

---

### 1.5 Entity: ProjectRoom

```java
// db/entity/ProjectRoomEntity.java
@Entity(
    tableName = "project_room",
    foreignKeys = @ForeignKey(
        entity    = ProjectEntity.class,
        parentColumns = "id",
        childColumns  = "project_id",
        onDelete  = ForeignKey.CASCADE        // каскадное физическое удаление
    ),
    indices = {
        @Index(value = {"project_id"})
    }
)
public class ProjectRoomEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "project_id")
    public String projectId;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "length")
    public BigDecimal length;

    @ColumnInfo(name = "width")
    public BigDecimal width;

    @ColumnInfo(name = "height")
    public BigDecimal height;

    /**
     * Если != null — автоматический расчёт площади игнорируется.
     * Используется для помещений со сложной геометрией.
     */
    @ColumnInfo(name = "manual_area_override")
    public BigDecimal manualAreaOverride;      // nullable

    @ColumnInfo(name = "created_at")  public long createdAt;
    @ColumnInfo(name = "updated_at")  public long updatedAt;
    @ColumnInfo(name = "version")     public long version;
    @ColumnInfo(name = "sync_state")  public String syncState;
}
```

**Геометрический движок (внутри модели / ViewModel):**
```
roughWallArea   = (length + width) × 2 × height
openingsArea    = SUM(opening.width × opening.height)
netWallArea     = roughWallArea − openingsArea

// если manualAreaOverride != null:
effectiveArea   = manualAreaOverride
```

---

### 1.6 Entity: Opening

```java
// db/entity/OpeningEntity.java
@Entity(
    tableName = "opening",
    foreignKeys = @ForeignKey(
        entity    = ProjectRoomEntity.class,
        parentColumns = "id",
        childColumns  = "project_room_id",
        onDelete  = ForeignKey.CASCADE
    ),
    indices = { @Index(value = {"project_room_id"}) }
)
public class OpeningEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "id")       public String id;
    @ColumnInfo(name = "project_room_id") public String projectRoomId;

    /** "DOOR" | "WINDOW" */
    @ColumnInfo(name = "type")     public String type;

    @ColumnInfo(name = "width")    public BigDecimal width;
    @ColumnInfo(name = "height")   public BigDecimal height;

    @ColumnInfo(name = "created_at") public long createdAt;
    @ColumnInfo(name = "updated_at") public long updatedAt;
    @ColumnInfo(name = "version")    public long version;
    @ColumnInfo(name = "sync_state") public String syncState;
}
```

---

### 1.7 Entity: EstimateItem (Смета)

```java
// db/entity/EstimateItemEntity.java
@Entity(
    tableName = "estimate_item",
    foreignKeys = @ForeignKey(
        entity    = ProjectRoomEntity.class,
        parentColumns = "id",
        childColumns  = "project_room_id",
        onDelete  = ForeignKey.CASCADE
    ),
    indices = { @Index(value = {"project_room_id"}) }  // часто используется
)
public class EstimateItemEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "id")              public String id;
    @ColumnInfo(name = "project_room_id") public String projectRoomId;

    /** Код ресурса ФГИС ЦС, например "01.1.01.01-0001". Null = ручной ввод */
    @ColumnInfo(name = "fgis_code")       public String fgisCode;

    @ColumnInfo(name = "name")            public String name;
    @ColumnInfo(name = "unit_measure")    public String unitMeasure;  // кг, м2, шт…

    /**
     * Ценовые поля (scale=4 при хранении через toPlainString):
     *   final_price = (base_price × taxMultiplier) × (1 + logisticsMarkup/100)
     *   total_price = final_price × quantity
     */
    @ColumnInfo(name = "base_price")      public BigDecimal basePrice;
    @ColumnInfo(name = "final_price")     public BigDecimal finalPrice;
    @ColumnInfo(name = "consumption_rate")public BigDecimal consumptionRate;  // scale ≤ 6
    @ColumnInfo(name = "quantity")        public BigDecimal quantity;          // scale ≤ 6
    @ColumnInfo(name = "total_price")     public BigDecimal totalPrice;        // scale = 2

    /**
     * Статус снабжения:
     *   "NEED_TO_BUY" (серый) | "ORDERED" (жёлтый) | "ON_SITE" (зелёный)
     *   + "UNITS_MISMATCH" — ожидает ручного подтверждения коэффициента
     */
    @ColumnInfo(name = "status")          public String status;

    @ColumnInfo(name = "created_at")  public long createdAt;
    @ColumnInfo(name = "updated_at")  public long updatedAt;
    @ColumnInfo(name = "version")     public long version;
    @ColumnInfo(name = "sync_state")  public String syncState;
}
```

> [!NOTE]
> Отрицательные значения запрещены для `quantity`, `consumptionRate`, `basePrice`, `finalPrice`, `totalPrice`. Валидация — в слое Repository/ViewModel перед вставкой.

---

### 1.8 Entity: WorkTask (Зарплата)

```java
// db/entity/WorkTaskEntity.java
@Entity(
    tableName = "work_task",
    foreignKeys = {
        @ForeignKey(entity = ProjectRoomEntity.class,
            parentColumns = "id", childColumns = "project_room_id",
            onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = WorkerEntity.class,
            parentColumns = "id", childColumns = "worker_id",
            onDelete = ForeignKey.SET_NULL)  // рабочий удалён — задача остаётся
    },
    indices = {
        @Index(value = {"project_room_id"}),
        @Index(value = {"worker_id"})
    }
)
public class WorkTaskEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "id")              public String id;
    @ColumnInfo(name = "project_room_id") public String projectRoomId;
    @ColumnInfo(name = "worker_id")       public String workerId;        // nullable
    @ColumnInfo(name = "task_name")       public String taskName;

    /** "PIECEWORK" (сдельная) | "FIXED" (фиксированная) */
    @ColumnInfo(name = "rate_type")       public String rateType;

    @ColumnInfo(name = "rate_value")      public BigDecimal rateValue;
    @ColumnInfo(name = "total_payment")   public BigDecimal totalPayment;

    @ColumnInfo(name = "created_at")  public long createdAt;
    @ColumnInfo(name = "updated_at")  public long updatedAt;
    @ColumnInfo(name = "version")     public long version;
    @ColumnInfo(name = "sync_state")  public String syncState;
}
```

---

### 1.9 Entity: Worker

```java
// db/entity/WorkerEntity.java
@Entity(
    tableName = "worker",
    indices = { @Index(value = {"user_id"}) }
)
public class WorkerEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "id")        public String id;
    @ColumnInfo(name = "user_id")   public String userId;
    @ColumnInfo(name = "full_name") public String fullName;
    @ColumnInfo(name = "phone")     public String phone;
    @ColumnInfo(name = "specialty") public String specialty;

    @ColumnInfo(name = "created_at")  public long createdAt;
    @ColumnInfo(name = "updated_at")  public long updatedAt;
    @ColumnInfo(name = "version")     public long version;
    @ColumnInfo(name = "sync_state")  public String syncState;
}
```

---

### 1.10 Entity: MaterialsCache (Справочник / кэш)

```java
// db/entity/MaterialsCacheEntity.java
@Entity(
    tableName = "materials_cache",
    indices = {
        @Index(value = {"fgis_code"}, unique = true),
        @Index(value = {"region_code"}),
        @Index(value = {"name"})   // для LIKE-поиска оффлайн
    }
)
public class MaterialsCacheEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "fgis_code")    public String fgisCode;   // "01.1.01.01-0001"

    @ColumnInfo(name = "name")         public String name;
    @ColumnInfo(name = "unit_measure") public String unitMeasure;
    @ColumnInfo(name = "base_price")   public BigDecimal basePrice;
    @ColumnInfo(name = "region_code")  public String regionCode;

    /** Источник: "SERVER" | "MANUAL" */
    @ColumnInfo(name = "source")       public String source;

    /** Дата последнего обновления с сервера */
    @ColumnInfo(name = "cached_at")    public long cachedAt;

    /** Приоритет в результатах поиска: recently used, popular, project-specific */
    @ColumnInfo(name = "priority_score") public int priorityScore;
}
```

---

### 1.11 Entity: UnitConversion

```java
// db/entity/UnitConversionEntity.java
@Entity(
    tableName = "unit_conversion",
    indices = { @Index(value = {"fgis_unit"}, unique = true) }
)
public class UnitConversionEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "id")              public String id;
    @ColumnInfo(name = "fgis_unit")       public String fgisUnit;         // "1000 шт"
    @ColumnInfo(name = "app_unit")        public String appUnit;           // "шт"
    @ColumnInfo(name = "conversion_factor") public BigDecimal conversionFactor; // 0.001
}
```

---

### 1.12 Сводная диаграмма связей

```
User
 └── Project  (soft delete → deleted_at)
      └── ProjectRoom  (cascade delete)
           ├── Opening        (cascade delete)
           ├── EstimateItem   (cascade delete)
           └── WorkTask       (cascade delete)
                └── Worker    (SET_NULL при удалении рабочего)

MaterialsCache   (независимая таблица — кэш справочника)
UnitConversion   (независимая таблица — коэффициенты)
```

---

## 2. Структура пакетов

### 2.1 Иерархия папок

```
com.smetrix.app/
│
├── db/                            # Всё, что связано с Room
│   ├── AppDatabase.java           # @Database — точка входа в Room
│   ├── converter/
│   │   └── BigDecimalConverter.java
│   ├── entity/                    # @Entity — таблицы Room
│   │   ├── ProjectEntity.java
│   │   ├── ProjectRoomEntity.java
│   │   ├── OpeningEntity.java
│   │   ├── EstimateItemEntity.java
│   │   ├── WorkTaskEntity.java
│   │   ├── WorkerEntity.java
│   │   ├── MaterialsCacheEntity.java
│   │   └── UnitConversionEntity.java
│   └── dao/                       # @Dao — SQL-запросы
│       ├── ProjectDao.java
│       ├── ProjectRoomDao.java
│       ├── OpeningDao.java
│       ├── EstimateItemDao.java
│       ├── WorkTaskDao.java
│       ├── WorkerDao.java
│       ├── MaterialsCacheDao.java
│       └── UnitConversionDao.java
│
├── model/                         # Чистые Java-модели (без Android/Room зависимостей)
│   ├── Project.java               # Domain-объект
│   ├── ProjectRoom.java
│   ├── EstimateItem.java
│   ├── WorkTask.java
│   ├── Worker.java
│   ├── Material.java
│   ├── SyncState.java             # ENUM
│   └── ItemStatus.java            # ENUM: NEED_TO_BUY, ORDERED, ON_SITE, UNITS_MISMATCH
│
├── repository/                    # Единственная точка доступа к данным для ViewModel
│   ├── ProjectRepository.java
│   ├── RoomRepository.java        # ProjectRoom (избегаем конфликта с android.Room)
│   ├── EstimateRepository.java
│   ├── WorkerRepository.java
│   └── MaterialRepository.java
│
├── network/                       # Retrofit + синхронизация
│   ├── ApiService.java            # Retrofit-интерфейс (/api/v1/...)
│   ├── dto/                       # JSON DTO (запросы / ответы)
│   │   ├── ProjectDto.java
│   │   ├── EstimateItemDto.java
│   │   └── ...
│   └── sync/
│       ├── SyncManager.java       # Оркестратор синхронизации
│       └── SyncWorker.java        # WorkManager Worker
│
├── viewmodel/                     # AndroidViewModel — между UI и Repository
│   ├── ProjectListViewModel.java
│   ├── ProjectDetailViewModel.java
│   ├── RoomDetailViewModel.java
│   └── WorkerViewModel.java
│
└── ui/                            # Activity / Fragment / Adapter
    ├── project/
    │   ├── ProjectListActivity.java
    │   ├── ProjectListAdapter.java
    │   └── ProjectDetailActivity.java
    ├── room/
    │   ├── RoomDetailFragment.java
    │   ├── EstimateAdapter.java
    │   └── WorkTaskAdapter.java
    ├── worker/
    │   └── WorkerActivity.java
    └── common/
        └── SyncStatusView.java    # Индикатор облака (синий/серый/красный/жёлтый)
```

---

### 2.2 Поток данных: Room → UI через Repository и LiveData

```
┌─────────────────────────────────────────────────────────────────┐
│  UI Layer (Activity / Fragment)                                  │
│  observe(viewModel.projects, observer → adapter.submitList())    │
└────────────────────┬────────────────────────────────────────────┘
                     │ observe LiveData<List<Project>>
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  ViewModel (AndroidViewModel)                                    │
│  LiveData<List<Project>> projects = repository.getActiveProjects│
│  fun deleteProject(id) { repository.softDelete(id) }            │
└────────────────────┬────────────────────────────────────────────┘
                     │ LiveData (уже wrapped из Room)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  Repository                                                      │
│  // Читает из Room, записывает в Room + помечает syncState       │
│  LiveData<List<ProjectEntity>> getActiveProjects() {            │
│      return projectDao.getWhereDeletedAtIsNull();               │
│  }                                                               │
│  void softDelete(String id) {                     // фон-поток  │
│      projectDao.markDeleted(id, System.currentTimeMillis());    │
│      syncManager.scheduleSync();                                │
│  }                                                               │
└────────────────────┬────────────────────────────────────────────┘
                     │ Room автоматически эмитит новые данные
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  Room DAO                                                        │
│  @Query("SELECT * FROM project WHERE deleted_at IS NULL         │
│          AND user_id = :userId ORDER BY updated_at DESC")       │
│  LiveData<List<ProjectEntity>> getWhereDeletedAtIsNull(userId); │
└─────────────────────────────────────────────────────────────────┘
```

**Ключевые правила потока:**

| Правило | Причина |
|---|---|
| DAO возвращает `LiveData<T>` | Room сам отслеживает изменения таблицы и эмитит обновления |
| Repository не держит данные в памяти | Единственный источник — Room; нет дублирования состояния |
| ViewModel не обращается к DAO напрямую | Инкапсуляция; Repository может подменить источник (Room ↔ Network) |
| Все записи через Repository в фоновом потоке | UI-поток никогда не блокируется; Room запрещает write на main thread |
| `AndroidViewModel` (не `ViewModel`) | Нужен `Application` для инициализации Room без утечки Context |

---

### 2.3 Пример: полный цикл для EstimateItem

```
1. Пользователь ищет материал → RoomDetailFragment.searchView.onQueryTextChange()
2. debounce 400ms в RoomDetailViewModel
3. ViewModel → MaterialRepository.search(query, regionCode)
4. Repository:
     а) сразу → MaterialsCacheDao.searchLocal(query)  → LiveData (оффлайн, мгновенно)
     б) при наличии сети → ApiService.searchMaterials(query) → кэширует в Room
5. Результаты объединяются → MediatorLiveData → адаптер обновляется
6. Пользователь выбирает материал → ViewModel.addEstimateItem(material, roomId)
7. Repository:
     а) создаёт EstimateItemEntity (UUID v7, syncState = PENDING_CREATE)
     б) вставляет в Room (@Transaction если пересчёт quantity)
     в) scheduleSync() → WorkManager
8. EstimateItemDao LiveData эмитит → RoomDetailFragment обновляет RecyclerView
9. Sticky Bottom Bar пересчитывает SUM(total_price) + SUM(total_payment) реактивно
```

---

> [!TIP]
> **Следующие части RFC:**
> - Часть 2: DAO-интерфейсы, миграции Room, AppDatabase
> - Часть 3: Repository-слой, WorkManager / SyncWorker
> - Часть 4: ViewModel-слой, расчётный движок (BigDecimal)
> - Часть 5: UI — XML-макеты, адаптеры, SyncStatusView
> - Часть 6: Network-слой — Retrofit, DTO, JWT-перехватчик
