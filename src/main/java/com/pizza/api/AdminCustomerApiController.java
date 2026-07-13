package com.pizza.api;

import com.pizza.api.dto.ApiResponses.CustomerResponse;
import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.dto.CustomerUpdateDTO;
import com.pizza.entity.Customer;
import com.pizza.service.AdminCustomerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin customer list and edit. Passwords are never read or written here. */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerApiController {

    private final AdminCustomerService adminCustomerService;

    @GetMapping
    public List<CustomerResponse> list(@RequestParam(required = false) String search,
                                       @RequestParam(required = false) String sort) {
        return ApiMappers.customers(adminCustomerService.search(search, sort));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return ApiMappers.customer(adminCustomerService.getById(id));
    }

    @PutMapping("/{id}")
    public Envelope<CustomerResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody CustomerUpdateDTO dto) {
        Customer customer = adminCustomerService.updateCustomer(id, dto);
        return Envelope.success(
                "Customer \"" + customer.getFullName() + "\" updated successfully.",
                ApiMappers.customer(customer));
    }
}
