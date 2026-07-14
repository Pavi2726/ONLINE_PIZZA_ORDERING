package com.pizza.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Form-backing object for adding/updating drinks. */
@Getter
@Setter
@NoArgsConstructor
public class DrinkDTO {

    /** Populated only when editing an existing drink. */
    private Long id;

    @NotBlank(message = "Drink name is required")
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

    @NotBlank(message = "Size is required")
    @Size(max = 20, message = "Size is too long")
    private String size;

    /** Pre-existing image URL when editing (display only). */
    private String imageUrl;

    private boolean available = true;
}
