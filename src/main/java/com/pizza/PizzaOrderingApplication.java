package com.pizza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Pizza Ordering System.
 *
 * <p>Implements the Sriya Pandey module (US-001 to US-007): customer
 * registration with local BCrypt authentication, session-based login,
 * pizza catalogue browsing, admin pizza management with Cloudinary image
 * storage and order placement.</p>
 */
@SpringBootApplication
public class PizzaOrderingApplication {

    public static void main(String[] args) {
        SpringApplication.run(PizzaOrderingApplication.class, args);
    }
}
