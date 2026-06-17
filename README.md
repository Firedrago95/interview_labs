# 에이블리 라이브코딩 — 설계 + 구현 올인원 정리

> 이 문서 하나만 보면 됨. 면접 직전 복습용.

---

## 목차

1. [재고 차감 + 주문 생성](#1-재고-차감--주문-생성)
2. [쿠폰 선착순 발급](#2-쿠폰-선착순-발급)
3. [장바구니 → 결제 상태머신](#3-장바구니--결제-상태머신)
4. [포인트 적립 / 차감](#4-포인트-적립--차감)
5. [리뷰 시스템 + 평점 집계](#5-리뷰-시스템--평점-집계)
6. [팔로우 / 알림 시스템](#6-팔로우--알림-시스템)
7. [외부 결제(PG) 연동 + 멱등성](#7-외부-결제pg-연동--멱등성)
8. [API Rate Limiting](#8-api-rate-limiting)
9. [장바구니 데이터 모델링](#9-장바구니-데이터-모델링)
10. [공통: 확장 시 원포인트 체크리스트](#10-공통-확장-시-원포인트-체크리스트)
11. [구현 단계 코드 습관 — 면접관이 보는 것](#11-구현-단계-코드-습관--면접관이-보는-것)
12. [면접관이 반드시 파고드는 질문 모음](#12-면접관이-반드시-파고드는-질문-모음)

---

## 1. 재고 차감 + 주문 생성

### 문제 형태

> "상품 구매 API를 구현하세요. 재고 부족 시 실패해야 합니다."

### 스키마

```sql
CREATE TABLE products (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    stock       INT NOT NULL DEFAULT 0,
    price       DECIMAL(10,2) NOT NULL,
    version     INT NOT NULL DEFAULT 0,   -- 낙관적 락용
    CHECK (stock >= 0)                    -- DB 레벨 방어
);

CREATE TABLE orders (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INT NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    status      ENUM('PENDING','PAID','SHIPPED','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id)
);
```

### 비관적 락 구현 (충돌 빈번한 이벤트 상황)

```java
@Transactional
public Order createOrder(Long userId, Long productId, int quantity) {
    // FOR UPDATE: 조회 시점에 row 잠금
    Product product = productRepository.findByIdWithLock(productId)
        .orElseThrow(() -> new ProductNotFoundException(productId));

    if (product.getStock() < quantity) {
        throw new InsufficientStockException();
    }

    product.decreaseStock(quantity);  // dirty checking으로 UPDATE
    
    Order order = Order.create(userId, productId, quantity, product.getPrice());
    return orderRepository.save(order);
}
```

```java
// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

### 낙관적 락 구현 (일반 구매 상황)

```java
@Transactional
public Order createOrder(Long userId, Long productId, int quantity) {
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new ProductNotFoundException(productId));

    product.decreaseStock(quantity);  // @Version 필드가 자동으로 version+1
    orderRepository.save(Order.create(userId, productId, quantity, product.getPrice()));
    // 충돌 시 OptimisticLockException 발생 → 상위에서 retry
}

// 재시도 래퍼
@Retryable(value = OptimisticLockException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
public Order createOrderWithRetry(Long userId, Long productId, int quantity) {
    return createOrder(userId, productId, quantity);
}
```

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **다중 상품 동시 구매** | 데드락 방지: 항상 id 오름차순으로 락 획득 |
| **재고 음수 방지** | DB `CHECK (stock >= 0)` + 애플리케이션 레벨 이중 방어 |
| **플래시 세일 (수만 동시)** | DB 락 제거 → Redis Lua Script로 차감, DB는 Kafka Consumer가 비동기 저장 |
| **주문 취소 시 재고 복구** | 보상 트랜잭션 or Saga 패턴. 단순하면 `stock + quantity` UPDATE |
| **재고 캐시** | Redis에 stock 캐싱 시 DB와 정합성 문제. 이벤트 기반 동기화 필요 |

---

## 2. 쿠폰 선착순 발급

### 문제 형태

> "10만 명이 동시에 요청해도 1000장만 발급되어야 합니다."

### 스키마

```sql
CREATE TABLE coupons (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(50) UNIQUE NOT NULL,
    type        ENUM('FIXED','PERCENT') NOT NULL,
    value       DECIMAL(10,2) NOT NULL,
    total_qty   INT NOT NULL,
    issued_qty  INT NOT NULL DEFAULT 0
);

CREATE TABLE coupon_issuance (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    coupon_id   BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    issued_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_coupon_user (coupon_id, user_id),  -- 1인 1장 + Idempotency Key
    FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);
```

### Redis Lua Script (핵심)

```lua
-- KEYS[1]: coupon:stock:{couponId}
-- KEYS[2]: coupon:issued:{couponId}
-- ARGV[1]: userId

local stock = redis.call('GET', KEYS[1])
if not stock or tonumber(stock) <= 0 then
    return -1  -- 재고 없음
end
if redis.call('SADD', KEYS[2], ARGV[1]) == 0 then
    return -2  -- 이미 발급됨 (중복)
end
return redis.call('DECR', KEYS[1])  -- 차감 후 남은 재고 반환
```

### Java 전체 흐름

```java
@Service
public class CouponService {

    public CouponIssueResult issueCoupon(Long couponId, Long userId) {
        // 1. Rate Limit (Redis SET NX EX)
        String rateLimitKey = "rate:coupon:" + userId;
        if (!redisTemplate.opsForValue().setIfAbsent(rateLimitKey, "1", 1, SECONDS)) {
            throw new TooManyRequestsException();
        }

        // 2. Redis Lua Script 원자적 처리
        String stockKey = "coupon:stock:" + couponId;
        String issuedKey = "coupon:issued:" + couponId;
        Long result = redisTemplate.execute(luaScript, 
            List.of(stockKey, issuedKey), userId.toString());

        if (result == -1L) throw new CouponSoldOutException();
        if (result == -2L) throw new CouponAlreadyIssuedException();

        // 3. Kafka 비동기 발행 (DB는 Consumer가 처리)
        kafkaTemplate.send("coupon-issued", new CouponIssuedEvent(couponId, userId));
        
        return CouponIssueResult.success();
    }
}

// Kafka Consumer
@KafkaListener(topics = "coupon-issued")
public void consume(CouponIssuedEvent event, Acknowledgment ack) {
    try {
        // UNIQUE KEY가 중복 INSERT 막음 (Idempotency)
        couponIssuanceRepository.save(
            CouponIssuance.of(event.couponId(), event.userId())
        );
        ack.acknowledge();
    } catch (DataIntegrityViolationException e) {
        // 이미 저장됨 (재전달 케이스) → 정상 처리
        ack.acknowledge();
    } catch (Exception e) {
        // 재시도 후 DLQ
        throw e;
    }
}
```

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **Redis 장애** | Redis Sentinel or Cluster 필수. 단일 Redis = SPOF |
| **Kafka 발행 실패** | 로컬 DB에 outbox 저장 후 재발행 (Transactional Outbox 패턴) |
| **쿠폰 만료** | `expired_at` 컬럼 + TTL Key. 만료된 쿠폰 Redis에서 자동 삭제 |
| **발급 취소** | Redis SADD로 넣었으면 SREM으로 복구. stock은 INCR. 정합성 주의 |
| **여러 종류 쿠폰** | stockKey를 `coupon:stock:{couponId}` 로 couponId별 분리 |
| **쿠폰 사용 (결제 연동)** | 주문 생성 트랜잭션 안에서 coupon_issuance.used_at UPDATE. 결제 실패 시 rollback |

---

## 3. 장바구니 → 결제 상태머신

### 문제 형태

> "주문 상태 전이를 구현하세요. 잘못된 전이는 막아야 합니다."

### 스키마

```sql
CREATE TABLE orders (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    status      ENUM('PENDING','PAID','SHIPPED','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    total_price DECIMAL(10,2) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id    BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INT NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE order_status_history (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id    BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by  BIGINT COMMENT 'userId or system'
);
```

### 상태 전이 Java 구현

```java
public enum OrderStatus {
    PENDING, PAID, SHIPPED, DELIVERED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
        PENDING,   Set.of(PAID, CANCELLED),
        PAID,      Set.of(SHIPPED, CANCELLED),
        SHIPPED,   Set.of(DELIVERED),
        DELIVERED, Set.of(),
        CANCELLED, Set.of()
    );

    public boolean canTransitTo(OrderStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }
}

@Transactional
public void changeStatus(Long orderId, OrderStatus newStatus) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));

    if (!order.getStatus().canTransitTo(newStatus)) {
        throw new InvalidStatusTransitionException(order.getStatus(), newStatus);
    }

    OrderStatus prevStatus = order.getStatus();
    order.setStatus(newStatus);

    // 이력 저장
    orderStatusHistoryRepository.save(
        OrderStatusHistory.of(orderId, prevStatus, newStatus)
    );
}
```

### DB 레벨 동시성 방어 (핵심)

```sql
-- 애플리케이션 체크만으로는 race condition 가능
-- WHERE 조건에 현재 status를 포함해야 원자적 전이 보장

UPDATE orders
SET status = 'PAID', updated_at = NOW()
WHERE id = ? AND status = 'PENDING';

-- affected_rows == 0 이면 → 이미 전이됨 → 예외 처리
```

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **전이 시 부가 작업** | `@TransactionalEventListener(AFTER_COMMIT)` 로 이벤트 발행. PAID 시 재고 확정, CANCELLED 시 재고 복구 |
| **전이 실패 알림** | Dead Letter Queue + 모니터링. CANCELLED 무한 루프 방지 |
| **부분 취소** | order_items별 status 컬럼 추가. 전체 주문 상태와 별도 관리 |
| **결제 외부 연동** | 결제 완료 Webhook 수신 → status 전이. Webhook 중복 수신 대비 idempotency_key 저장 |
| **이력 조회 성능** | order_status_history에 `(order_id, changed_at)` 복합 인덱스 |

---

## 4. 포인트 적립 / 차감

### 문제 형태

> "결제 완료 시 포인트를 적립하고, 포인트로 결제할 수 있게 해주세요."

### 스키마

```sql
CREATE TABLE point_ledger (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    type        ENUM('EARN','USE','EXPIRE','CANCEL') NOT NULL,
    amount      INT NOT NULL,           -- 항상 양수. 차감은 type으로 구분
    balance     INT NOT NULL,           -- 이 트랜잭션 후 잔액 (snapshot)
    ref_id      BIGINT COMMENT '연관 주문 ID',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at)
);

-- 현재 잔액은 집계하지 않고 별도 캐시 테이블로
CREATE TABLE point_balance (
    user_id     BIGINT PRIMARY KEY,
    balance     INT NOT NULL DEFAULT 0,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 핵심 구현 — 잔액 차감의 동시성

```java
@Transactional
public void usePoint(Long userId, int amount, Long orderId) {
    // point_balance를 FOR UPDATE로 잠금
    PointBalance balance = pointBalanceRepository.findByUserIdForUpdate(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    if (balance.getBalance() < amount) {
        throw new InsufficientPointException();
    }

    balance.decrease(amount);

    // ledger 기록 (이중 장부 — balance snapshot 포함)
    pointLedgerRepository.save(PointLedger.use(userId, amount, balance.getBalance(), orderId));
}
```

### 왜 Ledger(원장) 방식인가

- `point_balance`만 있으면: 잔액은 알지만 "왜 이 금액인지" 추적 불가
- Ledger 방식: 모든 변경 이력이 append-only로 쌓임 → 감사(audit), 포인트 만료 처리, CS 대응 가능
- `balance` snapshot을 각 row에 저장하면 최신 잔액 조회 시 집계 쿼리 불필요

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **포인트 만료** | `expired_at` 컬럼 추가. 배치로 만료 처리 시 `EXPIRE` 타입으로 ledger 기록 |
| **적립 취소 (주문 취소)** | `CANCEL` 타입으로 ledger 기록. 사용 포인트는 환불, 적립 포인트는 회수 |
| **등급별 적립률** | `earn_rate`를 users 테이블 또는 별도 policy 테이블로 관리 |
| **포인트 + 현금 혼합 결제** | 주문 생성 트랜잭션 안에서 포인트 차감 + 결제 처리 원자적으로. 결제 실패 시 포인트 rollback |
| **대용량 사용자** | point_balance 샤딩 고려. user_id % N 으로 파티셔닝 |

---

## 5. 리뷰 시스템 + 평점 집계

### 문제 형태

> "상품 리뷰를 작성하고 평균 평점을 실시간으로 보여주세요."

### 스키마

```sql
CREATE TABLE reviews (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    order_id    BIGINT NOT NULL,        -- 구매 인증
    rating      TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content     TEXT,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_order_review (order_id),  -- 주문당 1개만
    INDEX idx_product (product_id, created_at DESC)
);

-- 평점 집계를 매번 계산하면 느림 → 별도 테이블로 캐싱
CREATE TABLE product_rating_summary (
    product_id      BIGINT PRIMARY KEY,
    total_count     INT NOT NULL DEFAULT 0,
    total_score     INT NOT NULL DEFAULT 0,  -- SUM(rating)
    average_rating  DECIMAL(3,2) GENERATED ALWAYS AS (total_score / total_count) VIRTUAL,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id)
);
```

### 평점 집계 동시성

```java
@Transactional
public Review writeReview(Long userId, Long orderId, int rating, String content) {
    // 구매 인증
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));

    if (!order.getUserId().equals(userId)) throw new ForbiddenException();
    if (order.getStatus() != OrderStatus.DELIVERED) throw new ReviewNotAllowedException();

    Review review = reviewRepository.save(
        Review.create(order.getProductId(), userId, orderId, rating, content)
    );

    // 집계 테이블 원자적 업데이트
    productRatingSummaryRepository.incrementRating(order.getProductId(), rating);

    return review;
}
```

```sql
-- incrementRating: race condition 없이 원자적으로
INSERT INTO product_rating_summary (product_id, total_count, total_score)
VALUES (?, 1, ?)
ON DUPLICATE KEY UPDATE
    total_count = total_count + 1,
    total_score = total_score + VALUES(total_score);
```

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **리뷰 수정/삭제** | 삭제 시 집계 테이블 `total_count - 1`, `total_score - old_rating`. Soft delete 고려 |
| **리뷰 이미지** | S3 + CloudFront. review_images 별도 테이블. 이미지 업로드는 presigned URL |
| **리뷰 도움돼요 (좋아요)** | review_likes (review_id, user_id) UNIQUE. count는 Redis 카운터 |
| **리뷰 페이지네이션** | Cursor 기반 (`WHERE created_at < ?` + `LIMIT`) — Offset은 대용량에서 느림 |
| **평점 집계 대용량** | Redis에 `HINCRBY` 로 실시간 집계. 주기적으로 DB 동기화 |

---

## 6. 팔로우 / 알림 시스템

### 문제 형태

> "팔로우 기능과 새 게시글 알림을 구현하세요."

### 스키마

```sql
CREATE TABLE follows (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    follower_id     BIGINT NOT NULL,   -- 팔로우 하는 사람
    following_id    BIGINT NOT NULL,   -- 팔로우 받는 사람
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_follow (follower_id, following_id),
    INDEX idx_following (following_id)  -- "나를 팔로우하는 사람들" 조회용
);

CREATE TABLE notifications (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,       -- 수신자
    type        ENUM('NEW_POST','NEW_FOLLOWER','COMMENT','LIKE') NOT NULL,
    ref_id      BIGINT NOT NULL,       -- 관련 리소스 ID
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read, created_at DESC)
);
```

### 팔로우 + 알림 발송 흐름

```java
// 게시글 작성 시 팔로워들에게 알림
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onPostCreated(PostCreatedEvent event) {
    // 팔로워 수가 많으면 동기 처리 불가 → Kafka로 비동기
    kafkaTemplate.send("post-created", event);
}

@KafkaListener(topics = "post-created")
public void sendNotifications(PostCreatedEvent event) {
    // 팔로워 목록 배치 조회 (1000명씩 페이징)
    Pageable pageable = PageRequest.of(0, 1000);
    Page<Long> followerIds;
    do {
        followerIds = followRepository.findFollowerIds(event.authorId(), pageable);
        // 벌크 INSERT
        notificationRepository.saveAll(
            followerIds.map(fid -> 
                Notification.newPost(fid, event.postId())
            ).toList()
        );
        pageable = pageable.next();
    } while (followerIds.hasNext());
}
```

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **팔로워 수백만 명 (인플루언서)** | Fan-out on Write(푸시) → Fan-out on Read(풀) 전환. 팔로워 많으면 알림 INSERT가 수백만 건 |
| **알림 읽음 처리 성능** | `UPDATE WHERE user_id = ? AND is_read = 0` 인덱스 필수. 벌크 업데이트 |
| **실시간 알림 전송** | SSE(Server-Sent Events) or WebSocket. Redis Pub/Sub으로 알림 서버 간 브로드캐스트 |
| **알림 보관 기간** | TTL 설정 (30일). 오래된 알림 배치 삭제 or 파티셔닝 |
| **팔로우 취소** | `DELETE FROM follows WHERE follower_id=? AND following_id=?`. 소프트 딜리트 불필요 |

---

## 10. 공통: 확장 시 원포인트 체크리스트

라이브코딩에서 구현 후 면접관이 "이 시스템 확장한다면?" 물어볼 때 쓰는 체크리스트.

### 동시성 — 항상 먼저 생각

```
Q. 동시에 두 요청이 오면?
→ 공유 자원(재고, 잔액, 카운터)이 있으면 반드시 언급
→ DB 락 / Redis 원자 연산 / 낙관적 락 중 상황에 맞는 것 선택
→ 선택 이유를 "충돌 빈도" 기준으로 설명
```

### 트랜잭션 경계 — 어디까지 묶을까

```
Q. 외부 API 호출(PG사 결제)이 포함되면?
→ 트랜잭션 안에 외부 I/O 넣으면 커넥션 점유 시간 증가
→ 결제 먼저 → 성공 시 DB 저장 패턴
→ 실패 보상: 결제 취소 API 호출 (Saga 패턴)
```

### 성능 — 병목 위치 파악

```
핫패스에서 제거해야 할 것들:
- 집계 쿼리 (COUNT, SUM, AVG) → 별도 캐시 테이블 or Redis
- 대용량 INSERT (알림, 이벤트 로그) → 비동기 처리
- 외부 API 호출 → 비동기 or 타임아웃 설정
- 이미지/파일 처리 → S3 presigned URL, 직접 업로드
```

### 인덱스 — 쿼리 보이면 바로 떠올리기

```sql
-- 조회 패턴별 인덱스 전략
WHERE user_id = ? ORDER BY created_at DESC    → (user_id, created_at DESC)
WHERE product_id = ? AND status = ?           → (product_id, status)
WHERE created_at >= ? AND created_at < ?      → (created_at)  -- 범위 쿼리
GROUP BY category ORDER BY SUM(revenue) DESC  → (category) + covering index 고려
```

### 페이지네이션 — Offset은 언제나 위험

```sql
-- Offset 방식 (대용량에서 느림)
SELECT * FROM orders LIMIT 20 OFFSET 10000;  -- 10000개 스캔 후 버림

-- Cursor 방식 (항상 빠름)
SELECT * FROM orders
WHERE created_at < '2025-06-01 12:00:00'  -- 마지막 아이템의 created_at
ORDER BY created_at DESC
LIMIT 20;
```

### 멱등성 — 재시도가 있는 곳마다

```
Kafka Consumer 재전달, 네트워크 재시도, 클라이언트 중복 클릭
→ DB UNIQUE KEY (가장 간단, 충분함)
→ Redis SET NX (DB 부하 줄이기)
→ Idempotency-Key 헤더 (외부 API)
```

### 장애 격리 — 하나가 죽어도 전체가 죽으면 안 됨

```
외부 의존성 (결제, 메시지, 추천):
→ Timeout 설정 (기본값 믿지 말 것)
→ Circuit Breaker (Resilience4j)
→ Fallback (캐시 데이터, 기본값 반환)
→ Bulkhead (스레드 풀 분리)
```

---

## 12. 면접관이 반드시 파고드는 질문 모음

### 재고 관련

**Q. `stock >= 0` 보장을 어디서 해요?**
A. DB `CHECK` 제약 + 애플리케이션 레벨 이중 방어. DB가 최후 방어선이지만 애플리케이션에서 먼저 잡아야 사용자 친화적인 에러 메시지 가능.

**Q. 재고 조회와 차감 사이에 다른 트랜잭션이 끼어들면?**
A. 비관적 락은 `FOR UPDATE`로 원천 차단. 낙관적 락은 `version` 조건으로 감지 후 재시도. 대용량 이벤트는 Redis Lua로 DB 락 자체를 제거.

**Q. Redis 장애 시 재고 차감 어떻게 해요?**
A. Circuit Breaker로 Redis 장애 감지 → Fallback으로 DB 비관적 락으로 전환. 단, 처리량 저하는 감수. 또는 Redis Sentinel/Cluster로 가용성 확보.

### 트랜잭션 관련

**Q. `@Transactional` 메서드에서 외부 API 호출하면 안 되는 이유?**
A. 트랜잭션 = DB 커넥션 점유. 외부 API가 3초 걸리면 3초간 커넥션 점유. 동시 요청 100개면 커넥션 풀 즉시 고갈. → 외부 호출은 트랜잭션 밖으로 빼거나, `@TransactionalEventListener(AFTER_COMMIT)`로 커밋 후 처리.

**Q. `@TransactionalEventListener` 사용 시 이벤트 발행 실패하면?**
A. AFTER_COMMIT이라 이미 DB는 커밋됨. 이벤트 유실 가능. 신뢰성 필요하면 Transactional Outbox 패턴 — 이벤트를 DB 테이블에 같은 트랜잭션으로 저장, 별도 배치/릴레이가 발행.

### 설계 관련

**Q. 왜 Kafka 써요? REST API 직접 호출하면 안 되나요?**
A. 동기 호출은 수신자 장애가 발신자 장애로 전파됨. Kafka는 발신자/수신자 결합도 제거, 수신자 장애 시 메시지 보존, 처리량 조절 가능. 단순한 경우엔 오버엔지니어링이 될 수 있음.

**Q. 이 시스템 트래픽 10배 되면?**
A. 먼저 병목 찾기. 보통 순서: DB 커넥션 풀 고갈 → 슬로우 쿼리 → 인덱스 → 캐시 레이어 추가 → 읽기 복제본 분리 → 샤딩. 수직 확장으로 먼저 버티고, 한계 시 수평 확장.

**Q. 배포 중에 데이터 마이그레이션 어떻게 해요?**
A. 무중단 배포 = 컬럼 추가는 괜찮음, 컬럼 삭제/변경은 위험. 순서: 신규 컬럼 추가(nullable) → 애플리케이션 배포(둘 다 씀) → 데이터 마이그레이션 → 구 컬럼 제거.

---

---

## 7. 외부 결제(PG) 연동 + 멱등성

### 문제 형태

> "결제 버튼 연타 또는 PG사 웹훅 중복 수신 상황을 처리하세요."

### 왜 어려운가

결제는 **외부 시스템(PG사)과 내부 DB가 분리**되어 있음. 두 가지 실패 시나리오가 존재:

```
시나리오 A: PG사 승인 성공 → 내부 DB 저장 실패 → 돈은 빠졌는데 주문이 없음
시나리오 B: 네트워크 타임아웃 → 실제로는 승인됐는데 실패로 오판 → 중복 결제
```

### 스키마

```sql
CREATE TABLE payment_requests (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    idempotency_key     VARCHAR(100) UNIQUE NOT NULL,  -- 중복 요청 방어의 핵심
    order_id            BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    amount              DECIMAL(10,2) NOT NULL,
    status              ENUM('PENDING','APPROVED','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    pg_transaction_id   VARCHAR(100),                  -- PG사가 발급한 트랜잭션 ID
    pg_raw_response     JSON,                          -- PG사 원본 응답 저장
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_order (order_id)                     -- 주문당 결제 1건
);

CREATE TABLE payment_webhooks (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    pg_transaction_id   VARCHAR(100) NOT NULL,
    payload             JSON NOT NULL,
    processed           BOOLEAN NOT NULL DEFAULT FALSE,
    received_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pg_tx (pg_transaction_id)
);
```

### 핵심 구현 — 멱등성 처리

```java
@Service
public class PaymentService {

    @Transactional
    public PaymentResult processPayment(PaymentRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        // 1. 멱등성 키로 기존 처리 결과 조회
        return paymentRequestRepository.findByIdempotencyKey(idempotencyKey)
            .map(existing -> {
                // 이미 처리된 요청 → 기존 결과 그대로 반환 (로직 재실행 X)
                if (existing.getStatus() == PaymentStatus.APPROVED) {
                    return PaymentResult.alreadyApproved(existing.getPgTransactionId());
                }
                if (existing.getStatus() == PaymentStatus.PENDING) {
                    // 진행 중 — PG사에 상태 조회 후 동기화
                    return syncWithPg(existing);
                }
                return PaymentResult.failed(existing.getStatus());
            })
            .orElseGet(() -> executeNewPayment(request, idempotencyKey));
    }

    private PaymentResult executeNewPayment(PaymentRequest request, String idempotencyKey) {
        // 2. PENDING으로 먼저 저장 (PG 호출 전)
        //    → PG 호출 후 저장하면 타임아웃 시 결과를 알 수 없음
        PaymentRequestEntity entity = paymentRequestRepository.save(
            PaymentRequestEntity.pending(idempotencyKey, request)
        );

        // 3. PG사 API 호출 (트랜잭션 밖에서 해야 커넥션 점유 최소화)
        //    → 여기서 @Transactional을 쓰면 PG 응답 기다리는 동안 커넥션 묶임
        PgResponse pgResponse = pgClient.approve(request);

        // 4. 결과에 따라 상태 업데이트
        if (pgResponse.isSuccess()) {
            entity.approve(pgResponse.getTransactionId(), pgResponse.getRawResponse());
            paymentRequestRepository.save(entity);
            return PaymentResult.success(pgResponse.getTransactionId());
        } else {
            entity.fail();
            paymentRequestRepository.save(entity);
            return PaymentResult.failed(PaymentStatus.FAILED);
        }
    }
}
```

### 웹훅 중복 수신 처리

```java
@RestController
public class PaymentWebhookController {

    @PostMapping("/webhooks/payment")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-PG-Signature") String signature) {

        // 1. 서명 검증 (위변조 방지)
        if (!pgSignatureVerifier.verify(payload, signature)) {
            return ResponseEntity.status(401).build();
        }

        PgWebhookEvent event = objectMapper.readValue(payload, PgWebhookEvent.class);

        // 2. 중복 웹훅 방지 — pg_transaction_id로 처리 여부 확인
        if (webhookRepository.existsByPgTransactionIdAndProcessed(
                event.getTransactionId(), true)) {
            return ResponseEntity.ok().build();  // 이미 처리됨, 200 반환 (PG사 재전송 막기)
        }

        // 3. 처리
        webhookProcessingService.process(event);

        return ResponseEntity.ok().build();
    }
}
```

### 트랜잭션 설계 원칙

```
핵심 규칙: PG 호출은 반드시 트랜잭션 밖에서

잘못된 설계:
@Transactional
public void pay() {
    validateOrder();          // DB read
    PgResponse r = pgApi.approve();  // 외부 I/O — 커넥션 묶임!
    savePayment(r);           // DB write
}

올바른 설계:
public void pay() {
    validateAndSavePending(); // @Transactional — 짧게 끝남
    PgResponse r = pgApi.approve();  // 트랜잭션 밖
    updatePaymentResult(r);   // @Transactional — 짧게 끝남
}
```

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **PG사 타임아웃** | Timeout 설정 (ex. 10초). `PENDING` 상태로 남은 건 배치로 PG 조회 후 동기화 |
| **PG사 장애** | Circuit Breaker. Fallback: 결제 불가 안내 (잘못된 처리보다 명시적 실패가 나음) |
| **환불** | `REFUND` 상태 추가. PG사 환불 API 호출 → 성공 시 상태 전이 → 포인트/쿠폰 복구는 이벤트로 |
| **여러 PG사** | Strategy 패턴. `PgClient` 인터페이스 → `TossPgClient`, `KakaoPgClient` 구현체 |
| **결제 내역 조회** | `pg_raw_response` JSON 컬럼에 원본 보존 → 분쟁 시 증적 |

---

## 8. API Rate Limiting

### 문제 형태

> "특정 유저가 초당 100번씩 API를 호출하는 상황을 막으세요."

### 알고리즘 비교

| 알고리즘 | 원리 | 특징 | 적합한 상황 |
|---|---|---|---|
| **Fixed Window** | 1분 단위로 카운트 리셋 | 구현 단순, 경계 시점 버스트 가능 | 대략적인 제한으로 충분할 때 |
| **Sliding Window** | 최근 N초 요청 수 추적 | 정확하지만 메모리 사용 높음 | 정밀한 제어 필요할 때 |
| **Token Bucket** | 버킷에 토큰 채워지고, 요청마다 소비 | 버스트 허용 (토큰 쌓이면 한번에 사용 가능) | 일시적 스파이크 허용할 때 |
| **Leaky Bucket** | 큐에 넣고 일정 속도로 처리 | 처리율 일정, 큐 초과 시 드롭 | 처리율을 엄격히 제한할 때 |

### Fixed Window — 가장 간단, 면접에서 먼저 구현

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, String> redisTemplate;
    private static final int MAX_REQUESTS = 10;
    private static final int WINDOW_SECONDS = 1;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {
        String userId = extractUserId(request);  // 인증 토큰에서 추출
        String key = "rate_limit:" + userId + ":" + (System.currentTimeMillis() / 1000);

        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            // 첫 요청일 때만 TTL 설정 (윈도우 자동 만료)
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count > MAX_REQUESTS) {
            response.setStatus(429);  // Too Many Requests
            response.setHeader("Retry-After", "1");
            response.getWriter().write("{\"code\":\"RATE_LIMIT\",\"message\":\"요청 한도를 초과했습니다.\"}");
            return false;
        }
        return true;
    }
}
```

### Sliding Window Log — 정밀한 제어

```java
public boolean isAllowed(String userId) {
    String key = "rate:sliding:" + userId;
    long now = System.currentTimeMillis();
    long windowStart = now - 1000;  // 최근 1초

    // Lua Script로 원자적 처리
    String luaScript = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local window_start = tonumber(ARGV[2])
        local max_requests = tonumber(ARGV[3])
        
        -- 윈도우 밖 요청 제거
        redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
        
        -- 현재 카운트 확인
        local count = redis.call('ZCARD', key)
        if count >= max_requests then
            return 0  -- 거절
        end
        
        -- 현재 요청 추가
        redis.call('ZADD', key, now, now)
        redis.call('EXPIRE', key, 2)
        return 1  -- 허용
        """;

    Long result = redisTemplate.execute(
        new DefaultRedisScript<>(luaScript, Long.class),
        List.of(key),
        String.valueOf(now),
        String.valueOf(windowStart),
        String.valueOf(MAX_REQUESTS)
    );

    return result != null && result == 1L;
}
```

### Token Bucket — 버스트 허용

```java
public boolean tryAcquire(String userId) {
    String key = "token_bucket:" + userId;
    long now = System.currentTimeMillis();

    String luaScript = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local capacity = tonumber(ARGV[2])       -- 버킷 최대 토큰 수
        local refill_rate = tonumber(ARGV[3])    -- 초당 토큰 충전량
        
        local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
        local tokens = tonumber(bucket[1]) or capacity
        local last_refill = tonumber(bucket[2]) or now
        
        -- 경과 시간만큼 토큰 충전
        local elapsed = (now - last_refill) / 1000.0
        tokens = math.min(capacity, tokens + elapsed * refill_rate)
        
        if tokens < 1 then
            return 0  -- 토큰 없음
        end
        
        -- 토큰 소비
        redis.call('HMSET', key, 'tokens', tokens - 1, 'last_refill', now)
        redis.call('EXPIRE', key, 3600)
        return 1
        """;

    Long result = redisTemplate.execute(
        new DefaultRedisScript<>(luaScript, Long.class),
        List.of(key),
        String.valueOf(now),
        "10",   // capacity
        "5"     // 초당 5개 충전
    );

    return result != null && result == 1L;
}
```

### Spring 설정 (Interceptor 등록)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/health", "/api/auth/**");
    }
}
```

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **IP vs UserId** | 비인증 API는 IP 기준, 인증 API는 userId 기준. IP는 공유 IP(NAT) 문제 |
| **엔드포인트별 다른 한도** | `@RateLimit(max=5, window=60)` 커스텀 애노테이션 + AOP |
| **분산 서버 환경** | Redis 중앙화로 해결됨. 로컬 카운터는 서버별로 달라서 부정확 |
| **Rate Limit 초과 응답** | 429 + `Retry-After` 헤더 필수. 클라이언트가 재시도 시점 알 수 있게 |
| **화이트리스트** | 내부 서비스, 어드민 IP는 제외. Redis Set으로 관리 |

---

## 9. 장바구니 데이터 모델링

### 문제 형태

> "비로그인 유저 장바구니와 로그인 유저 장바구니를 어떻게 관리하고 병합할 것인가?"

### 저장소 선택 이유

```
장바구니 특성:
- 읽기/쓰기 매우 빈번 (상품 추가/삭제/수량 변경)
- 데이터 수명이 짧음 (결제하면 소멸)
- 정합성보다 가용성이 중요

→ Redis Hash가 최적
   HSET cart:{userId} {productId} {quantity}
   조회: HGETALL cart:{userId}
   수량 변경: HINCRBY cart:{userId} {productId} 1
   삭제: HDEL cart:{userId} {productId}
```

### 전체 흐름 설계

```
비로그인:                     로그인:
Cookie/Session에              Redis Hash
guest_cart_id 발급   ──▶   cart:{userId}
                    로그인 시
                    병합(Merge)
                              ↓
                          결제 시
                    DB order_items로 이관
                    Redis cart 삭제
```

### 구현

```java
@Service
public class CartService {

    private static final String CART_PREFIX = "cart:";
    private static final Duration CART_TTL = Duration.ofDays(30);

    // 상품 추가
    public void addItem(String cartId, Long productId, int quantity) {
        String key = CART_PREFIX + cartId;
        String field = productId.toString();

        // HINCRBY: 기존 수량에 더함 (없으면 0에서 시작)
        redisTemplate.opsForHash().increment(key, field, quantity);
        redisTemplate.expire(key, CART_TTL);

        // 최대 수량 초과 방지
        Object current = redisTemplate.opsForHash().get(key, field);
        if (Integer.parseInt(current.toString()) > 99) {
            redisTemplate.opsForHash().put(key, field, "99");
        }
    }

    // 비로그인 → 로그인 시 장바구니 병합
    public void mergeCarts(String guestCartId, Long userId) {
        String guestKey = CART_PREFIX + guestCartId;
        String userKey = CART_PREFIX + userId;

        Map<Object, Object> guestCart = redisTemplate.opsForHash().entries(guestKey);
        if (guestCart.isEmpty()) return;

        // 게스트 항목을 유저 장바구니에 병합
        // 동일 상품이 있으면 수량 합산
        guestCart.forEach((productId, qty) -> {
            redisTemplate.opsForHash().increment(
                userKey, productId, Long.parseLong(qty.toString())
            );
        });

        redisTemplate.expire(userKey, CART_TTL);
        redisTemplate.delete(guestKey);  // 게스트 장바구니 삭제
    }

    // 장바구니 → 주문 이관 (결제 시)
    @Transactional
    public Order checkout(Long userId) {
        String key = CART_PREFIX + userId;
        Map<Object, Object> cartItems = redisTemplate.opsForHash().entries(key);

        if (cartItems.isEmpty()) throw new EmptyCartException();

        // 재고 확인 + 주문 생성 (DB 트랜잭션)
        List<OrderItem> items = cartItems.entrySet().stream()
            .map(e -> {
                Long productId = Long.parseLong(e.getKey().toString());
                int qty = Integer.parseInt(e.getValue().toString());
                Product product = productRepository.findByIdWithLock(productId)
                    .orElseThrow();
                if (product.getStock() < qty) throw new InsufficientStockException(productId);
                product.decreaseStock(qty);
                return OrderItem.of(productId, qty, product.getPrice());
            })
            .toList();

        Order order = orderRepository.save(Order.create(userId, items));

        // 결제 성공 후 Redis 장바구니 삭제 (AFTER_COMMIT)
        // 실패 시 장바구니 보존
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.delete(key);
                }
            }
        );

        return order;
    }
}
```

### 스키마 (주문 이관 후 DB)

```sql
CREATE TABLE order_items (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id    BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    quantity    INT NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,  -- 주문 시점 가격 스냅샷 (가격 변경 대비)
    FOREIGN KEY (order_id) REFERENCES orders(id)
);
-- unit_price를 현재가로 참조하면 안 됨 — 나중에 가격 바뀌면 주문 내역이 바뀜
```

### 확장 시 원포인트

| 확장 요구사항 | 고려사항 |
|---|---|
| **Redis 장애 시 장바구니 유실** | Redis AOF 영속성 설정. 또는 로그인 유저는 DB에도 백업 |
| **상품 가격 변경** | 장바구니엔 productId만 저장, 조회 시 최신 가격 반영 (의도적) vs 스냅샷 저장 (가격 보장) — 정책 결정 필요 |
| **품절 상품 처리** | 결제 직전에 재고 확인. 장바구니 조회 시에도 품절 표시 (UX) |
| **장바구니 공유** | 별도 shared_cart 개념 or 장바구니 ID를 URL로 공유 |
| **최대 담기 수량** | 상품별 max_per_order 제한. Redis HGET 후 클라이언트/서버 양쪽 검증 |

---

## 11. 구현 단계 코드 습관 — 면접관이 보는 것

### 1. 동시성 검증 테스트 코드 — 가장 강력한 무기

비관적 락이나 Lua Script를 짰다고 말하면, 면접관은 "그게 진짜 동작하냐"고 물음. 테스트 코드로 증명할 수 있어야 함.

```java
@SpringBootTest
class StockDecrementConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("100명이 동시에 재고 1개 상품을 주문하면 1명만 성공해야 한다")
    void concurrentOrderShouldSucceedOnlyOnce() throws InterruptedException {
        // given
        int threadCount = 100;
        Long productId = createProductWithStock(1);  // 재고 1개

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);   // 동시 출발 신호
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            final Long userId = (long) i;
            executor.submit(() -> {
                try {
                    startLatch.await();  // 모든 스레드가 준비될 때까지 대기
                    orderService.createOrder(userId, productId, 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 동시 출발
        doneLatch.await(30, TimeUnit.SECONDS);

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);  // 음수 아님을 검증

        executor.shutdown();
    }
}
```

**면접에서 이 코드를 쓸 줄 안다는 것 자체가 신호**: "동시성 문제를 이론이 아닌 테스트로 검증하는 습관이 있다"

### 2. DDD 기반 엔티티 — 서비스 레이어에 로직 넣지 않기

```java
// 나쁜 예 — Service에 비즈니스 규칙이 있음
@Service
public class ProductService {
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId).orElseThrow();
        if (product.getStock() < quantity) {    // ← 비즈니스 규칙이 서비스에
            throw new InsufficientStockException();
        }
        product.setStock(product.getStock() - quantity);  // ← setter 남용
    }
}

// 좋은 예 — 비즈니스 규칙이 엔티티 안에
@Entity
public class Product {
    @Column(nullable = false)
    private int stock;

    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new InsufficientStockException(this.id, this.stock, quantity);
        }
        this.stock -= quantity;
    }
    // setter 없음 — 직접 필드 변경 불가
}

@Service
public class ProductService {
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId).orElseThrow();
        product.decreaseStock(quantity);  // 규칙은 엔티티가 알고 있음
    }
}
```

### 3. 트랜잭션 + 이벤트 + Outbox 패턴

```java
// AFTER_COMMIT 이벤트 발행 실패 시 유실 문제 해결
// Outbox 패턴: 이벤트를 같은 트랜잭션으로 DB에 저장 → 별도 릴레이가 발행

@Entity
public class OutboxEvent {
    private String aggregateType;   // "Order"
    private String eventType;       // "OrderCreated"
    private String payload;         // JSON
    private boolean published;      // 발행 완료 여부
    private LocalDateTime createdAt;
}

@Transactional
public Order createOrder(...) {
    Order order = orderRepository.save(...);

    // 같은 트랜잭션으로 Outbox에 저장 → 원자적 보장
    outboxRepository.save(OutboxEvent.of("Order", "OrderCreated", order));

    return order;
    // 트랜잭션 커밋됨 → Relay가 Outbox를 폴링해서 Kafka 발행
}

// Relay (별도 스케줄러 or CDC)
@Scheduled(fixedDelay = 1000)
public void relayOutboxEvents() {
    outboxRepository.findByPublishedFalse().forEach(event -> {
        kafkaTemplate.send(event.getEventType(), event.getPayload());
        event.markPublished();
        outboxRepository.save(event);
    });
}
```

주석으로만 남겨도 됨: `// TODO: Outbox 패턴으로 신뢰성 보장 가능`. 언급 자체가 시니어 시야를 보여줌.

### 4. 글로벌 예외 처리 — 코딩 시작 전 셋업

```java
// 커스텀 예외 계층
public abstract class BusinessException extends RuntimeException {
    private final String errorCode;

    protected BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException() {
        super("ERR_STOCK_001", "재고가 부족합니다.");
    }
}

public class CouponSoldOutException extends BusinessException {
    public CouponSoldOutException() {
        super("ERR_COUPON_001", "쿠폰이 모두 소진되었습니다.");
    }
}

// 전역 핸들러
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("Unexpected error on {}: {}", req.getRequestURI(), e.getMessage(), e);
        return ResponseEntity
            .internalServerError()
            .body(new ErrorResponse("ERR_INTERNAL", "서버 오류가 발생했습니다."));
    }
}

// 응답 형식
public record ErrorResponse(String code, String message) {}
// {"code": "ERR_STOCK_001", "message": "재고가 부족합니다."}
```

**라이브코딩 시작 5분 안에 이 구조를 먼저 만들어두면** 이후 구현에서 throw만 하면 됨. 면접관 눈에 "처음부터 설계하는 사람"으로 보임.

### 5. 라이브코딩 진행 순서 템플릿

```
1단계 (2분): 요구사항 확인 + 엣지 케이스 질문
  - "동시 요청 고려해야 하나요?"
  - "실패 시 재시도 필요한가요?"

2단계 (3분): 스키마 설계 + ERD 설명
  - 테이블, 컬럼, UNIQUE 제약, 인덱스

3단계 (5분): 글로벌 예외처리 + 엔티티 골격 작성

4단계 (20분): 핵심 로직 구현
  - 동시성 이슈 있으면 락/Redis 언급하며 구현

5단계 (5분): 테스트 코드 or 확장 방향 제시
  - CountDownLatch 테스트 or Outbox 패턴 언급
```

---

## 면접 직전 30분 복습 순서

1. 재고 차감 Lua Script 손으로 써보기
2. 주문 상태 전이 `Map<OrderStatus, Set<OrderStatus>>` 구조 기억하기
3. `@TransactionalEventListener(AFTER_COMMIT)` → 실패 시 Outbox 패턴 한 줄 언급
4. PG 연동 트랜잭션 원칙: "PG 호출은 반드시 트랜잭션 밖"
5. CountDownLatch 동시성 테스트 구조 머릿속에 그리기
6. 라이브코딩 시작 5분 — GlobalExceptionHandler + 커스텀 예외 먼저 만들기
7. "이 시스템 확장한다면?" → 동시성 → 캐시 → 비동기 → 장애격리 순서로 말하는 연습
