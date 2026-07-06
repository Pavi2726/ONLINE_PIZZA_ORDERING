package com.pizza.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pizza.dto.CustomerUpdateDTO;
import com.pizza.entity.Customer;
import com.pizza.exception.DuplicateEmailException;
import com.pizza.exception.DuplicatePhoneException;
import com.pizza.exception.ResourceNotFoundException;
import com.pizza.repository.CustomerRepository;
import com.pizza.testsupport.TestDataFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link AdminCustomerService} (US-015, US-016).
 */
@ExtendWith(MockitoExtension.class)
class AdminCustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private AdminCustomerService adminCustomerService;

    private CustomerUpdateDTO dto(String firstName, String lastName, String email, String phone, String address) {
        CustomerUpdateDTO dto = new CustomerUpdateDTO();
        dto.setFirstName(firstName);
        dto.setLastName(lastName);
        dto.setEmail(email);
        dto.setPhone(phone);
        dto.setAddress(address);
        return dto;
    }

    // ---------------------------------------------------------------- getById

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> adminCustomerService.getById(99L));
        assertThat(ex.getMessage()).isEqualTo("Customer not found");
    }

    // ---------------------------------------------------------------- updateCustomer

    @Test
    void updateCustomer_notFound_throwsResourceNotFoundException() {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminCustomerService.updateCustomer(1L,
                        dto("Jane", "Doe", "jane@example.com", "5551234567", "1 Test St")));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateCustomer_emailBelongsToDifferentCustomer_throwsDuplicateEmailException() {
        Customer target = TestDataFactory.customer();
        target.setId(1L);
        target.setEmail("original@example.com");
        target.setPhone("5551110000");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(target));

        Customer other = TestDataFactory.customer();
        other.setId(2L);
        other.setEmail("taken@example.com");
        when(customerRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(other));

        DuplicateEmailException ex = assertThrows(DuplicateEmailException.class,
                () -> adminCustomerService.updateCustomer(1L,
                        dto("Jane", "Doe", "taken@example.com", "5551110000", "1 Test St")));

        assertThat(ex.getMessage()).isEqualTo("Another account with this email already exists");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateCustomer_ownCurrentEmail_isAllowed() {
        Customer target = TestDataFactory.customer();
        target.setId(1L);
        target.setEmail("jane@example.com");
        target.setPhone("5551110000");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(target));
        // findByEmail("jane@example.com") resolves to the same customer (id 1) being updated.
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(target));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer result = adminCustomerService.updateCustomer(1L,
                dto("Jane", "Doe", "jane@example.com", "5551110000", "1 New St"));

        assertThat(result.getEmail()).isEqualTo("jane@example.com");
        assertThat(result.getAddress()).isEqualTo("1 New St");
        verify(customerRepository).save(target);
    }

    @Test
    void updateCustomer_phoneBelongsToDifferentCustomer_throwsDuplicatePhoneException() {
        Customer target = TestDataFactory.customer();
        target.setId(1L);
        target.setEmail("jane@example.com");
        target.setPhone("5551110000");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(target));
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(target));
        when(customerRepository.existsByPhone("5559998888")).thenReturn(true);

        DuplicatePhoneException ex = assertThrows(DuplicatePhoneException.class,
                () -> adminCustomerService.updateCustomer(1L,
                        dto("Jane", "Doe", "jane@example.com", "5559998888", "1 Test St")));

        assertThat(ex.getMessage()).isEqualTo("Another account with this phone number already exists");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateCustomer_ownCurrentPhone_isAllowed() {
        Customer target = TestDataFactory.customer();
        target.setId(1L);
        target.setEmail("jane@example.com");
        target.setPhone("5551110000");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(target));
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(target));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer result = adminCustomerService.updateCustomer(1L,
                dto("Jane", "Doe", "jane@example.com", "5551110000", "1 Test St"));

        assertThat(result.getPhone()).isEqualTo("5551110000");
        // Same phone as current -> existsByPhone must never even be consulted.
        verify(customerRepository, never()).existsByPhone(any());
        verify(customerRepository).save(target);
    }

    @Test
    void updateCustomer_happyPath_updatesAllFieldsAndTrimsThem() {
        Customer target = TestDataFactory.customer();
        target.setId(1L);
        target.setEmail("jane@example.com");
        target.setPhone("5551110000");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(target));
        when(customerRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(customerRepository.existsByPhone("5552223333")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer result = adminCustomerService.updateCustomer(1L,
                dto("  Janet  ", "  Smith  ", "  New@Example.com  ", "  5552223333  ", "  22 New Ave  "));

        assertThat(result.getFirstName()).isEqualTo("Janet");
        assertThat(result.getLastName()).isEqualTo("Smith");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getPhone()).isEqualTo("5552223333");
        assertThat(result.getAddress()).isEqualTo("22 New Ave");
    }

    // ---------------------------------------------------------------- search

    @Test
    void search_withNullSearchAndNullSort_matchesFindAllExactly() {
        List<Customer> all = new ArrayList<>(List.of(TestDataFactory.customer(), TestDataFactory.customer()));
        when(customerRepository.findAll()).thenReturn(all);

        List<Customer> searchResult = adminCustomerService.search(null, null);
        List<Customer> findAllResult = adminCustomerService.findAll();

        assertThat(searchResult).isEqualTo(findAllResult);
        assertThat(searchResult).isEqualTo(all);
    }

    @Test
    void search_withBlankSearchTerm_dispatchesToFindAll() {
        List<Customer> all = new ArrayList<>(List.of(TestDataFactory.customer()));
        when(customerRepository.findAll()).thenReturn(all);

        List<Customer> result = adminCustomerService.search("   ", null);

        assertThat(result).isEqualTo(all);
        verify(customerRepository, never()).searchByNameOrEmail(any());
    }

    @Test
    void search_withSearchTerm_dispatchesToSearchByNameOrEmail_trimmed() {
        Customer match = TestDataFactory.customer();
        when(customerRepository.searchByNameOrEmail("jane")).thenReturn(new ArrayList<>(List.of(match)));

        List<Customer> result = adminCustomerService.search("  jane  ", null);

        assertThat(result).containsExactly(match);
        verify(customerRepository).searchByNameOrEmail("jane");
        verify(customerRepository, never()).findAll();
    }

    @Test
    void search_sortsByNameAscending() {
        Customer bob = TestDataFactory.customer("Bob", "Zephyr", "Passw0rd!", "1 Test St");
        Customer alice = TestDataFactory.customer("Alice", "Anderson", "Passw0rd!", "1 Test St");
        when(customerRepository.findAll()).thenReturn(new ArrayList<>(List.of(bob, alice)));

        List<Customer> result = adminCustomerService.search(null, "nameAsc");

        assertThat(result).containsExactly(alice, bob);
    }

    @Test
    void search_sortsByNameDescending() {
        Customer bob = TestDataFactory.customer("Bob", "Zephyr", "Passw0rd!", "1 Test St");
        Customer alice = TestDataFactory.customer("Alice", "Anderson", "Passw0rd!", "1 Test St");
        when(customerRepository.findAll()).thenReturn(new ArrayList<>(List.of(alice, bob)));

        List<Customer> result = adminCustomerService.search(null, "nameDesc");

        assertThat(result).containsExactly(bob, alice);
    }

    @Test
    void search_sortsByRegisteredAscending_oldestFirst() {
        Customer older = TestDataFactory.customer();
        older.setCreatedAt(LocalDateTime.now().minusDays(5));
        Customer newer = TestDataFactory.customer();
        newer.setCreatedAt(LocalDateTime.now());
        when(customerRepository.findAll()).thenReturn(new ArrayList<>(List.of(newer, older)));

        List<Customer> result = adminCustomerService.search(null, "registeredAsc");

        assertThat(result).containsExactly(older, newer);
    }

    @Test
    void search_sortsByRegisteredDescending_newestFirst() {
        Customer older = TestDataFactory.customer();
        older.setCreatedAt(LocalDateTime.now().minusDays(5));
        Customer newer = TestDataFactory.customer();
        newer.setCreatedAt(LocalDateTime.now());
        when(customerRepository.findAll()).thenReturn(new ArrayList<>(List.of(older, newer)));

        List<Customer> result = adminCustomerService.search(null, "registeredDesc");

        assertThat(result).containsExactly(newer, older);
    }

    @Test
    void search_withNullSort_leavesRepositoryOrderUnchanged() {
        Customer first = TestDataFactory.customer();
        Customer second = TestDataFactory.customer();
        when(customerRepository.findAll()).thenReturn(new ArrayList<>(List.of(first, second)));

        List<Customer> result = adminCustomerService.search(null, null);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void search_withUnrecognizedSort_leavesRepositoryOrderUnchanged() {
        Customer first = TestDataFactory.customer();
        Customer second = TestDataFactory.customer();
        when(customerRepository.findAll()).thenReturn(new ArrayList<>(List.of(first, second)));

        List<Customer> result = adminCustomerService.search(null, "bogus");

        assertThat(result).containsExactly(first, second);
    }
}
