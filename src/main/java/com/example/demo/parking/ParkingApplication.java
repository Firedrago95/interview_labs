package com.example.demo.parking;

import java.util.Map;

public class ParkingApplication {
    public static void main(String[] args) {
        // 1. 주차장 오픈 (소형 2대, 중형 1대, 대형 1대)
        ParkingLot parkingLot = new ParkingLot(Map.of(
                Type.SMALL, 2,
                Type.MEDIUM, 1,
                Type.LARGE, 1
        ));

        System.out.println("=== ☀️ 주차장 오픈 ===");
        parkingLot.printAvailableSpots();

        // 2. 차량 도착
        Car car1 = new Car("12가1234", Type.SMALL);
        Car car2 = new Car("34나5678", Type.LARGE);
        Car car3 = new Car("56다9012", Type.SMALL);

        // 3. 입차 진행
        System.out.println("\n=== 🚗 입차 진행 ===");
        Ticket ticket1 = parkingLot.enterLot(car1);

        Ticket ticket2 = parkingLot.enterLot(car2);

        Ticket ticket3 = parkingLot.enterLot(car3);

        // 4. 입차 후 현황
        System.out.println("\n=== 📊 입차 후 주차장 현황 ===");
        parkingLot.printAvailableSpots();

        // 5. 출차 진행
        System.out.println("\n=== 💨 출차 진행 ===");
        Long fee1 = parkingLot.exitLot(ticket1);

        // 6. 출차 후 현황
        System.out.println("\n=== 📊 출차 후 주차장 현황 ===");
        parkingLot.printAvailableSpots();
        
        // 주의: Type.java의 calculatePrice 로직상 (현재시간 - 현재시간) = 0분 이지만,
        // Math.ceil 로직 때문에 0 / 60.0 올림으로 0원이 될 수 있습니다.
        // 테스트 시 Thread.sleep()을 주거나, 요금 계산 시 최소 1시간 부과 등 예외처리가 필요할 수 있습니다.
    }
}
