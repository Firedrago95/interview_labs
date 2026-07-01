package com.example.demo.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderService(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    // 1. 락 없이 재고를 차감하는 메서드 (Race Condition 발생)
    @Transactional
    public Long orderWithoutLock(Long productId, Long userId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않습니다."));

        product.decreaseStock(quantity);

        Order order = new Order(productId, userId, quantity);
        orderRepository.save(order);

        return order.getId();
    }

    // 2. 비관적 락을 적용하여 재고를 차감하는 메서드 (동시성 제어 성공)
    @Transactional
    public Long orderWithPessimisticLock(Long productId, Long userId, int quantity) {
        // 비관적 락을 통해 조회 시점에 row-level lock (SELECT FOR UPDATE) 을 획득
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않습니다."));

        product.decreaseStock(quantity);

        Order order = new Order(productId, userId, quantity);
        orderRepository.save(order);

        return order.getId();
    }
}
