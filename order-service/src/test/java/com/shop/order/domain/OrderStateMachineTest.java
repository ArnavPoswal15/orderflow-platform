package com.shop.order.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    @Test
    void allowsHappyPath() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.RESERVED));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.RESERVED, OrderStatus.CONFIRMED));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.SHIPPED));
    }

    @Test
    void blocksConfirmedFromCancelled() {
        assertFalse(OrderStateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.CONFIRMED));
    }

    @Test
    void requireTransitionThrowsForIllegalShip() {
        assertThrows(
                OrderStateMachine.IllegalTransitionException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.PENDING, OrderStatus.SHIPPED));
    }
}
