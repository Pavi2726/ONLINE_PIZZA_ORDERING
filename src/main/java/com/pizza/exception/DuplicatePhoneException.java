package com.pizza.exception;

/** Thrown during registration when the phone number is already in use. */
public class DuplicatePhoneException extends RuntimeException {

    public DuplicatePhoneException(String message) {
        super(message);
    }
}
