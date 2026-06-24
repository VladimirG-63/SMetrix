// app/src/main/java/com/smetrix/app/viewmodel/SyncViewModel.java
package com.smetrix.app.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.entity.ConflictEntity;
import com.smetrix.app.model.SyncStatusResult;
import com.smetrix.app.network.sync.SyncManager;

import androidx.work.WorkManager;

import java.util.List;

/**
 * ViewModel для экрана (или панели) управления синхронизацией.
 *
 * <p><b>Архитектурная роль (MVVM):</b><br>
 * {@code SyncViewModel} агрегирует информацию о глобальном состоянии синхронизации
 * и о неразрешённых конфликтах, предоставляя UI реактивные данные через {@link LiveData}.
 * Дополнительно предоставляет команду {@link #forceSyncNow()} для запуска
 * немедленной принудительной синхронизации по нажатию кнопки «Синхронизировать».
 *
 * <p><b>Источники данных:</b>
 * <ul>
 *   <li>{@link #syncStatus} — из {@code SyncStatusDao.getGlobalSyncStatus()}.
 *       Это агрегирующий SQL-запрос через {@code UNION ALL} по четырём таблицам
 *       (project, project_room, estimate_item, work_task). Результат содержит
 *       два поля: {@code globalStatus} (строка: CONFLICT/FAILED/PENDING/SYNCED)
 *       и {@code conflictCount} (int: количество конфликтующих записей).</li>
 *   <li>{@link #conflicts} — из {@code ConflictDao.getAll()}.
 *       Полный список объектов {@link ConflictEntity}, ожидающих ручного
 *       разрешения пользователем.</li>
 * </ul>
 *
 * <p><b>Команда forceSyncNow():</b><br>
 * Вызывает {@link SyncManager#scheduleSyncForce()}, который планирует однократную
 * задачу {@code SyncWorker} через WorkManager с политикой {@code REPLACE}.
 * Это означает, что любая ожидающая задача синхронизации будет заменена новой.
 *
 * <p><b>Типичный сценарий использования:</b>
 * <ol>
 *   <li>Иконка синхронизации в Toolbar отображает бейдж с количеством конфликтов.</li>
 *   <li>Пользователь нажимает кнопку «Синхронизировать» → вызывается {@link #forceSyncNow()}.</li>
 *   <li>После синхронизации {@code syncStatus} автоматически обновляется до {@code SYNCED}.</li>
 *   <li>При конфликтах {@code conflicts} заполняется → экран разрешения конфликтов.</li>
 * </ol>
 *
 * <p><b>Пример использования в Fragment:</b>
 * <pre>
 *   SyncViewModel vm = new ViewModelProvider(this).get(SyncViewModel.class);
 *
 *   vm.getSyncStatus().observe(getViewLifecycleOwner(), new Observer&lt;SyncStatusResult&gt;() {
 *       {@literal @}Override
 *       public void onChanged(SyncStatusResult result) {
 *           if (result != null) {
 *               syncIcon.setStatus(result.globalStatus);
 *               badgeCount.setText(String.valueOf(result.conflictCount));
 *           }
 *       }
 *   });
 *
 *   syncNowButton.setOnClickListener(new View.OnClickListener() {
 *       {@literal @}Override
 *       public void onClick(View v) {
 *           vm.forceSyncNow();
 *       }
 *   });
 * </pre>
 *
 * @see com.smetrix.app.db.dao.SyncStatusDao
 * @see com.smetrix.app.db.dao.ConflictDao
 * @see SyncManager
 * @see SyncStatusResult
 */
public class SyncViewModel extends AndroidViewModel {

    // ─────────────────────────────────────────────────────────────────────────
    // Константы
    // ─────────────────────────────────────────────────────────────────────────

    /** Тег для LogCat-сообщений. */
    private static final String TAG = "SyncViewModel";

    // ─────────────────────────────────────────────────────────────────────────
    // Зависимости
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Менеджер синхронизации для планирования задач через WorkManager.
     * Используется только методом {@link #forceSyncNow()}.
     */
    private final SyncManager syncManager;

    // ─────────────────────────────────────────────────────────────────────────
    // LiveData — данные для UI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Глобальный статус синхронизации всего приложения.
     *
     * <p>Получается из {@code SyncStatusDao.getGlobalSyncStatus()} — агрегирующего
     * SQL-запроса, который через {@code UNION ALL} объединяет поля {@code sync_state}
     * из четырёх таблиц и определяет приоритетный статус через {@code CASE WHEN}.
     *
     * <p>Возможные значения {@link SyncStatusResult#globalStatus}:
     * <ul>
     *   <li>{@code "CONFLICT"} — есть хотя бы одна запись с конфликтом.</li>
     *   <li>{@code "FAILED"}   — есть ошибки синхронизации (без конфликтов).</li>
     *   <li>{@code "PENDING"}  — есть записи, ожидающие отправки на сервер.</li>
     *   <li>{@code "SYNCED"}   — все записи синхронизированы.</li>
     * </ul>
     *
     * <p>Обновляется автоматически при любом изменении в таблицах
     * {@code project}, {@code project_room}, {@code estimate_item}, {@code work_task}.
     */
    private final LiveData<SyncStatusResult> syncStatus;

    /**
     * Список всех неразрешённых конфликтов синхронизации.
     *
     * <p>Получается из {@code ConflictDao.getAll()}. Каждый {@link ConflictEntity}
     * содержит идентификатор конфликтующей записи, снимок серверных данных и
     * метаданные для разрешения конфликта.
     *
     * <p>UI (экран разрешения конфликтов) подписывается на этот LiveData
     * и отображает список конфликтов пользователю для ручного выбора версии:
     * «Оставить локальную» или «Принять серверную».
     *
     * <p>Обновляется автоматически при каждом изменении таблицы {@code conflict}
     * (вставка нового конфликта SyncWorker'ом или удаление при разрешении).
     */
    private final LiveData<List<ConflictEntity>> conflicts;

    /**
     * Количество неразрешённых конфликтов для отображения бейджа на иконке синхронизации.
     *
     * <p>Получается из {@code ConflictDao.getCount()}.
     * Обновляется автоматически при добавлении / удалении записей в таблице conflict.
     * Используется UI для отображения числа на бейдже иконки синхронизации.
     */
    private final LiveData<Integer> conflictCount;

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Конструктор ViewModel, автоматически вызываемый фреймворком через
     * {@link androidx.lifecycle.ViewModelProvider}.
     *
     * <p>Инициализирует:
     * <ul>
     *   <li>{@link AppDatabase} через {@link AppDatabase#getInstance(android.content.Context)}.</li>
     *   <li>{@link SyncManager} через {@link WorkManager#getInstance(android.content.Context)}.</li>
     *   <li>{@code syncStatus} — из {@code SyncStatusDao.getGlobalSyncStatus()}.</li>
     *   <li>{@code conflicts} — из {@code ConflictDao.getAll()}.</li>
     *   <li>{@code conflictCount} — из {@code ConflictDao.getCount()}.</li>
     * </ul>
     *
     * @param application экземпляр {@link Application}, предоставляемый Android фреймворком.
     *                    Никогда не бывает {@code null}.
     */
    public SyncViewModel(@NonNull Application application) {
        super(application);

        Log.d(TAG, "Инициализация SyncViewModel.");

        // ── Шаг 1: Получаем AppDatabase ───────────────────────────────────────
        AppDatabase database = AppDatabase.getInstance(application);

        // ── Шаг 2: Строим SyncManager ─────────────────────────────────────────
        // WorkManager.getInstance() — потокобезопасный singleton.
        WorkManager workManager = WorkManager.getInstance(application);
        this.syncManager = new SyncManager(workManager);

        // ── Шаг 3: Получаем LiveData глобального статуса синхронизации ────────
        // SyncStatusDao выполняет сложный агрегирующий SQL через UNION ALL.
        // Room сам управляет подпиской на изменения всех четырёх таблиц.
        this.syncStatus = database.syncStatusDao().getGlobalSyncStatus();

        // ── Шаг 4: Получаем LiveData списка конфликтов ────────────────────────
        // ConflictDao.getAll() возвращает все записи таблицы «conflict».
        // LiveData обновляется при каждом INSERT/DELETE в этой таблице.
        this.conflicts = database.conflictDao().getAll();

        // ── Шаг 5: Получаем LiveData счётчика конфликтов (для бейджа) ─────────
        this.conflictCount = database.conflictDao().getCount();

        Log.d(TAG, "SyncViewModel успешно инициализирован.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичные методы — геттеры LiveData
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает реактивный глобальный статус синхронизации приложения.
     *
     * <p>Fragment подписывается через:
     * <pre>
     *   vm.getSyncStatus().observe(viewLifecycleOwner, new Observer&lt;SyncStatusResult&gt;() {
     *       {@literal @}Override
     *       public void onChanged(SyncStatusResult result) { ... }
     *   });
     * </pre>
     *
     * <p>Гарантируется, что Room эмитирует {@code SyncStatusResult} с
     * {@code globalStatus = "SYNCED"} и {@code conflictCount = 0} даже
     * при пустых таблицах (благодаря {@code COALESCE} в SQL-запросе).
     *
     * @return {@link LiveData} с объектом {@link SyncStatusResult}.
     *         Никогда не возвращает {@code null}.
     */
    public LiveData<SyncStatusResult> getSyncStatus() {
        return syncStatus;
    }

    /**
     * Возвращает реактивное количество неразрешённых конфликтов для бейджа UI.
     *
     * <p>Автоматически обновляется при добавлении / удалении конфликтов.
     *
     * @return {@code LiveData<Integer>} — число конфликтов, никогда не {@code null}.
     */
    public LiveData<Integer> getConflictCount() {
        return conflictCount;
    }

    /**
     * Возвращает реактивный список всех неразрешённых конфликтов синхронизации.
     *
     * @return {@link LiveData} со списком {@link ConflictEntity}.
     *         Пустой список означает отсутствие конфликтов. Никогда не {@code null}.
     */
    public LiveData<List<ConflictEntity>> getConflicts() {
        return conflicts;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичные методы — команды от UI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Запускает немедленную принудительную синхронизацию с сервером.
     *
     * <p>Вызывает {@link SyncManager#scheduleSyncForce()}, который ставит
     * в очередь WorkManager однократную задачу {@code SyncWorker} с политикой
     * {@code ExistingWorkPolicy.REPLACE}. Это означает:
     * <ul>
     *   <li>Если задача синхронизации уже ожидает в очереди (ENQUEUED) —
     *       она будет <b>заменена</b> новой немедленной задачей.</li>
     *   <li>Если задача уже выполняется (RUNNING) — она будет прервана
     *       и заменена.</li>
     *   <li>Ограничение {@code NetworkType.CONNECTED} по-прежнему действует:
     *       задача выполнится только при наличии сетевого подключения.</li>
     * </ul>
     *
     * <p>Этот метод вызывается из Fragment по нажатию кнопки
     * «Синхронизировать сейчас» в UI toolbar или на экране синхронизации.
     *
     * <p><b>Нет блокировки UI:</b> метод мгновенно планирует задачу через
     * WorkManager и возвращает управление. Фактическая синхронизация
     * происходит асинхронно в {@code SyncWorker}.
     */
    public void forceSyncNow() {
        Log.d(TAG, "forceSyncNow: планируем принудительную синхронизацию (REPLACE).");

        // Делегируем вызов SyncManager.scheduleSyncForce().
        // Этот метод планирует OneTimeWorkRequest с ExistingWorkPolicy.REPLACE,
        // заменяя любую ожидающую задачу с именем «SYNC_ONE_TIME».
        syncManager.scheduleSyncForce();

        Log.d(TAG, "forceSyncNow: задача SyncWorker поставлена в очередь WorkManager.");
    }
}
