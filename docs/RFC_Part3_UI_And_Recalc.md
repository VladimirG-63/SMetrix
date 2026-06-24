# RFC — Smetrix: Часть 3. UI и Логика пересчёта

---

## 1. RecyclerView для сметы с DiffUtil

### 1.1 Модель отображения (Display Model)

```java
// model/EstimateItemDisplay.java
public class EstimateItemDisplay {
    public String id;
    public String name;
    public String unitMeasure;
    public BigDecimal quantity;
    public BigDecimal finalPrice;
    public BigDecimal totalPrice;
    public String status;          // NEED_TO_BUY | ORDERED | ON_SITE | UNITS_MISMATCH
    public String syncState;
}
```

> [!NOTE]
> ViewModel маппит `EstimateItemEntity` → `EstimateItemDisplay` перед передачей в адаптер. UI-слой никогда не видит Entity напрямую.

---

### 1.2 DiffUtil.Callback

```java
// ui/room/EstimateDiffCallback.java
public class EstimateDiffCallback extends DiffUtil.Callback {

    private final List<EstimateItemDisplay> oldList;
    private final List<EstimateItemDisplay> newList;

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        // Сравниваем по UUID — определяет, та же ли это строка
        return oldList.get(oldPos).id.equals(newList.get(newPos).id);
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        EstimateItemDisplay o = oldList.get(oldPos);
        EstimateItemDisplay n = newList.get(newPos);
        // Сравниваем все поля, влияющие на отображение
        return o.name.equals(n.name)
            && o.quantity.compareTo(n.quantity) == 0
            && o.totalPrice.compareTo(n.totalPrice) == 0
            && o.status.equals(n.status)
            && o.syncState.equals(n.syncState);
    }

    @Nullable
    @Override
    public Object getChangePayload(int oldPos, int newPos) {
        // Частичное обновление — перерисовываем только изменившиеся поля
        Bundle diff = new Bundle();
        EstimateItemDisplay o = oldList.get(oldPos);
        EstimateItemDisplay n = newList.get(newPos);
        if (o.quantity.compareTo(n.quantity) != 0) diff.putString("quantity", n.quantity.toPlainString());
        if (o.totalPrice.compareTo(n.totalPrice) != 0) diff.putString("total_price", n.totalPrice.toPlainString());
        if (!o.status.equals(n.status)) diff.putString("status", n.status);
        return diff.isEmpty() ? null : diff;
    }
}
```

---

### 1.3 Адаптер

```java
// ui/room/EstimateAdapter.java
public class EstimateAdapter extends RecyclerView.Adapter<EstimateAdapter.ViewHolder> {

    private List<EstimateItemDisplay> items = new ArrayList<>();
    private final OnItemActionListener listener;

    /** Вызывается из Fragment при каждом обновлении LiveData */
    public void submitList(List<EstimateItemDisplay> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(
            new EstimateDiffCallback(items, newList));
        items = new ArrayList<>(newList);
        result.dispatchUpdatesTo(this);     // только изменившиеся строки перерисуются
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (!payloads.isEmpty()) {
            // Частичное обновление — не вызываем полный bind
            Bundle diff = (Bundle) payloads.get(0);
            if (diff.containsKey("quantity"))
                holder.tvQuantity.setText(diff.getString("quantity"));
            if (diff.containsKey("total_price"))
                holder.tvTotalPrice.setText(formatPrice(diff.getString("total_price")));
            if (diff.containsKey("status"))
                holder.updateStatusIndicator(diff.getString("status"));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        EstimateItemDisplay item = items.get(position);
        holder.tvName.setText(item.name);
        holder.tvUnit.setText(item.unitMeasure);
        holder.tvQuantity.setText(item.quantity.setScale(2, HALF_UP).toPlainString());
        holder.tvPrice.setText(formatPrice(item.finalPrice));
        holder.tvTotal.setText(formatPrice(item.totalPrice));
        holder.updateStatusIndicator(item.status);

        // Долгое нажатие — batch selection
        holder.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(item.id);
            return true;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvUnit, tvQuantity, tvPrice, tvTotal;
        View statusDot;

        void updateStatusIndicator(String status) {
            int color;
            switch (status) {
                case "ORDERED":       color = Color.parseColor("#FF9800"); break; // жёлтый
                case "ON_SITE":       color = Color.parseColor("#4CAF50"); break; // зелёный
                case "UNITS_MISMATCH":color = Color.parseColor("#F44336"); break; // красный
                default:              color = Color.parseColor("#9E9E9E"); break; // серый
            }
            statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    public interface OnItemActionListener {
        void onLongClick(String itemId);
        void onStatusChange(String itemId, String newStatus);
    }
}
```

---

### 1.4 Fragment — подписка на LiveData

```java
// ui/room/RoomDetailFragment.java (только часть observe)
roomViewModel.estimateItems.observe(getViewLifecycleOwner(), items -> {
    estimateAdapter.submitList(items);              // DiffUtil внутри
    // Sticky Bottom Bar обновляется реактивно
});

roomViewModel.roomTotals.observe(getViewLifecycleOwner(), totals -> {
    tvMaterials.setText(formatRub(totals.materialsTotal));
    tvSalaries.setText(formatRub(totals.salariesTotal));
    tvRoomTotal.setText(formatRub(totals.roomTotal));
});
```

---

## 2. Sticky Bottom Bar в XML

### 2.1 Структура макета `fragment_room_detail.xml`

```xml
<!-- fragment_room_detail.xml -->
<androidx.coordinatorlayout.widget.CoordinatorLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Скроллируемый контент комнаты -->
    <androidx.core.widget.NestedScrollView
        android:id="@+id/scrollContent"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:paddingBottom="80dp"
        android:clipToPadding="false">

        <LinearLayout
            android:orientation="vertical"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <!-- Блок размеров комнаты -->
            <include layout="@layout/block_room_dimensions" />

            <!-- Список проёмов (Material Chips) -->
            <include layout="@layout/block_openings" />

            <!-- RecyclerView — смета материалов -->
            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rvEstimateItems"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:nestedScrollingEnabled="false" />

            <!-- RecyclerView — зарплаты -->
            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rvWorkTasks"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:nestedScrollingEnabled="false" />

        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

    <!-- ───── Sticky Bottom Bar ───── -->
    <LinearLayout
        android:id="@+id/stickyBottomBar"
        android:layout_width="match_parent"
        android:layout_height="64dp"
        android:layout_gravity="bottom"
        android:background="#0D47A1"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:elevation="8dp">

        <TextView
            android:id="@+id/tvMaterialsTotal"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:textColor="#FFFFFF"
            android:textSize="12sp"
            android:text="МАТЕРИАЛЫ\n0 ₽" />

        <View
            android:layout_width="1dp"
            android:layout_height="32dp"
            android:background="#3D5AFE" />

        <TextView
            android:id="@+id/tvSalariesTotal"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:textColor="#FFFFFF"
            android:textSize="12sp"
            android:gravity="center"
            android:text="ЗАРПЛАТЫ\n0 ₽" />

        <View
            android:layout_width="1dp"
            android:layout_height="32dp"
            android:background="#3D5AFE" />

        <TextView
            android:id="@+id/tvRoomTotal"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:textColor="#FF9800"
            android:textStyle="bold"
            android:textSize="12sp"
            android:gravity="end"
            android:text="ИТОГО\n0 ₽" />

    </LinearLayout>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

> [!IMPORTANT]
> `android:paddingBottom="80dp"` на `NestedScrollView` гарантирует, что последний элемент списка не перекрывается Sticky Bar при прокрутке до конца. `android:clipToPadding="false"` позволяет контенту рисоваться под паддингом.

---

### 2.2 Почему `CoordinatorLayout` + `layout_gravity="bottom"`

| Подход | Проблема |
|---|---|
| `RelativeLayout` + `alignParentBottom` | Работает, но теряет совместимость с `AppBarLayout` и `FAB` |
| `ConstraintLayout` | Сложнее с динамической высотой контента |
| **`CoordinatorLayout` + `layout_gravity="bottom"`** | ✅ Нативно поддерживает `elevation`, не зависит от длины списка, совместим с `Snackbar` |

---

## 3. Алгоритм пересчёта площади и стоимости

### 3.1 Формулы

```
roughWallArea   = (length + width) × 2 × height
openingsArea    = SUM(opening.width × opening.height)
netWallArea     = roughWallArea − openingsArea

effectiveArea   = manualAreaOverride ?? netWallArea

// Для каждого EstimateItem:
quantity        = effectiveArea × consumptionRate
totalPrice      = finalPrice × quantity

// Для каждого WorkTask (сдельная):
totalPayment    = effectiveArea × rateValue
```

---

### 3.2 Trigger: Edit Warning перед изменением размеров

```java
// ui/room/RoomDetailFragment.java
etLength.setOnFocusChangeListener((v, hasFocus) -> {
    if (!hasFocus) return;
    boolean roomHasData = viewModel.hasEstimateItems() || viewModel.hasWorkTasks();
    if (roomHasData) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Внимание!")
            .setMessage("Изменение размеров приведёт к автоматическому пересчёту " +
                        "материалов и заработной платы рабочих. Продолжить?")
            .setPositiveButton("Продолжить", (d, w) -> etLength.setEnabled(true))
            .setNegativeButton("Отмена", (d, w) -> etLength.clearFocus())
            .show();
    }
});
```

---

### 3.3 DAO — методы для транзакции

```java
// db/dao/ProjectRoomDao.java
@Dao
public interface ProjectRoomDao {

    @Query("UPDATE project_room SET length=:l, width=:w, height=:h, " +
           "updated_at=:ts, version=version+1, sync_state='PENDING_UPDATE' WHERE id=:id")
    void updateDimensions(String id, BigDecimal l, BigDecimal w, BigDecimal h, long ts);

    @Query("SELECT * FROM opening WHERE project_room_id = :roomId")
    List<OpeningEntity> getOpeningsSync(String roomId);

    @Query("SELECT * FROM estimate_item WHERE project_room_id = :roomId")
    List<EstimateItemEntity> getEstimateItemsSync(String roomId);

    @Query("SELECT * FROM work_task WHERE project_room_id = :roomId AND rate_type = 'PIECEWORK'")
    List<WorkTaskEntity> getPieceworkTasksSync(String roomId);
}

// db/dao/EstimateItemDao.java
@Dao
public interface EstimateItemDao {
    @Query("UPDATE estimate_item SET quantity=:q, total_price=:tp, " +
           "updated_at=:ts, sync_state='PENDING_UPDATE' WHERE id=:id")
    void updateQuantityAndTotal(String id, BigDecimal q, BigDecimal tp, long ts);
}
```

---

### 3.4 @Transaction — полный пересчёт

```java
// repository/RoomRepository.java
@Transaction
public void updateDimensionsAndRecalculate(
        String roomId,
        BigDecimal newLength, BigDecimal newWidth, BigDecimal newHeight) {

    long now = System.currentTimeMillis();

    // 1. Обновляем размеры комнаты
    projectRoomDao.updateDimensions(roomId, newLength, newWidth, newHeight, now);

    // 2. Считаем черновую площадь
    BigDecimal roughArea = (newLength.add(newWidth))
        .multiply(BigDecimal.valueOf(2))
        .multiply(newHeight);

    // 3. Вычитаем проёмы
    List<OpeningEntity> openings = projectRoomDao.getOpeningsSync(roomId);
    BigDecimal openingsArea = openings.stream()
        .map(o -> o.width.multiply(o.height))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal netArea = roughArea.subtract(openingsArea)
        .max(BigDecimal.ZERO);  // защита от отрицательной площади

    // 4. Получаем effectiveArea (manualOverride или расчётная)
    ProjectRoomEntity room = projectRoomDao.getById(roomId);
    BigDecimal effectiveArea = (room.manualAreaOverride != null)
        ? room.manualAreaOverride
        : netArea;

    // 5. Пересчитываем каждый EstimateItem
    List<EstimateItemEntity> items = estimateItemDao.getEstimateItemsSync(roomId);
    for (EstimateItemEntity item : items) {
        BigDecimal newQty = effectiveArea
            .multiply(item.consumptionRate)
            .setScale(6, HALF_UP);
        BigDecimal newTotal = item.finalPrice
            .multiply(newQty)
            .setScale(2, HALF_UP);
        estimateItemDao.updateQuantityAndTotal(item.id, newQty, newTotal, now);
    }

    // 6. Пересчитываем сдельные WorkTask
    List<WorkTaskEntity> tasks = workTaskDao.getPieceworkTasksSync(roomId);
    for (WorkTaskEntity task : tasks) {
        BigDecimal newPayment = effectiveArea
            .multiply(task.rateValue)
            .setScale(2, HALF_UP);
        workTaskDao.updatePayment(task.id, newPayment, now);
    }

    // 7. Помечаем комнату на синхронизацию
    syncManager.scheduleSync();
}
```

> [!IMPORTANT]
> Весь метод выполняется внутри одной транзакции Room. Если любой шаг упадёт — все изменения откатятся. UI обновится реактивно через LiveData только после полного коммита.

---

### 3.5 Поток пересчёта от UI до LiveData

```
Пользователь подтвердил изменение размеров
        │
        ▼
RoomDetailViewModel.updateDimensions(l, w, h)
        │  (Executor / фоновый поток — не UI)
        ▼
RoomRepository.updateDimensionsAndRecalculate()  ← @Transaction
   ├── updateDimensions()
   ├── пересчёт netArea
   ├── EstimateItem × N → updateQuantityAndTotal()
   └── WorkTask × M   → updatePayment()
        │
        ▼  Room коммитит транзакцию
        │
LiveData<List<EstimateItemDisplay>> эмитит новый список
        │
EstimateAdapter.submitList() → DiffUtil → только изменившиеся строки перерисовываются
        │
LiveData<RoomTotals> эмитит → Sticky Bottom Bar обновляет суммы
```

---

### 3.6 RoomTotals — агрегированный запрос для Bottom Bar

```java
// db/dao/EstimateItemDao.java
@Query("SELECT " +
       "  COALESCE(SUM(total_price), 0) AS materialsTotal, " +
       "  0 AS salariesTotal " +
       "FROM estimate_item WHERE project_room_id = :roomId")
LiveData<RoomTotals> getMaterialsTotal(String roomId);

// db/dao/WorkTaskDao.java
@Query("SELECT COALESCE(SUM(total_payment), 0) AS salariesTotal " +
       "FROM work_task WHERE project_room_id = :roomId")
LiveData<BigDecimal> getSalariesTotal(String roomId);
```

```java
// viewmodel/RoomDetailViewModel.java
// Объединяем два LiveData в один через MediatorLiveData
MediatorLiveData<RoomTotals> roomTotals = new MediatorLiveData<>();

roomTotals.addSource(materialsTotalLd, mat -> recalcTotals());
roomTotals.addSource(salariesTotalLd,  sal -> recalcTotals());

private void recalcTotals() {
    BigDecimal mat = materialsTotalLd.getValue();
    BigDecimal sal = salariesTotalLd.getValue();
    if (mat == null || sal == null) return;
    BigDecimal total = mat.add(sal).setScale(0, HALF_UP);
    roomTotals.setValue(new RoomTotals(mat, sal, total));
}
```

---

> [!TIP]
> **Следующие части RFC:**
> - Часть 4: DAO-интерфейсы, миграции Room, AppDatabase
> - Часть 5: Network-слой — Retrofit, DTO, JWT-перехватчик
> - Часть 6: Серверный парсер ФГИС ЦС (Apache POI Streaming API)
