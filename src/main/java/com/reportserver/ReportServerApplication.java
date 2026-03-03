package com.reportserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReportServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportServerApplication.class, args);
    }
}
