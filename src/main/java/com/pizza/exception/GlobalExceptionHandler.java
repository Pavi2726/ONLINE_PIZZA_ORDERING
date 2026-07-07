package com.pizza.exception;

import com.pizza.entity.Customer;
import com.pizza.service.CartService;
import com.pizza.util.SessionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.RequiredArgsConstructor;

/**
 * Centralised exception handling. Renders the friendly {@code error.html}
 * page with a human-readable message and the appropriate HTTP status.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_VIEW = "error";

    private final CartService cartService;

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, HttpServletRequest request, Model model) {
        return render(request, model, HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({DuplicateEmailException.class, DuplicatePhoneException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDuplicate(RuntimeException ex, HttpServletRequest request, Model model) {
        return render(request, model, HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleAuth(RuntimeException ex, HttpServletRequest request, Model model) {
        return render(request, model, HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(CloudinaryException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleCloudinary(CloudinaryException ex, HttpServletRequest request, Model model) {
        log.error("Cloudinary error", ex);
        return render(request, model, HttpStatus.BAD_GATEWAY,
                "We could not process the pizza image right now. Please try again.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request, Model model) {
        return render(request, model, HttpStatus.PAYLOAD_TOO_LARGE,
                "The uploaded image is too large. Maximum allowed size is 5MB.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException ex, HttpServletRequest request, Model model) {
        return render(request, model, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleDatabase(DataAccessException ex, HttpServletRequest request, Model model) {
        log.error("Database error", ex);
        return render(request, model, HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex, HttpServletRequest request, Model model) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return render(request, model, HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again later.");
    }

    /**
     * {@link com.pizza.controller.GlobalModelAdvice}'s {@code currentAdmin}/
     * {@code currentCustomer}/{@code cartItemCount} model attributes aren't
     * populated for views rendered from this advice's own {@code
     * @ExceptionHandler} methods (a different {@code @ControllerAdvice}), so
     * error.html - which reuses the normal navbar fragment - would otherwise
     * always render as logged out. Resolved directly from the session here
     * instead, mirroring what {@code GlobalModelAdvice} does.
     */
    private String render(HttpServletRequest request, Model model, HttpStatus status, String message) {
        HttpSession session = request.getSession(false);
        Customer currentCustomer = session == null ? null : SessionUtil.getCurrentCustomer(session);
        model.addAttribute("currentAdmin", session == null ? null : SessionUtil.getCurrentAdmin(session));
        model.addAttribute("currentCustomer", currentCustomer);
        model.addAttribute("cartItemCount",
                currentCustomer == null ? 0 : cartService.getItemCount(currentCustomer.getEmail()));
        model.addAttribute("statusCode", status.value());
        model.addAttribute("statusText", status.getReasonPhrase());
        model.addAttribute("message", message);
        return ERROR_VIEW;
    }
}
