package com.payagent.settlement;

import com.payagent.common.contracts.InvoiceLifecycleStatus;
import com.payagent.common.contracts.PaymentLifecycleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transition policy for PayPal webhook/reconciliation state mutations.
 *
 * <p>PayPal events can arrive late or out of order. The goal is to apply
 * authorized, monotonic transitions and to suppress or defer contradictory
 * ones rather than blindly overwriting a locally persisted state with an
 * older or inconsistent remote event.
 *
 * <p>Result values:
 * <ul>
 *   <li>{@link Apply}: event reflects a new or later state; mutate locally.</li>
 *   <li>{@link NoChange}: event is consistent but already reflected locally.</li>
 *   <li>{@link Conflict}: event contradicts local state; log and defer to
 *   authoritative PayPal reconciliation rather than guessing.</li>
 * </ul>
 */
public final class SettlementStateGuard {

    private static final Logger log = LoggerFactory.getLogger(SettlementStateGuard.class);

    private SettlementStateGuard() {}

    public enum Result {
        APPLY,
        NO_CHANGE,
        CONFLICT
    }

    // An authorization created after the fact cannot un-capture or un-void.
    public static Result canApplyAuthorizationCreated(PaymentLifecycleStatus current) {
        if (current == null) {
            return Result.APPLY;
        }
        return switch (current) {
            case ORDER_CREATED, UNKNOWN -> Result.APPLY;
            case AUTHORIZED -> Result.NO_CHANGE;          // idempotent re-affirmation
            case CAPTURE_PENDING, COMPLETED, VOIDED, DECLINED -> Result.CONFLICT;
            default -> Result.CONFLICT;
        };
    }

    // PayPal authorization voided: never void a completed capture.
    public static Result canApplyAuthorizationVoided(PaymentLifecycleStatus current) {
        if (current == null) {
            return Result.APPLY;
        }
        return switch (current) {
            case ORDER_CREATED, AUTHORIZED, CAPTURE_PENDING, UNKNOWN -> Result.APPLY;
            case VOIDED -> Result.NO_CHANGE;
            case COMPLETED, DECLINED -> Result.CONFLICT;
            default -> Result.CONFLICT;
        };
    }

    // Capture completed / captured: never mark a void/declined authorization as completed
    // without authoritative confirmation.
    public static Result canApplyCaptureCompleted(PaymentLifecycleStatus current) {
        if (current == null) {
            return Result.APPLY;
        }
        return switch (current) {
            case ORDER_CREATED, AUTHORIZED, CAPTURE_PENDING, UNKNOWN -> Result.APPLY;
            case COMPLETED -> Result.NO_CHANGE;
            case VOIDED, DECLINED -> Result.CONFLICT;
            default -> Result.CONFLICT;
        };
    }

    // Capture pending: reflect the pending state but never regress a terminal one.
    public static Result canApplyCapturePending(PaymentLifecycleStatus current) {
        if (current == null) {
            return Result.APPLY;
        }
        return switch (current) {
            case ORDER_CREATED, AUTHORIZED, UNKNOWN, CAPTURE_PENDING -> Result.APPLY;
            case COMPLETED, VOIDED, DECLINED -> Result.CONFLICT;
            default -> Result.CONFLICT;
        };
    }

    // Capture denied: never mark a completed or voided payment as declined.
    public static Result canApplyCaptureDenied(PaymentLifecycleStatus current) {
        if (current == null) {
            return Result.APPLY;
        }
        return switch (current) {
            case ORDER_CREATED, AUTHORIZED, CAPTURE_PENDING, UNKNOWN -> Result.APPLY;
            case DECLINED -> Result.NO_CHANGE;
            case COMPLETED, VOIDED -> Result.CONFLICT;
            default -> Result.CONFLICT;
        };
    }

    public static Result canApplyInvoicePaid(InvoiceLifecycleStatus current) {
        if (current == null) {
            return Result.APPLY;
        }
        return switch (current) {
            case NOT_CREATED, DRAFT, SENT, UNKNOWN -> Result.APPLY;
            case PAID -> Result.NO_CHANGE;
            case CANCELLED -> Result.CONFLICT;
            default -> Result.CONFLICT;
        };
    }

    public static Result canApplyInvoiceCancelled(InvoiceLifecycleStatus current) {
        if (current == null) {
            return Result.APPLY;
        }
        return switch (current) {
            case NOT_CREATED, DRAFT, SENT, UNKNOWN -> Result.APPLY;
            case CANCELLED -> Result.NO_CHANGE;
            case PAID -> Result.CONFLICT;
            default -> Result.CONFLICT;
        };
    }

    static void logConflict(String where, String key, Enum<?> current, Enum<?> incoming) {
        log.error("CONFLICT {} key={} current={} incoming={} — leaving local state unchanged; authoritative PayPal reconciliation required",
                where, key, current, incoming);
    }
}
