package com.pizza.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Form-backing object for placing an order (US-007). */
@Getter
@Setter
@NoArgsConstructor
public class OrderDTO {

    @NotNull(message = "Pizza selection is required")
    private Long pizzaId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 50, message = "Quantity cannot exceed 50")
    private Integer quantity;

    @NotBlank(message = "Delivery address is required")
    @Size(max = 255, message = "Address is too long")
    private String deliveryAddress;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone must be 10 to 15 digits")
    private String phone;
}
