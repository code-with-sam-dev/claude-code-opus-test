The `payment-settled` consumer in this service (`PaymentSettledListener`) needs
proper error handling. Today a record that fails is retried ten times back to
back and then only logged.

- **Transient failures are retried with a backoff.** When `OrderService.markPaid`
  throws a `org.springframework.dao.TransientDataAccessException`, the record
  gets at most 4 attempts in total, at least 100 ms apart, and the whole
  sequence finishes within 5 seconds.
- **Some records can never succeed and are not retried at all:** a message that
  is not valid JSON for `PaymentSettled`, and a payment for an order that does
  not exist. For the second, `markPaid` must throw a new `OrderNotFoundException`
  (a `RuntimeException` in this package, with a constructor
  `OrderNotFoundException(String orderId)`) instead of the current
  `IllegalStateException`.
- **Failed records go to a dead letter topic.** A record that is not retried, or
  is still failing after its last attempt, is published to `payment-settled.DLT`
  with its original value, and the consumer carries on with the next record.
- The listener keeps calling `OrderService.markPaid(PaymentSettled)` for every
  record it can parse.

Keep everything else as it is and add tests. Run `./mvnw test` before you
finish; `JAVA_HOME` already points at the JDK 25 this project needs. No Postgres
or Kafka is running here, so your tests must not need them (an embedded broker
from `spring-kafka-test` is fine).
