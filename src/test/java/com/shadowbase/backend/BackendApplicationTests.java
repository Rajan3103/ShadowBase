
package com.shadowbase.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldConnectToMySQLContainer() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1", Integer.class
        );

        assertEquals(1, result);
    }
}
