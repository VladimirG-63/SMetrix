
package com.smetrix.app.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;






















@Entity(
    tableName = "project",
    indices = {
        @Index(value = {"user_id"}),
        @Index(value = {"deleted_at"}),
        @Index(value = {"sync_state"})
    }
)
public class ProjectEntity {






    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;





    @ColumnInfo(name = "user_id")
    public String userId;





    @ColumnInfo(name = "name")
    public String name;





    @ColumnInfo(name = "city")
    public String city;





    @ColumnInfo(name = "region_code")
    public String regionCode;






    @ColumnInfo(name = "tax_multiplier")
    public BigDecimal taxMultiplier;






    @ColumnInfo(name = "logistics_markup")
    public BigDecimal logisticsMarkup;








    @Nullable
    @ColumnInfo(name = "deleted_at")
    public Long deletedAt;





    @ColumnInfo(name = "last_synced_at")
    public long lastSyncedAt;





    @ColumnInfo(name = "created_at")
    public long createdAt;





    @ColumnInfo(name = "updated_at")
    public long updatedAt;







    @ColumnInfo(name = "version")
    public long version;









    @ColumnInfo(name = "sync_state")
    public String syncState;






    public ProjectEntity() {

    }
}
