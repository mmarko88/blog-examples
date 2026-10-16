package dev.professional.blog.carwash;

import java.math.BigDecimal;
import java.util.function.Function;

public class CarWashFunctions {

    // Function that works with any Washable
    public static BigDecimal quoteWash(Washable vehicle) {
        return BigDecimal.valueOf(8).add(BigDecimal.valueOf(vehicle.dirtLevel() * 2L));
    }

    public static Car inspect(Car car) {
        if (car.plate() == null || car.dirtLevel() == 0) {
            throw new IllegalArgumentException("Nothing to wash");
        }
        return new Car(
            car.plate(),
            car.owner(),
            car.dirtLevel(),
            WashStatus.INSPECTED
        );
    }

    public static Car scrub(Car car) {
        if (car.status() != WashStatus.INSPECTED) {
            throw new IllegalStateException("Car must be inspected first");
        }
        return new Car(
            car.plate(),
            car.owner(),
            Math.max(0, car.dirtLevel() - 4),
            WashStatus.SCRUBBED
        );
    }

    public static Car wax(Car car) {
        if (car.status() != WashStatus.SCRUBBED) {
            throw new IllegalStateException("Car must be scrubbed first");
        }
        return new Car(
            car.plate(),
            car.owner(),
            Math.max(0, car.dirtLevel() - 2),
            WashStatus.WAXED
        );
    }

    public static Car dry(Car car) {
        if (car.status() != WashStatus.WAXED) {
            throw new IllegalStateException("Car must be waxed first");
        }
        return new Car(
            car.plate(),
            car.owner(),
            car.dirtLevel(),
            WashStatus.DRIED
        );
    }

    // Wash cycle pipeline: compose small functions with andThen
    public static Car runWashCycle(Car car) {
        if (car == null) {
            throw new IllegalArgumentException("Car must not be null");
        }
        Function<Car, Car> washCycle = ((Function<Car, Car>) CarWashFunctions::inspect)
            .andThen(CarWashFunctions::scrub)
            .andThen(CarWashFunctions::wax)
            .andThen(CarWashFunctions::dry);
        return washCycle.apply(car);
    }
}
