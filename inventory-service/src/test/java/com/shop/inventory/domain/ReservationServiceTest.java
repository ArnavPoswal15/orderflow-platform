package com.shop.inventory.domain;

import com.shop.events.EventTypes;
import com.shop.events.payload.OrderCreatedPayload;
import com.shop.events.payload.OrderLineItem;
import com.shop.inventory.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    InventoryRepository inventoryRepository;

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    OutboxRepository outboxRepository;

    ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(inventoryRepository, reservationRepository, outboxRepository, "");
    }

    @Test
    void outOfStock_emitsRejectedWithoutReservation() {
        UUID orderId = UUID.randomUUID();
        var payload = new OrderCreatedPayload(List.of(new OrderLineItem("LASTITEM", 1)), 1999, "ok");
        when(reservationRepository.existsById(orderId)).thenReturn(false);
        when(inventoryRepository.decrementStock("LASTITEM", 1)).thenReturn(0);

        service.handleOrderCreated(orderId, payload);

        verify(reservationRepository, never()).save(any());
        verify(outboxRepository).save(argThat(row -> row.getPayload().contains(EventTypes.INVENTORY_REJECTED)));
    }

    @Test
    void success_emitsReserved() {
        UUID orderId = UUID.randomUUID();
        var payload = new OrderCreatedPayload(List.of(new OrderLineItem("MILK", 1)), 399, "ok");
        when(reservationRepository.existsById(orderId)).thenReturn(false);
        when(inventoryRepository.decrementStock("MILK", 1)).thenReturn(1);

        service.handleOrderCreated(orderId, payload);

        verify(reservationRepository).save(any(ReservationEntity.class));
        verify(outboxRepository).save(argThat(row -> row.getPayload().contains(EventTypes.INVENTORY_RESERVED)));
    }
}
