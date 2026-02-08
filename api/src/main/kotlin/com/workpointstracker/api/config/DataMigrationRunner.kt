package com.workpointstracker.api.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class DataMigrationRunner(
    private val jdbcTemplate: JdbcTemplate
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DataMigrationRunner::class.java)

    override fun run(args: ApplicationArguments) {
        migratePausedMinutesToSeconds()
    }

    private fun migratePausedMinutesToSeconds() {
        try {
            // Check if the old column exists (case-insensitive for Postgres)
            val columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE LOWER(table_name) = 'sessions' AND LOWER(column_name) = 'total_paused_minutes'",
                String::class.java
            )

            if (columns.isNotEmpty()) {
                val updated = jdbcTemplate.update(
                    "UPDATE sessions SET total_paused_seconds = total_paused_minutes * 60 WHERE total_paused_minutes > 0 AND (total_paused_seconds IS NULL OR total_paused_seconds = 0)"
                )
                if (updated > 0) {
                    logger.info("Migrated {} sessions: total_paused_minutes → total_paused_seconds", updated)
                }
            }

            // Ensure no nulls remain in total_paused_seconds
            val nullFixed = jdbcTemplate.update(
                "UPDATE sessions SET total_paused_seconds = 0 WHERE total_paused_seconds IS NULL"
            )
            if (nullFixed > 0) {
                logger.info("Set total_paused_seconds = 0 for {} rows with NULL", nullFixed)
            }
        } catch (e: Exception) {
            logger.warn("Paused-minutes migration skipped: {}", e.message)
        }
    }
}
