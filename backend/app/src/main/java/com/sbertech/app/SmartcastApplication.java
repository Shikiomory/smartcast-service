package com.sbertech.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sbertech")
public class SmartcastApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartcastApplication.class, args);
    }

}
