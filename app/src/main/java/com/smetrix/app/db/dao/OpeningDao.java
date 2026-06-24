// app/src/main/java/com/smetrix/app/db/dao/OpeningDao.java
package com.smetrix.app.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import com.smetrix.app.db.entity.OpeningEntity;

import java.util.List;

/**
 * DAO (Data Access Object) для работы с таблицей "opening".
 *
 * Открытие ({@code OpeningEntity}) — дверь или окно в комнате.
 * Суммарная площадь открытий вычитается из площади стен при расчёте
 * чистой площади (netArea) в {@code RoomRepository}.
 *
 * Этот DAO намеренно минимален: основная работа с открытиями
 * происходит через {@code ProjectRoomDao.getOpeningsSync()}, который
 * используется в транзакциях пересчёта.
 *
 * На данном этапе (Фаза 3, шаг 3.1.7) интерфейс содержит минимальные
 * заглушки методов. Полные SQL-запросы будут добавлены в Фазе 4.
 */
@Dao
public interface OpeningDao {

    /**
     * Вставляет новое открытие (дверь или окно) в таблицу "opening".
     *
     * @param entity объект {@link OpeningEntity}, который нужно сохранить.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(OpeningEntity entity);

    @Upsert
    void upsertFromSync(OpeningEntity entity);

    /**
     * Физически удаляет открытие из таблицы "opening" по идентификатору.
     *
     * В отличие от проектов и других сущностей, открытия не имеют
     * механизма мягкого удаления (soft delete): они либо существуют,
     * либо нет. При удалении открытия необходимо запустить пересчёт
     * площади комнаты в {@code RoomRepository}.
     *
     * @param id идентификатор открытия, которое нужно удалить.
     */
    @Query("DELETE FROM opening WHERE id = :id")
    void delete(String id);

    @Query("SELECT * FROM opening WHERE id = :id")
    OpeningEntity getById(String id);

    @Query("UPDATE opening SET updated_at = :deletedAt, version = version + 1, sync_state = 'PENDING_DELETE' WHERE id = :id")
    void markDeleted(String id, long deletedAt);

    /**
     * Возвращает список открытий, находящихся в одном из указанных
     * состояний синхронизации.
     *
     * Используется в {@code SyncWorker} для сбора записей,
     * ожидающих отправки на сервер.
     *
     * @param states список допустимых состояний синхронизации.
     * @return синхронный список открытий, соответствующих условию.
     */
    @Query("SELECT * FROM opening WHERE sync_state IN (:states)")
    List<OpeningEntity> getByStates(List<String> states);

    @Query("UPDATE opening SET version = :version, updated_at = :updatedAt, sync_state = :syncState WHERE id = :id")
    void updateAfterSync(String id, long version, long updatedAt, String syncState);

    @Query("UPDATE opening SET sync_state = :syncState WHERE id = :id")
    void updateSyncState(String id, String syncState);

    @Query("UPDATE opening SET version=:version, updated_at=:updatedAt, sync_state='PENDING_UPDATE' WHERE id=:id")
    void bumpVersionAndMarkPending(String id, long version, long updatedAt);
}
