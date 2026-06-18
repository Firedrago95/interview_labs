package com.example.demo.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        // 재고가 100개인 상품을 미리 DB에 세팅
        product = new Product("재고 차감 테스트 상품", 100);
        productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("락 없이 동시에 100건 주문 시 - Race Condition 발생으로 재고가 0이 되지 않음 (실패 케이스 확인용)")
    void orderWithoutLock_concurrency() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    orderService.orderWithoutLock(product.getId(), 1L, 1);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Product findProduct = productRepository.findById(product.getId()).orElseThrow();
        // 100개가 모두 차감되지 않고 갱신 손실이 발생함을 눈으로 확인하기 위함
        System.out.println("락 미적용 시 남은 재고: " + findProduct.getStock());
        assertThat(findProduct.getStock()).isNotEqualTo(0);
    }

    @Test
    @DisplayName("비관적 락을 적용하여 동시에 100건 주문 시 - 재고가 정확히 0이 됨")
    void orderWithPessimisticLock_concurrency() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    orderService.orderWithPessimisticLock(product.getId(), 1L, 1);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Product findProduct = productRepository.findById(product.getId()).orElseThrow();
        System.out.println("비관적 락 적용 시 남은 재고: " + findProduct.getStock());
        // 100개의 스레드가 순차적으로 락을 획득하며 차감했으므로 재고는 0이어야 함
        assertThat(findProduct.getStock()).isEqualTo(0);
    }
}
