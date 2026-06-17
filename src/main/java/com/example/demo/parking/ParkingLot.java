package com.example.demo.parking;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class ParkingLot {

    private final Map<String, ParkingSpot> parkedSpots;
    private final Map<Type, Queue<ParkingSpot>> availableSpots;

    public ParkingLot(Map<Type, Integer> limitSpots) {
        parkedSpots = new HashMap<>();
        availableSpots = new HashMap<>();

        for (Map.Entry<Type, Integer> entry : limitSpots.entrySet()) {
            Type type = entry.getKey();
            Integer count = entry.getValue();

            Queue<ParkingSpot> parkingSpots = new LinkedList<>();
            for (int i = 0; i < count; i++) {
                parkingSpots.add(new ParkingSpot(type));
            }
            availableSpots.put(type, parkingSpots);
        }
    }

    public void enterLot(Car car) {
        if (!checkEmptySpot(car)) {
            throw new IllegalStateException("해당 차량이 주차할 공간이 없습니다.");
        }

        ParkingSpot emptySpot = availableSpots.get(car.getType()).poll();
        emptySpot.enterCar(car);
        parkedSpots.put(car.getLicensePlate(), emptySpot);
    }

    private boolean checkEmptySpot(Car car) {
        Queue<ParkingSpot> parkingSpots = availableSpots.get(car.getType());
        return !parkingSpots.isEmpty();
    }
}
