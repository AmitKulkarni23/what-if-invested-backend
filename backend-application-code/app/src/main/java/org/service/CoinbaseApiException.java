package org.service;

public class CoinbaseApiException extends RuntimeException {
    public CoinbaseApiException(String message) { super(message); }
    public CoinbaseApiException(String message, Throwable cause) { super(message, cause); }
}

