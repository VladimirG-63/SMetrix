# Фаза 7: SyncStatusView + ConflictResolutionActivity — Отчёт

## ✅ BUILD SUCCESSFUL (45s, 7 executed)

---

## Что сделано

### Шаг 6.2 — 4 векторные иконки в `res/drawable/`

| Файл | Состояние | Цвет |
|---|---|---|
| `ic_cloud_blue.xml` | `SYNCED` | `#1976D2` (Material Blue 700) |
| `ic_cloud_grey.xml` | `PENDING` | `#9E9E9E` + белая точка |
| `ic_cloud_red.xml` | `FAILED` | `#D32F2F` + белый крест |
| `ic_cloud_yellow.xml` | `CONFLICT` | `#F57F17` + белый восклицательный знак |

Все иконки — `<vector>` с `viewportWidth/Height=24`, размер `24dp`.

---

### Шаг 6.3 — `res/layout/view_sync_status.xml` + `SyncStatusView.java`

**Макет** (`view_sync_status.xml`): `FrameLayout` 40×40dp с:
- `ivSyncIcon` (28dp, по центру) — векторная иконка
- `tvConflictBadge` (16dp, top|end) — красный кружок с белым числом, `visibility=GONE`

**Класс** (`ui/common/SyncStatusView.java`):
```java
public void bind(SyncViewModel syncViewModel, LifecycleOwner owner) {
    syncViewModel.getSyncStatus().observe(owner, status -> applyStatus(status));
}

private void applyStatus(SyncStatusResult status) {
    switch (status.globalStatus) {
        case "SYNCED":   ivSyncIcon.setImageResource(R.drawable.ic_cloud_blue);   tvConflictBadge.setVisibility(GONE);    break;
        case "PENDING":  ivSyncIcon.setImageResource(R.drawable.ic_cloud_grey);   tvConflictBadge.setVisibility(GONE);    break;
        case "FAILED":   ivSyncIcon.setImageResource(R.drawable.ic_cloud_red);    tvConflictBadge.setVisibility(GONE);    break;
        case "CONFLICT": ivSyncIcon.setImageResource(R.drawable.ic_cloud_yellow);
                         tvConflictBadge.setText(String.valueOf(status.conflictCount));
                         tvConflictBadge.setVisibility(VISIBLE); break;
        default: ivSyncIcon.setImageResource(R.drawable.ic_cloud_grey); break; // безопасный фолбэк
    }
}
```

---

### Шаг 6.9 — `ConflictResolutionActivity`

**Макет** (`activity_conflict_resolution.xml`):
- `Toolbar` с заголовком `@string/title_conflict_resolution`
- `ScrollView` с двумя колонками `weight=1` (Моя версия | Версия сервера)
- `tvLocalSnapshot` + `tvServerSnapshot` — `fontFamily=monospace`, 11sp
- Кнопки снизу: `btnKeepLocal`, `btnAcceptServer`, `btnCancel`

**Как Activity парсит и выводит JSON-снимки конфликта:**

```java
// 1. Intent → entityId
entityId = getIntent().getStringExtra(EXTRA_ENTITY_ID);

// 2. LiveData → находим ConflictEntity по entityId
syncViewModel.getConflicts().observe(this, conflicts -> {
    for (ConflictEntity c : conflicts) {
        if (entityId.equals(c.entityId)) { target = c; break; }
    }
    // 3. localSnapshot / serverSnapshot — сырые JSON-строки из SyncWorker
    // 4. JsonParser.parseString() → JsonElement
    // 5. GsonBuilder().setPrettyPrinting().create().toJson(element)
    // 6. Результат → TextView (отформатированный JSON с отступами)
    tvLocalSnapshot.setText(formatJsonSnapshot(target.localSnapshot));
    tvServerSnapshot.setText(formatJsonSnapshot(target.serverSnapshot));
});
```

```java
private String formatJsonSnapshot(String rawJson) {
    try {
        JsonElement element = JsonParser.parseString(rawJson);
        return prettyGson.toJson(element);         // ← красивый JSON с отступами
    } catch (JsonSyntaxException e) {
        Log.w(TAG, "невалидный JSON: " + e.getMessage());
        return rawJson;                            // ← фолбэк: сырая строка
    }
}
```

**Логика кнопок (DB-операции на `AppExecutors.diskIO()`):**

| Кнопка | Действие |
|---|---|
| `btnKeepLocal` | `conflictRepository.resolveKeepLocal(entityId, serverVersion)` → `finish()` |
| `btnAcceptServer` | `conflictRepository.resolveAcceptServer(entityId, serverSnapshotJson)` → `finish()` |
| `btnCancel` | `finish()` (конфликт остаётся в БД) |

**Авто-закрытие:** если после разрешения конфликта LiveData возвращает список без нужного `entityId` — Activity сама вызывает `finish()`.

---

### Шаг 6.10 — Интеграция в `MainActivity`

В `AndroidManifest.xml` зарегистрирована:
```xml
<activity
    android:name=".ui.conflict.ConflictResolutionActivity"
    android:exported="false"
    android:windowSoftInputMode="adjustResize"
    android:parentActivityName=".MainActivity" />
```

В `MainActivity.java` добавлен `initSyncStatusView()`:
```java
syncViewModel = new ViewModelProvider(this).get(SyncViewModel.class);
syncStatusView = new SyncStatusView(this);
binding.toolbar.addView(syncStatusView, layoutParams);
syncStatusView.bind(syncViewModel, this);  // подписка на LiveData

syncStatusView.setOnClickListener(v -> {
    List<ConflictEntity> conflicts = syncViewModel.getConflicts().getValue();
    if (conflicts != null && !conflicts.isEmpty()) {
        Intent intent = new Intent(this, ConflictResolutionActivity.class);
        intent.putExtra(ConflictResolutionActivity.EXTRA_ENTITY_ID, conflicts.get(0).entityId);
        startActivity(intent);
    }
});
```

---

## Созданные файлы

| Файл | Тип |
|---|---|
| `res/drawable/ic_cloud_blue.xml` | Новый |
| `res/drawable/ic_cloud_grey.xml` | Новый |
| `res/drawable/ic_cloud_red.xml` | Новый |
| `res/drawable/ic_cloud_yellow.xml` | Новый |
| `res/layout/view_sync_status.xml` | Новый |
| `res/layout/activity_conflict_resolution.xml` | Новый |
| `ui/common/SyncStatusView.java` | Новый |
| `ui/conflict/ConflictResolutionActivity.java` | Новый |
| `AndroidManifest.xml` | Изменён |
| `MainActivity.java` | Изменён |
