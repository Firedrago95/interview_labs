package com.example.demo.parking;

import java.time.LocalDateTime;

public class Car {

    private final String licensePlate;
    private final Type type;

    public Car(String licensePlate, Type type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public Long calcualtePrice(LocalDateTime entryTime, LocalDateTime exitTime) {
        return type.calculatePrice(entryTime, exitTime);
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public Type getType() {
        return type;
    }
}
