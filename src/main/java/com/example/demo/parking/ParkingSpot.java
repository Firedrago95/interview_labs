package com.example.demo.parking;

public class ParkingSpot {

    private Type allowType;
    private Car car;

    public ParkingSpot(Type allowType) {
        this.allowType = allowType;
    }

    public void enterCar(Car car) {
        if (this.car != null) {
            throw new IllegalStateException("이미 주차된 차량이 있습니다.");
        }
        this.car = car;
    }
}
