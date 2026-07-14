package com.pizza.api;

import com.pizza.api.dto.ApiResponses.AdminResponse;
import com.pizza.api.dto.ApiResponses.DashboardStatsResponse;
import com.pizza.api.dto.ApiResponses.Envelope;
import com.pizza.dto.LoginRequest;
import com.pizza.entity.Admin;
import com.pizza.service.AdminService;
import com.pizza.service.PizzaService;
import com.pizza.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin authentication and dashboard stats. Login/logout are excluded from the admin guard. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final AdminService adminService;
    private final PizzaService pizzaService;

    @PostMapping("/login")
    public Envelope<AdminResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        Admin admin = adminService.login(request.getEmail(), request.getPassword());
        SessionUtil.loginAdmin(session, admin);
        return Envelope.success("Welcome, " + admin.getName() + "!", ApiMappers.admin(admin));
    }

    @PostMapping("/logout")
    public Envelope<Void> logout(HttpSession session) {
        SessionUtil.logoutAdmin(session);
        return Envelope.success("You have been logged out.", null);
    }

    @GetMapping("/dashboard")
    public DashboardStatsResponse dashboard() {
        return new DashboardStatsResponse(
                pizzaService.countAll(),
                pizzaService.countAvailable(),
                pizzaService.countOutOfStock());
    }
}
