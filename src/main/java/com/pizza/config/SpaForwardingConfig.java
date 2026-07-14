package com.pizza.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app and gives it client-side routing.
 *
 * <p>The SPA owns paths like {@code /admin/orders/5} that exist only in the browser's
 * router. A hard refresh or a pasted deep link asks the server for them, and the server
 * has no such resource — so anything that isn't a real static file falls back to
 * index.html and lets the router take over.</p>
 *
 * <p>Two things must survive that fallback: real assets (they resolve normally and are
 * returned as-is), and the API — {@code /api/**} is excluded, so a bad API path still
 * answers 404 as JSON instead of handing back a page of HTML.</p>
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
