package dev.example.store;

import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// A slice: the schema, the controller and the exception resolver, no database.
@GraphQlTest(StoreController.class)
@Import(StoreExceptionResolver.class)
class StoreControllerTests {

    @Autowired
    GraphQlTester graphQlTester;

    @MockitoBean OrderRepository orders;
    @MockitoBean OrderLineRepository lines;
    @MockitoBean CustomerRepository customers;
    @MockitoBean ItemRepository items;
    @MockitoBean OrderService service;

    @Test
    void returnsTheFieldsAskedFor() {
        given(orders.findById(1L)).willReturn(
                Optional.of(new Order(1L, 3_000, Instant.parse("2026-09-01T10:00:00Z"))));

        graphQlTester.document("{ order(id: 1) { status total } }")
                .execute()
                .path("order.status").entity(String.class).isEqualTo("PLACED")
                .path("order.total").entity(Integer.class).isEqualTo(3000);
    }

    @Test
    void aMissingOrderIsNotFound() {
        given(orders.findById(999L)).willReturn(Optional.empty());

        graphQlTester.document("{ order(id: 999) { id } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND);
    }
}
