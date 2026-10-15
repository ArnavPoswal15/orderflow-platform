package com.shop.order.domain;

import java.util.Map;
import java.util.Set;

public final class OrderStateMachine {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.RESERVED, OrderStatus.CANCELLED),
            OrderStatus.RESERVED, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.CANCELLED, OrderStatus.SHIPPED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.DELIVERED, Set.of());

    private OrderStateMachine() {}

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        if (from.equals(to)) {
            return true;
        }
        if (OrderStatus.CANCELLED.equals(from) && OrderStatus.CONFIRMED.equals(to)) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(String from, String to) {
        if (from.equals(to)) {
            return;
        }
        if (!canTransition(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
    }

    public static class IllegalTransitionException extends RuntimeException {
        private final String from;
        private final String to;

        public IllegalTransitionException(String from, String to) {
            super("Illegal transition from " + from + " to " + to);
            this.from = from;
            this.to = to;
        }

        public String from() {
            return from;
        }

        public String to() {
            return to;
        }
    }
}
