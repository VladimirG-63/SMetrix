// app/src/main/java/com/smetrix/app/db/dao/MaterialsCacheDao.java
package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smetrix.app.db.entity.MaterialsCacheEntity;

import java.util.List;

/**
 * DAO (Data Access Object) для работы с таблицей "materials_cache".
 *
 * Кэш материалов из ФГИС (Федеральной государственной информационной системы)
 * позволяет пользователю работать с ценами на материалы без подключения
 * к интернету. Данные загружаются с сервера и сохраняются локально.
 *
 * На данном этапе (Фаза 3, шаг 3.1.6) интерфейс содержит минимальные
 * заглушки методов. Полные SQL-запросы (с LIKE-поиском по name, fgis_code)
 * будут добавлены в Фазе 4.
 */
@Dao
public interface MaterialsCacheDao {

    /**
     * Вставляет запись о материале в кэш, заменяя существующую при конфликте.
     *
     * Стратегия {@code REPLACE} обеспечивает поведение "upsert" (insert or update):
     * если запись с таким же первичным ключом ({@code fgis_code}) уже существует,
     * она будет полностью заменена новой. Это идеально для обновления кэша
     * данными с сервера.
     *
     * @param entity объект {@link MaterialsCacheEntity}, который нужно сохранить или обновить.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(MaterialsCacheEntity entity);

    /**
     * Выполняет локальный поиск материалов по подстроке в названии.
     *
     * {@code LiveData} гарантирует реактивное обновление UI при изменении
     * данных в таблице.
     *
     * Поиск выполняется по названию и коду ФГИС в пределах выбранного региона.
     *                с учётом регистра (LIKE '%' || :query || '%')
     *
     * @param query строка для поиска (подстрока названия материала).
     * @return {@code LiveData} со списком найденных материалов.
     */
    @Query("SELECT * FROM materials_cache " +
            "WHERE region_code = :regionCode AND " +
            "(name LIKE '%' || :query || '%' OR fgis_code LIKE '%' || :query || '%') " +
            "ORDER BY priority_score DESC, cached_at DESC LIMIT 30")
    LiveData<List<MaterialsCacheEntity>> searchLocal(String query, String regionCode);

    @Query("SELECT priority_score FROM materials_cache WHERE fgis_code = :fgisCode AND region_code = :regionCode LIMIT 1")
    Integer getPriorityScore(String fgisCode, String regionCode);

    @Query("UPDATE materials_cache SET priority_score = priority_score + 1, cached_at = :usedAt " +
            "WHERE fgis_code = :fgisCode AND region_code = :regionCode")
    void markUsed(String fgisCode, String regionCode, long usedAt);
}
