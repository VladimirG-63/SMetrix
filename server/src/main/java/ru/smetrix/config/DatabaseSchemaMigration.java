package ru.smetrix.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!test")
public class DatabaseSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigration.class);
    private static final int MATERIAL_NAME_LENGTH = 4000;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        expandVarchar("material_cache", "name", MATERIAL_NAME_LENGTH);
        expandVarchar("estimate_items", "name", MATERIAL_NAME_LENGTH);
    }

    private void expandVarchar(String table, String column, int requiredLength) {
        Integer currentLength = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, table, column);

        if (currentLength != null && currentLength < requiredLength) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column
                    + " TYPE VARCHAR(" + requiredLength + ")");
            log.info("Database migration applied: {}.{} expanded from {} to {} characters",
                    table, column, currentLength, requiredLength);
        }
    }
}
