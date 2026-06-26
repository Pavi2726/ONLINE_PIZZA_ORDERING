package com.pizza.service;

import com.pizza.dto.RegisterRequest;
import com.pizza.entity.Customer;
import com.pizza.exception.DuplicateEmailException;
import com.pizza.exception.DuplicatePhoneException;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for customer registration and login (US-001, US-002).
 *
 * <p>Authentication is local and simple: passwords are hashed once with BCrypt
 * and stored in MySQL; login compares the raw password against the stored hash
 * with {@link PasswordEncoder#matches}. Sessions are managed by the controller
 * layer (no Spring Security / JWT / email verification).</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a new customer.
     *
     * <ol>
     *   <li>Validate duplicate email / phone in MySQL.</li>
     *   <li>Hash the password once with BCrypt.</li>
     *   <li>Save the customer in MySQL.</li>
     * </ol>
     *
     * @param request the validated registration form
     * @return the persisted customer
     */
    @Transactional
    public Customer register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();

        log.info("Registration started for email={}", email);

        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("An account with this email already exists");
        }
        if (customerRepository.existsByPhone(phone)) {
            throw new DuplicatePhoneException("An account with this phone number already exists");
        }

        Customer customer = Customer.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(request.getPassword()))
                .address(request.getAddress().trim())
                .build();

        Customer saved = customerRepository.saveAndFlush(customer);
        log.info("Registration complete for email={}, customerId={}", email, saved.getId());
        return saved;
    }

    /**
     * Authenticates a customer (US-002).
     *
     * @param email    the login email
     * @param password the raw password
     * @return the authenticated customer
     * @throws InvalidCredentialsException if the email/password is wrong
     */
    @Transactional(readOnly = true)
    public Customer login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();
        log.info("Login attempt for email={}", normalizedEmail);

        Customer customer = customerRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(password, customer.getPassword())) {
            log.warn("Login failed: password mismatch for email={}", normalizedEmail);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("Login successful for email={}", normalizedEmail);
        return customer;
    }
}
