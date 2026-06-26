package com.pizza.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers authentication interceptors:
 * <ul>
 *   <li>{@link AdminAuthInterceptor} — protects {@code /admin/**}</li>
 *   <li>{@link CustomerAuthInterceptor} — protects {@code /orders/**}</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final CustomerAuthInterceptor customerAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login", "/admin/logout");

        registry.addInterceptor(customerAuthInterceptor)
                .addPathPatterns("/orders/**");
    }
}
