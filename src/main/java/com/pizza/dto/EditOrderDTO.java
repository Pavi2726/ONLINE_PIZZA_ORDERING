package com.pizza.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditOrderDTO {

    private Long orderId;

    @NotBlank
    private String deliveryAddress;

    @NotBlank
    private String phone;

    private String couponCode;

    private List<EditOrderItemDTO> items = new ArrayList<>();
}