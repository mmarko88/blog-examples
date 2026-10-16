package dev.professional.blog.carwash;

import java.math.BigDecimal;

public class Main {
    public static void main(String[] args) {
        Owner owner = new Owner("O-1", "John Doe");
        Car car = new Car("ABC-123", owner, 9);

        System.out.println("=== Car rolls in ===");
        System.out.println("Plate: " + car.plate());
        System.out.println("Status: " + car.status());
        System.out.println("Dirt level: " + car.dirtLevel());
        System.out.println("Quote: $" + CarWashFunctions.quoteWash(car));

        System.out.println("\n=== Running wash cycle ===");

        Car washed = CarWashFunctions.runWashCycle(car);

        System.out.println("Final status: " + washed.status());
        System.out.println("Final dirt level: " + washed.dirtLevel());

        System.out.println("\n=== Original car is unchanged ===");
        System.out.println("Original status: " + car.status());
        System.out.println("Original dirt level: " + car.dirtLevel());

        // The same function works for any Washable
        Truck truck = new Truck("TRK-77", 3, 12);
        Motorcycle bike = new Motorcycle("BIK-42", 7);
        System.out.println("\nTruck quote: $" + CarWashFunctions.quoteWash(truck));
        System.out.println("Motorcycle quote: $" + CarWashFunctions.quoteWash(bike));
    }
}
