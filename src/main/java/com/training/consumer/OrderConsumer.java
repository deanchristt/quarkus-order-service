package com.training.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.model.entity.Order;
import com.training.repository.OrderRepository;
import io.vertx.mutiny.redis.client.RedisAPI;
import io.vertx.mutiny.redis.client.Response;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class OrderConsumer {

    @Inject
    OrderRepository orderRepository;

    @Inject
    RedisAPI redisAPI;

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Incoming("orders-in")
    @Transactional
    public void consume(String message) {
        try {
            log.info("📥 Received Kafka message | {}", message);

            // Deserialize JSON untuk dapat orderId
            Order incomingOrder = objectMapper.readValue(message, Order.class);
            String redisKey = "order:" + incomingOrder.getId();

            // Cek Redis
            Response cached = redisAPI.get(redisKey).await().indefinitely();
            Order orderToPersist;
            if (cached != null) {
                String cachedValue = cached.toString(); // atau cached.toBuffer().toString(StandardCharsets.UTF_8)
                log.info("✅ Found order in Redis | id={}", incomingOrder.getId());
                orderToPersist = objectMapper.readValue(cachedValue.toString(), Order.class);
            } else {
                log.info("⚠️ Order not found in Redis, using Kafka message | id={}", incomingOrder.getId());
                orderToPersist = incomingOrder;
            }

            // Persist ke DB
            if (!orderToPersist.getStatus().equals(Order.Status.INITIATED.toString())){
                orderRepository.persist(orderToPersist);
                log.info("💾 Order persisted to DB | id={}", orderToPersist.getId());
            }
            log.info("💾 No need to persisted INITIATED Order to DB | id={}", orderToPersist.getId());


        } catch (Exception e) {
            log.error("❌ Failed to process order message", e);
        }
    }

}
