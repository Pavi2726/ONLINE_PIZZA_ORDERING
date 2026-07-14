package com.pizza.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the authentication interceptors:
 * <ul>
 *   <li>{@link AdminAuthInterceptor} — protects {@code /api/admin/**}</li>
 *   <li>{@link CustomerAuthInterceptor} — protects {@code /api/cart/**} and {@code /api/orders/**}</li>
 * </ul>
 *
 * <p>Only API paths are guarded. Paths like {@code /cart} and {@code /admin/orders/5} are
 * now routes inside the React app, not server-rendered pages: the server's only job for
 * them is to hand back the SPA shell (see {@link SpaForwardingConfig}), which then calls
 * the API and gets a real 401 if the visitor is not entitled to the data. Guarding those
 * paths here would redirect the shell itself and the app would never load — and it would
 * buy nothing, because every route that returns data is already covered below.</p>
 *
 * <p>{@code /api/me} is deliberately unguarded: it is the SPA's session-bootstrap probe
 * and must answer 200 with nulls when logged out, not 401.</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final CustomerAuthInterceptor customerAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login", "/api/admin/logout");

        registry.addInterceptor(customerAuthInterceptor)
                .addPathPatterns("/api/cart/**", "/api/orders/**");
    }
}
