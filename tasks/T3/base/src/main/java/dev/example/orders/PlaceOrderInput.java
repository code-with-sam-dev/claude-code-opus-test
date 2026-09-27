package dev.example.orders;

import java.util.List;

public record PlaceOrderInput(String customerId, String currency, List<LineInput> lines) {

    public record LineInput(String sku, int quantity) {
    }
}
