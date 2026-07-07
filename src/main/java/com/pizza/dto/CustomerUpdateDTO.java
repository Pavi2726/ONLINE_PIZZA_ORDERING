package com.pizza.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Form-backing object for admin-driven customer edits (US-016). No password field — admins do not set passwords. */
@Getter
@Setter
@NoArgsConstructor
public class CustomerUpdateDTO {

    @NotBlank(message = "First name is required")
    @Size(max = 60, message = "First name is too long")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 60, message = "Last name is too long")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone must be 10 to 15 digits")
    private String phone;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address is too long")
    private String address;
}
