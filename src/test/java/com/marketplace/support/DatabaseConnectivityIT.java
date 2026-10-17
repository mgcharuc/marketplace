package com.marketplace.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DatabaseConnectivityIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("La aplicación arranca contra PostgreSQL 17")
    void connectsToPostgres17() {
        Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
        String version = jdbc.queryForObject("SHOW server_version", String.class);

        assertThat(one).isEqualTo(1);
        assertThat(version).startsWith("17");
    }
}
