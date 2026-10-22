package com.payagent.common.contracts;

public enum PaymentLifecycleStatus {
    NOT_STARTED,
    ORDER_CREATED,
    AUTHORIZED,
    CAPTURE_PENDING,
    COMPLETED,
    VOIDED,
    DECLINED,
    UNKNOWN
}
