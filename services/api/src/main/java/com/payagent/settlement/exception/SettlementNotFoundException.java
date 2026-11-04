package com.payagent.settlement.exception;

public class SettlementNotFoundException extends RuntimeException {
    public SettlementNotFoundException(String message) {
        super(message);
    }
}
