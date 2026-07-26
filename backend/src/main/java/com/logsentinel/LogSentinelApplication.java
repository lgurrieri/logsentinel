package com.logsentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LogSentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogSentinelApplication.class, args);
    }

}
