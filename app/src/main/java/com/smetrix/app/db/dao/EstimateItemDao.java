// app/src/main/java/com/smetrix/app/db/dao/EstimateItemDao.java
package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import com.smetrix.app.db.entity.EstimateItemEntity;
import com.smetrix.app.model.SyncState;

import java.math.BigDecimal;
import java.util.List;

/**
 * DAO (Data Access Object) для работы с таблицей "estimate_item".
 *
 * Позиции сметы — основной объект вычислений: их количество ({@code quantity})
 * и суммарная стоимость ({@code total_price}) пересчитываются при каждом
 * изменении размеров комнаты.
 *
 * Фаза 4 (шаг 4.3.1, 4.3.4): добавлены полные SQL-запросы для агрегации
 * итогов ({@code getMaterialsTotal}) и управления жизненным циклом записи
 * ({@code updateQuantityAndTotal}, {@code updateAfterSync}, {@code updateSyncState},
 * {@code getByStates}, {@code getByRoomId}).
 */
@Dao
public interface EstimateItemDao {

    /**
     * Вставляет новую позицию сметы в таблицу "estimate_item".
     *
     * @param entity объект {@link EstimateItemEntity}, который нужно сохранить.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(EstimateItemEntity entity);

    @Upsert
    void upsertFromSync(EstimateItemEntity entity);

    @Query("SELECT * FROM estimate_item WHERE id = :id")
    EstimateItemEntity getById(String id);

    /**
     * Обновляет количество и суммарную стоимость позиции сметы.
     *
     * Вызывается в транзакции пересчёта площади ({@code RoomRepository})
     * после вычисления эффективной площади комнаты.
     *
     * @param id         идентификатор позиции сметы.
     * @param quantity   новое расчётное количество (effectiveArea × consumptionRate).
     * @param totalPrice новая суммарная стоимость (finalPrice × quantity).
     * @param updatedAt  временная метка обновления (Unix-время в миллисекундах).
     */
    @Query("UPDATE estimate_item SET quantity = :quantity, total_price = :totalPrice, updated_at = :updatedAt, version = version + 1, sync_state = 'PENDING_UPDATE' WHERE id = :id")
    void updateQuantityAndTotal(String id, BigDecimal quantity, BigDecimal totalPrice, long updatedAt);

    @Query("UPDATE estimate_item SET consumption_rate = NULL, quantity = :quantity, total_price = :totalPrice, updated_at = :updatedAt, version = version + 1, sync_state = 'PENDING_UPDATE' WHERE id = :id")
    void updateManualQuantity(String id, BigDecimal quantity, BigDecimal totalPrice, long updatedAt);

    /**
     * Обновляет данные позиции сметы после успешной синхронизации с сервером.
     *
     * После того как сервер подтвердил получение данных, необходимо
     * сохранить серверную версию и временную метку, а состояние синхронизации
     * установить в {@code SYNCED}.
     *
     * @param id          идентификатор позиции сметы.
     * @param version     версия записи, присвоенная сервером.
     * @param updatedAt   временная метка обновления с сервера.
     * @param syncState   новое состояние синхронизации (обычно {@code "SYNCED"}).
     */
    @Query("UPDATE estimate_item SET version = :version, updated_at = :updatedAt, sync_state = :syncState WHERE id = :id")
    void updateAfterSync(String id, long version, long updatedAt, String syncState);

    /**
     * Обновляет только состояние синхронизации для указанной позиции сметы.
     *
     * Используется для пометки записей как {@code PENDING_CREATE},
     * {@code PENDING_UPDATE}, {@code FAILED} и т.д. без затрагивания
     * бизнес-данных.
     *
     * @param id        идентификатор позиции сметы.
     * @param syncState новое состояние синхронизации.
     */
    @Query("UPDATE estimate_item SET sync_state = :syncState WHERE id = :id")
    void updateSyncState(String id, String syncState);

    /**
     * Возвращает список позиций сметы, находящихся в одном из указанных
     * состояний синхронизации.
     *
     * Используется в {@code SyncWorker} для сбора всех записей, которые
     * нужно отправить на сервер (например, со статусами
     * {@code PENDING_CREATE}, {@code PENDING_UPDATE}, {@code PENDING_DELETE}).
     *
     * @param states список допустимых состояний синхронизации (строки из enum {@code SyncState}).
     * @return синхронный список подходящих позиций сметы.
     */
    @Query("SELECT * FROM estimate_item WHERE sync_state IN (:states)")
    List<EstimateItemEntity> getByStates(List<String> states);

    /**
     * Возвращает реактивный список позиций сметы для указанной комнаты.
     *
     * UI (Fragment/Activity) подписывается на этот {@code LiveData}
     * и автоматически обновляется при любом изменении данных в таблице.
     *
     * @param roomId идентификатор комнаты (project_room_id).
     * @return {@code LiveData} со списком позиций сметы для данной комнаты.
     */
    @Query("SELECT * FROM estimate_item WHERE project_room_id = :roomId AND sync_state != 'PENDING_DELETE' ORDER BY created_at ASC")
    LiveData<List<EstimateItemEntity>> getByRoomId(String roomId);

    /**
     * Возвращает реактивную суммарную стоимость всех материалов в указанной комнате.
     *
     * Используется в {@code RoomDetailViewModel} для формирования итоговой строки
     * Sticky Bottom Bar. Функция {@code COALESCE} гарантирует, что при отсутствии
     * позиций сметы запрос вернёт {@code 0}, а не {@code null}.
     *
     * Формула: {@code SUM(total_price)} по всем строкам
     * {@code estimate_item WHERE project_room_id = :roomId}.
     *
     * @param roomId идентификатор комнаты (project_room_id).
     * @return {@code LiveData<BigDecimal>} с суммарной стоимостью материалов.
     *         Никогда не эмитирует {@code null} — минимальное значение {@code 0}.
     */
    @Query("SELECT total_price FROM estimate_item WHERE project_room_id = :roomId AND sync_state != 'PENDING_DELETE'")
    LiveData<List<String>> getMaterialPrices(String roomId);

    /**
     * Обновляет версию записи и помечает её как {@code PENDING_UPDATE}.
     *
     * <p>Используется в {@link com.smetrix.app.repository.ConflictRepository#resolveKeepLocal}
     * для стратегии «оставить локальную версию». Устанавливает версию =
     * {@code serverVersion + 1}, чтобы при повторной синхронизации сервер
     * принял нашу версию как более новую.
     *
     * @param id         идентификатор позиции сметы.
     * @param newVersion новое значение версии (serverVersion + 1).
     * @param updatedAt  временная метка обновления (Unix-время в миллисекундах).
     */
    @Query("UPDATE estimate_item SET version = :newVersion, sync_state = 'PENDING_UPDATE', updated_at = :updatedAt WHERE id = :id")
    void bumpVersionAndMarkPending(String id, long newVersion, long updatedAt);

    /**
     * Физически удаляет позицию сметы по её идентификатору.
     *
     * <p>Используется при явном удалении пользователем через диалог подтверждения.
     * Для позиций, уже синхронизированных с сервером, перед вызовом этого метода
     * следует пометить запись как {@code PENDING_DELETE} через {@link #updateSyncState}.
     *
     * @param id идентификатор позиции сметы для удаления.
     */
    @Query("DELETE FROM estimate_item WHERE id = :id")
    void deleteById(String id);

    /**
     * Обновляет логистический статус позиции сметы.
     *
     * <p>Используется из UI при смене статуса в адаптере.
     * Значения статуса: {@code NEED_TO_BUY}, {@code ORDERED}, {@code ON_SITE},
     * {@code UNITS_MISMATCH}.
     *
     * @param id     идентификатор позиции сметы.
     * @param status новый статус.
     */
    @Query("UPDATE estimate_item SET status = :status, version = version + 1, sync_state = 'PENDING_UPDATE' WHERE id = :id")
    void updateStatus(String id, String status);

    @Query("UPDATE estimate_item SET sync_state = :state, updated_at = :time WHERE project_room_id IN (SELECT id FROM project_room WHERE project_id = :projectId)")
    void markDeletedByProject(String projectId, long time, SyncState state);
}
