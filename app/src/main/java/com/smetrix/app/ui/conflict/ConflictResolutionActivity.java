// app/src/main/java/com/smetrix/app/ui/conflict/ConflictResolutionActivity.java
package com.smetrix.app.ui.conflict;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.smetrix.app.R;
import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.entity.ConflictEntity;
import com.smetrix.app.repository.AppExecutors;
import com.smetrix.app.repository.ConflictRepository;
import com.smetrix.app.network.sync.SyncManager;
import com.smetrix.app.viewmodel.SyncViewModel;

import java.util.List;

import androidx.work.WorkManager;

/**
 * Экран разрешения конфликта синхронизации.
 *
 * <p><b>Запуск:</b>
 * <pre>
 *   Intent intent = new Intent(context, ConflictResolutionActivity.class);
 *   intent.putExtra(ConflictResolutionActivity.EXTRA_ENTITY_ID, conflictEntity.entityId);
 *   context.startActivity(intent);
 * </pre>
 *
 * <p><b>Как Activity парсит и отображает JSON-снимки конфликта:</b>
 * <ol>
 *   <li>Из {@link android.content.Intent} извлекается строковый {@code entity_id}.</li>
 *   <li>Activity подписывается на {@link SyncViewModel#getConflicts()} и ищет
 *       в списке {@link ConflictEntity} запись с совпадающим {@code entityId}.</li>
 *   <li>Найденный {@link ConflictEntity#localSnapshot} и {@link ConflictEntity#serverSnapshot}
 *       — это сырые JSON-строки, сохранённые {@code SyncWorker}.</li>
 *   <li>JSON парсится через {@code JsonParser.parseString()} → {@link JsonElement}.
 *       Если JSON корректный объект, он форматируется через
 *       {@code GsonBuilder().setPrettyPrinting().create().toJson(element)},
 *       чтобы получить читаемый (pretty-printed) текст.</li>
 *   <li>Готовый текст устанавливается в {@code tvLocalSnapshot} и {@code tvServerSnapshot}.</li>
 * </ol>
 *
 * <p><b>Разрешение конфликта:</b>
 * <ul>
 *   <li>{@code btnKeepLocal}    → {@link ConflictRepository#resolveKeepLocal} на diskIO → finish()</li>
 *   <li>{@code btnAcceptServer} → {@link ConflictRepository#resolveAcceptServer} на diskIO → finish()</li>
 *   <li>{@code btnCancel}       → finish() (syncState остаётся CONFLICT)</li>
 * </ul>
 */
public class ConflictResolutionActivity extends AppCompatActivity {

    // ── Константы ────────────────────────────────────────────────────────────

    private static final String TAG = "ConflictResolutionAct";

    /** Ключ Intent-extra для передачи entityId конфликтующей записи. */
    public static final String EXTRA_ENTITY_ID = "entity_id";

    // ── Зависимости ──────────────────────────────────────────────────────────

    private SyncViewModel      syncViewModel;
    private ConflictRepository conflictRepository;

    /** Gson с pretty-printing для форматирования JSON-снимков. */
    private final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

    // ── Состояние ─────────────────────────────────────────────────────────────

    /** entityId, переданный через Intent. Не null после onCreate. */
    private String entityId;

    /**
     * Серверная версия конфликтующей записи.
     * Извлекается из {@link ConflictEntity#serverSnapshot} для передачи
     * в {@link ConflictRepository#resolveKeepLocal(String, long)}.
     */
    private long serverVersion = 0L;

    /**
     * Сырой JSON серверного снимка.
     * Используется для {@link ConflictRepository#resolveAcceptServer(String, String)}.
     */
    private String serverSnapshotJson = null;

    // ── Виджеты ───────────────────────────────────────────────────────────────

    private TextView       tvEntityInfo;
    private TextView       tvLocalSnapshot;
    private TextView       tvServerSnapshot;
    private MaterialButton btnKeepLocal;
    private MaterialButton btnAcceptServer;
    private MaterialButton btnCancel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conflict_resolution);

        // ── Получаем entityId из Intent ──────────────────────────────────────
        entityId = getIntent().getStringExtra(EXTRA_ENTITY_ID);
        if (entityId == null || entityId.isEmpty()) {
            Log.e(TAG, "onCreate: EXTRA_ENTITY_ID не передан! Закрываем Activity.");
            finish();
            return;
        }
        Log.d(TAG, "onCreate: entityId=" + entityId);

        // ── Toolbar ──────────────────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbarConflict);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // ── Виджеты ──────────────────────────────────────────────────────────
        tvEntityInfo     = findViewById(R.id.tvEntityInfo);
        tvLocalSnapshot  = findViewById(R.id.tvLocalSnapshot);
        tvServerSnapshot = findViewById(R.id.tvServerSnapshot);
        btnKeepLocal     = findViewById(R.id.btnKeepLocal);
        btnAcceptServer  = findViewById(R.id.btnAcceptServer);
        btnCancel        = findViewById(R.id.btnCancel);

        // ── Зависимости ──────────────────────────────────────────────────────
        AppDatabase database = AppDatabase.getInstance(getApplication());
        WorkManager workManager = WorkManager.getInstance(getApplication());
        conflictRepository = new ConflictRepository(
                database,
                database.conflictDao(),
                database.estimateItemDao(),
                database.projectDao(),
                database.projectRoomDao(),
                database.openingDao(),
                database.workerDao(),
                database.workTaskDao(),
                new SyncManager(workManager)
        );

        // ── ViewModel ────────────────────────────────────────────────────────
        syncViewModel = new ViewModelProvider(this).get(SyncViewModel.class);

        // ── Подписка на список конфликтов ────────────────────────────────────
        syncViewModel.getConflicts().observe(this, this::displayConflict);

        // ── Кнопки ───────────────────────────────────────────────────────────
        setupButtons();
    }

    /** Обрабатывает нажатие Up Button в Toolbar — эквивалентно кнопке «Назад». */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ── Логика отображения ────────────────────────────────────────────────────

    /**
     * Ищет конфликт по {@link #entityId} в списке и отображает оба снимка.
     *
     * <p>Вызывается автоматически при каждом обновлении LiveData.
     * Если конфликт уже разрешён (запись удалена из БД), список больше
     * не содержит нужного entityId — Activity автоматически закрывается.
     *
     * @param conflicts актуальный список неразрешённых конфликтов из Room.
     */
    private void displayConflict(List<ConflictEntity> conflicts) {
        if (conflicts == null) {
            Log.w(TAG, "displayConflict: получен null список конфликтов.");
            return;
        }

        ConflictEntity target = null;
        for (ConflictEntity c : conflicts) {
            if (entityId.equals(c.entityId)) {
                target = c;
                break;
            }
        }

        if (target == null) {
            // Конфликт был разрешён (запись удалена из таблицы conflict).
            Log.d(TAG, "displayConflict: конфликт entityId=" + entityId
                    + " разрешён или не найден. Закрываем экран.");
            finish();
            return;
        }

        // Запоминаем данные для кнопок разрешения.
        serverSnapshotJson = target.serverSnapshot;
        serverVersion      = extractVersion(target.serverSnapshot);

        // Отображаем мета-информацию.
        String entityInfo = "Тип: " + (target.entityType != null ? target.entityType : "—")
                + "\nID: " + target.entityId;
        tvEntityInfo.setText(entityInfo);

        // Форматируем и отображаем JSON-снимки.
        tvLocalSnapshot.setText(formatJsonSnapshot(target.localSnapshot));
        tvServerSnapshot.setText(formatJsonSnapshot(target.serverSnapshot));
    }

    // ── Кнопки ───────────────────────────────────────────────────────────────

    /**
     * Настраивает слушатели трёх кнопок разрешения конфликта.
     */
    private void setupButtons() {

        // «Оставить мою версию»
        btnKeepLocal.setOnClickListener(v -> {
            Log.d(TAG, "btnKeepLocal: resolveKeepLocal entityId=" + entityId
                    + ", serverVersion=" + serverVersion);
            final long versionToSend = serverVersion;
            AppExecutors.diskIO().execute(() -> {
                conflictRepository.resolveKeepLocal(entityId, versionToSend);
                runOnUiThread(this::finish);
            });
        });

        // «Принять серверную»
        btnAcceptServer.setOnClickListener(v -> {
            Log.d(TAG, "btnAcceptServer: resolveAcceptServer entityId=" + entityId);
            final String jsonToApply = serverSnapshotJson;
            AppExecutors.diskIO().execute(() -> {
                conflictRepository.resolveAcceptServer(entityId, jsonToApply);
                runOnUiThread(this::finish);
            });
        });

        // «Отмена» — просто закрываем экран, syncState остаётся CONFLICT.
        btnCancel.setOnClickListener(v -> {
            Log.d(TAG, "btnCancel: пользователь отменил разрешение конфликта.");
            finish();
        });
    }

    // ── Утилитарные методы ───────────────────────────────────────────────────

    /**
     * Форматирует сырой JSON-снимок в читаемый pretty-printed текст.
     *
     * <p><b>Алгоритм:</b>
     * <ol>
     *   <li>{@code JsonParser.parseString(rawJson)} — парсит строку в {@link JsonElement}.</li>
     *   <li>{@code prettyGson.toJson(element)} — форматирует с отступами.</li>
     *   <li>Если JSON невалидный ({@link JsonSyntaxException}) — возвращает сырую строку.</li>
     * </ol>
     *
     * @param rawJson сырой JSON из {@link ConflictEntity#localSnapshot} или
     *                {@link ConflictEntity#serverSnapshot}.
     * @return отформатированный JSON-текст для отображения в {@code TextView}.
     */
    private String formatJsonSnapshot(String rawJson) {
        if (rawJson == null || rawJson.isEmpty()) {
            return getString(R.string.empty_search_results);
        }
        try {
            JsonElement element = JsonParser.parseString(rawJson);
            return prettyGson.toJson(element);
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "formatJsonSnapshot: JSON невалидный, показываем сырую строку. "
                    + e.getMessage());
            return rawJson;
        }
    }

    /**
     * Извлекает значение поля {@code version} из JSON-снимка серверной версии.
     *
     * <p>Используется для передачи {@code serverVersion} в
     * {@link ConflictRepository#resolveKeepLocal(String, long)}.
     *
     * @param serverSnapshot JSON-строка серверной версии.
     * @return значение поля {@code version}, или {@code 0} если поле отсутствует/невалидно.
     */
    private long extractVersion(String serverSnapshot) {
        if (serverSnapshot == null || serverSnapshot.isEmpty()) {
            return 0L;
        }
        try {
            JsonElement element = JsonParser.parseString(serverSnapshot);
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("version") && !obj.get("version").isJsonNull()) {
                    return obj.get("version").getAsLong();
                }
            }
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "extractVersion: не удалось распарсить version из serverSnapshot. "
                    + e.getMessage());
        }
        return 0L;
    }
}
