package com.payagent.agreements;

public class AgreementNotFoundException extends RuntimeException {
    public AgreementNotFoundException(String message) {
        super(message);
    }
}
