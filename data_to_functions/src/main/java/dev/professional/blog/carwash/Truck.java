package dev.professional.blog.carwash;

public record Truck(String plate, int axles, int dirtLevel) implements Washable {
}
