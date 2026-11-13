
package com.shadowbase.backend.service;

import org.springframework.stereotype.Service;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Service
public class MySQLContainerService {

    public MySQLContainer createContainer() {

        MySQLContainer mysqlContainer = new MySQLContainer(
                DockerImageName.parse("mysql:8.4")
        );

        mysqlContainer.start();

        return mysqlContainer;
    }
}
