package com.pizza.exception;

/** Thrown during registration when the email is already in use. */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
