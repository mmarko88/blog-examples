package dev.professional.blog.functions_to_data;

import java.util.List;

public record Order(String id, Customer customer, List<Item> items, OrderStatus status) implements ItemContainer {
    public Order(String id, Customer customer, List<Item> items) {
        this(id, customer, items, OrderStatus.PENDING);
    }
}
