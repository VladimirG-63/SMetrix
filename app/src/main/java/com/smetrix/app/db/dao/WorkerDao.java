// app/src/main/java/com/smetrix/app/db/dao/WorkerDao.java
package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Update;
import androidx.room.Query;
import androidx.room.Upsert;

import com.smetrix.app.db.entity.WorkerEntity;

import java.util.List;

/**
 * DAO (Data Access Object) для работы с таблицей "worker".
 *
 * Рабочий ({@code WorkerEntity}) — сущность, привязанная к пользователю
 * приложения. Список рабочих отображается в UI и используется при
 * назначении задач в комнатах.
 *
 * На данном этапе (Фаза 3, шаг 3.1.5) интерфейс содержит минимальные
 * заглушки методов. Полные SQL-запросы будут добавлены в Фазе 4.
 */
@Dao
public interface WorkerDao {

    /**
     * Вставляет нового рабочего в таблицу "worker".
     *
     * Стратегия конфликта {@code ABORT}: если запись с таким же
     * первичным ключом уже существует, Room бросит исключение
     * {@code SQLiteConstraintException}, которое перехватывается
     * в {@code WorkerRepository} и конвертируется в {@code DuplicateEntityException}.
     *
     * @param entity объект {@link WorkerEntity}, который нужно сохранить.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(WorkerEntity entity);

    @Upsert
    void upsertFromSync(WorkerEntity entity);

    /**
     * Обновляет существующую запись рабочего в таблице "worker".
     *
     * Room ищет запись по первичному ключу ({@code id}) объекта entity.
     *
     * @param entity объект {@link WorkerEntity} с обновлёнными данными.
     */
    @Update
    void update(WorkerEntity entity);

    /**
     * Возвращает реактивный список всех рабочих указанного пользователя.
     *
     * UI подписывается на этот {@code LiveData} и автоматически обновляется
     * при любом изменении данных в таблице.
     *
     * @param userId идентификатор пользователя, чьих рабочих нужно получить.
     * @return {@code LiveData} со списком всех рабочих пользователя.
     */
    @Query("SELECT * FROM worker WHERE user_id = :userId AND sync_state != 'PENDING_DELETE'")
    LiveData<List<WorkerEntity>> getAll(String userId);

    /**
     * Возвращает список рабочих, находящихся в одном из указанных
     * состояний синхронизации.
     *
     * Используется в {@code SyncWorker} для сбора записей,
     * ожидающих отправки на сервер.
     *
     * @param states список допустимых состояний синхронизации (строки SyncState enum).
     * @return синхронный список рабочих, соответствующих условию.
     */
    @Query("SELECT * FROM worker WHERE sync_state IN (:states)")
    List<WorkerEntity> getByStates(List<String> states);

    /**
     * Обновляет только редактируемые поля рабочего (без userId и createdAt).
     *
     * @param id        идентификатор рабочего.
     * @param fullName  новое полное имя.
     * @param phone     нормализованный телефон.
     * @param specialty специализация.
     * @param updatedAt временная метка обновления.
     */
    @Query("UPDATE worker SET full_name = :fullName, phone = :phone, specialty = :specialty, updated_at = :updatedAt, version = version + 1, sync_state = 'PENDING_UPDATE' WHERE id = :id")
    void updateFields(String id, String fullName, String phone, String specialty, long updatedAt);

    @Query("SELECT * FROM worker WHERE id = :id")
    WorkerEntity getById(String id);

    @Query("UPDATE worker SET updated_at = :deletedAt, version = version + 1, sync_state = 'PENDING_DELETE' WHERE id = :id")
    void markDeleted(String id, long deletedAt);

    /**
     * Удаляет рабочего по идентификатору.
     *
     * @param id идентификатор рабочего.
     */
    @Query("DELETE FROM worker WHERE id = :id")
    void deleteById(String id);

    @Query("UPDATE worker SET version = :version, updated_at = :updatedAt, sync_state = :syncState WHERE id = :id")
    void updateAfterSync(String id, long version, long updatedAt, String syncState);

    @Query("UPDATE worker SET sync_state = :syncState WHERE id = :id")
    void updateSyncState(String id, String syncState);

    @Query("UPDATE worker SET version=:version, updated_at=:updatedAt, sync_state='PENDING_UPDATE' WHERE id=:id")
    void bumpVersionAndMarkPending(String id, long version, long updatedAt);
}
