package com.smetrix.app.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.smetrix.app.db.entity.UnitConversionEntity;

import java.util.List;

@Dao
public interface UnitConversionDao {
    @Query("SELECT * FROM unit_conversion ORDER BY fgis_unit")
    LiveData<List<UnitConversionEntity>> observeAll();
}
