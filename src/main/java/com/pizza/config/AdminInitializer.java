package com.pizza.config;

import com.pizza.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds an administrator account on startup <strong>only</strong> when both
 * {@code ADMIN_DEFAULT_EMAIL} and {@code ADMIN_DEFAULT_PASSWORD} are provided.
 * No credentials are ever hardcoded; if the variables are missing, no admin is
 * created and a clear message is logged.
 */
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AdminService adminService;

    @Value("${admin.default.name:Administrator}")
    private String defaultName;

    @Value("${admin.default.email:}")
    private String defaultEmail;

    @Value("${admin.default.password:}")
    private String defaultPassword;

    @Override
    public void run(String... args) {
        boolean hasEmail = defaultEmail != null && !defaultEmail.isBlank();
        boolean hasPassword = defaultPassword != null && !defaultPassword.isBlank();

        if (!hasEmail || !hasPassword) {
            log.warn("No default admin created: ADMIN_DEFAULT_EMAIL and "
                    + "ADMIN_DEFAULT_PASSWORD environment variables are not both set. "
                    + "Create an admin account manually or provide these variables.");
            return;
        }

        boolean created = adminService.seedDefaultAdmin(defaultName, defaultEmail, defaultPassword);
        if (created) {
            log.info("Default admin account created for '{}'.", defaultEmail.trim().toLowerCase());
        } else {
            log.info("Default admin not created: an admin account already exists.");
        }
    }
}
