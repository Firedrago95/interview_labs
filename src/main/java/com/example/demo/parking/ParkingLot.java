package com.example.demo.parking;

import java.util.List;
import java.util.Map;
import java.util.Queue;

public class ParkingLot {

    private final Map<String, ParkingSpot> parkedSpots;
    private final Map<Type, Queue<ParkingSpot>> availableSpots;

    public ParkingLot(Map<String, ParkingSpot> parkedSpots, Map<Type, Queue<ParkingSpot>> availableSpots) {
        this.parkedSpots = parkedSpots;
        this.availableSpots = availableSpots;
    }
}
