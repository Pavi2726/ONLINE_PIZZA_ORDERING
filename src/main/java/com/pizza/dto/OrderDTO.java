package com.pizza.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderDTO {

    @NotBlank(message = "Delivery address is required")
    @Size(max = 255, message = "Address is too long")
    private String deliveryAddress;

    @NotBlank(message = "Phone is required")
    @Pattern(
        regexp = "^[0-9]{10,15}$",
        message = "Phone must be 10 to 15 digits"
    )
    private String phone;

    private String couponCode;
}