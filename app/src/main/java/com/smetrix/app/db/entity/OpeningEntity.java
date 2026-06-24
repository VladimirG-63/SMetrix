// app/src/main/java/com/smetrix/app/db/entity/OpeningEntity.java
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
 * Room Entity для таблицы "opening".
 *
 * Представляет элемент помещения, влияющий на расчёт площади стен.
 *
 * Типы элементов (поле type):
 *  - "DOOR"   — дверь. Вычитает (width × height) из площади стен.
 *  - "WINDOW" — окно.  Вычитает (width × height) из площади стен.
 *  - "VENT"   — вентиляционное отверстие. Вычитает (width × height) из площади стен.
 *  - "COLUMN" — колонна/короб. ПРИБАВЛЯЕТ к площади стен.
 *               Математика зависит от поля placementType:
 *               • FREESTANDING  (отдельно стоящая, 4 стороны):
 *                   +2*(width + depth)*height
 *               • WALL_ADJACENT (пристенная, 3 стороны):
 *                   +(width + 2*depth)*height
 *               В обоих случаях (width × depth) может вычитаться из площади пола
 *               (не реализовано в данной версии, т.к. пол считается отдельно).
 *
 * Поле depth используется только для типа COLUMN.
 * Поле placementType используется только для типа COLUMN.
 */
@Entity(
    tableName = "opening",
    foreignKeys = {
        @ForeignKey(
            entity = ProjectRoomEntity.class,
            parentColumns = "id",
            childColumns = "project_room_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index(value = {"project_room_id"})
    }
)
public class OpeningEntity {

    /**
     * Уникальный идентификатор элемента.
     * Генерируется на клиенте (UUID v7) при создании.
     */
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;

    /**
     * Идентификатор комнаты, в которой находится этот элемент.
     * Внешний ключ → ProjectRoomEntity.id.
     */
    @NonNull
    @ColumnInfo(name = "project_room_id")
    public String projectRoomId;

    /**
     * Тип элемента помещения.
     * Допустимые значения: "DOOR", "WINDOW", "VENT", "COLUMN".
     */
    @ColumnInfo(name = "type")
    public String type;

    /**
     * Ширина элемента в метрах.
     * Для колонны — это одна из сторон (например, сторона, смотрящая «в комнату»).
     */
    @ColumnInfo(name = "width")
    public BigDecimal width;

    /**
     * Высота элемента в метрах.
     * Для DOOR/WINDOW/VENT — высота проёма.
     * Для COLUMN — высота колонны (обычно равна высоте потолка).
     */
    @ColumnInfo(name = "height")
    public BigDecimal height;

    /**
     * Глубина элемента в метрах.
     * Используется только для типа COLUMN.
     * Для остальных типов — null.
     * Для пристенной колонны — это расстояние, на которое колонна выступает из стены.
     * Для отдельно стоящей — это вторая сторона колонны.
     */
    @Nullable
    @ColumnInfo(name = "depth")
    public BigDecimal depth;

    /**
     * Тип размещения колонны.
     * Используется только для типа COLUMN.
     * Допустимые значения:
     *  - "FREESTANDING"  — отдельно стоящая (4 стороны под отделку).
     *  - "WALL_ADJACENT" — пристенная (3 стороны под отделку).
     * Для остальных типов — null.
     */
    @Nullable
    @ColumnInfo(name = "placement_type")
    public String placementType;

    /**
     * Временная метка создания записи на клиенте.
     * UNIX-timestamp в миллисекундах.
     */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    /**
     * Временная метка последнего изменения записи.
     * UNIX-timestamp в миллисекундах.
     */
    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    /**
     * Версия записи для оптимистичной блокировки при синхронизации.
     */
    @ColumnInfo(name = "version")
    public long version;

    /**
     * Состояние синхронизации.
     * Допустимые значения: "SYNCED", "PENDING_CREATE", "PENDING_UPDATE",
     * "PENDING_DELETE", "FAILED", "CONFLICT".
     */
    @ColumnInfo(name = "sync_state")
    public String syncState;

    /**
     * Пустой конструктор, обязательный для Room.
     */
    public OpeningEntity() {}
}
