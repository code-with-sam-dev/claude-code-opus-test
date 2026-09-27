package dev.example.store;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long customerId;
    private long total;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private Instant placedAt;

    protected Order() {
    }

    public Order(Long customerId, long total, Instant placedAt) {
        this.customerId = customerId;
        this.total = total;
        this.status = OrderStatus.PLACED;
        this.placedAt = placedAt;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public long getTotal() { return total; }
    public OrderStatus getStatus() { return status; }
    public Instant getPlacedAt() { return placedAt; }

    public void ship() {
        this.status = OrderStatus.SHIPPED;
    }
}
