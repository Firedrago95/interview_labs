package com.example.demo.parking;

public class ParkingSpot {

    private Type allowedType;
    private Car car;

    public ParkingSpot(Type allowType) {
        this.allowedType = allowType;
    }

    public void enterCar(Car car) {
        if (this.allowedType != car.getType()) {
            throw new IllegalArgumentException("이 주차칸은 %s 차량만 주차가능합니다.".formatted(allowedType.toString()));
        }
        if (this.car != null) {
            throw new IllegalArgumentException("이미 주차된 차량이 있습니다.");
        }
        this.car = car;
    }

    public void leaveCar() {
        if (this.car == null) {
            throw new IllegalArgumentException("이미 빈 주차장입니다.");
        }
        this.car = null;
    }
}
