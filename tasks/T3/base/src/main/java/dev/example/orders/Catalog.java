package dev.example.orders;

import java.util.Map;
import org.springframework.stereotype.Component;

// Prices in cents. The server decides the price, never the client.
@Component
public class Catalog {

    private static final Map<String, Long> PRICES = Map.of(
            "KEYBOARD", 8_900L,
            "MOUSE", 2_500L,
            "MONITOR", 21_900L);

    public long priceOf(String sku) {
        Long price = PRICES.get(sku);
        if (price == null) {
            throw new IllegalArgumentException("Unknown SKU " + sku);
        }
        return price;
    }
}
