package com.pizza.exception;

/** Thrown when a requested entity (pizza, customer, order) does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
