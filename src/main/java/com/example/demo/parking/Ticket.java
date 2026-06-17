package com.example.demo.parking;

import java.time.LocalDateTime;

public class Ticket {

    private final Car car;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private Long price;

    public Ticket(Car car, LocalDateTime entryTime) {
        this.car = car;
        this.entryTime = entryTime;
    }

    public Long calculatePrice(LocalDateTime exitTime) {
        Long price = car.calculatePrice(entryTime, exitTime);
        this.price = price;
        return price;
    }

    public Car getCar() {
        return car;
    }
}
