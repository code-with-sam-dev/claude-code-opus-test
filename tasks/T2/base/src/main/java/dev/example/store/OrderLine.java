package dev.example.store;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;
    private String code;
    private int quantity;
    private long unitPrice;

    protected OrderLine() {
    }

    public OrderLine(Long orderId, String code, int quantity, long unitPrice) {
        this.orderId = orderId;
        this.code = code;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getOrderId() { return orderId; }
    public String getCode() { return code; }
    public int getQuantity() { return quantity; }
    public long getUnitPrice() { return unitPrice; }
}
