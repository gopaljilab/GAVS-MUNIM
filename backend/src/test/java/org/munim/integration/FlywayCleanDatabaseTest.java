package org.munim.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FlywayCleanDatabaseTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/munim_flyway_clean_test";
    private static final String USER = "munim";
    private static final String PASS = "munim-local";

    @BeforeAll
    static void prepareDatabase() {
        try (Connection conn = DriverManager.getConnection(ADMIN_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'munim_flyway_clean_test'");
            if (!rs.next()) {
                stmt.execute("CREATE DATABASE munim_flyway_clean_test");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare test database munim_flyway_clean_test", e);
        }
    }

    @Test
    @DisplayName("Verify clean Flyway migration sequence V1 -> V2 -> V3 -> V4 on a completely empty PostgreSQL database")
    void verifyCleanFlywayMigrationSequence() {
        // 1. Programmatically configure and execute Flyway on clean database
        Flyway flyway = Flyway.configure()
                .dataSource(DB_URL, USER, PASS)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        // Ensure database is completely clean before migration
        flyway.clean();

        // 2. Execute migration
        MigrateResult result = flyway.migrate();

        // 3. Verify migration execution results
        assertTrue(result.success, "Flyway migration must succeed cleanly");
        assertEquals(4, result.migrationsExecuted, "Exactly 4 migrations (V1, V2, V3, V4) must be executed");
        assertEquals("4", result.targetSchemaVersion, "Target schema version must be 4");

        // 4. Verify flyway_schema_history records
        DriverManagerDataSource ds = new DriverManagerDataSource(DB_URL, USER, PASS);
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT installed_rank, version, description, type, script, success " +
                        "FROM flyway_schema_history ORDER BY installed_rank"
        );

        assertEquals(4, history.size(), "flyway_schema_history must contain exactly 4 migration records");
        assertEquals("1", history.get(0).get("version"));
        assertEquals("procurement slice", history.get(0).get("description"));
        assertTrue((Boolean) history.get(0).get("success"));

        assertEquals("2", history.get(1).get("version"));
        assertEquals("approvals and audit", history.get(1).get("description"));
        assertTrue((Boolean) history.get(1).get("success"));

        assertEquals("3", history.get(2).get("version"));
        assertEquals("quote intelligence and clarifications", history.get(2).get("description"));
        assertTrue((Boolean) history.get(2).get("success"));

        assertEquals("4", history.get(3).get("version"));
        assertEquals("profitability and customer quotes", history.get(3).get("description"));
        assertTrue((Boolean) history.get(3).get("success"));

        // 5. Query and verify exact list of created tables
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class
        );

        List<String> expectedTables = List.of(
                "approvals",
                "audit_events",
                "bom_items",
                "boms",
                "clarification_messages",
                "commercial_proposals",
                "costing_sheets",
                "customer_enquiries",
                "customer_quotations",
                "evaluation_lines",
                "flyway_schema_history",
                "idempotency_records",
                "materials",
                "quote_evaluations",
                "quote_extractions",
                "rfqs",
                "supplier_quote_revisions",
                "supplier_quotes",
                "suppliers"
        );

        assertEquals(19, tables.size(), "Total tables (18 application tables + 1 flyway history table) must equal 19");
        assertEquals(expectedTables, tables, "Exact table list must match schema design");
    }
}
