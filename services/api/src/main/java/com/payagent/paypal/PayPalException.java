package com.payagent.paypal;

public class PayPalException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public PayPalException(String message) {
        super(message);
        this.statusCode = 500;
        this.errorCode = "PAYPAL_ERROR";
    }

    public PayPalException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
        this.errorCode = "PAYPAL_ERROR";
    }

    public PayPalException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public PayPalException(int statusCode, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
