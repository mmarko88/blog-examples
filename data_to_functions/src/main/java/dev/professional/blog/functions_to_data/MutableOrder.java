package dev.professional.blog.functions_to_data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MutableOrder implements ItemContainer {
    private String id;
    private Customer customer;
    private List<Item> items;
    private OrderStatus status;

    public MutableOrder(String id, Customer customer, List<Item> items) {
        this.id = id;
        this.customer = customer;
        this.items = new ArrayList<>(items);
        this.status = OrderStatus.PENDING;
    }

    public String id() { return id; }
    public Customer customer() { return customer; }
    public List<Item> items() { return items; }
    public OrderStatus status() { return status; }

    public void validate() {
        if (customer == null || items.isEmpty()) {
            throw new IllegalArgumentException("Invalid order");
        }
        this.status = OrderStatus.VALIDATED;
    }

    public void applyDiscount(BigDecimal discountRate) {
        if (status != OrderStatus.VALIDATED) {
            throw new IllegalStateException("Order must be validated first");
        }
        
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            items.set(i, item.withPrice(item.price().multiply(discountRate)));
        }
        this.status = OrderStatus.DISCOUNTED;
    }

    public void calculateTax(BigDecimal taxRate) {
        if (status != OrderStatus.DISCOUNTED) {
            throw new IllegalStateException("Order must be discounted first");
        }
        
        BigDecimal total = items.stream()
            .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal tax = total.multiply(taxRate);
        items.add(new Item("TAX", 1, tax));
        this.status = OrderStatus.TAXED;
    }

    public void complete() {
        if (status != OrderStatus.TAXED) {
            throw new IllegalStateException("Order must be taxed first");
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void process(BigDecimal discountRate, BigDecimal taxRate) {
        validate();
        applyDiscount(discountRate);
        calculateTax(taxRate);
        complete();
    }
}
