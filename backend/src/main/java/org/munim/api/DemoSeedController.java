package org.munim.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoSeedController {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedController.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DemoSeedController(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoSeedIfEmpty() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM customer_enquiries", Integer.class);
            if (count == null || count == 0) {
                log.info("Database is empty on startup. Automatically seeding authentic Indian MSME demo data...");
                seedDemoData();
                log.info("Automatic demo data seeding completed successfully.");
            }
        } catch (Exception e) {
            log.warn("Could not auto-seed demo data on startup (tables may still be initializing): {}", e.getMessage());
        }
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed() {
        try {
            seedDemoData();
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Authentic Indian MSME demo data seeded successfully (Apex Steel, Mahalaxmi Metals, Defence Chassis, Bharat Forge)."
            ));
        } catch (Exception e) {
            log.error("Failed to seed demo data: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to seed demo data: " + e.getMessage()
            ));
        }
    }

    private void seedDemoData() {
        ClassPathResource resource = new ClassPathResource("seed_demo_data.sql");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resource);
        populator.execute(dataSource);
    }
}
