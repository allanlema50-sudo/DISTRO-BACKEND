package com.example.distrobackend.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(prefix = "firebase.push", name = "enabled", havingValue = "true")
public class FirebasePushConfiguration {

    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp(@Value("${firebase.push.project-id:}") String projectId) throws IOException {
        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault());
        if (projectId != null && !projectId.isBlank()) options.setProjectId(projectId.trim());
        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
