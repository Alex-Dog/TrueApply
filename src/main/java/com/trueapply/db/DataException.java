package com.trueapply.db;

/** Unchecked wrapper for SQL failures; the UI reports these as generic errors. */
public class DataException extends RuntimeException {
    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}
