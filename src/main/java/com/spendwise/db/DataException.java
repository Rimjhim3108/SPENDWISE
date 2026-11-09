package com.spendwise.db;

/** Unchecked wrapper for database failures so UI code stays readable. */
public class DataException extends RuntimeException {
    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}
