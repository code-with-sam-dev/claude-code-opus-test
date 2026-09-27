package dev.example.orders;

import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Publishes what the transaction wrote. If Kafka is down, the rows wait here
// and go out when it comes back. At least once: a crash after send and before
// the update publishes the row again, so consumers must be idempotent.
@Component
public class OutboxRelay {

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPending() throws Exception {
        for (OutboxEvent event : outbox.findTop100ByPublishedAtIsNullOrderById()) {
            kafka.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
            event.markPublished();
        }
    }
}
