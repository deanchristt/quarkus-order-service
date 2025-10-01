package com.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.model.dto.CreateOrderDto;
import com.training.model.entity.Order;
import com.training.model.entity.OrderItem;
import com.training.model.entity.User;
import com.training.repository.OrderRepository;
import com.training.repository.UserRepository;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;
import io.vertx.mutiny.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class OrderService {

    @Inject
    OrderRepository orderRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    @Channel("orders-out")
    Emitter<String> orderEmitter;

    @Inject
    RedisAPI redisAPI;

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // STEP 1: Create order
    @Transactional
    public Order createOrder(CreateOrderDto dto) {
        User user = userRepository.findByEmail(dto.getEmail());
        if (user == null) throw new RuntimeException("User not found: " + dto.getEmail());

        Order order = new Order();
        order.setUser(user);
        order.setStatus(Order.Status.INITIATED.name());

        for (OrderItem item : dto.getItems()) order.addItem(item);

        orderRepository.persist(order);

        pushToKafka(order);
        pushToRedis(order);

        return order;
    }

    // STEP 2: Update shipping address
    @Transactional
    public Order updateShippingAddress(Order order, String address) {
        Order orderData = orderRepository.findById(order.getId());
        if (orderData == null) throw new RuntimeException("Order not found: " + order.getId());

        orderData.setAddress(address);
        orderData.setStatus(Order.Status.ADDRESS_FILLED.name());

        pushToKafka(orderData);
        pushToRedis(orderData);

        return orderData;
    }

    // STEP 3: Confirm payment
    @Transactional
    public Order confirmPayment(Order order) {
        Order orderData = orderRepository.findById(order.getId());
        if (orderData == null) throw new RuntimeException("Order not found: " + order.getId());

        orderData.setStatus(Order.Status.PAYMENT_PENDING.name());

        pushToKafka(orderData);
        pushToRedis(orderData);

        return orderData;
    }

    // STEP 4: Validate PIN
    @Transactional
    public boolean validateProcessPin(Order order, String pin) {
        Order orderData = orderRepository.findById(order.getId());
        if (orderData == null) throw new RuntimeException("Order not found: " + order.getId());

        User user = orderData.getUser();
        if (user == null) throw new RuntimeException("Order has no associated user");

        return user.getPin().equals(pin);
    }

    // STEP 4b: Process payment
    @Transactional
    public Order processPayment(Order order, String pin) {
        boolean valid = validateProcessPin(order, pin);
        Order orderData = orderRepository.findById(order.getId());

        if (valid) {
            orderData.setStatus(Order.Status.PAYMENT_CONFIRMED.name());
        } else {
            throw new RuntimeException("Invalid PIN");
        }

        pushToKafka(orderData);
        pushToRedis(orderData);

        return orderData;
    }

    // STEP 5: Ship order
    @Transactional
    public Order shipOrder(Order order) {
        Order orderData = orderRepository.findById(order.getId());
        if (orderData == null) throw new RuntimeException("Order not found: " + order.getId());

        orderData.setStatus(Order.Status.SHIPPED.name());

        pushToKafka(orderData);
        pushToRedis(orderData);

        return orderData;
    }

    // STEP 6: Complete order
    @Transactional
    public Order completeOrder(Order order) {
        Order orderData = orderRepository.findById(order.getId());
        if (orderData == null) throw new RuntimeException("Order not found: " + order.getId());

        orderData.setStatus(Order.Status.COMPLETED.name());

        pushToKafka(orderData);
        pushToRedis(orderData);

        return orderData;
    }

    // ----------------- Utility: Push to Kafka -----------------
    private void pushToKafka(Order order) {
        try {
            String json = objectMapper.writeValueAsString(order);
            orderEmitter.send(json);
            log.info("📤 Order sent to Kafka | id={}", order.getId());
        } catch (Exception e) {
            log.error("Failed to send order to Kafka", e);
        }
    }

    // ----------------- Utility: Push to Redis -----------------
    private void pushToRedis(Order order) {
        try {
            String key = "order:" + order.getId();
            String value = objectMapper.writeValueAsString(order);
            redisAPI.set(List.of(key, value));
            log.info("📤 Order sent to Redis | id={}", order.getId());
        } catch (Exception e) {
            log.error("Failed to push order to Redis", e);
        }
    }
}
