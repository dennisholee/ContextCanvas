package com.contextcanvas.server.validation;

/** Exception thrown when SQL validation fails (DDL detected, parameter mismatch, etc.). */
public class SqlValidationException extends RuntimeException {

    public SqlValidationException(String message) {
        super(message);
    }
}