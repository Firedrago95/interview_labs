package com.example.demo.parking;

public enum Type {

    SMALL(1_000),
    MEDIUM(2_000),
    LARGE(3_000);

    private final int pricePerHour;

    Type(int pricePerHour) {
        this.pricePerHour = pricePerHour;
    }
}
