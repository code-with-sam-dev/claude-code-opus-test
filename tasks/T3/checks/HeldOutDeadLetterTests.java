package dev.example.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Held out from the model. A real listener container on an embedded broker, with
// OrderService replaced so each failure can be injected by order id.
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"payment-settled", "payment-settled.DLT", "order-placed"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class HeldOutDeadLetterTests {

    record Call(String orderId, long nanos) {}

    static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    static final Map<String, AtomicInteger> TRANSIENT_FAILURES_LEFT = new ConcurrentHashMap<>();
    static final Map<String, Boolean> NOT_FOUND = new ConcurrentHashMap<>();

    @MockitoBean OrderService orders;
    @Autowired EmbeddedKafkaBroker broker;

    @BeforeEach
    void inject() {
        doAnswer(inv -> {
            PaymentSettled e = inv.getArgument(0);
            CALLS.add(new Call(e.orderId(), System.nanoTime()));
            if (NOT_FOUND.containsKey(e.orderId())) {
                throw new OrderNotFoundException(e.orderId());
            }
            AtomicInteger left = TRANSIENT_FAILURES_LEFT.get(e.orderId());
            if (left != null && left.getAndDecrement() > 0) {
                throw new TransientDataAccessResourceException("database briefly unavailable");
            }
            return null;
        }).when(orders).markPaid(any());
    }

    void send(String value) throws Exception {
        Properties p = new Properties();
        p.put("bootstrap.servers", broker.getBrokersAsString());
        try (var producer = new KafkaProducer<>(p, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>("payment-settled", "k", value)).get();
        }
    }

    static String event(String orderId) {
        return "{\"orderId\":\"" + orderId + "\",\"paymentId\":\"pay-" + orderId + "\",\"amount\":1000}";
    }

    List<Call> calls(String orderId) {
        return CALLS.stream().filter(c -> c.orderId().equals(orderId)).toList();
    }

    // Waits until at least n calls for the order, then long enough for any
    // further retry to show, and returns what arrived.
    List<Call> settle(String orderId, int n) throws InterruptedException {
        long end = System.currentTimeMillis() + 60_000;
        while (calls(orderId).size() < n && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        Thread.sleep(6_000);
        return calls(orderId);
    }

    List<String> deadLetters() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "held-out-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<String> values = new ArrayList<>();
        try (var c = new KafkaConsumer<>(p, new StringDeserializer(), new StringDeserializer())) {
            c.subscribe(List.of("payment-settled.DLT"));
            long end = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < end) {
                c.poll(Duration.ofMillis(250)).forEach(r -> values.add(r.value()));
            }
        }
        return values;
    }

    @Test
    void goodRecordIsPaidOnce() throws Exception {
        send(event("201"));
        assertEquals(1, settle("201", 1).size());
        assertTrue(deadLetters().stream().noneMatch(v -> v.contains("\"201\"")));
    }

    @Test
    void transientFailureRecovers() throws Exception {
        TRANSIENT_FAILURES_LEFT.put("202", new AtomicInteger(2));
        send(event("202"));
        assertEquals(3, settle("202", 3).size());
        assertTrue(deadLetters().stream().noneMatch(v -> v.contains("\"202\"")));
    }

    @Test
    void transientRetriesAreBoundedAndBackedOff() throws Exception {
        TRANSIENT_FAILURES_LEFT.put("203", new AtomicInteger(1_000));
        send(event("203"));
        List<Call> c = settle("203", 4);
        assertEquals(4, c.size(), "attempts");
        for (int i = 1; i < c.size(); i++) {
            long gap = (c.get(i).nanos() - c.get(i - 1).nanos()) / 1_000_000;
            assertTrue(gap >= 90, "gap " + i + " was " + gap + " ms");
        }
        long total = (c.get(c.size() - 1).nanos() - c.get(0).nanos()) / 1_000_000;
        assertTrue(total <= 5_000, "retry sequence took " + total + " ms");
        assertTrue(deadLetters().contains(event("203")), "exhausted record not on the DLT");
    }

    @Test
    void malformedRecordGoesStraightToTheDlt() throws Exception {
        send("this is not json {");
        send(event("204"));
        assertEquals(1, settle("204", 1).size(), "the good record after it");
        assertEquals(1, deadLetters().stream().filter("this is not json {"::equals).count());
    }

    @Test
    void unknownOrderIsNotRetried() throws Exception {
        NOT_FOUND.put("205", true);
        send(event("205"));
        assertEquals(1, settle("205", 1).size(), "attempts");
        assertTrue(deadLetters().contains(event("205")), "not on the DLT");
    }

    @Test
    void goodRecordAfterAPoisonOneIsPaid() throws Exception {
        NOT_FOUND.put("206", true);
        send(event("206"));
        send(event("207"));
        assertEquals(1, settle("207", 1).size());
    }
}
