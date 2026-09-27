package dev.example.store;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// The same data on every start: three customers, five items, thirty orders.
@Component
public class SeedData implements ApplicationRunner {

    private static final String LONG = "Built for long sessions at a desk. ".repeat(12);

    private final CustomerRepository customers;
    private final ItemRepository items;
    private final OrderRepository orders;
    private final OrderLineRepository lines;

    public SeedData(CustomerRepository customers, ItemRepository items,
                    OrderRepository orders, OrderLineRepository lines) {
        this.customers = customers;
        this.items = items;
        this.orders = orders;
        this.lines = lines;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        customers.saveAll(List.of(
                new Customer(1L, "Ada", "ada@example.com", "GOLD"),
                new Customer(2L, "Grace", "grace@example.com", "SILVER"),
                new Customer(3L, "Linus", "linus@example.com", "BRONZE")));
        items.saveAll(List.of(
                new Item("KEYBOARD", "Keyboard", LONG, 8_900),
                new Item("MOUSE", "Mouse", LONG, 2_500),
                new Item("MONITOR", "Monitor", LONG, 21_900),
                new Item("DOCK", "Dock", LONG, 14_900),
                new Item("CABLE", "Cable", LONG, 900)));
        String[] codes = {"KEYBOARD", "MOUSE", "MONITOR", "DOCK", "CABLE"};
        for (int i = 0; i < 30; i++) {
            Instant placedAt = Instant.parse("2026-09-01T10:00:00Z").plusSeconds(i * 3600L);
            Order order = orders.save(new Order((long) (i % 3) + 1, 3_000, placedAt));
            lines.save(new OrderLine(order.getId(), codes[i % 5], 1, 1_000));
            lines.save(new OrderLine(order.getId(), codes[(i + 1) % 5], 2, 1_000));
        }
    }
}
