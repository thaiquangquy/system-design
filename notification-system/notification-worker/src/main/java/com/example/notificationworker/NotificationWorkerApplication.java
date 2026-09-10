package com.example.notificationworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Explicit scanBasePackages is required because the shared notification-common types (including
 * the @Component stub providers) live under com.example.notification.common, which is not a
 * sub-package of com.example.notificationworker and so falls outside the default scan root.
 */
@SpringBootApplication(scanBasePackages = {"com.example.notificationworker", "com.example.notification.common"})
public class NotificationWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationWorkerApplication.class, args);
    }
}
