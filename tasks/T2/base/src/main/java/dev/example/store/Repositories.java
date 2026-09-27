package dev.example.store;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerRepository extends JpaRepository<Customer, Long> {
}

interface ItemRepository extends JpaRepository<Item, String> {
}

interface OrderRepository extends JpaRepository<Order, Long> {

    // Keyset scrolling: the cursor is a position, not a page number.
    Window<Order> findBy(ScrollPosition position, Limit limit, Sort sort);

    List<Order> findTop20ByOrderById();
}

interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrderId(Long orderId);

    List<OrderLine> findByOrderIdIn(Collection<Long> orderIds);
}
