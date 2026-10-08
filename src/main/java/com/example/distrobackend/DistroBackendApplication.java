package com.example.distrobackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class  DistroBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistroBackendApplication.class, args);
    }

}
