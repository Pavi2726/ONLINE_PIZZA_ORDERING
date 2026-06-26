package com.pizza.service;

import com.pizza.entity.Admin;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for administrator authentication (separate from customers).
 * Passwords are BCrypt-hashed; authentication is session based (no Spring
 * Security / JWT).
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticates an administrator.
     *
     * @param email    the admin email
     * @param password the raw password
     * @return the authenticated admin
     * @throws InvalidCredentialsException if email/password is wrong
     */
    @Transactional(readOnly = true)
    public Admin login(String email, String password) {
        Admin admin = adminRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return admin;
    }

    /**
     * Seeds an administrator account when none exist yet. Credentials are
     * supplied by the caller (from environment variables) — never hardcoded.
     *
     * @param name        the display name
     * @param email       the login email
     * @param rawPassword the raw password (will be BCrypt-hashed)
     * @return {@code true} if an admin was created, {@code false} if one
     *         already existed
     */
    @Transactional
    public boolean seedDefaultAdmin(String name, String email, String rawPassword) {
        if (adminRepository.count() > 0) {
            return false;
        }
        Admin admin = Admin.builder()
                .name(name == null || name.isBlank() ? "Administrator" : name.trim())
                .email(email.trim().toLowerCase())
                .password(passwordEncoder.encode(rawPassword))
                .build();
        adminRepository.save(admin);
        return true;
    }
}
