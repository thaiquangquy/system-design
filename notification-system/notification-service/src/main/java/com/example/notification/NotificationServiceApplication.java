package com.example.notification;

import com.example.notification.config.NotificationProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(NotificationProviderProperties.class)
public class NotificationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
