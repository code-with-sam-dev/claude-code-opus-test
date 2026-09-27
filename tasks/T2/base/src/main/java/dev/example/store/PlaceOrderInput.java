package dev.example.store;

import java.util.List;

public record PlaceOrderInput(Long customerId, List<LineInput> lines) {

    public record LineInput(String code, int quantity) {
    }
}
