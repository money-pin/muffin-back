package com.muffin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class MuffinApplication {

    public static void main(String[] args) {
        SpringApplication.run(MuffinApplication.class, args);
    }
}
