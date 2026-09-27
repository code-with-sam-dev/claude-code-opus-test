package dev.example.orders;

public record OrderPlaced(String orderId, String customerId, long total,
                          String currency) {
}
