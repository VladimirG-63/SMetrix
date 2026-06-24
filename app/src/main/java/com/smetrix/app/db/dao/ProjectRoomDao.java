// app/src/main/java/com/smetrix/app/db/dao/ProjectRoomDao.java
package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import com.smetrix.app.db.entity.EstimateItemEntity;
import com.smetrix.app.db.entity.OpeningEntity;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.db.entity.WorkTaskEntity;
import com.smetrix.app.model.SyncState;

import java.math.BigDecimal;
import java.util.List;

/**
 * DAO (Data Access Object) для работы с таблицей "project_room".
 *
 * Помимо базовых операций над самой комнатой, этот DAO предоставляет
 * синхронные методы для чтения связанных сущностей (открытий, позиций
 * сметы, задач), используемых в транзакционном пересчёте площадей
 * в {@code RoomRepository}.
 *
 * Аннотация {@code @Dao} сообщает Room, что данный интерфейс является
 * объектом доступа к данным.
 *
 * На данном этапе (Фаза 3, шаг 3.1.2) интерфейс содержит минимальный
 * набор методов-заглушек. Полные аннотации и запросы будут добавлены
 * в Фазе 4.
 */
@Dao
public interface ProjectRoomDao {

    /**
     * Вставляет новую комнату в таблицу "project_room".
     *
     * @param entity объект {@link ProjectRoomEntity}, который нужно сохранить.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ProjectRoomEntity entity);

    @Upsert
    void upsertFromSync(ProjectRoomEntity entity);

    /**
     * Обновляет физические размеры помещения.
     *
     * <p>Также обновляет временную метку последнего изменения ({@code updated_at})
     * и устанавливает {@code sync_state = 'PENDING_UPDATE'}.
     *
     * @param id        идентификатор комнаты.
     * @param length    новое значение длины.
     * @param width     новое значение ширины.
     * @param height    новое значение высоты.
     * @param updatedAt временная метка обновления.
     */
    @Query("UPDATE project_room SET length = :length, width = :width, height = :height, " +
           "updated_at = :updatedAt, version = version + 1, sync_state = 'PENDING_UPDATE' WHERE id = :id")
    void updateDimensions(String id, BigDecimal length, BigDecimal width, BigDecimal height,
                          long updatedAt);

    /**
     * Возвращает комнату по её идентификатору синхронно (без LiveData).
     *
     * Метод используется внутри транзакций на фоновом потоке, где
     * реактивный подход (LiveData) неуместен.
     *
     * @param id идентификатор комнаты.
     * @return объект {@link ProjectRoomEntity} или {@code null}, если не найден.
     */
    @Query("SELECT * FROM project_room WHERE id = :id")
    ProjectRoomEntity getById(String id);

    @Query("SELECT * FROM project_room WHERE id = :id")
    LiveData<ProjectRoomEntity> getByIdLiveData(String id);

    /**
     * Возвращает список всех открытий (дверей/окон) для указанной комнаты
     * синхронно, без реактивного обёртывания.
     *
     * Используется в транзакции пересчёта площади для вычитания
     * площади проёмов из общей площади стен.
     *
     * @param roomId идентификатор комнаты.
     * @return список {@link OpeningEntity} для данной комнаты.
     */
    @Query("SELECT * FROM opening WHERE project_room_id = :roomId AND sync_state != 'PENDING_DELETE'")
    List<OpeningEntity> getOpeningsSync(String roomId);

    /**
     * Возвращает реактивный список проёмов (дверей/окон) для указанной комнаты.
     *
     * <p>Используется в {@code RoomDetailViewModel} для отображения чипов
     * в блоке проёмов. Room автоматически уведомляет наблюдателей при любом
     * изменении таблицы {@code opening} для данной комнаты.
     *
     * @param roomId идентификатор комнаты.
     * @return {@code LiveData} со списком {@link OpeningEntity}. Никогда не null.
     */
    @Query("SELECT * FROM opening WHERE project_room_id = :roomId AND sync_state != 'PENDING_DELETE' ORDER BY created_at ASC")
    LiveData<List<OpeningEntity>> getOpeningsLiveData(String roomId);

    /**
     * Возвращает список всех позиций сметы для указанной комнаты
     * синхронно, без реактивного обёртывания.
     *
     * Используется в транзакции пересчёта для обновления количества
     * и суммарной стоимости каждой позиции.
     *
     * @param roomId идентификатор комнаты.
     * @return список {@link EstimateItemEntity} для данной комнаты.
     */
    @Query("SELECT * FROM estimate_item WHERE project_room_id = :roomId")
    List<EstimateItemEntity> getEstimateItemsSync(String roomId);

    /**
     * Возвращает список сдельных задач (rateType = 'PIECEWORK') для
     * указанной комнаты синхронно.
     *
     * Используется в транзакции пересчёта площади для обновления
     * суммарной оплаты сдельных задач.
     *
     * @param roomId идентификатор комнаты.
     * @return список {@link WorkTaskEntity} с типом оплаты "PIECEWORK".
     */
    @Query("SELECT * FROM work_task WHERE project_room_id = :roomId AND rate_type = 'PIECEWORK'")
    List<WorkTaskEntity> getPieceworkTasksSync(String roomId);

    /**
     * Возвращает реактивный список комнат проекта, упорядоченных по дате создания.
     *
     * <p>Используется в {@code RoomListFragment} для отображения всех комнат
     * текущего проекта через {@code LiveData}.
     *
     * @param projectId идентификатор проекта.
     * @return {@code LiveData} со списком {@link ProjectRoomEntity}.
     */
    @Query("SELECT * FROM project_room WHERE project_id = :projectId AND sync_state != 'PENDING_DELETE' ORDER BY created_at ASC")
    androidx.lifecycle.LiveData<List<ProjectRoomEntity>> getRoomsByProject(String projectId);

    /**
     * Удаляет комнату по её идентификатору.
     *
     * <p>Каскадное удаление всех связанных {@code estimate_item} и {@code work_task}
     * гарантировано ограничениями {@code ForeignKey.CASCADE} в этих сущностях.
     *
     * @param roomId идентификатор комнаты для удаления.
     */
    @Query("DELETE FROM project_room WHERE id = :roomId")
    void deleteById(String roomId);

    @Query("UPDATE project_room SET updated_at = :deletedAt, version = version + 1, sync_state = 'PENDING_DELETE' WHERE id = :roomId")
    void markDeleted(String roomId, long deletedAt);

    /**
     * Обновляет название комнаты по её идентификатору.
     *
     * Одновременно обновляет {@code updated_at} и устанавливает
     * {@code sync_state = 'PENDING_UPDATE'} для последующей синхронизации.
     *
     * @param id        идентификатор комнаты.
     * @param name      новое название комнаты.
     * @param updatedAt временная метка обновления (System.currentTimeMillis()).
     */
    @Query("UPDATE project_room SET name=:name, updated_at=:updatedAt, version=version + 1, sync_state='PENDING_UPDATE' WHERE id=:id")
    void updateRoomName(String id, String name, long updatedAt);

    @Query("SELECT * FROM project_room WHERE sync_state IN (:states)")
    List<ProjectRoomEntity> getByStates(List<String> states);

    @Query("UPDATE project_room SET version=:version, updated_at=:updatedAt, sync_state=:syncState WHERE id=:id")
    void updateAfterSync(String id, long version, long updatedAt, String syncState);

    @Query("UPDATE project_room SET sync_state=:syncState WHERE id=:id")
    void updateSyncState(String id, String syncState);

    @Query("UPDATE project_room SET version=:version, updated_at=:updatedAt, sync_state='PENDING_UPDATE' WHERE id=:id")
    void bumpVersionAndMarkPending(String id, long version, long updatedAt);

    /**
     * Устанавливает или сбрасывает ручное переопределение площади комнаты.
     *
     * <p>Если {@code override} равен {@code null} — площадь возвращается
     * к автоматическому расчёту (2*(L+W)*H минус проёмы + колонны).
     *
     * @param id       идентификатор комнаты.
     * @param override новое значение ручной площади, или {@code null} для сброса.
     * @param updatedAt временная метка обновления.
     */
    @Query("UPDATE project_room SET manual_area_override = :override, updated_at = :updatedAt, version = version + 1, sync_state = 'PENDING_UPDATE' WHERE id = :id")
    void updateManualAreaOverride(String id, BigDecimal override, long updatedAt);

    @Query("UPDATE project_room SET sync_state = :state, updated_at = :time WHERE project_id = :projectId")
    void markDeletedByProject(String projectId, long time, SyncState state);
}
