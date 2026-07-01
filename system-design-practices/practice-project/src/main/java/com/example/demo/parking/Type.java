package com.example.demo.parking;

import java.time.Duration;
import java.time.LocalDateTime;

public enum Type {

    SMALL(1_000),
    MEDIUM(2_000),
    LARGE(3_000);

    private final int pricePerHour;

    Type(int pricePerHour) {
        this.pricePerHour = pricePerHour;
    }

    public Long calculatePrice(LocalDateTime entryTime, LocalDateTime exitTime) {
        Duration duration = Duration.between(entryTime, exitTime);
        long minutes = duration.toMinutes();
        double hours = Math.ceil((double) minutes / 60.0);
        return (long) (hours * this.pricePerHour);
    }
}
