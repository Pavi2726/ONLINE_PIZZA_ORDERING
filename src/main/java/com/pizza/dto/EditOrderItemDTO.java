package com.pizza.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditOrderItemDTO {

    private Long orderItemId;

    private Long pizzaId;

    private String pizzaName;

    private Integer quantity;

    private java.math.BigDecimal price;
}