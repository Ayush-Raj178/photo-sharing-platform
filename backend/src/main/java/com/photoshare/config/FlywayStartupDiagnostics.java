package com.photoshare.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            recoverInterruptedV1IfSafe(flyway);
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

    private void recoverInterruptedV1IfSafe(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
            if (!"photoshare".equals(databaseName(connection)) || !hasExactFailedV1History(connection)
                    || !hasExactV1Tables(connection) || !allApplicationTablesAreEmpty(connection)
                    || !hasExpectedV1Columns(connection) || !hasExpectedV1Constraints(connection)) {
                throw new IllegalStateException(
                        "Refusing Flyway V1 recovery because the production schema did not match all safety checks");
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE flyway_schema_history SET success = TRUE "
                            + "WHERE installed_rank = 1 AND version = '1' "
                            + "AND script = 'V1__create_core_schema.sql' AND checksum = 513978092 "
                            + "AND success = FALSE")) {
                int updated = statement.executeUpdate();
                if (updated != 1) {
                    throw new IllegalStateException(
                            "Refusing Flyway V1 recovery because the guarded history update affected " + updated + " rows");
                }
            }
            log.info("Recovered verified interrupted Flyway V1 history; normal Flyway migration will continue");
        } catch (SQLException exception) {
            throw new IllegalStateException("Guarded Flyway V1 recovery failed", exception);
        }
    }

    private String databaseName(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            result.next();
            return result.getString(1);
        }
    }

    private boolean hasExactFailedV1History(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT installed_rank, version, script, checksum, success "
                             + "FROM flyway_schema_history ORDER BY installed_rank")) {
            if (!result.next()) {
                return false;
            }
            boolean matches = result.getInt("installed_rank") == 1
                    && "1".equals(result.getString("version"))
                    && "V1__create_core_schema.sql".equals(result.getString("script"))
                    && result.getInt("checksum") == 513978092
                    && !result.getBoolean("success");
            return matches && !result.next();
        }
    }

    private boolean hasExactV1Tables(Connection connection) throws SQLException {
        Set<String> actual = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SHOW TABLES")) {
            while (result.next()) {
                actual.add(result.getString(1));
            }
        }
        return actual.equals(Set.of(
                "users", "events", "event_members", "photos", "galleries", "gallery_photos",
                "flyway_schema_history"));
    }

    private boolean allApplicationTablesAreEmpty(Connection connection) throws SQLException {
        for (String table : List.of(
                "users", "events", "event_members", "photos", "galleries", "gallery_photos")) {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                result.next();
                if (result.getLong(1) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasExpectedV1Columns(Connection connection) throws SQLException {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("event_members", List.of(
                "event_id:bigint:NO", "user_id:bigint:NO", "added_at:timestamp(6):NO"));
        expected.put("events", List.of(
                "id:bigint:NO", "owner_id:bigint:NO", "name:varchar(150):NO",
                "description:varchar(2000):YES", "created_at:timestamp(6):NO"));
        expected.put("galleries", List.of(
                "id:bigint:NO", "event_id:bigint:NO", "title:varchar(150):NO",
                "status:varchar(10):NO", "pin_hash:varchar(255):YES", "pin_version:int:NO",
                "share_token:varchar(32):NO", "created_at:timestamp(6):NO",
                "updated_at:timestamp(6):NO", "published_at:timestamp(6):YES"));
        expected.put("gallery_photos", List.of(
                "gallery_id:bigint:NO", "photo_id:bigint:NO", "event_id:bigint:NO",
                "position:int:NO", "selected_at:timestamp(6):NO"));
        expected.put("photos", List.of(
                "id:bigint:NO", "event_id:bigint:NO", "uploaded_by:bigint:NO",
                "original_filename:varchar(255):NO", "storage_key:varchar(512):NO",
                "content_type:varchar(50):NO", "file_size_bytes:bigint:NO", "width_px:int:NO",
                "height_px:int:NO", "status:varchar(10):NO", "created_at:timestamp(6):NO",
                "updated_at:timestamp(6):NO", "failure_code:varchar(50):YES"));
        expected.put("users", List.of(
                "id:bigint:NO", "email:varchar(254):NO", "display_name:varchar(100):NO",
                "password_hash:varchar(255):NO", "role:varchar(20):NO",
                "provisioned_by:bigint:YES", "created_at:timestamp(6):NO"));

        Map<String, List<String>> actual = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE "
                             + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                             + "AND TABLE_NAME <> 'flyway_schema_history' "
                             + "ORDER BY TABLE_NAME, ORDINAL_POSITION")) {
            while (result.next()) {
                actual.computeIfAbsent(result.getString("TABLE_NAME"), ignored -> new ArrayList<>())
                        .add(result.getString("COLUMN_NAME") + ":" + result.getString("COLUMN_TYPE")
                                + ":" + result.getString("IS_NULLABLE"));
            }
        }
        return expected.equals(actual);
    }

    private boolean hasExpectedV1Constraints(Connection connection) throws SQLException {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("event_members", Set.of(
                "PRIMARY", "fk_event_members_event", "fk_event_members_user"));
        expected.put("events", Set.of("PRIMARY", "fk_events_owner"));
        expected.put("galleries", Set.of(
                "PRIMARY", "uk_galleries_event", "uk_galleries_id_event", "uk_galleries_share_token",
                "fk_galleries_event", "ck_galleries_status", "ck_galleries_pin",
                "ck_galleries_publication"));
        expected.put("gallery_photos", Set.of(
                "PRIMARY", "uk_gallery_photos_position", "fk_gallery_photos_gallery",
                "fk_gallery_photos_photo", "ck_gallery_photos_position"));
        expected.put("photos", Set.of(
                "PRIMARY", "uk_photos_storage_key", "uk_photos_id_event", "fk_photos_membership",
                "ck_photos_size", "ck_photos_dimensions", "ck_photos_status"));
        expected.put("users", Set.of(
                "PRIMARY", "uk_users_email", "fk_users_provisioned_by", "ck_users_role",
                "ck_users_provisioning"));

        Map<String, Set<String>> actual = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT TABLE_NAME, CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS "
                             + "WHERE CONSTRAINT_SCHEMA = DATABASE() "
                             + "AND TABLE_NAME <> 'flyway_schema_history' ORDER BY TABLE_NAME, CONSTRAINT_NAME")) {
            while (result.next()) {
                actual.computeIfAbsent(result.getString("TABLE_NAME"), ignored -> new LinkedHashSet<>())
                        .add(result.getString("CONSTRAINT_NAME"));
            }
        }
        return expected.equals(actual);
    }
}
