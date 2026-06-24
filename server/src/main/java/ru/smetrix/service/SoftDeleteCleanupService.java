package ru.smetrix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** Permanently removes soft-deleted aggregates after the 30-day retention period. */
@Service
public class SoftDeleteCleanupService {
    private static final Logger log = LoggerFactory.getLogger(SoftDeleteCleanupService.class);
    private static final long RETENTION_MILLIS = Duration.ofDays(30).toMillis();

    private final JdbcTemplate jdbcTemplate;

    public SoftDeleteCleanupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${cleanup.soft-delete-cron:0 30 2 * * *}")
    @Transactional
    public void purgeExpired() {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        String expiredRooms = "SELECT r.id FROM project_rooms r LEFT JOIN projects p ON p.id=r.project_id " +
                "WHERE (r.deleted_at IS NOT NULL AND r.deleted_at < ?) " +
                "OR (p.deleted_at IS NOT NULL AND p.deleted_at < ?)";
        int tasks = jdbcTemplate.update("DELETE FROM work_tasks WHERE project_room_id IN (" + expiredRooms + ")",
                cutoff, cutoff);
        int estimates = jdbcTemplate.update("DELETE FROM estimate_items WHERE project_room_id IN (" + expiredRooms + ")",
                cutoff, cutoff);
        int openings = jdbcTemplate.update("DELETE FROM openings WHERE project_room_id IN (" + expiredRooms + ")",
                cutoff, cutoff);
        int rooms = jdbcTemplate.update("DELETE FROM project_rooms WHERE id IN (" + expiredRooms + ")", cutoff, cutoff);
        int projects = jdbcTemplate.update("DELETE FROM projects WHERE deleted_at IS NOT NULL AND deleted_at < ?", cutoff);
        int workers = jdbcTemplate.update("DELETE FROM workers WHERE deleted_at IS NOT NULL AND deleted_at < ?", cutoff);
        int total = tasks + estimates + openings + rooms + projects + workers;
        if (total > 0) {
            log.info("Soft-delete cleanup removed {} rows", total);
        }
    }
}
