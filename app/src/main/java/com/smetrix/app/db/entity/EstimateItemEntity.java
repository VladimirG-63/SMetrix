
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
    tableName = "estimate_item",
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
public class EstimateItemEntity {





    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id;





    @NonNull
    @ColumnInfo(name = "project_room_id")
    public String projectRoomId;






    @Nullable
    @ColumnInfo(name = "fgis_code")
    public String fgisCode;





    @ColumnInfo(name = "name")
    public String name;







    @ColumnInfo(name = "unit_measure")
    public String unitMeasure;






    @ColumnInfo(name = "base_price")
    public BigDecimal basePrice;






    @ColumnInfo(name = "final_price")
    public BigDecimal finalPrice;






    @ColumnInfo(name = "consumption_rate")
    public BigDecimal consumptionRate;







    @ColumnInfo(name = "quantity")
    public BigDecimal quantity;

    @ColumnInfo(name = "calculation_method")
    public String calculationMethod;

    @ColumnInfo(name = "waste_percent")
    public BigDecimal wastePercent;

    @ColumnInfo(name = "layers")
    public Integer layers;

    @ColumnInfo(name = "thickness_meters")
    public BigDecimal thicknessMeters;

    @ColumnInfo(name = "manual_quantity")
    public BigDecimal manualQuantity;

    @ColumnInfo(name = "coverage_per_piece")
    public BigDecimal coveragePerPiece;

    @ColumnInfo(name = "coverage_per_package")
    public BigDecimal coveragePerPackage;

    @ColumnInfo(name = "package_size")
    public BigDecimal packageSize;

    @ColumnInfo(name = "formula_description")
    public String formulaDescription;







    @ColumnInfo(name = "total_price")
    public BigDecimal totalPrice;









    @ColumnInfo(name = "status")
    public String status;





    @ColumnInfo(name = "created_at")
    public long createdAt;





    @ColumnInfo(name = "updated_at")
    public long updatedAt;





    @ColumnInfo(name = "version")
    public long version;









    @ColumnInfo(name = "sync_state")
    public String syncState;




    public EstimateItemEntity() {

    }
}
