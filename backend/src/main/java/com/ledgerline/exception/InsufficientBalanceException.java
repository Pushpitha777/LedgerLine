package com.ledgerline.exception;

public class InsufficientBalanceException extends ApiException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
