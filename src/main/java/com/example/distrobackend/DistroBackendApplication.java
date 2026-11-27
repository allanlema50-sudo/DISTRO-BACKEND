package com.example.distrobackend;

import com.example.distrobackend.configuration.DeploymentEnvironmentDiagnostics;
import com.example.distrobackend.configuration.ProductionDemoFlagGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class  DistroBackendApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DistroBackendApplication.class);
        application.addInitializers(new ProductionDemoFlagGuard(), new DeploymentEnvironmentDiagnostics());
        application.run(args);
    }

}
