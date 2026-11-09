package com.spendwise.service;

/** A user-correctable problem; the message is safe to show in the UI. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}
