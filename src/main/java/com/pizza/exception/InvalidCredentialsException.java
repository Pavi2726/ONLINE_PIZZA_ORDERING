package com.pizza.exception;

/** Thrown when login fails due to a wrong email/password combination. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
