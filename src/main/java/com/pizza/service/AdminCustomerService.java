package com.pizza.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pizza.dto.CustomerUpdateDTO;
import com.pizza.entity.Customer;
import com.pizza.exception.DuplicateEmailException;
import com.pizza.exception.DuplicatePhoneException;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

/** Admin-facing customer management (US-015, US-016). */
@Service
@RequiredArgsConstructor
public class AdminCustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    /**
     * Admin customer-list query supporting search-by-name-or-email and sort
     * (US-015), mirroring {@code PizzaService.search}'s branch-in-Java style.
     *
     * @param search optional name/email fragment
     * @param sort   "nameAsc", "nameDesc", "registeredAsc", "registeredDesc" or null
     */
    @Transactional(readOnly = true)
    public List<Customer> search(String search, String sort) {
        List<Customer> results = (search != null && !search.isBlank())
                ? customerRepository.searchByNameOrEmail(search.trim())
                : customerRepository.findAll();

        if ("nameAsc".equals(sort)) {
            results.sort(Comparator.comparing(c -> c.getFullName().toLowerCase()));
        } else if ("nameDesc".equals(sort)) {
            results.sort(Comparator.comparing((Customer c) -> c.getFullName().toLowerCase()).reversed());
        } else if ("registeredAsc".equals(sort)) {
            results.sort(Comparator.comparing(Customer::getCreatedAt));
        } else if ("registeredDesc".equals(sort)) {
            results.sort(Comparator.comparing(Customer::getCreatedAt).reversed());
        }
        return results;
    }

    @Transactional(readOnly = true)
    public Customer getById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    @Transactional
    public Customer updateCustomer(Long id, CustomerUpdateDTO dto) {
        Customer customer = getById(id);

        String email = dto.getEmail().trim().toLowerCase();
        String phone = dto.getPhone().trim();

        customerRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateEmailException("Another account with this email already exists");
            }
        });
        if (!phone.equals(customer.getPhone()) && customerRepository.existsByPhone(phone)) {
            throw new DuplicatePhoneException("Another account with this phone number already exists");
        }

        customer.setFirstName(dto.getFirstName().trim());
        customer.setLastName(dto.getLastName().trim());
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setAddress(dto.getAddress().trim());

        return customerRepository.save(customer);
    }
}
