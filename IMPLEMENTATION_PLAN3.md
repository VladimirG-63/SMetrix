## Фаза 6: UI (XML и Activity/Fragment)

> Цель: реализовать все экраны приложения — список проектов, детали комнаты с RecyclerView, Sticky Bottom Bar, SyncStatusView, ConflictResolutionActivity. Все макеты — **XML only**, разметка — **ConstraintLayout** для сложных экранов. Все строки — в `strings.xml`, все размеры — в `dimens.xml`, стили — в `themes.xml`.

### 6.1 Ресурсы: strings, dimens, themes

- [ ] **6.1.1** Открыть `res/values/strings.xml`. Добавить все строки UI (нет магических строк в Java-коде):
  ```xml
  <string name="label_materials">МАТЕРИАЛЫ</string>
  <string name="label_salaries">ЗАРПЛАТЫ</string>
  <string name="label_total">ИТОГО</string>
  <string name="btn_keep_local">Оставить мою версию</string>
  <string name="btn_accept_server">Принять серверную</string>
  <string name="btn_cancel">Отмена</string>
  <string name="warning_recalc_title">Внимание!</string>
  <string name="warning_recalc_message">Изменение размеров приведёт к автоматическому пересчёту материалов и заработной платы. Продолжить?</string>
  <string name="cd_sync_status_icon">Статус синхронизации</string>
  ```

- [ ] **6.1.2** Открыть `res/values/dimens.xml`. Добавить:
  ```xml
  <dimen name="sticky_bar_height">64dp</dimen>
  <dimen name="sticky_bar_padding_bottom">80dp</dimen>
  <dimen name="sticky_bar_divider_width">1dp</dimen>
  <dimen name="sticky_bar_divider_height">32dp</dimen>
  <dimen name="sticky_bar_padding_horizontal">12dp</dimen>
  <dimen name="card_corner_radius">8dp</dimen>
  <dimen name="list_item_padding">16dp</dimen>
  ```

- [ ] **6.1.3** В `res/values/themes.xml` убедиться, что тема наследует `Theme.MaterialComponents.DayNight` (нужен для `MaterialAlertDialogBuilder`).

### 6.2 Drawable-ресурсы для SyncStatusView

- [ ] **6.2.1** Создать 4 векторных иконки в `res/drawable/`:
  - `ic_cloud_blue.xml` — синее облако (состояние `SYNCED`).
  - `ic_cloud_grey.xml` — серое облако + точка (состояние `PENDING`).
  - `ic_cloud_red.xml` — красное облако (состояние `FAILED`).
  - `ic_cloud_yellow.xml` — жёлтое облако (состояние `CONFLICT`).
  - Использовать `<vector>` с `android:tint` для окраски.

### 6.3 SyncStatusView (ui/common/)

- [ ] **6.3.1** Создать `res/layout/view_sync_status.xml`:
  ```xml
  <FrameLayout ...>
      <ImageView android:id="@+id/ivSyncIcon"
          android:contentDescription="@string/cd_sync_status_icon" />
      <TextView android:id="@+id/tvConflictBadge"
          android:visibility="gone" />
  </FrameLayout>
  ```

- [ ] **6.3.2** Создать `ui/common/SyncStatusView.java` extends `FrameLayout`:
  - Конструкторы для XML-инфляции (`Context`, `AttributeSet`).
  - Метод `bind(SyncViewModel syncViewModel, LifecycleOwner owner)`:
    - Наблюдает `syncViewModel.syncStatus`.
    - `switch (status.globalStatus)`:
      - `"SYNCED"` → `ivSyncIcon.setImageResource(R.drawable.ic_cloud_blue)`, `tvConflictBadge.setVisibility(GONE)`.
      - `"PENDING"` → серое облако.
      - `"FAILED"` → красное облако.
      - `"CONFLICT"` → жёлтое облако, `tvConflictBadge.setText(String.valueOf(status.conflictCount))`, `tvConflictBadge.setVisibility(VISIBLE)`.
  - Каждый `case` — конкретный `setImageResource()`. Пустых `catch`-блоков нет.

### 6.4 DiffUtil для сметы

- [ ] **6.4.1** Создать `ui/room/EstimateDiffCallback.java` extends `DiffUtil.Callback`:
  - Конструктор: `List<EstimateItemDisplay> oldList`, `List<EstimateItemDisplay> newList`.
  - `areItemsTheSame` — сравнивать `id` через `.equals()`.
  - `areContentsTheSame` — сравнивать `name`, `quantity.compareTo()==0`, `totalPrice.compareTo()==0`, `status`, `syncState`.
  - `getChangePayload` — возвращать `Bundle` с только изменившимися полями (`quantity`, `total_price`, `status`). Если Bundle пуст — вернуть `null`.

### 6.5 EstimateAdapter (ui/room/)

- [ ] **6.5.1** Создать `res/layout/item_estimate.xml` — `ConstraintLayout`:
  - `TextView` с id: `tvItemName`, `tvItemUnit`, `tvItemQuantity`, `tvItemPrice`, `tvItemTotal`.
  - `View` с id `statusDot` (круглый индикатор статуса, 8dp).
  - Все размеры через `@dimen/`, все строки через `@string/`.

- [ ] **6.5.2** Создать `ui/room/EstimateAdapter.java` extends `RecyclerView.Adapter<EstimateAdapter.ViewHolder>`:
  - Поле `List<EstimateItemDisplay> items` (инициализировать как `new ArrayList<>()`).
  - Поле `OnItemActionListener listener`.
  - Метод `submitList(List<EstimateItemDisplay> newList)`:
    - `DiffUtil.calculateDiff(new EstimateDiffCallback(items, newList))`.
    - `items = new ArrayList<>(newList)`.
    - `result.dispatchUpdatesTo(this)`.
  - Метод `onBindViewHolder(ViewHolder, int, List<Object> payloads)`:
    - Если `payloads` не пуст — частичное обновление через `Bundle`:
      - `"quantity"` → `holder.tvQuantity.setText(...)`.
      - `"total_price"` → `holder.tvTotal.setText(formatPrice(...))`.
      - `"status"` → `holder.updateStatusIndicator(...)`.
      - Вернуть после частичного обновления, не вызывать полный `bind`.
  - Метод `onBindViewHolder(ViewHolder, int)` — полный `bind`:
    - `tvItemName.setText(item.name)`.
    - `tvItemUnit.setText(item.unitMeasure)`.
    - `tvItemQuantity.setText(item.quantity.setScale(2, HALF_UP).toPlainString())`.
    - `tvItemPrice.setText(formatPrice(item.finalPrice))`.
    - `tvItemTotal.setText(formatPrice(item.totalPrice))`.
    - `holder.updateStatusIndicator(item.status)`.
    - `holder.itemView.setOnLongClickListener(v -> { listener.onLongClick(item.id); return true; })`.
  - Внутренний класс `ViewHolder`:
    - Метод `updateStatusIndicator(String status)` — `switch` по статусу, `statusDot.setBackgroundTintList(ColorStateList.valueOf(color))`:
      - `ORDERED` → `#FF9800` (жёлтый).
      - `ON_SITE` → `#4CAF50` (зелёный).
      - `UNITS_MISMATCH` → `#F44336` (красный).
      - `default` → `#9E9E9E` (серый).
  - Интерфейс `OnItemActionListener`:
    - `void onLongClick(String itemId)`.
    - `void onStatusChange(String itemId, String newStatus)`.

- [ ] **6.5.3** Вспомогательный метод `formatPrice(BigDecimal value)` в `EstimateAdapter`:
  - Форматировать через `NumberFormat.getCurrencyInstance(new Locale("ru","RU"))` или `String.format(Locale.ROOT, "%,.2f ₽", value)`.
  - Не хардкодить символ рубля в коде — использовать `@string/currency_symbol` либо Locale.

### 6.6 Макет fragment_room_detail.xml

- [ ] **6.6.1** Создать `res/layout/fragment_room_detail.xml`:
  - Корень — `CoordinatorLayout`.
  - Внутри — `NestedScrollView` с `android:paddingBottom="@dimen/sticky_bar_padding_bottom"` и `android:clipToPadding="false"`.
  - Внутри `NestedScrollView` — `LinearLayout` (vertical) с:
    - `<include layout="@layout/block_room_dimensions" />`.
    - `<include layout="@layout/block_openings" />`.
    - `RecyclerView` с id `rvEstimateItems` (`android:nestedScrollingEnabled="false"`).
    - `RecyclerView` с id `rvWorkTasks` (`android:nestedScrollingEnabled="false"`).
  - **Sticky Bottom Bar** — `LinearLayout` с id `stickyBottomBar`:
    - `android:layout_gravity="bottom"`, высота `@dimen/sticky_bar_height`, `android:elevation="8dp"`.
    - Три `TextView` с `layout_weight="1"`: `tvMaterialsTotal`, `tvSalariesTotal`, `tvRoomTotal`.
    - Разделители между ними — `View` шириной `@dimen/sticky_bar_divider_width`, высотой `@dimen/sticky_bar_divider_height`.
    - `tvRoomTotal` — `android:textStyle="bold"`, цвет акцента.

- [ ] **6.6.2** Создать `res/layout/block_room_dimensions.xml` — `ConstraintLayout`:
  - `EditText` с id: `etLength`, `etWidth`, `etHeight` — `inputType="numberDecimal"`.
  - `TextView` подписи рядом с каждым полем.
  - Все размеры через `@dimen/`.

- [ ] **6.6.3** Создать `res/layout/block_openings.xml` — `ConstraintLayout`:
  - `ChipGroup` с id `chipGroupOpenings` для отображения проёмов.
  - `Button` с id `btnAddOpening` — «Добавить проём».

### 6.7 RoomDetailFragment (ui/room/)

- [ ] **6.7.1** Создать `ui/room/RoomDetailFragment.java` extends `Fragment`:
  - Инфлейтить `fragment_room_detail.xml`.
  - Инициализировать `RoomDetailViewModel` через `ViewModelProvider`.
  - Настроить `rvEstimateItems`: `LinearLayoutManager` + `EstimateAdapter`.
  - **Подписки на LiveData:**
    ```java
    roomViewModel.estimateItems.observe(getViewLifecycleOwner(), items -> {
        estimateAdapter.submitList(items);
    });
    roomViewModel.roomTotals.observe(getViewLifecycleOwner(), totals -> {
        tvMaterialsTotal.setText(formatRub(totals.materialsTotal));
        tvSalariesTotal.setText(formatRub(totals.salariesTotal));
        tvRoomTotal.setText(formatRub(totals.roomTotal));
    });
    ```

- [ ] **6.7.2** Реализовать Edit Warning перед изменением размеров комнаты:
  - `etLength.setOnFocusChangeListener((v, hasFocus) -> { ... })`.
  - Если `hasFocus == true` и `viewModel.hasEstimateItems()` — показать `MaterialAlertDialogBuilder`:
    - Заголовок: `@string/warning_recalc_title`.
    - Сообщение: `@string/warning_recalc_message`.
    - Позитивная кнопка — разрешить редактирование.
    - Негативная кнопка — `etLength.clearFocus()`.
  - Аналогично для `etWidth` и `etHeight`.

- [ ] **6.7.3** Подписаться на `roomViewModel.errorMessage`:
  - Показывать `Snackbar` с текстом ошибки (не `Toast` — Snackbar совместим с `CoordinatorLayout`).

### 6.8 ProjectListFragment (ui/project/)

- [ ] **6.8.1** Создать `res/layout/fragment_project_list.xml` — `ConstraintLayout`:
  - `RecyclerView` с id `rvProjects`.
  - `FloatingActionButton` с id `fabAddProject`.
  - В тулбаре — место для `SyncStatusView` (кастомная вью или `ImageView` + `TextView` badge).

- [ ] **6.8.2** Создать `res/layout/item_project.xml` — `ConstraintLayout`:
  - `TextView`: `tvProjectName`, `tvProjectCity`, `tvProjectUpdatedAt`.
  - `ImageView` с id `ivSyncDot` — маленький индикатор `syncState` (аналог `statusDot`). Указать `contentDescription`.

- [ ] **6.8.3** Создать `ui/project/ProjectAdapter.java` — аналогично `EstimateAdapter`:
  - `DiffUtil.Callback` сравнивает по `id`; `areContentsTheSame` — по `name`, `updatedAt`, `syncState`.
  - `onBindViewHolder` выставляет `tvProjectName`, `tvProjectCity`, `tvProjectUpdatedAt`.
  - Долгое нажатие — диалог удаления.

- [ ] **6.8.4** Создать `ui/project/ProjectListFragment.java` extends `Fragment`:
  - `ViewModelProvider` → `ProjectListViewModel`.
  - `projectListViewModel.projects.observe(...)` → `projectAdapter.submitList(items)`.
  - `fabAddProject.setOnClickListener` → показать `DialogFragment` или `BottomSheetDialogFragment` для создания проекта.
  - Встроить `SyncStatusView` — вызвать `syncStatusView.bind(syncViewModel, getViewLifecycleOwner())`.

### 6.9 ConflictResolutionActivity (ui/conflict/)

- [ ] **6.9.1** Создать `res/layout/activity_conflict_resolution.xml` — `ConstraintLayout`:
  - Два блока бок о бок (`ConstraintLayout` или `LinearLayout` с `weightSum`):
    - Левый — «Моя версия»: `TextView` с полями `quantity`, `totalPrice`, `updatedAt`.
    - Правый — «Версия сервера»: аналогично.
  - Три кнопки: `btnKeepLocal` (`@string/btn_keep_local`), `btnAcceptServer` (`@string/btn_accept_server`), `btnCancel` (`@string/btn_cancel`).

- [ ] **6.9.2** Создать `ui/conflict/ConflictResolutionActivity.java` extends `AppCompatActivity`:
  - Получить `entityId` из `Intent.getStringExtra("entity_id")`.
  - Инициализировать `SyncViewModel` через `ViewModelProvider`.
  - `syncViewModel.conflicts.observe(...)` — найти конфликт по `entityId`, отобразить оба снимка.
  - Логика кнопок:
    - `btnKeepLocal.setOnClickListener` → `conflictRepository.resolveKeepLocal(entityId, serverVersion)` через `AppExecutors.diskIO()` → `finish()`.
    - `btnAcceptServer.setOnClickListener` → `conflictRepository.resolveAcceptServer(entityId, serverSnapshotJson)` → `finish()`.
    - `btnCancel.setOnClickListener` → `finish()` (syncState остаётся `CONFLICT`).
  - Зарегистрировать Activity в `AndroidManifest.xml`.

### 6.10 SyncStatusView — встройка в тулбар MainActivity

- [ ] **6.10.1** Создать `res/layout/activity_main.xml` — с `Toolbar` и `NavHostFragment`.
- [ ] **6.10.2** В `MainActivity.java`:
  - Инфлейтить `SyncStatusView` и добавить в `Toolbar` через `toolbar.addView(syncStatusView)`.
  - Инициализировать `SyncViewModel`, вызвать `syncStatusView.bind(syncViewModel, this)`.
  - При клике на `SyncStatusView` с `syncState == CONFLICT` — запустить `ConflictResolutionActivity`.

### 6.11 Room Migration — напоминание

- [ ] **6.11.1** При любом изменении схемы БД (добавление поля/индекса) — создать `Migration(oldVersion, newVersion)` и зарегистрировать в `AppDatabase.Builder`:
  ```java
  Room.databaseBuilder(context, AppDatabase.class, "smetrix.db")
      .addMigrations(MIGRATION_1_2)
      .build();
  ```
  Никогда не использовать `fallbackToDestructiveMigration()` в продакшне.

### 6.12 Финальная проверка Фазы 6

- [ ] **6.12.1** Запустить приложение на эмуляторе. Проверить:
  - Список проектов загружается из Room через LiveData.
  - Sticky Bottom Bar остаётся внизу при прокрутке длинного списка сметы.
  - Изменение размеров комнаты → диалог предупреждения → пересчёт → обновление RecyclerView через DiffUtil (только изменившиеся строки мигают).
  - `SyncStatusView` меняет иконку в соответствии с состоянием синхронизации.
- [ ] **6.12.2** Проверить доступность (Accessibility):
  - Все `ImageView` имеют `contentDescription`.
  - Контрастность текста на Sticky Bottom Bar соответствует WCAG AA.
- [ ] **6.12.3** Проверить UTF-8: создать проект с кириллическим названием → убедиться, что Room и Retrofit корректно сохраняют и передают данные.
- [ ] **6.12.4** Финальный `Build → Clean → Rebuild Project`. Убедиться, что нет `@SuppressWarnings("unchecked")` без комментария и нет пустых `catch`-блоков.
- [ ] **6.12.5** Сверить список реализованных правил с `.cursorrules`:
  - [ ] Java only, no Kotlin.
  - [ ] XML Layouts only, no Compose.
  - [ ] Никакие DB-операции не выполняются на Main Thread.
  - [ ] `@Transaction` на `updateDimensionsAndRecalculate()`.
  - [ ] `EncryptedSharedPreferences` для токенов.
  - [ ] Нет хардкодинга строк в Java: все через `strings.xml` / ENUM-константы.
  - [ ] Специфичные `catch`-блоки везде, нет пустых.
  - [ ] `contentDescription` на всех `ImageView`.
  - [ ] `ConstraintLayout` для всех сложных макетов.
  - [ ] Все размеры в `dimens.xml`, стили в `themes.xml`.

---
