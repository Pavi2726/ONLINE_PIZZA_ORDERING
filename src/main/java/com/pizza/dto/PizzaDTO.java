package com.pizza.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Form-backing object for adding/updating pizzas (US-004, US-005). */
@Getter
@Setter
@NoArgsConstructor
public class PizzaDTO {

    /** Populated only when editing an existing pizza. */
    private Long id;

    @NotBlank(message = "Pizza name is required")
    @Size(max = 120, message = "Name is too long")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description is too long")
    private String description;

    @NotBlank(message = "Category is required")
    @Size(max = 60, message = "Category is too long")
    private String category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    /** Pre-existing image URL when editing (display only). */
    private String imageUrl;

    private boolean available = true;
}
