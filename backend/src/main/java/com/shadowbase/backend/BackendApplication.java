package com.shadowbase.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
//remember everyone i added this exclude property so spring will not try to connect to
// database automatically.if you have connected a database then remove the exclude propery
//  in the springbootapplication annotation.
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class
})
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
