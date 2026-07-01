package com.example.demo.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
@Getter
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private int stock;

    // JPA 기본 생성자
    protected Product() {}

    public Product(String name, int stock) {
        this.name = name;
        this.stock = stock;
    }

    // 도메인 주도 설계 (객체 상태 변경은 객체 안에서)
    public void decreaseStock(int quantity) {
        if (this.stock - quantity < 0) {
            throw new IllegalArgumentException("재고가 부족합니다.");
        }
        this.stock -= quantity;
    }
}
