// app/src/main/java/com/smetrix/app/db/entity/WorkerEntity.java
package com.smetrix.app.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room Entity для таблицы "worker".
 *
 * Представляет рабочего (мастера, бригадира, специалиста), которого
 * пользователь добавил в свою базу подрядчиков.
 *
 * Связь с проектами:
 *  Рабочий не привязан к конкретному проекту напрямую. Вместо этого,
 *  конкретные задания (WorkTaskEntity) ссылаются на этого рабочего
 *  через поле workerId. Один рабочий может выполнять задания в разных
 *  помещениях и проектах.
 *
 * Поле userId:
 *  Идентификатор пользователя (владельца аккаунта), которому принадлежит
 *  этот рабочий. Обеспечивает изоляцию данных — каждый пользователь
 *  видит только своих рабочих. Индексируется для быстрой выборки.
 *
 * Удаление рабочего:
 *  При удалении WorkerEntity связанные WorkTaskEntity не удаляются, а
 *  получают workerId = null (onDelete = SET_NULL в WorkTaskEntity).
 *  Это позволяет сохранить историю заданий даже после "архивирования"
 *  рабочего из базы контактов.
 *
 * Дублирование:
 *  Защита от дублей реализована в WorkerRepository через перехват
 *  SQLiteConstraintException при вставке — не на уровне схемы БД,
 *  потому что уникальность phone не гарантируется (у рабочего может
 *  не быть телефона или телефон может быть общим).
 */
@Entity(
    tableName = "worker",
    indices = {
        @Index(value = {"user_id"})
    }
)
public class WorkerEntity {

    /**
     * Уникальный идентификатор рабочего.
     * Генерируется на клиенте (UUID v7) при создании.
     */
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /**
     * Идентификатор пользователя-владельца этой записи.
     * Соответствует идентификатору авторизованного пользователя.
     * Индексируется для быстрой выборки: SELECT * FROM worker WHERE user_id = ?
     */
    @NonNull
    @ColumnInfo(name = "user_id")
    public String userId;

    /**
     * Полное имя рабочего.
     * Пример: "Иванов Иван Иванович" или "Мастер Сергей".
     * Валидируется в WorkerRepository: длина от 1 до 100 символов.
     */
    @ColumnInfo(name = "full_name")
    public String fullName;

    /**
     * Контактный телефон рабочего.
     * Хранится в нормализованном виде (только цифры и символ '+').
     * Пример: "+79161234567".
     * Может быть пустой строкой, если телефон не указан.
     * Валидируется в WorkerRepository: максимальная длина 20 символов.
     */
    @ColumnInfo(name = "phone")
    public String phone;

    /**
     * Специализация рабочего.
     * Произвольная строка, описывающая профиль работ.
     * Примеры: "Маляр-штукатур", "Плиточник", "Электрик".
     * Используется для фильтрации и удобства поиска нужного специалиста.
     */
    @ColumnInfo(name = "specialty")
    public String specialty;

    /**
     * Временная метка создания записи на клиенте.
     * UNIX-timestamp в миллисекундах.
     */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    /**
     * Временная метка последнего изменения записи.
     * UNIX-timestamp в миллисекундах. Обновляется при редактировании данных рабочего.
     */
    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    /**
     * Версия записи для оптимистичной блокировки при синхронизации.
     * Начальное значение при создании на клиенте: 0.
     * Сервер увеличивает значение при каждом успешном обновлении.
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
    public WorkerEntity() {
        // Пустой конструктор требуется Room для десериализации строк БД.
    }
}
