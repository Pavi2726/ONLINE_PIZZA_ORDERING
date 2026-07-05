package com.pizza.service;

import com.pizza.dto.RegisterRequest;
import com.pizza.entity.Customer;
import com.pizza.exception.DuplicateEmailException;
import com.pizza.exception.DuplicatePhoneException;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.repository.CustomerRepository;
import com.pizza.testsupport.TestDataFactory;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link CustomerService} (US-001, US-002).
 *
 * <p>No Spring context is loaded - {@link CustomerRepository} and
 * {@link PasswordEncoder} are plain Mockito mocks.</p>
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerService customerService;

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("Jane.Doe@Example.com");
        request.setPhone("5551234567");
        request.setPassword("Passw0rd!");
        request.setConfirmPassword("Passw0rd!");
        request.setAddress("1 Test Street");
        return request;
    }

    // ---------------------------------------------------------------- register

    @Test
    void register_savesNewCustomerWithPasswordEncodedExactlyOnce() {
        RegisterRequest request = validRequest();
        when(customerRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(customerRepository.existsByPhone("5551234567")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("ENCODED_HASH");
        when(customerRepository.saveAndFlush(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        Customer result = customerService.register(request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getFirstName()).isEqualTo("Jane");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(result.getPhone()).isEqualTo("5551234567");
        // The stored password must be the encoded hash, never the raw value.
        assertThat(result.getPassword()).isEqualTo("ENCODED_HASH");

        verify(passwordEncoder, times(1)).encode("Passw0rd!");
        verify(customerRepository, times(1)).saveAndFlush(any(Customer.class));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        RegisterRequest request = validRequest();
        when(customerRepository.existsByEmail("jane.doe@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> customerService.register(request));

        // Registration must stop at the email check - no phone check, no hashing, no save.
        verify(customerRepository, never()).existsByPhone(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(customerRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_rejectsDuplicatePhone() {
        RegisterRequest request = validRequest();
        when(customerRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(customerRepository.existsByPhone("5551234567")).thenReturn(true);

        assertThrows(DuplicatePhoneException.class, () -> customerService.register(request));

        verify(passwordEncoder, never()).encode(anyString());
        verify(customerRepository, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------- login

    @Test
    void login_succeedsWithCorrectCredentials() {
        Customer stored = TestDataFactory.customer();
        stored.setEmail("jane.doe@example.com");
        stored.setPassword("ENCODED_HASH");
        when(customerRepository.findByEmail("jane.doe@example.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("Passw0rd!", "ENCODED_HASH")).thenReturn(true);

        Customer result = customerService.login("Jane.Doe@Example.com", "Passw0rd!");

        assertThat(result).isSameAs(stored);
    }

    @Test
    void login_failsWithUnknownEmail_throwsInvalidCredentials() {
        when(customerRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> customerService.login("missing@example.com", "whatever"));
        assertThat(ex.getMessage()).isEqualTo("Invalid email or password");
    }

    @Test
    void login_failsWithWrongPassword_throwsSameExceptionTypeAsUnknownEmail() {
        Customer stored = TestDataFactory.customer();
        stored.setEmail("jane.doe@example.com");
        stored.setPassword("ENCODED_HASH");
        when(customerRepository.findByEmail("jane.doe@example.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("wrongpass", "ENCODED_HASH")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> customerService.login("jane.doe@example.com", "wrongpass"));
        assertThat(ex.getMessage()).isEqualTo("Invalid email or password");
    }
}
