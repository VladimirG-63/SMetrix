// app/src/main/java/com/smetrix/app/db/entity/WorkTaskEntity.java
package com.smetrix.app.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * Room Entity для таблицы "work_task".
 *
 * Представляет одно задание на выполнение работ в конкретном помещении,
 * назначенное конкретному рабочему (или никому — если workerId = null).
 *
 * Модель оплаты труда (rateType):
 *  В приложении поддерживаются два типа начисления оплаты:
 *
 *  1. "PIECEWORK" (Сдельная оплата):
 *     totalPayment = effectiveArea × rateValue   (scale 2, HALF_UP)
 *     где effectiveArea — площадь стен помещения с учётом проёмов.
 *     rateValue = ставка за 1 кв.м работы.
 *     Пример: покраска стен — 150 руб/м², площадь 42 м² → оплата 6300 руб.
 *     Пересчитывается автоматически при изменении размеров помещения
 *     (в методе RoomRepository.updateDimensionsAndRecalculate).
 *
 *  2. "FIXED" (Фиксированная оплата):
 *     totalPayment = rateValue (не зависит от площади).
 *     rateValue = фиксированная сумма за выполнение задания целиком.
 *     Пример: замена электропроводки в комнате — фиксировано 15000 руб.
 *     НЕ пересчитывается при изменении размеров комнаты.
 *
 * Две внешние связи (ForeignKey):
 *
 *  1. → ProjectRoomEntity (onDelete = CASCADE):
 *     Задание существует только в контексте помещения. При удалении
 *     помещения все его задания удаляются автоматически.
 *
 *  2. → WorkerEntity (onDelete = SET_NULL):
 *     Задание может существовать без рабочего (workerId = null).
 *     Сценарий: задание создано, но рабочий ещё не назначен, или
 *     рабочий был удалён из базы контактов, а задание нужно сохранить
 *     в истории. SET_NULL устанавливает workerId = null при удалении
 *     WorkerEntity — задание не удаляется, только "открепляется" от рабочего.
 *
 * Индексы:
 *  - project_room_id: для быстрого получения всех заданий помещения.
 *  - worker_id: для быстрого получения всех заданий рабочего.
 */
@Entity(
    tableName = "work_task",
    foreignKeys = {
        @ForeignKey(
            entity = ProjectRoomEntity.class,
            parentColumns = "id",
            childColumns = "project_room_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = WorkerEntity.class,
            parentColumns = "id",
            childColumns = "worker_id",
            onDelete = ForeignKey.SET_NULL
        )
    },
    indices = {
        @Index(value = {"project_room_id"}),
        @Index(value = {"worker_id"})
    }
)
public class WorkTaskEntity {

    /**
     * Уникальный идентификатор задания на выполнение работ.
     * Генерируется на клиенте (UUID v7) при создании.
     */
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /**
     * Идентификатор помещения, в котором выполняется это задание.
     * Внешний ключ → ProjectRoomEntity.id. Обязательное поле.
     * При удалении ProjectRoomEntity данная запись удаляется (CASCADE).
     * Индексируется для быстрого запроса: SELECT * FROM work_task WHERE project_room_id = ?
     */
    @NonNull
    @ColumnInfo(name = "project_room_id")
    public String projectRoomId;

    /**
     * Идентификатор рабочего, которому назначено задание.
     * Внешний ключ → WorkerEntity.id. Nullable — задание может существовать
     * без назначенного рабочего (например, задание запланировано, но
     * исполнитель ещё не выбран).
     * При удалении WorkerEntity Room устанавливает это поле в null (SET_NULL),
     * сохраняя запись о задании в истории помещения.
     */
    @Nullable
    @ColumnInfo(name = "worker_id")
    public String workerId;

    /**
     * Название/описание задания.
     * Произвольный текст, описывающий вид работ.
     * Примеры: "Покраска стен латексной краской в 2 слоя",
     *           "Укладка ламината с подложкой", "Монтаж гипсокартонных перегородок".
     */
    @ColumnInfo(name = "task_name")
    public String taskName;

    /**
     * Тип расчёта оплаты труда.
     * Строковое значение. Допустимые варианты:
     *  - "PIECEWORK" — сдельная оплата (totalPayment = area × rateValue).
     *  - "FIXED"     — фиксированная оплата (totalPayment = rateValue).
     */
    @ColumnInfo(name = "rate_type")
    public String rateType;

    /**
     * Ставка оплаты за единицу работы.
     * Хранится как TEXT через BigDecimalConverter для финансовой точности.
     *
     * Интерпретация зависит от rateType:
     *  - "PIECEWORK": ставка в рублях за 1 м² (например, 150.00 руб/м²).
     *  - "FIXED": итоговая фиксированная сумма в рублях (например, 15000.00 руб).
     */
    @ColumnInfo(name = "rate_value")
    public BigDecimal rateValue;

    /**
     * Итоговая рассчитанная сумма оплаты за это задание.
     * Хранится как TEXT через BigDecimalConverter. Масштаб: 2 знака после запятой.
     *
     * Расчёт:
     *  - "PIECEWORK": totalPayment = effectiveArea × rateValue (HALF_UP, scale 2).
     *  - "FIXED":     totalPayment = rateValue (копируется без пересчёта).
     *
     * Пересчитывается автоматически в RoomRepository.updateDimensionsAndRecalculate()
     * только для заданий типа "PIECEWORK".
     */
    @ColumnInfo(name = "total_payment")
    public BigDecimal totalPayment;

    /**
     * Временная метка создания записи на клиенте.
     * UNIX-timestamp в миллисекундах.
     */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    /**
     * Временная метка последнего изменения записи.
     * UNIX-timestamp в миллисекундах. Обновляется при любом изменении задания.
     */
    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    /**
     * Версия записи для оптимистичной блокировки при синхронизации.
     * Начальное значение при создании на клиенте: 0.
     * Сервер увеличивает значение при каждом подтверждённом обновлении.
     */
    @ColumnInfo(name = "version")
    public long version;

    /**
     * Состояние синхронизации этой записи с сервером.
     * Строковое представление SyncState enum.
     * Допустимые значения: "SYNCED", "PENDING_CREATE", "PENDING_UPDATE",
     * "PENDING_DELETE", "FAILED", "CONFLICT".
     *
     * @see com.smetrix.app.model.SyncState
     */
    @ColumnInfo(name = "sync_state")
    public String syncState;

    /**
     * Пустой конструктор, обязательный для Room.
     * Room использует его при десериализации строк из базы данных в объекты Java.
     */
    public WorkTaskEntity() {
        // Пустой конструктор требуется Room для десериализации строк БД.
    }
}
