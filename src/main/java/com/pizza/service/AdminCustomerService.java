package com.pizza.service;

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
