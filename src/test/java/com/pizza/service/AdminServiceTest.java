package com.pizza.service;

import com.pizza.entity.Admin;
import com.pizza.exception.InvalidCredentialsException;
import com.pizza.repository.AdminRepository;
import com.pizza.testsupport.TestDataFactory;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link AdminService}: admin authentication and
 * default-admin seeding.
 *
 * <p>The seeding logic ({@code seedDefaultAdmin}) actually lives on
 * {@link AdminService} itself - {@code AdminInitializer} is only a
 * {@code CommandLineRunner} that reads env vars and delegates to it - so it is
 * tested directly here rather than via the initializer.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminService adminService;

    // ---------------------------------------------------------------- login

    @Test
    void login_succeedsWithCorrectCredentials() {
        Admin stored = TestDataFactory.admin();
        stored.setEmail("admin@example.com");
        stored.setPassword("ENCODED_HASH");
        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("Passw0rd!", "ENCODED_HASH")).thenReturn(true);

        Admin result = adminService.login("Admin@Example.com", "Passw0rd!");

        assertThat(result).isSameAs(stored);
    }

    @Test
    void login_failsWithUnknownEmail_throwsInvalidCredentials() {
        when(adminRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> adminService.login("missing@example.com", "whatever"));
    }

    @Test
    void login_failsWithWrongPassword_throwsSameExceptionTypeAsUnknownEmail() {
        Admin stored = TestDataFactory.admin();
        stored.setEmail("admin@example.com");
        stored.setPassword("ENCODED_HASH");
        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("wrongpass", "ENCODED_HASH")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> adminService.login("admin@example.com", "wrongpass"));
    }

    // ---------------------------------------------------------------- seedDefaultAdmin

    @Test
    void seedDefaultAdmin_createsAdmin_whenNoneExistYet() {
        when(adminRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("Str0ngPass!")).thenReturn("ENCODED_HASH");
        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        when(adminRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = adminService.seedDefaultAdmin("Site Admin", "Seed@Example.com", "Str0ngPass!");

        assertThat(created).isTrue();
        Admin saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Site Admin");
        assertThat(saved.getEmail()).isEqualTo("seed@example.com");
        assertThat(saved.getPassword()).isEqualTo("ENCODED_HASH");
    }

    @Test
    void seedDefaultAdmin_noOps_whenAnAdminAlreadyExists() {
        when(adminRepository.count()).thenReturn(1L);

        boolean created = adminService.seedDefaultAdmin("Site Admin", "seed@example.com", "Str0ngPass!");

        assertThat(created).isFalse();
        verify(adminRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }
}
