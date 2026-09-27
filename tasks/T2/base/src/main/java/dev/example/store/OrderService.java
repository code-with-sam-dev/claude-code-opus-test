package dev.example.store;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The transaction belongs to the use case, not to the transport. A REST
// controller or a GraphQL controller can call this the same way.
@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public Order ship(Long id) {
        Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        order.ship();
        return order;
    }
}
