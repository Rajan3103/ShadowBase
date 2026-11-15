
package com.shadowbase.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@EnabledIf("isDockerAvailable")
class BackendApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

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
