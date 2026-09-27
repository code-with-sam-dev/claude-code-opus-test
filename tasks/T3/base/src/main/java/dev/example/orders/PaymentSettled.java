package dev.example.orders;

public record PaymentSettled(String orderId, String paymentId, long amount) {
}
