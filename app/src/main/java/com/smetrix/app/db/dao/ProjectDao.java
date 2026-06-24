// app/src/main/java/com/smetrix/app/db/dao/ProjectDao.java
package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import com.smetrix.app.db.entity.ProjectEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * DAO (Data Access Object) для работы с таблицей "project".
 *
 * Аннотация {@code @Dao} сообщает Room, что данный интерфейс является
 * объектом доступа к данным. Room сгенерирует конкретную реализацию
 * во время компиляции.
 *
 * На данном этапе (Фаза 3, шаг 3.1.1) интерфейс содержит минимальный
 * набор методов-заглушек, необходимых для компиляции репозиториев.
 * Полные SQL-запросы (с фильтрацией по userId, syncState и т.д.)
 * будут добавлены в Фазе 4.
 */
@Dao
public interface ProjectDao {

    /**
     * Вставляет новый проект в таблицу "project".
     *
     * Стратегия конфликта {@code REPLACE}: если запись с таким же
     * первичным ключом уже существует, она будет заменена новой.
     * Это упрощает логику "вставить или обновить" (upsert).
     *
     * @param entity объект {@link ProjectEntity}, который нужно сохранить.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ProjectEntity entity);

    @Upsert
    void upsertFromSync(ProjectEntity entity);

    /**
     * Обновляет существующую запись проекта в таблице "project".
     *
     * Room ищет запись по первичному ключу ({@code id}) объекта entity
     * и обновляет все остальные поля.
     *
     * @param entity объект {@link ProjectEntity} с обновлёнными данными.
     *               Поле {@code id} должно совпадать с существующей записью.
     */
    @Update
    void update(ProjectEntity entity);

    /**
     * Возвращает реактивный список активных (не удалённых) проектов
     * для указанного пользователя.
     *
     * {@code LiveData} гарантирует, что UI автоматически получит
     * обновлённые данные при любом изменении в таблице.
     *
     * Запрос фильтрует данные текущего пользователя и исключает удалённые записи.
     *
     * @param userId идентификатор пользователя, чьи проекты нужно получить.
     * @return {@code LiveData} со списком проектов пользователя.
     */
    @Query("SELECT * FROM project WHERE user_id = :userId AND deleted_at IS NULL")
    LiveData<List<ProjectEntity>> getActiveProjects(String userId);

    /**
     * Помечает проект как удалённый (soft delete).
     *
     * Вместо физического удаления записи устанавливается timestamp
     * удаления ({@code deleted_at}) и состояние синхронизации меняется
     * на {@code PENDING_DELETE}, чтобы сервер тоже удалил проект
     * при следующей синхронизации.
     *
     * Состояние синхронизации атомарно переводится в {@code PENDING_DELETE}.
     *
     * @param id        идентификатор проекта, который нужно пометить удалённым.
     * @param deletedAt временная метка удаления (Unix-время в миллисекундах).
     */
    @Query("UPDATE project SET deleted_at = :deletedAt, updated_at = :deletedAt, version = version + 1, sync_state = 'PENDING_DELETE' WHERE id = :id")
    void markDeleted(String id, long deletedAt);

    /**
     * Возвращает синхронный список проектов, находящихся в одном из указанных
     * состояний синхронизации.
     *
     * Используется в {@code SyncWorker} для сбора всех проектов,
     * ожидающих отправки на сервер (PENDING_CREATE, PENDING_DELETE).
     *
     * @param states список допустимых состояний синхронизации.
     * @return синхронный список проектов, соответствующих условию.
     */
    @Query("SELECT * FROM project WHERE sync_state IN (:states)")
    List<ProjectEntity> getByStates(List<String> states);

    @Query("SELECT * FROM project WHERE user_id = :userId AND sync_state IN (:states)")
    List<ProjectEntity> getByStatesForUser(String userId, List<String> states);

    @Query("SELECT * FROM project WHERE id = :id")
    ProjectEntity getById(String id);

    @Query("SELECT * FROM project WHERE id = :id")
    LiveData<ProjectEntity> getByIdLiveData(String id);

    /**
     * Обновляет основные поля проекта по его идентификатору.
     *
     * Одновременно обновляет {@code updated_at} и меняет {@code sync_state} на
     * {@code PENDING_UPDATE}, чтобы SyncWorker отправил изменения на сервер.
     *
     * @param id  идентификатор проекта.
     * @param n   новое название проекта.
     * @param c   новый город.
     * @param r   новый код региона.
     * @param ts  временная метка обновления (System.currentTimeMillis()).
     */
    @Query("UPDATE project SET name=:n, city=:c, region_code=:r, " +
            "tax_multiplier=:tax, logistics_markup=:logistics, updated_at=:ts, " +
            "version=version + 1, sync_state='PENDING_UPDATE' WHERE id=:id")
    void updateProject(String id, String n, String c, String r,
                       BigDecimal tax, BigDecimal logistics, long ts);

    @Query("UPDATE project SET version=:version, updated_at=:updatedAt, last_synced_at=:lastSyncedAt, sync_state=:syncState WHERE id=:id")
    void updateAfterSync(String id, long version, long updatedAt, long lastSyncedAt, String syncState);

    @Query("UPDATE project SET sync_state=:syncState WHERE id=:id")
    void updateSyncState(String id, String syncState);

    @Query("UPDATE project SET version=:version, updated_at=:updatedAt, sync_state='PENDING_UPDATE' WHERE id=:id")
    void bumpVersionAndMarkPending(String id, long version, long updatedAt);
}
