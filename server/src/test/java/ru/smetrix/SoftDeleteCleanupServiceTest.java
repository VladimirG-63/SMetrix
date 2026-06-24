package ru.smetrix;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.smetrix.service.SoftDeleteCleanupService;

class SoftDeleteCleanupServiceTest {
    @Test
    void purgesAggregateTablesInDependencyOrder() {
        final int[] calls = {0};
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public int update(String sql, Object... args) { calls[0]++; return 0; }
        };
        new SoftDeleteCleanupService(jdbc).purgeExpired();
        org.assertj.core.api.Assertions.assertThat(calls[0]).isEqualTo(6);
    }
}
