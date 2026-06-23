
package com.smetrix.app.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;





















@Entity(
    tableName = "project_room",
    foreignKeys = {
        @ForeignKey(
            entity = ProjectEntity.class,
            parentColumns = "id",
            childColumns = "project_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index(value = {"project_id"})
    }
)
public class ProjectRoomEntity {





    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;






    @NonNull
    @ColumnInfo(name = "project_id")
    public String projectId;





    @ColumnInfo(name = "name")
    public String name;






    @Nullable
    @ColumnInfo(name = "length")
    public BigDecimal length;





    @Nullable
    @ColumnInfo(name = "width")
    public BigDecimal width;





    @Nullable
    @ColumnInfo(name = "height")
    public BigDecimal height;







    @Nullable
    @ColumnInfo(name = "manual_area_override")
    public BigDecimal manualAreaOverride;







    @ColumnInfo(name = "created_at")
    public long createdAt;





    @ColumnInfo(name = "updated_at")
    public long updatedAt;





    @ColumnInfo(name = "version")
    public long version;









    @ColumnInfo(name = "sync_state")
    public String syncState;




    public ProjectRoomEntity() {

    }
}
