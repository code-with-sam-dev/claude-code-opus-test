package dev.example.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerId;
    private String currency;
    private long total;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    protected Order() {
    }

    public Order(String customerId, String currency, long total) {
        this.customerId = customerId;
        this.currency = currency;
        this.total = total;
        this.status = OrderStatus.PLACED;
    }

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getCurrency() { return currency; }
    public long getTotal() { return total; }
    public OrderStatus getStatus() { return status; }

    public void markPaid() {
        this.status = OrderStatus.PAID;
    }
}
