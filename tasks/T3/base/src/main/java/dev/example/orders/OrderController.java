package dev.example.orders;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.graphql.data.federation.EntityMapping;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class OrderController {

    private final OrderRepository orders;
    private final OrderLineRepository lines;
    private final OrderService service;

    public OrderController(OrderRepository orders, OrderLineRepository lines,
                           OrderService service) {
        this.orders = orders;
        this.lines = lines;
        this.service = service;
    }

    @QueryMapping
    public Order order(@Argument Long id) {
        return orders.findById(id).orElse(null);
    }

    @QueryMapping
    public List<Order> ordersByCustomer(@Argument String customerId) {
        return orders.findByCustomerIdOrderById(customerId);
    }

    @MutationMapping
    public Order placeOrder(@Argument PlaceOrderInput input) {
        return service.placeOrder(input);
    }

    // One query for the lines of every order in the response, not one per order.
    @BatchMapping
    public Map<Order, List<OrderLine>> lines(List<Order> batch) {
        Map<Long, List<OrderLine>> byOrder = lines
                .findByOrderIdIn(batch.stream().map(Order::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OrderLine::getOrderId));
        return batch.stream().collect(Collectors.toMap(
                order -> order,
                order -> byOrder.getOrDefault(order.getId(), List.of())));
    }

    // The router calls this when another subgraph hands it an Order reference.
    @EntityMapping
    public List<Order> order(@Argument List<Long> idList) {
        Map<Long, Order> found = orders.findAllById(idList).stream()
                .collect(Collectors.toMap(Order::getId, order -> order));
        return idList.stream().map(found::get).toList();
    }
}
