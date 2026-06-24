// app/src/main/java/com/smetrix/app/model/SyncStatusResult.java
package com.smetrix.app.model;

import androidx.room.ColumnInfo;

/**
 * POJO-результат сложного SQL-запроса из {@code SyncStatusDao.getGlobalSyncStatus()}.
 *
 * <p>Room проецирует результат SQL-запроса (через UNION ALL и CASE WHEN) в поля
 * этого класса по совпадению имён столбцов. Аннотация {@link ColumnInfo} явно
 * указывает соответствие поля Java → столбца SQL.
 *
 * <h3>Семантика поля {@code globalStatus}:</h3>
 * <ul>
 *   <li>{@code "CONFLICT"} — хотя бы одна запись в любой из таблиц имеет статус
 *       {@code CONFLICT}. Требуется ручное разрешение пользователем.</li>
 *   <li>{@code "FAILED"}   — нет конфликтов, но есть записи с ошибкой синхронизации
 *       ({@code FAILED}). Можно попробовать повторить синхронизацию.</li>
 *   <li>{@code "PENDING"}  — нет ошибок и конфликтов, но есть записи,
 *       ожидающие отправки на сервер ({@code PENDING_*}).</li>
 *   <li>{@code "SYNCED"}   — все записи синхронизированы с сервером.</li>
 * </ul>
 *
 * <h3>Порядок приоритетности статусов:</h3>
 * <p>CONFLICT > FAILED > PENDING > SYNCED (объявлен в SQL через CASE WHEN).
 *
 * @see com.smetrix.app.db.dao.SyncStatusDao
 */
public class SyncStatusResult {

    /**
     * Глобальный статус синхронизации всего приложения.
     *
     * <p>Возможные значения строки: {@code "CONFLICT"}, {@code "FAILED"},
     * {@code "PENDING"}, {@code "SYNCED"}.
     *
     * <p>Соответствует псевдониму столбца {@code globalStatus} в SQL-запросе
     * ({@code ... END AS globalStatus}).
     */
    @ColumnInfo(name = "globalStatus")
    public String globalStatus;

    /**
     * Количество записей, находящихся в состоянии {@code CONFLICT}
     * (суммарно по всем четырём таблицам: project, project_room,
     * estimate_item, work_task).
     *
     * <p>Используется для отображения числового бейджа на иконке синхронизации
     * (например: «!2» означает два неразрешённых конфликта).
     *
     * <p>Соответствует псевдониму столбца {@code conflictCount} в SQL-запросе.
     */
    @ColumnInfo(name = "conflictCount")
    public int conflictCount;
}
