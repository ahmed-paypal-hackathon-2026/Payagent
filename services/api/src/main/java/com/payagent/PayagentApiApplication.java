package com.payagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PayagentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayagentApiApplication.class, args);
    }
}
