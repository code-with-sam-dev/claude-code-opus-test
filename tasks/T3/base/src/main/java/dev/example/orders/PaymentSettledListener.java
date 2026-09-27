package dev.example.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentSettledListener {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentSettledListener.class);

    private final OrderService orders;
    private final JsonMapper json;

    public PaymentSettledListener(OrderService orders, JsonMapper json) {
        this.orders = orders;
        this.json = json;
    }

    @KafkaListener(topics = "payment-settled", groupId = "orders")
    public void on(String message) {
        PaymentSettled event = json.readValue(message, PaymentSettled.class);
        log.info("payment-settled attempt for order {}", event.orderId());
        orders.markPaid(event);
    }
}
