package com.example.demo.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "orders") // order는 SQL 예약어이므로 보통 orders로 매핑
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    private Long userId;

    private int quantity;

    protected Order() {}

    public Order(Long productId, Long userId, int quantity) {
        this.productId = productId;
        this.userId = userId;
        this.quantity = quantity;
    }
}
