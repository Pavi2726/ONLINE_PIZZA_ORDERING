package com.pizza.exception;

/** Thrown when a Cloudinary upload/delete/replace operation fails. */
public class CloudinaryException extends RuntimeException {

    public CloudinaryException(String message, Throwable cause) {
        super(message, cause);
    }

    public CloudinaryException(String message) {
        super(message);
    }
}
