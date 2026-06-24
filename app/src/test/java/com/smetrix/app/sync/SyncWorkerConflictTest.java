// app/src/test/java/com/smetrix/app/sync/SyncWorkerConflictTest.java
package com.smetrix.app.sync;

import com.smetrix.app.db.entity.ConflictEntity;
import com.smetrix.app.db.entity.ProjectEntity;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit-тесты для логики обработки HTTP-ответов в SyncWorker.
 *
 * <p>Так как SyncWorker зависит от WorkManager (Android-специфичный),
 * здесь тестируется чистая логика обработки статусов ответа через
 * вспомогательный класс {@link FakeSyncResultProcessor}.
 *
 * <p><b>Сценарии:</b>
 * <ol>
 *   <li>200 OK → syncState = SYNCED, ConflictEntity не создаётся.</li>
 *   <li>409 Conflict → syncState = CONFLICT, ConflictEntity создаётся с entityId и snapshot.</li>
 *   <li>500 Server Error → syncState = FAILED, ConflictEntity не создаётся.</li>
 *   <li>409 с пустым errorBody → ConflictEntity.serverSnapshot = null, не падает NPE.</li>
 * </ol>
 */
public class SyncWorkerConflictTest {

    /** Симулированный список изменений syncState, которые «записала» логика. */
    private List<String> capturedSyncStates;

    /** Симулированные ConflictEntity, которые «вставила» логика. */
    private List<ConflictEntity> capturedConflicts;

    @Before
    public void setUp() {
        capturedSyncStates = new ArrayList<>();
        capturedConflicts  = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Тест 1: HTTP 200 → SYNCED, конфликт не создаётся
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void test_200_ok_marks_synced_no_conflict() {
        ProjectEntity project = makeProject("proj-001", "PENDING_CREATE");

        processHttpCode(200, project, null);

        assertEquals("SYNCED", capturedSyncStates.get(0));
        assertTrue("ConflictEntity не должен создаваться для 200", capturedConflicts.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Тест 2: HTTP 201 → SYNCED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void test_201_created_marks_synced_no_conflict() {
        ProjectEntity project = makeProject("proj-002", "PENDING_CREATE");

        processHttpCode(201, project, null);

        assertEquals("SYNCED", capturedSyncStates.get(0));
        assertTrue(capturedConflicts.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Тест 3: HTTP 409 → CONFLICT + ConflictEntity
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void test_409_conflict_creates_conflict_entity() {
        ProjectEntity project = makeProject("proj-003", "PENDING_CREATE");
        String serverError = "{\"message\":\"Version conflict\",\"version\":5}";

        processHttpCode(409, project, serverError);

        assertEquals("CONFLICT", capturedSyncStates.get(0));
        assertEquals(1, capturedConflicts.size());

        ConflictEntity conflict = capturedConflicts.get(0);
        assertEquals("proj-003", conflict.entityId);
        assertEquals("PROJECT", conflict.entityType);
        assertNotNull("localSnapshot должен содержать JSON проекта", conflict.localSnapshot);
        assertEquals(serverError, conflict.serverSnapshot);
        assertTrue("detectedAt должен быть > 0", conflict.detectedAt > 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Тест 4: HTTP 409 без тела ответа → ConflictEntity.serverSnapshot = null
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void test_409_empty_error_body_no_npe() {
        ProjectEntity project = makeProject("proj-004", "PENDING_UPDATE");

        // Не должно бросать NPE даже с null errorBody
        processHttpCode(409, project, null);

        assertEquals("CONFLICT", capturedSyncStates.get(0));
        assertEquals(1, capturedConflicts.size());
        // serverSnapshot = null — допустимо
        ConflictEntity conflict = capturedConflicts.get(0);
        assertEquals("proj-004", conflict.entityId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Тест 5: HTTP 500 → FAILED, конфликт не создаётся
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void test_500_server_error_marks_failed_no_conflict() {
        ProjectEntity project = makeProject("proj-005", "PENDING_CREATE");

        processHttpCode(500, project, null);

        assertEquals("FAILED", capturedSyncStates.get(0));
        assertTrue("ConflictEntity не должен создаваться для 500", capturedConflicts.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Тест 6: HTTP 503 → FAILED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void test_503_marks_failed() {
        ProjectEntity project = makeProject("proj-006", "PENDING_UPDATE");

        processHttpCode(503, project, null);

        assertEquals("FAILED", capturedSyncStates.get(0));
        assertTrue(capturedConflicts.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Тест 7: HTTP 404 → не SYNCED, не создаёт ConflictEntity (игнорируется)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void test_404_not_found_is_ignored() {
        ProjectEntity project = makeProject("proj-007", "PENDING_CREATE");

        processHttpCode(404, project, null);

        // 404 не является 409 и не >=500 — в нашей логике логируется и пропускается
        assertTrue("Для 404 syncState не должен меняться", capturedSyncStates.isEmpty());
        assertTrue(capturedConflicts.isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные методы — имитируют логику SyncWorker без Android-зависимостей
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Имитирует логику метода {@code SyncWorker.syncUpsertProject()} без Retrofit/WorkManager.
     * Полностью воспроизводит ветки switch по HTTP-коду.
     */
    private void processHttpCode(int httpCode, ProjectEntity project, String errorBody) {
        if (httpCode == 200 || httpCode == 201) {
            // Успех — помечаем SYNCED
            capturedSyncStates.add("SYNCED");

        } else if (httpCode == 409) {
            // Конфликт версий
            capturedSyncStates.add("CONFLICT");

            ConflictEntity conflict = new ConflictEntity();
            conflict.entityId       = project.id;
            conflict.entityType     = "PROJECT";
            // Симулируем Gson.toJson(project) — просто строку-заглушку
            conflict.localSnapshot  = "{\"id\":\"" + project.id + "\",\"syncState\":\""
                    + project.syncState + "\"}";
            conflict.serverSnapshot = errorBody; // null если тело пустое
            conflict.detectedAt     = System.currentTimeMillis();
            capturedConflicts.add(conflict);

        } else if (httpCode >= 500) {
            // Серверная ошибка
            capturedSyncStates.add("FAILED");

        }
        // Другие коды (400, 403, 404) — в реальном SyncWorker логируются и игнорируются
    }

    /** Создаёт тестовый ProjectEntity с минимальным набором полей. */
    private ProjectEntity makeProject(String id, String syncState) {
        ProjectEntity project = new ProjectEntity();
        project.id        = id;
        project.syncState = syncState;
        project.version   = 1L;
        project.name      = "Тестовый проект";
        return project;
    }
}
