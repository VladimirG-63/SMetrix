# RFC — Smetrix: Часть 2. Offline-first и Сеть

---

## 1. Механизм синхронизации через WorkManager

### 1.1 Жизненный цикл sync_state

Каждая локальная сущность проходит через следующие состояния:

```
                  [Пользователь создаёт запись]
                           │
                           ▼
                    PENDING_CREATE
                           │
              ┌────────────┴────────────┐
              │ Есть сеть               │ Нет сети
              ▼                         │
        SyncWorker запускается          │ WorkManager ждёт
              │                         │ (NetworkType.CONNECTED)
              ▼                         │
        POST /api/v1/...  ◄─────────────┘
              │
     ┌────────┴────────────┬─────────────┐
     │ 201 Created         │ 409 Conflict │ Сеть упала / 5xx
     ▼                     ▼              ▼
   SYNCED             CONFLICT          FAILED
                                          │
                                    retry (exponential
                                    backoff WorkManager)
```

| sync_state | Когда выставляется | Действие SyncWorker |
|---|---|---|
| `PENDING_CREATE` | При создании записи на клиенте | `POST /api/v1/{entity}` |
| `PENDING_UPDATE` | При любом изменении записи | `PUT /api/v1/{entity}/{id}` |
| `PENDING_DELETE` | При удалении (кроме soft delete Project) | `DELETE /api/v1/{entity}/{id}` |
| `SYNCED` | После успешного ответа сервера | Ничего |
| `FAILED` | После исчерпания retry | Уведомление пользователя |
| `CONFLICT` | При получении HTTP 409 | Показать ConflictResolutionActivity |

---

### 1.2 Триггеры запуска SyncWorker

```java
// network/sync/SyncManager.java
public class SyncManager {

    private final WorkManager workManager;

    /** Вызывается из Repository после каждой write-операции */
    public void scheduleSync() {
        OneTimeWorkRequest syncWork = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS)          // 30s → 60s → 120s → ... (max 5h)
            .addTag("SYNC_ONE_TIME")
            .build();

        workManager.enqueueUniqueWork(
            "SYNC_ONE_TIME",
            ExistingWorkPolicy.KEEP,           // не дублируем, если уже в очереди
            syncWork);
    }

    /** Фоновая периодическая синхронизация */
    public void schedulePeriodicSync() {
        PeriodicWorkRequest periodicSync = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .addTag("SYNC_PERIODIC")
            .build();

        workManager.enqueueUniquePeriodicWork(
            "SYNC_PERIODIC",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSync);
    }
}
```

**Четыре сценария запуска:**

| Сценарий | Механизм |
|---|---|
| Пользователь изменил данные | `scheduleSync()` из Repository |
| Запуск приложения | `scheduleSync()` из `Application.onCreate()` |
| Кнопка «Синхронизировать» | `scheduleSync()` с `ExistingWorkPolicy.REPLACE` |
| Периодически в фоне | `schedulePeriodicSync()` — раз в 15 минут при наличии сети |

---

### 1.3 SyncWorker — пакетная синхронизация

```java
// network/sync/SyncWorker.java
public class SyncWorker extends Worker {

    @NonNull
    @Override
    public Result doWork() {
        try {
            // 1. Собираем все pending-записи батчами
            syncPendingProjects();
            syncPendingRooms();
            syncPendingEstimateItems();
            syncPendingWorkTasks();
            return Result.success();

        } catch (IOException e) {
            // Сетевая ошибка — WorkManager повторит с backoff
            Log.e("SyncWorker", "Network error, retrying", e);
            return Result.retry();

        } catch (Exception e) {
            // Неожиданная ошибка — фиксируем, не повторяем
            Log.e("SyncWorker", "Fatal sync error", e);
            return Result.failure();
        }
    }

    private void syncPendingEstimateItems() {
        List<EstimateItemEntity> pending = estimateItemDao
            .getByStates(List.of("PENDING_CREATE", "PENDING_UPDATE", "PENDING_DELETE"));

        // Разбиваем на батчи (размер определяется сервером, по умолчанию 50)
        Lists.partition(pending, BATCH_SIZE).forEach(batch -> {
            try {
                syncBatch(batch);
            } catch (ConflictException e) {
                // Конфликт одной записи НЕ откатывает весь батч
                markAsConflict(e.getEntityId(), e.getServerEntity());
            }
            // Успешные записи уже обновлены внутри syncBatch()
        });
    }

    @Transaction
    private void syncBatch(List<EstimateItemEntity> batch) {
        // Собираем batch-запрос
        SyncBatchRequest request = buildBatchRequest(batch);
        SyncBatchResponse response = apiService.syncEstimateItems(request).execute().body();

        // Обрабатываем каждый результат независимо
        for (SyncItemResult result : response.results) {
            if (result.status == 200 || result.status == 201) {
                estimateItemDao.updateAfterSync(
                    result.id, result.version, result.updatedAt, "SYNCED");

            } else if (result.status == 409) {
                estimateItemDao.updateSyncState(result.id, "CONFLICT");
                conflictDao.saveConflict(result.id, result.serverSnapshot);

            } else if (result.status >= 500) {
                estimateItemDao.updateSyncState(result.id, "FAILED");
            }
        }
    }
}
```

> [!IMPORTANT]
> При частичной ошибке батча **успешные записи не откатываются**. Каждая запись обрабатывается и помечается независимо.

---

### 1.4 Optimistic Locking через version

**Принцип:** клиент отправляет `version`, сервер сравнивает с текущей версией в БД.

```
Клиент                              Сервер (PostgreSQL)
──────                              ───────────────────
PUT /api/v1/estimate-items/{id}
  { "version": 3, "name": "..." }
                                    SELECT version FROM estimate_item WHERE id = ?
                                    // version в БД = 3  ✓ → обновляем, version = 4
                                    ← 200 OK { "version": 4, "updatedAt": "..." }

[другое устройство успело обновить]

PUT /api/v1/estimate-items/{id}
  { "version": 3, "name": "..." }
                                    SELECT version FROM estimate_item WHERE id = ?
                                    // version в БД = 4  ✗ → конфликт!
                                    ← 409 Conflict { "serverSnapshot": { ... } }
```

**Логика на клиенте после ответа:**

```java
// В SyncWorker / Repository
private void handleSyncResponse(EstimateItemEntity local, Response<SyncResponse> response) {

    if (response.code() == 200 || response.code() == 201) {
        SyncResponse body = response.body();
        // Обновляем version и updatedAt — это единственные поля, которые сервер диктует
        estimateItemDao.updateAfterSync(local.id, body.version, body.updatedAt, "SYNCED");

    } else if (response.code() == 409) {
        ConflictResponse conflict = parseConflict(response.errorBody());
        // Сохраняем серверный снимок для экрана разрешения конфликта
        conflictDao.insert(new ConflictEntity(
            local.id,
            toJson(local),           // локальная версия
            toJson(conflict.serverSnapshot)  // серверная версия
        ));
        estimateItemDao.updateSyncState(local.id, "CONFLICT");
        // Уведомляем пользователя (жёлтое облако в UI)
        notifyConflict(local.id);
    }
}
```

---

## 2. API Контракты

### 2.1 Базовые соглашения

| Параметр | Значение |
|---|---|
| Base URL | `https://api.smetrix.ru/api/v1/` |
| Формат | `application/json`, кодировка `UTF-8` |
| Авторизация | `Authorization: Bearer {accessToken}` |
| Версионирование | Путь `/api/v1/` — изменение контракта требует `/api/v2/` |
| Числа | `BigDecimal` передаётся как **строка** в JSON (сохраняет точность) |

> [!WARNING]
> **BigDecimal → JSON**: передавать как `"total_price": "12345.67"` (строка), **не** как число. Иначе JSON-парсер потеряет точность при десериализации в `double`.

---

### 2.2 Структура базового ответа

```json
// Успех одной записи
{
  "id": "019603d2-7b3a-7000-8000-000000000001",
  "version": 4,
  "updated_at": "2025-10-01T12:00:00Z",
  "sync_state": "SYNCED"
}

// Ошибка
{
  "error": {
    "code": "CONFLICT",
    "message": "Version mismatch. Expected 3, found 4.",
    "entity_id": "019603d2-7b3a-7000-8000-000000000001"
  }
}
```

---

### 2.3 Batch-синхронизация EstimateItem

**Запрос:** `POST /api/v1/estimate-items/sync`

```json
{
  "project_id": "019603d2-...",
  "items": [
    {
      "id": "019603d2-7b3a-7000-8000-000000000001",
      "operation": "CREATE",
      "version": 0,
      "project_room_id": "019603d2-...",
      "fgis_code": "01.1.01.01-0001",
      "name": "Цемент М400",
      "unit_measure": "кг",
      "base_price": "45.5000",
      "final_price": "54.6000",
      "consumption_rate": "8.500000",
      "quantity": "120.000000",
      "total_price": "6552.00",
      "status": "NEED_TO_BUY",
      "created_at": "2025-10-01T10:00:00Z",
      "updated_at": "2025-10-01T10:00:00Z"
    },
    {
      "id": "019603d2-7b3a-7000-8000-000000000002",
      "operation": "UPDATE",
      "version": 3,
      "quantity": "150.000000",
      "total_price": "8190.00",
      "updated_at": "2025-10-01T11:30:00Z"
    },
    {
      "id": "019603d2-7b3a-7000-8000-000000000003",
      "operation": "DELETE",
      "version": 1
    }
  ]
}
```

**Ответ: `200 OK`** (всегда 200, статус каждой записи — внутри)

```json
{
  "results": [
    {
      "id": "019603d2-7b3a-7000-8000-000000000001",
      "status": 201,
      "version": 1,
      "updated_at": "2025-10-01T12:00:00Z"
    },
    {
      "id": "019603d2-7b3a-7000-8000-000000000002",
      "status": 409,
      "error_code": "CONFLICT",
      "server_snapshot": {
        "id": "019603d2-7b3a-7000-8000-000000000002",
        "version": 5,
        "quantity": "200.000000",
        "total_price": "10920.00",
        "updated_at": "2025-10-01T11:45:00Z",
        "updated_by_device": "device-uuid-xyz"
      }
    },
    {
      "id": "019603d2-7b3a-7000-8000-000000000003",
      "status": 200
    }
  ],
  "batch_size": 3,
  "processed_at": "2025-10-01T12:00:01Z"
}
```

---

### 2.4 Синхронизация Project

**Создание:** `POST /api/v1/projects`

```json
// Запрос
{
  "id": "019603d2-7b3a-7000-8000-000000000010",
  "name": "Квартира на Ленина",
  "city": "Москва",
  "region_code": "77",
  "tax_multiplier": "1.2000",
  "logistics_markup": "10.00",
  "version": 0,
  "created_at": "2025-10-01T09:00:00Z"
}

// Ответ 201 Created
{
  "id": "019603d2-7b3a-7000-8000-000000000010",
  "version": 1,
  "updated_at": "2025-10-01T09:00:05Z"
}
```

**Soft Delete:** `DELETE /api/v1/projects/{id}`

```json
// Запрос
{ "version": 3 }

// Ответ 200 OK
{
  "id": "019603d2-...",
  "deleted_at": "2025-10-01T12:00:00Z"
}
```

---

### 2.5 Поиск материалов (Full-Text Search)

**`GET /api/v1/materials?q=цемент&region=77&limit=20&offset=0`**

```json
{
  "items": [
    {
      "fgis_code": "01.1.01.01-0001",
      "name": "Портландцемент М400",
      "unit_measure": "кг",
      "base_price": "45.5000",
      "region_code": "77",
      "quarter": "2025-Q3"
    },
    {
      "fgis_code": "01.1.01.01-0002",
      "name": "Цемент белый М500",
      "unit_measure": "кг",
      "base_price": "89.0000",
      "region_code": "77",
      "quarter": "2025-Q3"
    }
  ],
  "total": 47,
  "limit": 20,
  "offset": 0
}
```

---

### 2.6 JWT: обновление Access Token

**`POST /api/v1/auth/refresh`**

```json
// Запрос
{ "refresh_token": "eyJhbGci..." }

// Ответ 200 OK
{
  "access_token": "eyJhbGci...",
  "access_token_expires_in": 900,
  "refresh_token": "eyJhbGci...",
  "refresh_token_expires_in": 2592000
}

// Ответ 401 Unauthorized (refresh истёк или отозван)
{
  "error": {
    "code": "REFRESH_TOKEN_EXPIRED",
    "message": "Please log in again."
  }
}
```

---

## 3. Обработка HTTP 409 Conflict на клиенте

### 3.1 Общий алгоритм

```
SyncWorker получает 409 для записи X
        │
        ▼
conflictDao.insert(localSnapshot, serverSnapshot)
        │
        ▼
estimateItemDao.updateSyncState(id, "CONFLICT")
        │
        ▼
LiveData<List<ConflictEntity>> эмитит → ConflictViewModel
        │
        ▼
UI: жёлтое облако + badge с количеством конфликтов
        │
     Пользователь нажимает
        │
        ▼
ConflictResolutionActivity
  ┌─────────────────────────────────────────┐
  │  Моя версия       │  Версия сервера      │
  │  quantity: 120    │  quantity: 200        │
  │  price: 6552.00   │  price: 10920.00     │
  │  изменено: 11:30  │  изменено: 11:45     │
  └─────────────────────────────────────────┘
  [Оставить мою]  [Принять серверную]  [Отмена]
```

---

### 3.2 ConflictResolutionActivity — логика кнопок

```java
// ui/conflict/ConflictResolutionActivity.java

// Кнопка «Оставить мою версию»
btnKeepLocal.setOnClickListener(v -> {
    viewModel.resolveKeepLocal(conflictId);
    // → Repository:
    //   1. Берём локальный снимок
    //   2. Принудительно увеличиваем version = serverVersion + 1
    //   3. syncState = PENDING_UPDATE
    //   4. Запускаем scheduleSync()
    //   → Сервер получит version > текущей и примет запись
});

// Кнопка «Принять серверную версию»
btnAcceptServer.setOnClickListener(v -> {
    viewModel.resolveAcceptServer(conflictId);
    // → Repository:
    //   1. Перезаписываем локальную запись данными из serverSnapshot
    //   2. syncState = SYNCED
    //   3. Удаляем конфликт из conflictDao
    //   → Локальные изменения потеряны, синхронизация не нужна
});

// Кнопка «Отмена» — отложить решение
btnCancel.setOnClickListener(v -> {
    // syncState остаётся CONFLICT
    // жёлтое облако продолжает отображаться
    finish();
});
```

---

### 3.3 ConflictEntity — хранение снимков

```java
// db/entity/ConflictEntity.java
@Entity(tableName = "conflict")
public class ConflictEntity {

    @PrimaryKey @NonNull
    @ColumnInfo(name = "entity_id")
    public String entityId;           // ID спорной записи

    @ColumnInfo(name = "entity_type")
    public String entityType;         // "EstimateItem" | "Project" | ...

    @ColumnInfo(name = "local_snapshot")
    public String localSnapshot;      // JSON-строка локальной версии

    @ColumnInfo(name = "server_snapshot")
    public String serverSnapshot;     // JSON-строка серверной версии

    @ColumnInfo(name = "detected_at")
    public long detectedAt;           // Unix timestamp
}
```

---

### 3.4 Индикатор синхронизации в UI

```java
// ui/common/SyncStatusView.java
// Наблюдает за LiveData из SyncViewModel

syncViewModel.syncStatus.observe(this, status -> {
    switch (status) {
        case SYNCED:
            icon.setImageResource(R.drawable.ic_cloud_blue);    // синее облако
            break;
        case PENDING:
            icon.setImageResource(R.drawable.ic_cloud_grey);    // серое + точка
            break;
        case FAILED:
            icon.setImageResource(R.drawable.ic_cloud_red);     // красное облако
            break;
        case CONFLICT:
            icon.setImageResource(R.drawable.ic_cloud_yellow);  // жёлтое облако
            badge.setText(String.valueOf(status.conflictCount));
            badge.setVisibility(View.VISIBLE);
            break;
    }
});
```

**Логика определения глобального статуса:**

```sql
-- SyncViewModel получает агрегированный статус одним запросом
SELECT
  CASE
    WHEN COUNT(*) FILTER (WHERE sync_state = 'CONFLICT') > 0 THEN 'CONFLICT'
    WHEN COUNT(*) FILTER (WHERE sync_state = 'FAILED')   > 0 THEN 'FAILED'
    WHEN COUNT(*) FILTER (WHERE sync_state LIKE 'PENDING%') > 0 THEN 'PENDING'
    ELSE 'SYNCED'
  END AS global_status,
  COUNT(*) FILTER (WHERE sync_state = 'CONFLICT') AS conflict_count
FROM (
  SELECT sync_state FROM project
  UNION ALL SELECT sync_state FROM project_room
  UNION ALL SELECT sync_state FROM estimate_item
  UNION ALL SELECT sync_state FROM work_task
);
```

---

### 3.5 Обработка HTTP 401 (Token Refresh)

```java
// network/AuthInterceptor.java (OkHttp Interceptor)
public class AuthInterceptor implements Interceptor {

    @NonNull @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Response response = chain.proceed(withAuth(original));

        if (response.code() == 401) {
            synchronized (this) {
                // Пробуем обновить access token
                TokenResponse tokens = authRepository.refreshTokenSync();
                if (tokens != null) {
                    // Повторяем исходный запрос с новым токеном
                    return chain.proceed(withAuth(original, tokens.accessToken));
                } else {
                    // Refresh истёк → logout
                    authRepository.logout();
                    // Сигнал UI через EventBus / BroadcastReceiver
                    broadcastLogout();
                }
            }
        }
        return response;
    }
}
```

---

## 4. Сводная таблица: HTTP-коды и действия клиента

| HTTP | Сценарий | Действие клиента |
|---|---|---|
| `200` | Запись обновлена | `syncState = SYNCED`, обновить `version` и `updatedAt` |
| `201` | Запись создана на сервере | `syncState = SYNCED`, обновить `version` |
| `204` | Запись удалена | Физически удалить из Room |
| `400` | Невалидные данные | `syncState = FAILED`, лог ошибки |
| `401` | Токен истёк | Auto-refresh → retry; при неудаче — logout |
| `404` | Запись не найдена на сервере | Для DELETE — считать успехом; для UPDATE — `syncState = FAILED` |
| `409` | Конфликт версий | `syncState = CONFLICT`, сохранить `serverSnapshot`, уведомить пользователя |
| `5xx` | Ошибка сервера | `Result.retry()` с exponential backoff |
| Нет сети | `IOException` | `Result.retry()`, WorkManager ждёт `CONNECTED` |

---

> [!TIP]
> **Следующие части RFC:**
> - Часть 3: DAO-интерфейсы, миграции Room, AppDatabase
> - Часть 4: ViewModel-слой, расчётный движок (BigDecimal, геометрия, зарплаты)
> - Часть 5: UI — XML-макеты, Sticky Bottom Bar, Search-as-you-type
> - Часть 6: Серверный парсер ФГИС ЦС (Apache POI Streaming API)
