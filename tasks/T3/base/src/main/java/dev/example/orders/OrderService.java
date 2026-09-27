package dev.example.orders;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderLineRepository lines;
    private final OutboxRepository outbox;
    private final Catalog catalog;
    private final JsonMapper json;

    public OrderService(OrderRepository orders, OrderLineRepository lines,
                        OutboxRepository outbox, Catalog catalog, JsonMapper json) {
        this.orders = orders;
        this.lines = lines;
        this.outbox = outbox;
        this.catalog = catalog;
        this.json = json;
    }

    // The order, its lines and the event commit together, or not at all.
    @Transactional
    public Order placeOrder(PlaceOrderInput input) {
        long total = input.lines().stream()
                .mapToLong(line -> catalog.priceOf(line.sku()) * line.quantity())
                .sum();
        Order order = orders.save(new Order(input.customerId(), input.currency(), total));
        List<OrderLine> saved = input.lines().stream()
                .map(line -> new OrderLine(order.getId(), line.sku(), line.quantity(),
                        catalog.priceOf(line.sku())))
                .toList();
        lines.saveAll(saved);

        String orderId = order.getId().toString();
        OrderPlaced event = new OrderPlaced(orderId, order.getCustomerId(), total,
                order.getCurrency());
        outbox.save(new OutboxEvent("order-placed", orderId,
                json.writeValueAsString(event)));
        return order;
    }

    @Transactional
    public void markPaid(PaymentSettled event) {
        Order order = orders.findById(Long.valueOf(event.orderId()))
                .orElseThrow(() ->
                        new IllegalStateException("No order " + event.orderId()));
        order.markPaid();
    }
}
