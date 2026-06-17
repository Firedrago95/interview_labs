package com.example.demo.parking;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

public class ParkingLot {

    private final Map<String, ParkingSpot> parkedSpots;
    private final Map<Type, Queue<ParkingSpot>> availableSpots;

    public ParkingLot(Map<Type, Integer> limitSpots) {
        parkedSpots = new HashMap<>();
        availableSpots = new HashMap<>();

        for (Entry<Type, Integer> entry : limitSpots.entrySet()) {
            Type type = entry.getKey();
            Integer count = entry.getValue();

            Queue<ParkingSpot> parkingSpots = new LinkedList<>();
            for (int i = 0; i < count; i++) {
                parkingSpots.add(new ParkingSpot(type));
            }
            availableSpots.put(type, parkingSpots);
        }
    }

    public Ticket enterLot(Car car) {
        if (!checkEmptySpot(car)) {
            throw new IllegalStateException("해당 차량이 주차할 공간이 없습니다.");
        }

        ParkingSpot emptySpot = availableSpots.get(car.getType()).poll();
        emptySpot.enterCar(car);
        parkedSpots.put(car.getLicensePlate(), emptySpot);
        System.out.printf("%s %s 차량 주차%n", car.getLicensePlate(), car.getLicensePlate());
        return new Ticket(car, LocalDateTime.now());
    }

    public Long exitLot(Ticket ticket) {
        Long price = ticket.calculatePrice(LocalDateTime.now());

        Car car = ticket.getCar();
        String licensePlate = car.getLicensePlate();
        Type type = car.getType();

        if (!parkedSpots.containsKey(licensePlate)) {
            throw new IllegalStateException("주차되어 있지 않은 차량입니다.");
        }

        ParkingSpot removedSpot = parkedSpots.remove(licensePlate);
        removedSpot.leaveCar();
        availableSpots.get(type).add(removedSpot);
        System.out.printf("%s %s 차량 출차완료 요금 : %d %n", car.getLicensePlate(), car.getLicensePlate(), price);
        return price;
    }

    public String printAvailableSpots() {
        StringBuilder sb = new StringBuilder();

        for (Entry<Type, Queue<ParkingSpot>> e : availableSpots.entrySet()) {
            Type type = e.getKey();
            Queue<ParkingSpot> slots = e.getValue();
            sb.append(String.format("%s : %d대 주차가능\n", e.getKey(), e.getValue().size()));
        }

        String message = sb.toString();
        System.out.println(message);
        return message;
    }

    private boolean checkEmptySpot(Car car) {
        Queue<ParkingSpot> parkingSpots = availableSpots.get(car.getType());
        return !parkingSpots.isEmpty();
    }
}
