package com.ledgerline.exception;

/**
 * Thrown whenever an authenticated user attempts to access or perform operations
 * on an account that does not belong to them (Insecure Direct Object Reference - IDOR protection).
 */
public class UnauthorizedAccountAccessException extends ApiException {
    public UnauthorizedAccountAccessException(String message) {
        super(message);
    }
}
