// app/src/main/java/com/smetrix/app/db/dao/ConflictDao.java
package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smetrix.app.db.entity.ConflictEntity;

import java.util.List;

/**
 * DAO (Data Access Object) для работы с таблицей "conflict".
 *
 * Конфликт ({@code ConflictEntity}) возникает, когда локальная версия
 * записи и серверная версия расходятся и не могут быть объединены
 * автоматически. Пользователь должен вручную разрешить конфликт,
 * выбрав одну из версий.
 *
 * Таблица конфликтов служит очередью задач для экрана разрешения
 * конфликтов в UI. При разрешении конфликта запись удаляется из
 * этой таблицы.
 *
 * На данном этапе (Фаза 3, шаг 3.1.8) интерфейс содержит минимальные
 * заглушки методов. Полные SQL-запросы будут добавлены в Фазе 4.
 */
@Dao
public interface ConflictDao {

    /**
     * Вставляет новый конфликт в таблицу "conflict".
     *
     * Стратегия {@code REPLACE}: если конфликт для данного {@code entityId}
     * уже существует (например, при повторной синхронизации), он будет
     * заменён актуальным снимком данных.
     *
     * @param entity объект {@link ConflictEntity}, представляющий конфликт синхронизации.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ConflictEntity entity);

    /**
     * Удаляет разрешённый конфликт из таблицы "conflict".
     *
     * Вызывается после того, как пользователь выбрал, какую версию
     * данных оставить (локальную или серверную).
     *
     * @param entityId идентификатор сущности (первичный ключ таблицы "conflict"),
     *                 конфликт которой нужно удалить.
     */
    @Query("DELETE FROM conflict WHERE entity_id = :entityId")
    void delete(String entityId);

    @Query("SELECT * FROM conflict WHERE entity_id = :entityId LIMIT 1")
    ConflictEntity getById(String entityId);

    /**
     * Возвращает реактивный список всех неразрешённых конфликтов.
     *
     * UI (например, значок на иконке синхронизации) подписывается на
     * этот {@code LiveData} и отображает количество активных конфликтов,
     * требующих внимания пользователя.
     *
     * @return {@code LiveData} со списком всех записей в таблице конфликтов.
     */
    @Query("SELECT * FROM conflict")
    LiveData<List<ConflictEntity>> getAll();

    /**
     * Возвращает реактивное количество неразрешённых конфликтов.
     *
     * <p>Используется для отображения бейджа (числа) на иконке синхронизации.
     * Автоматически обновляется при добавлении или удалении конфликтов.
     *
     * @return {@code LiveData<Integer>} — число конфликтов, никогда не {@code null}.
     */
    @Query("SELECT COUNT(*) FROM conflict")
    LiveData<Integer> getCount();
}
