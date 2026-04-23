package com.eum.paymentserver.config;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LegacyPaymentSchemaCleanup {

    private static final Logger log = LoggerFactory.getLogger(LegacyPaymentSchemaCleanup.class);

    private final JdbcTemplate jdbcTemplate;

    public LegacyPaymentSchemaCleanup(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void cleanupLegacyColumns() {
        dropIfExists("order_name");
        dropIfExists("customer_name");
        dropIfExists("customer_email");
    }

    private void dropIfExists(String columnName) {
        jdbcTemplate.execute("ALTER TABLE IF EXISTS payments DROP COLUMN IF EXISTS " + columnName);
        log.info("Checked legacy payment column cleanup: {}", columnName);
    }
}
