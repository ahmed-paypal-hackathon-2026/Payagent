package com.payagent.settlement.persistence;

import java.io.Serializable;
import java.util.Objects;

public class IdempotencyKeyId implements Serializable {

    private String idempotencyKey;
    private String route;

    public IdempotencyKeyId() {}

    public IdempotencyKeyId(String idempotencyKey, String route) {
        this.idempotencyKey = idempotencyKey;
        this.route = route;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyKeyId that = (IdempotencyKeyId) o;
        return Objects.equals(idempotencyKey, that.idempotencyKey) && Objects.equals(route, that.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey, route);
    }
}
