// app/src/main/java/com/smetrix/app/db/dao/SyncStatusDao.java
package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.smetrix.app.model.SyncStatusResult;

/**
 * DAO для получения глобального статуса синхронизации приложения.
 *
 * <p>Этот DAO не работает с отдельной таблицей. Вместо этого он выполняет
 * агрегирующий SQL-запрос, который через {@code UNION ALL} собирает поле
 * {@code sync_state} из четырёх основных таблиц и на основе логики
 * {@code CASE WHEN} вычисляет единый глобальный статус.
 *
 * <h3>Логика вычисления глобального статуса (приоритет убывает):</h3>
 * <ol>
 *   <li>{@code CONFLICT} — есть хотя бы одна запись с состоянием {@code CONFLICT}.
 *       Пользователю нужно вручную разрешить конфликт.</li>
 *   <li>{@code FAILED}   — нет конфликтов, но есть записи с ошибкой ({@code FAILED}).
 *       Рекомендуется повторить синхронизацию.</li>
 *   <li>{@code PENDING}  — нет ошибок, но есть записи в статусах {@code PENDING_*},
 *       ожидающие отправки на сервер.</li>
 *   <li>{@code SYNCED}   — все записи синхронизированы.</li>
 * </ol>
 *
 * <h3>Таблицы, участвующие в агрегации:</h3>
 * <ul>
 *   <li>{@code project}      — таблица проектов.</li>
 *   <li>{@code project_room} — таблица помещений.</li>
 *   <li>{@code estimate_item}— таблица позиций сметы.</li>
 *   <li>{@code work_task}    — таблица рабочих задач.</li>
 * </ul>
 *
 * <h3>Примечание о совместимости SQLite:</h3>
 * <p>Стандартный SQLite (до версии 3.30.0) не поддерживает синтаксис
 * {@code COUNT(*) FILTER (WHERE ...)}. Начиная с Android API 30 (Android 11)
 * используется SQLite 3.32.2+, где {@code FILTER} поддерживается.
 * Для совместимости с более старыми версиями Android используется
 * эквивалентный вариант через {@code SUM(CASE WHEN ... THEN 1 ELSE 0 END)}.
 *
 * @see SyncStatusResult
 */
@Dao
public interface SyncStatusDao {

    /**
     * Возвращает реактивный глобальный статус синхронизации всего приложения.
     *
     * <p>Запрос объединяет ({@code UNION ALL}) поле {@code sync_state} из четырёх
     * основных таблиц в один плоский список, затем агрегирует результаты через
     * {@code SUM(CASE WHEN ...)} для подсчёта количества записей каждого статуса,
     * и через {@code CASE WHEN} вычисляет итоговый приоритетный статус.
     *
     * <p>Использование {@code LiveData} гарантирует, что UI (иконка синхронизации,
     * бейдж с количеством конфликтов) автоматически обновляется при любом
     * изменении данных в перечисленных таблицах.
     *
     * <p><b>Эквивалентность с FILTER:</b> Выражение
     * {@code SUM(CASE WHEN sync_state = 'CONFLICT' THEN 1 ELSE 0 END)} идентично
     * {@code COUNT(*) FILTER (WHERE sync_state = 'CONFLICT')} по результату,
     * но работает на всех версиях SQLite, поддерживаемых Android Room.
     *
     * @return {@code LiveData<SyncStatusResult>} — реактивный объект, обновляемый
     *         Room при изменении любой из четырёх таблиц. Никогда не возвращает
     *         {@code null}: если таблицы пусты, возвращает статус {@code "SYNCED"}
     *         с {@code conflictCount = 0}.
     */
    @Query("SELECT " +
           "  CASE " +
           "    WHEN SUM(CASE WHEN sync_state = 'CONFLICT'      THEN 1 ELSE 0 END) > 0 THEN 'CONFLICT' " +
           "    WHEN SUM(CASE WHEN sync_state = 'FAILED'        THEN 1 ELSE 0 END) > 0 THEN 'FAILED' " +
           "    WHEN SUM(CASE WHEN sync_state LIKE 'PENDING%'   THEN 1 ELSE 0 END) > 0 THEN 'PENDING' " +
           "    ELSE 'SYNCED' " +
           "  END AS globalStatus, " +
           "  SUM(CASE WHEN sync_state = 'CONFLICT' THEN 1 ELSE 0 END) AS conflictCount " +
           "FROM (" +
           "  SELECT sync_state FROM project       UNION ALL " +
           "  SELECT sync_state FROM project_room  UNION ALL " +
           "  SELECT sync_state FROM estimate_item UNION ALL " +
           "  SELECT sync_state FROM work_task     UNION ALL " +
           "  SELECT sync_state FROM opening       UNION ALL " +
           "  SELECT sync_state FROM worker" +
           ")")
    LiveData<SyncStatusResult> getGlobalSyncStatus();
}
