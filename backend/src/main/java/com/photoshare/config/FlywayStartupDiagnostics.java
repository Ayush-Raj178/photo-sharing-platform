package com.photoshare.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporary production diagnostics for identifying the database seen by Flyway
 * before validation runs. This class never logs connection strings or secrets.
 */
@Configuration(proxyBeanMethods = false)
class FlywayStartupDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(FlywayStartupDiagnostics.class);

    @Bean
    FlywayMigrationStrategy diagnosticFlywayMigrationStrategy() {
        return flyway -> {
            logDatabaseIdentity(flyway);
            logDatasourceOverridePresence();
            flyway.migrate();
        };
    }

    private void logDatabaseIdentity(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT DATABASE(), @@hostname, @@port, VERSION()")) {
                if (result.next()) {
                    log.info("Flyway diagnostic [database={}, server={}, port={}, productVersion={}]",
                            result.getString(1), result.getString(2), result.getInt(3), result.getString(4));
                }
            }

            List<String> tables = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SHOW TABLES")) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }
            log.info("Flyway diagnostic [tables={}]", tables);

            logTableState(connection, tables);

            if (tables.stream().anyMatch("flyway_schema_history"::equalsIgnoreCase)) {
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery(
                             "SELECT installed_rank, version, description, type, script, checksum, "
                                     + "installed_on, execution_time, success "
                                     + "FROM flyway_schema_history ORDER BY installed_rank")) {
                    while (result.next()) {
                        log.info("Flyway diagnostic history [rank={}, version={}, description={}, type={}, "
                                        + "script={}, checksum={}, installedOn={}, executionTimeMs={}, success={}]",
                                result.getInt("installed_rank"), result.getString("version"),
                                result.getString("description"), result.getString("type"),
                                result.getString("script"), result.getObject("checksum"),
                                result.getTimestamp("installed_on"), result.getInt("execution_time"),
                                result.getBoolean("success"));
                    }
                }
            }
        } catch (SQLException exception) {
            log.warn("Flyway diagnostic query failed [sqlState={}, errorCode={}, message={}]",
                    exception.getSQLState(), exception.getErrorCode(), exception.getMessage());
        }
    }

    private void logTableState(Connection connection, List<String> tables) throws SQLException {
        List<String> expectedTables = List.of(
                "users", "events", "event_members", "photos", "galleries", "gallery_photos");
        for (String table : expectedTables) {
            if (tables.stream().noneMatch(table::equalsIgnoreCase)) {
                log.info("Flyway diagnostic table [name={}, present=false]", table);
                continue;
            }

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM `" + table + "`")) {
                result.next();
                log.info("Flyway diagnostic table [name={}, present=true, rowCount={}]",
                        table, result.getLong(1));
            }

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
                result.next();
                log.info("Flyway diagnostic definition [name={}, ddl={}]", table, result.getString(2));
            }
        }

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT TABLE_NAME, ENGINE, CREATE_TIME FROM information_schema.TABLES "
                             + "WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME")) {
            while (result.next()) {
                log.info("Flyway diagnostic table metadata [name={}, engine={}, createdAt={}]",
                        result.getString("TABLE_NAME"), result.getString("ENGINE"),
                        result.getTimestamp("CREATE_TIME"));
            }
        }
    }

    private void logDatasourceOverridePresence() {
        List<String> candidates = List.of(
                "DB_JDBC_URL",
                "SPRING_DATASOURCE_URL",
                "JDBC_DATABASE_URL",
                "DATABASE_URL",
                "MYSQL_URL",
                "SPRING_FLYWAY_URL",
                "SPRING_PROFILES_ACTIVE",
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS");
        List<String> present = candidates.stream()
                .filter(name -> System.getenv(name) != null)
                .toList();
        log.info("Flyway diagnostic [presentConfigurationKeys={}]", present);
    }
}
