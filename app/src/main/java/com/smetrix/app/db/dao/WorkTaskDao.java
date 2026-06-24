// app/src/main/java/com/smetrix/app/db/dao/WorkTaskDao.java
package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import com.smetrix.app.db.entity.WorkTaskEntity;
import com.smetrix.app.model.SyncState;

import java.math.BigDecimal;
import java.util.List;

/**
 * DAO (Data Access Object) для работы с таблицей "work_task".
 *
 * Задача ({@code WorkTaskEntity}) представляет собой единицу работы,
 * привязанную к комнате и (опционально) к рабочему. Суммарная оплата
 * для сдельных задач ({@code rateType = "PIECEWORK"}) пересчитывается
 * каждый раз при изменении размеров комнаты.
 *
 * Фаза 4 (шаги 4.3.2, 4.3.5): добавлены полные SQL-запросы для агрегации
 * фонда оплаты труда ({@code getSalariesTotal}) и управления состоянием
 * синхронизации ({@code updatePayment}, {@code updateSyncState}, {@code getByStates}).
 */
@Dao
public interface WorkTaskDao {

    /**
     * Вставляет новую рабочую задачу в таблицу "work_task".
     *
     * @param entity объект {@link WorkTaskEntity}, который нужно сохранить.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WorkTaskEntity entity);

    @Upsert
    void upsertFromSync(WorkTaskEntity entity);

    /**
     * Обновляет суммарную оплату за рабочую задачу.
     *
     * Вызывается в транзакции пересчёта площади ({@code RoomRepository})
     * для сдельных задач: {@code totalPayment = effectiveArea × rateValue}.
     *
     * @param id           идентификатор задачи.
     * @param totalPayment новое значение суммарной оплаты.
     * @param updatedAt    временная метка обновления (Unix-время в миллисекундах).
     */
    @Query("UPDATE work_task SET total_payment = :totalPayment, updated_at = :updatedAt, version = version + 1, sync_state = 'PENDING_UPDATE' WHERE id = :id")
    void updatePayment(String id, BigDecimal totalPayment, long updatedAt);

    /**
     * Обновляет только состояние синхронизации для указанной задачи.
     *
     * Используется для пометки записей как {@code PENDING_CREATE},
     * {@code PENDING_UPDATE}, {@code FAILED} и т.д.
     *
     * @param id        идентификатор задачи.
     * @param syncState новое состояние синхронизации.
     */
    @Query("UPDATE work_task SET sync_state = :syncState WHERE id = :id")
    void updateSyncState(String id, String syncState);

    /**
     * Возвращает список задач, находящихся в одном из указанных
     * состояний синхронизации.
     *
     * Используется в {@code SyncWorker} для сбора всех записей,
     * ожидающих отправки на сервер.
     *
     * @param states список допустимых состояний синхронизации.
     * @return синхронный список задач, соответствующих условию.
     */
    @Query("SELECT * FROM work_task WHERE sync_state IN (:states)")
    List<WorkTaskEntity> getByStates(List<String> states);

    /**
     * Возвращает реактивную суммарную оплату труда по всем задачам в указанной комнате.
     *
     * Используется в {@code RoomDetailViewModel} для формирования итоговой строки
     * Sticky Bottom Bar. Функция {@code COALESCE} гарантирует возврат {@code 0}
     * при отсутствии задач в комнате (не {@code null}).
     *
     * Учитываются все задачи в комнате независимо от типа оплаты
     * ({@code PIECEWORK} и {@code FIXED}).
     *
     * @param roomId идентификатор комнаты (project_room_id).
     * @return {@code LiveData<BigDecimal>} с суммарным ФОТ по комнате.
     *         Никогда не эмитирует {@code null} — минимальное значение {@code 0}.
     */
    @Query("SELECT COALESCE(SUM(total_payment), 0) FROM work_task WHERE project_room_id = :roomId AND sync_state != 'PENDING_DELETE'")
    LiveData<BigDecimal> getSalariesTotal(String roomId);

    /**
     * Возвращает реактивный список всех задач для указанной комнаты.
     *
     * <p>Используется в {@code RoomDetailFragment} для отображения
     * списка рабочих задач через {@link WorkTaskAdapter}.
     *
     * @param roomId идентификатор комнаты (project_room_id).
     * @return {@code LiveData} со списком {@link WorkTaskEntity}.
     */
    @Query("SELECT * FROM work_task WHERE project_room_id = :roomId AND sync_state != 'PENDING_DELETE' ORDER BY created_at ASC")
    LiveData<List<WorkTaskEntity>> getByRoom(String roomId);

    @Query("SELECT * FROM work_task WHERE id = :id")
    WorkTaskEntity getById(String id);

    /**
     * Обновляет существующую рабочую задачу.
     *
     * @param entity обновлённый объект задачи. Room ищет запись по PK.
     */
    @Update
    void update(WorkTaskEntity entity);

    /**
     * Обновляет редактируемые поля рабочей задачи без затирания системных полей.
     *
     * @param id           идентификатор задачи.
     * @param workerId     новый идентификатор рабочего (может быть null).
     * @param taskName     новое название вида работ.
     * @param rateType     новый тип оплаты.
     * @param rateValue    новая ставка.
     * @param totalPayment новая суммарная оплата (= rateValue для фиксированных).
     * @param updatedAt    временная метка обновления.
     */
    @Query("UPDATE work_task SET worker_id = :workerId, task_name = :taskName, rate_type = :rateType, rate_value = :rateValue, total_payment = :totalPayment, updated_at = :updatedAt, version = version + 1, sync_state = 'PENDING_UPDATE' WHERE id = :id")
    void updateTaskFields(String id, String workerId, String taskName, String rateType, BigDecimal rateValue, BigDecimal totalPayment, long updatedAt);

    /**
     * Удаляет задачу по идентификатору.
     *
     * @param id идентификатор задачи.
     */
    @Query("DELETE FROM work_task WHERE id = :id")
    void deleteById(String id);

    @Query("UPDATE work_task SET updated_at = :deletedAt, version = version + 1, sync_state = 'PENDING_DELETE' WHERE id = :id")
    void markDeleted(String id, long deletedAt);

    @Query("UPDATE work_task SET version = :version, updated_at = :updatedAt, sync_state = :syncState WHERE id = :id")
    void updateAfterSync(String id, long version, long updatedAt, String syncState);

    @Query("UPDATE work_task SET version=:version, updated_at=:updatedAt, sync_state='PENDING_UPDATE' WHERE id=:id")
    void bumpVersionAndMarkPending(String id, long version, long updatedAt);

    @Query("UPDATE work_task SET sync_state = :state, updated_at = :time WHERE project_room_id IN (SELECT id FROM project_room WHERE project_id = :projectId)")
    void markDeletedByProject(String projectId, long time, SyncState state);
}
