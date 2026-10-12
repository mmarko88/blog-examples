package dev.professional.blog.functions_to_data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OrderFunctions {

    // Function that works with any ItemContainer
    public static BigDecimal calculateTotal(ItemContainer container) {
        return container.items().stream()
            .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Validate order
    public static Order validateOrder(Order order) {
        if (order.customer() == null || order.items().isEmpty()) {
            throw new IllegalArgumentException("Invalid order");
        }
        return new Order(
            order.id(),
            order.customer(),
            order.items(),
            OrderStatus.VALIDATED
        );
    }

    // Apply discount
    public static Order applyDiscount(Order order, BigDecimal discountRate) {
        if (order.status() != OrderStatus.VALIDATED) {
            throw new IllegalStateException("Order must be validated first");
        }
        
        List<Item> discountedItems = order.items().stream()
            .map(item -> item.withPrice(item.price().multiply(discountRate)))
            .toList();
        
        return new Order(
            order.id(),
            order.customer(),
            discountedItems,
            OrderStatus.DISCOUNTED
        );
    }

    // Calculate tax
    public static Order calculateTax(Order order, BigDecimal taxRate) {
        if (order.status() != OrderStatus.DISCOUNTED) {
            throw new IllegalStateException("Order must be discounted first");
        }
        
        BigDecimal total = order.items().stream()
            .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal tax = total.multiply(taxRate);
        
        // Add tax as a special item
        Item taxItem = new Item("TAX", 1, tax);
        List<Item> itemsWithTax = new ArrayList<>(order.items());
        itemsWithTax.add(taxItem);
        
        return new Order(
            order.id(),
            order.customer(),
            itemsWithTax,
            OrderStatus.TAXED
        );
    }

    // Complete order
    public static Order completeOrder(Order order) {
        if (order.status() != OrderStatus.TAXED) {
            throw new IllegalStateException("Order must be taxed first");
        }
        
        return new Order(
            order.id(),
            order.customer(),
            order.items(),
            OrderStatus.COMPLETED
        );
    }

    // Process order pipeline
    public static Order processOrder(Order order, BigDecimal discountRate, BigDecimal taxRate) {
        Order validated = validateOrder(order);
        Order discounted = applyDiscount(validated, discountRate);
        Order taxed = calculateTax(discounted, taxRate);
        return completeOrder(taxed);
    }
}
