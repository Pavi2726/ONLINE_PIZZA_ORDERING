package com.pizza.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * How an unauthenticated request is turned away.
 *
 * <p>The server-rendered pages want a 302 to a login page. An XHR from the SPA
 * cannot follow that usefully — {@code fetch} would transparently follow the
 * redirect and hand back the login HTML with a 200, so the client could never
 * tell "not logged in" from "succeeded". API requests therefore get a plain 401
 * with a JSON body, which the api client turns into a redirect of its own.</p>
 */
final class AuthChallenge {

    private AuthChallenge() {
    }

    static boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    static void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"status":401,"error":"Unauthorized","message":"%s"}""".formatted(message));
    }
}
