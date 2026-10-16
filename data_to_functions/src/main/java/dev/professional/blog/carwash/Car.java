package dev.professional.blog.carwash;

public record Car(String plate, Owner owner, int dirtLevel, WashStatus status) implements Washable {

    // Convenience: a car rolling in off the street starts DIRTY
    public Car(String plate, Owner owner, int dirtLevel) {
        this(plate, owner, dirtLevel, WashStatus.DIRTY);
    }
}
