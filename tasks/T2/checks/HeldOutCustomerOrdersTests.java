package dev.example.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.jdbc.core.simple.JdbcClient;

// Held out from the model. Runs the real GraphQL engine against Postgres and
// counts every statement prepared on a JDBC connection, whatever issued it.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HeldOutCustomerOrdersTests {

    static final AtomicInteger STATEMENTS = new AtomicInteger();
    static final AtomicBoolean COUNTING = new AtomicBoolean();
    static final Set<String> PREPARE = Set.of("prepareStatement", "prepareCall", "createStatement");

    @TestConfiguration
    static class Counting {
        @Bean
        static BeanPostProcessor countStatements() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource ds)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                            new Class<?>[] {DataSource.class}, (p, m, a) -> {
                                Object r = invoke(ds, m, a);
                                if (r instanceof Connection c) {
                                    return Proxy.newProxyInstance(Connection.class.getClassLoader(),
                                            new Class<?>[] {Connection.class}, (p2, m2, a2) -> {
                                                if (COUNTING.get() && PREPARE.contains(m2.getName())) {
                                                    STATEMENTS.incrementAndGet();
                                                }
                                                return invoke(c, m2, a2);
                                            });
                                }
                                return r;
                            });
                }
            };
        }

        static Object invoke(Object target, java.lang.reflect.Method m, Object[] a) throws Throwable {
            try {
                return m.invoke(target, a);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Autowired ExecutionGraphQlService service;
    @Autowired DataSource dataSource;
    GraphQlTester tester;

    @BeforeEach
    void setUp() {
        tester = ExecutionGraphQlServiceTester.create(service);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        // A fourth customer with two orders, so a count that only works for the
        // seed data, or a batch that assumes three customers, is caught.
        if (jdbc.sql("select count(*) from customer where id = 4").query(Long.class).single() == 0) {
            jdbc.sql("insert into customer (id, name, email, tier) values (4, 'Mary', 'mary@example.com', 'GOLD')").update();
            for (int i = 0; i < 2; i++) {
                jdbc.sql("insert into orders (customer_id, total, status, placed_at) values (4, 1000, 'PLACED', now())").update();
            }
        }
    }

    int statements(Runnable r) {
        STATEMENTS.set(0);
        COUNTING.set(true);
        try {
            r.run();
        } finally {
            COUNTING.set(false);
        }
        return STATEMENTS.get();
    }

    @Test
    void orderCountIsCorrect() {
        tester.document("{ order(id: 2) { customer { id orderCount } } }").execute()
                .path("order.customer.orderCount").entity(Integer.class).isEqualTo(10);
    }

    @Test
    void orderCountIsBatched() {
        int[] seen = new int[5];
        int n = statements(() -> {
            List<java.util.Map<String, Object>> customers = tester
                    .document("{ orders(first: 50) { edges { node { customer { id orderCount } } } } }")
                    .execute().path("orders.edges[*].node.customer").entityList(Object.class).get()
                    .stream().map(o -> (java.util.Map<String, Object>) o).toList();
            for (var c : customers) {
                seen[Integer.parseInt(c.get("id").toString())] = ((Number) c.get("orderCount")).intValue();
            }
        });
        assertEquals(10, seen[1]);
        assertEquals(10, seen[2]);
        assertEquals(10, seen[3]);
        assertEquals(2, seen[4]);
        assertTrue(n <= 3, "statements for 32 orders and 4 customers: " + n);
    }

    @Test
    void firstPageOfOneCustomer() {
        var r = tester.document("{ customerOrders(customerId: 2, first: 3) { edges { node { id customer { id } } } pageInfo { hasNextPage } } }").execute();
        r.path("customerOrders.edges[*].node.id").entityList(String.class).containsExactly("2", "5", "8");
        r.path("customerOrders.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    void nextPageContinuesFromTheCursor() {
        String cursor = tester.document("{ customerOrders(customerId: 2, first: 3) { pageInfo { endCursor } } }")
                .execute().path("customerOrders.pageInfo.endCursor").entity(String.class).get();
        tester.document("query($c: String) { customerOrders(customerId: 2, first: 3, after: $c) { edges { node { id } } } }")
                .variable("c", cursor).execute()
                .path("customerOrders.edges[*].node.id").entityList(String.class).containsExactly("11", "14", "17");
    }

    @Test
    void defaultPageIsTen() {
        tester.document("{ customerOrders(customerId: 2) { edges { node { id } } } }").execute()
                .path("customerOrders.edges[*].node.id").entityList(String.class)
                .containsExactly("2", "5", "8", "11", "14", "17", "20", "23", "26", "29");
    }

    @Test
    void unknownCustomerIsNotFound() {
        tester.document("{ customerOrders(customerId: 424242) { edges { node { id } } } }").execute()
                .errors().satisfy(errors -> {
                    assertFalse(errors.isEmpty(), "no error for a missing customer");
                    assertTrue(errors.stream().anyMatch(e ->
                            "NOT_FOUND".equals(String.valueOf(e.getErrorType()))
                                    && e.getMessage() != null && e.getMessage().contains("424242")),
                            "errors: " + errors);
                });
    }

    @Test
    void nestedFieldsStayBatched() {
        int n = statements(() -> tester
                .document("{ customerOrders(customerId: 1, first: 10) { edges { node { customer { name } lines { quantity item { name } } } } } }")
                .execute().path("customerOrders.edges").entityList(Object.class).hasSize(10));
        assertTrue(n <= 6, "statements for one page of ten orders: " + n);
    }
}
