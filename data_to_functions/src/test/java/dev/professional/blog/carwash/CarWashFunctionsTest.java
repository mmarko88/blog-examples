package dev.professional.blog.carwash;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CarWashFunctionsTest {

    private final Owner owner = new Owner("O-1", "John");

    @Test
    void scrubReducesDirt() {
        Car car = new Car("ABC-123", owner, 9, WashStatus.INSPECTED);

        Car result = CarWashFunctions.scrub(car);

        assertEquals(5, result.dirtLevel());
        assertEquals(9, car.dirtLevel()); // original untouched
    }

    @Test
    void quoteWashScalesWithDirt() {
        Car car = new Car("ABC-123", owner, 9);

        BigDecimal price = CarWashFunctions.quoteWash(car);

        assertEquals(0, new BigDecimal("26").compareTo(price));
    }

    @Test
    void quoteWashWorksForAnyWashable() {
        Car car = new Car("ABC-123", owner, 9);
        Truck truck = new Truck("TRK-77", 3, 12);
        Motorcycle bike = new Motorcycle("BIK-42", 7);

        assertEquals(0, new BigDecimal("26").compareTo(CarWashFunctions.quoteWash(car)));
        assertEquals(0, new BigDecimal("32").compareTo(CarWashFunctions.quoteWash(truck)));
        assertEquals(0, new BigDecimal("22").compareTo(CarWashFunctions.quoteWash(bike)));
    }

    @Test
    void inspectRejectsCleanCar() {
        Car car = new Car("ABC-123", owner, 0);

        assertThrows(IllegalArgumentException.class, () -> CarWashFunctions.inspect(car));
    }

    @Test
    void scrubRequiresInspection() {
        Car car = new Car("ABC-123", owner, 9); // still DIRTY

        assertThrows(IllegalStateException.class, () -> CarWashFunctions.scrub(car));
    }

    @Test
    void runWashCycleWalksTheStatusMachine() {
        Car car = new Car("ABC-123", owner, 9);

        Car washed = CarWashFunctions.runWashCycle(car);

        assertEquals(WashStatus.DRIED, washed.status());
        assertEquals(3, washed.dirtLevel()); // 9 - 4 (scrub) - 2 (wax)
        // Original untouched
        assertEquals(WashStatus.DIRTY, car.status());
        assertEquals(9, car.dirtLevel());
    }

    @Test
    void runWashCycleRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> CarWashFunctions.runWashCycle(null));
    }

    @Test
    void runWashCycleRejectsCleanCar() {
        Car car = new Car("ABC-123", owner, 0);

        assertThrows(IllegalArgumentException.class, () -> CarWashFunctions.runWashCycle(car));
    }
}
