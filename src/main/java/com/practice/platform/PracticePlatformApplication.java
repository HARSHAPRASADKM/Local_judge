package com.practice.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PracticePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PracticePlatformApplication.class, args);
    }
}
