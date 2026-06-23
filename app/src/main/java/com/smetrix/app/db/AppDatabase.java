
package com.smetrix.app.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.smetrix.app.db.converter.BigDecimalConverter;
import com.smetrix.app.db.dao.ConflictDao;
import com.smetrix.app.db.dao.EstimateItemDao;
import com.smetrix.app.db.dao.MaterialsCacheDao;
import com.smetrix.app.db.dao.OpeningDao;
import com.smetrix.app.db.dao.ProjectDao;
import com.smetrix.app.db.dao.ProjectRoomDao;
import com.smetrix.app.db.dao.SyncStatusDao;
import com.smetrix.app.db.dao.UnitConversionDao;
import com.smetrix.app.db.dao.WorkTaskDao;
import com.smetrix.app.db.dao.WorkerDao;
import com.smetrix.app.db.entity.ConflictEntity;
import com.smetrix.app.db.entity.EstimateItemEntity;
import com.smetrix.app.db.entity.MaterialsCacheEntity;
import com.smetrix.app.db.entity.OpeningEntity;
import com.smetrix.app.db.entity.ProjectEntity;
import com.smetrix.app.db.entity.ProjectRoomEntity;
import com.smetrix.app.db.entity.UnitConversionEntity;
import com.smetrix.app.db.entity.WorkTaskEntity;
import com.smetrix.app.db.entity.WorkerEntity;








































@Database(
    entities = {
        ProjectEntity.class,
        ProjectRoomEntity.class,
        OpeningEntity.class,
        EstimateItemEntity.class,
        WorkerEntity.class,
        WorkTaskEntity.class,
        MaterialsCacheEntity.class,
        UnitConversionEntity.class,
        ConflictEntity.class
    },
    version = 7,
    exportSchema = true
)
@TypeConverters({BigDecimalConverter.class})
public abstract class AppDatabase extends RoomDatabase {

    private static final Callback UNIT_CONVERSION_SEED = new Callback() {
        @Override
        public void onOpen(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            super.onOpen(database);
            insertConversion(database, "м2", "м²", "1");
            insertConversion(database, "м²", "м²", "1");
            insertConversion(database, "кв.м", "м²", "1");
            insertConversion(database, "м3", "м³", "1");
            insertConversion(database, "м³", "м³", "1");
            insertConversion(database, "куб.м", "м³", "1");
            insertConversion(database, "кг", "кг", "1");
            insertConversion(database, "т", "кг", "1000");
            insertConversion(database, "шт", "шт", "1");
            insertConversion(database, "1000 шт", "шт", "0.001");
            insertConversion(database, "л", "л", "1");
            insertConversion(database, "м", "м", "1");
            insertConversion(database, "пог.м", "м", "1");
        }

        private void insertConversion(SupportSQLiteDatabase database,
                                      String fgisUnit, String appUnit, String factor) {
            database.execSQL(
                    "INSERT OR IGNORE INTO unit_conversion " +
                            "(fgis_unit, app_unit, conversion_factor) VALUES (?, ?, ?)",
                    new Object[]{fgisUnit, appUnit, factor}
            );
        }
    };







    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE project_room ADD COLUMN corners_count INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE project_room ADD COLUMN columns_count INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE project_room ADD COLUMN ventilation_area TEXT");
        }
    };








    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE opening ADD COLUMN depth TEXT");
            database.execSQL("ALTER TABLE opening ADD COLUMN placement_type TEXT");
        }
    };












    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {

            database.execSQL(
                "CREATE TABLE project_room_new (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "project_id TEXT NOT NULL, " +
                "name TEXT, " +
                "length TEXT, " +
                "width TEXT, " +
                "height TEXT, " +
                "manual_area_override TEXT, " +
                "created_at INTEGER NOT NULL DEFAULT 0, " +
                "updated_at INTEGER NOT NULL DEFAULT 0, " +
                "version INTEGER NOT NULL DEFAULT 0, " +
                "sync_state TEXT, " +
                "FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE" +
                ")"
            );

            database.execSQL(
                "INSERT INTO project_room_new " +
                "(id, project_id, name, length, width, height, manual_area_override, " +
                "created_at, updated_at, version, sync_state) " +
                "SELECT id, project_id, name, length, width, height, manual_area_override, " +
                "created_at, updated_at, version, sync_state FROM project_room"
            );

            database.execSQL("DROP TABLE project_room");

            database.execSQL("ALTER TABLE project_room_new RENAME TO project_room");

            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_project_room_project_id ON project_room(project_id)"
            );
        }
    };


    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE materials_cache_new (" +
                    "fgis_code TEXT NOT NULL, name TEXT, unit_measure TEXT, " +
                    "region_code TEXT NOT NULL, base_price TEXT, source TEXT, " +
                    "cached_at INTEGER NOT NULL, priority_score INTEGER NOT NULL, " +
                    "PRIMARY KEY(fgis_code, region_code))"
            );
            database.execSQL(
                    "INSERT INTO materials_cache_new " +
                    "SELECT fgis_code, name, unit_measure, COALESCE(region_code, ''), " +
                    "base_price, source, cached_at, priority_score FROM materials_cache"
            );
            database.execSQL("DROP TABLE materials_cache");
            database.execSQL("ALTER TABLE materials_cache_new RENAME TO materials_cache");
            database.execSQL("CREATE INDEX index_materials_cache_region_code ON materials_cache(region_code)");
            database.execSQL("CREATE INDEX index_materials_cache_name ON materials_cache(name)");
        }
    };


    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE materials_cache ADD COLUMN consumption_rate TEXT");
        }
    };


    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN calculation_method TEXT");
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN waste_percent TEXT");
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN layers INTEGER");
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN thickness_meters TEXT");
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN manual_quantity TEXT");
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN coverage_per_piece TEXT");
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN coverage_per_package TEXT");
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN package_size TEXT");
            database.execSQL("ALTER TABLE estimate_item ADD COLUMN formula_description TEXT");
        }
    };








    private static volatile AppDatabase instance;





    private static final String DATABASE_NAME = "smetrix.db";






















    public static AppDatabase getInstance(Context context) {


        if (instance == null) {


            synchronized (AppDatabase.class) {


                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                            .addCallback(UNIT_CONVERSION_SEED)
                            .build();
                }
            }
        }
        return instance;
    }











    public abstract ProjectDao projectDao();






    public abstract ProjectRoomDao projectRoomDao();






    public abstract OpeningDao openingDao();






    public abstract EstimateItemDao estimateItemDao();






    public abstract WorkerDao workerDao();






    public abstract WorkTaskDao workTaskDao();






    public abstract MaterialsCacheDao materialsCacheDao();






    public abstract ConflictDao conflictDao();










    public abstract SyncStatusDao syncStatusDao();

    public abstract UnitConversionDao unitConversionDao();
}
