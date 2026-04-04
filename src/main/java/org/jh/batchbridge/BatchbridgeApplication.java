package org.jh.batchbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BatchbridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchbridgeApplication.class, args);
    }

}
