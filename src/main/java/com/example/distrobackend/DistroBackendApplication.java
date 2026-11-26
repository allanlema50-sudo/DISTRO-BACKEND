package com.example.distrobackend;

import com.example.distrobackend.configuration.DeploymentEnvironmentDiagnostics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class  DistroBackendApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DistroBackendApplication.class);
        application.addInitializers(new DeploymentEnvironmentDiagnostics());
        application.run(args);
    }

}
