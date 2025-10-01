# Order Service Local Development

## 📌 Repository Overview

Repository ini adalah implementasi **Order Service** menggunakan **Quarkus**, mensimulasikan alur **checkout marketplace** dari sisi user.

Tujuan utama repository ini:

1. Menangani **proses order** dari user:

    * Create order → update address → confirm payment → ship → complete.
2. Menyimpan order ke **PostgreSQL** sebagai source of truth.
3. Mengirim event order ke **Kafka** untuk workflow event-driven.
4. Menyimpan snapshot order ke **Redis** untuk akses cepat dan optimasi performa.
5. **Consumer Kafka** (`OrderConsumer`) membaca order, cek Redis, lalu insert/update DB.

---

## 🧹 System Flow Overview

Diagram end-to-end alur sistem:

```
+-------+        +----------------+        +-----------+
| User  | -----> | OrderService   | -----> | PostgreSQL|
|       | create | - insert order |        | - orders  |
|       | order  | - update order|        | - items   |
+-------+        | - push Kafka   |        +-----------+
                 | - push Redis   |
                 +-------+--------+
                         |
                         v
                    +----------+
                    |  Kafka   |
                    | orders-in|
                    +----------+
                         |
                         v
                 +----------------+
                 | OrderConsumer  |
                 | - get Redis    |
                 | - insert/update|
                 |   DB           |
                 +----------------+
```

---

## 🏋️ BPMN Workflow

Simplified **Order Checkout BPMN**:

```
Start --> Create Order --> Input Shipping Address --> Confirm Payment --> Validate PIN
        --> [PIN valid?] --Yes--> Process Payment --> Ship Order --> Complete Order --> End
                        \--No--> Input PIN
```

**Notes:**

* XOR Gateway: PIN validation (valid → process payment, invalid → retry input PIN)
* Setiap step memicu: DB update, Kafka event, Redis cache update

---

## 🛠 Setup Local Development

### 1️⃣ PostgreSQL

**Step 1: Create database and user**

```sql
-- Connect to postgres
psql -U postgres

-- Create database
CREATE DATABASE orderdb;

-- Create user
CREATE USER orderuser WITH ENCRYPTED PASSWORD 'password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE orderdb TO orderuser;
```

**Step 2: Execute SQL to create tables and sample data**

```sql
-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    pin VARCHAR(10) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Orders table
CREATE TYPE order_status AS ENUM ('INITIATED','ADDRESS_FILLED','PAYMENT_PENDING','PAYMENT_CONFIRMED','SHIPPED','COMPLETED');

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    address VARCHAR(255),
    status order_status NOT NULL DEFAULT 'INITIATED',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Order items table
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    price NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Sample data
INSERT INTO users (name, email, pin) VALUES ('Alice', 'alice@example.com', '1234'), ('Bob', 'bob@example.com', '5678');
INSERT INTO orders (user_id, address, status) VALUES (1, 'Jl. Merdeka No.1', 'INITIATED'), (2, 'Jl. Sudirman No.2', 'INITIATED');
INSERT INTO order_items (order_id, product_name, quantity, price) VALUES (1, 'Laptop', 1, 15000000), (1, 'Mouse', 2, 150000), (2, 'Keyboard', 1, 500000);
```

**Step 3: Verify tables & data**

```sql
\dt
SELECT * FROM users;
SELECT * FROM orders;
SELECT * FROM order_items;
```

---

### 2️⃣ Redis

* Redis digunakan sebagai **cache** untuk order.
* Docker compose sudah tersedia di `docker/redis-docker-compose.yml`.
* Jalankan Redis:

```bash
docker compose -f docker/redis-docker-compose.yml up -d
```

* Pastikan port Redis sesuai konfigurasi (`6379` default).
* Test Redis connection:

```bash
redis-cli -p 6379 ping
# Expected: PONG
```

* **Redis key format:**

```
order:<orderId> → JSON order
```

---

### 3️⃣ Kafka

* Kafka digunakan untuk **event-driven workflow**.
* Docker compose sudah tersedia di `docker/kafka-docker-compose.yml`.
* Jalankan Kafka:

```bash
docker compose -f docker/kafka-docker-compose.yml up -d
```

* Pastikan port Kafka sesuai konfigurasi (`9092` default).
* Verify topics:

```bash
docker exec -it <kafka-container-id> kafka-topics.sh --list --bootstrap-server localhost:9092
# Expected: orders-in, orders-out
```

---

### 4️⃣ Quarkus Service Configuration

`application.properties`:

```properties
# PostgreSQL
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=orderuser
quarkus.datasource.password=password
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/orderdb
quarkus.hibernate-orm.database.generation=update

# Kafka
mp.messaging.incoming.orders-in.bootstrap.servers=localhost:9092
mp.messaging.incoming.orders-in.topic=orders-in
mp.messaging.outgoing.orders-out.bootstrap.servers=localhost:9092
mp.messaging.outgoing.orders-out.topic=orders-out

# Redis
quarkus.redis.hosts=redis://localhost:6379
```

---

### 5️⃣ Running the Application

```bash
# Run Quarkus in dev mode
./mvnw quarkus:dev
```

---

### 6️⃣ Testing Local

1. **Create Order** via API (`OrderService.createOrder`)

    * Order tersimpan di DB
    * Kafka message dikirim ke `orders-out`
    * Redis cache ter-update

2. **Update Order** (address, payment, shipping)

    * Status berubah
    * Redis dan Kafka ter-update

3. **OrderConsumer** (`orders-in`)

    * Cek Redis untuk order data
    * Insert / update ke DB sesuai order ID

4. **Verify Redis**

```bash
redis-cli -p 6379 get order:1
```

5. **Verify DB**

```sql
SELECT * FROM orders;
SELECT * FROM order_items;
```

---

### 7️⃣ Directory Structure

```
/sql          → SQL script untuk init DB
/src/main     → Source code Quarkus (service, consumer, repository, entities)
/docker       → Docker Compose files (redis-docker-compose.yml, kafka-docker-compose.yml)
```

---

### 8️⃣ Notes

* **Redis** → cache cepat
* **Kafka** → event-driven order update
* **PostgreSQL** → source of truth
* **BPMN** → workflow checkout termasuk validasi PIN, payment, shipping
* **Flow Summary:**

```
User -> OrderService -> DB + Kafka + Redis -> OrderConsumer -> DB
```

* Semua operasi DB bersifat transactional (`@Transactional`)

---

**✅ With this setup, you can run the full local environment and test the OrderService end-to-end.**

### 9️⃣ Testing Local via API (curl or Postman)

Semua endpoint untuk OrderService sudah tersedia dalam Postman Collection.
```
/src/main/resources/postman-collection/[IFG] Order Service.postman_collection.json
```



