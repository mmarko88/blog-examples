package dev.professional.blog.functions_to_data;

import java.math.BigDecimal;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Create sample data
        Customer customer = new Customer("CUST-001", "John Doe");
        List<Item> items = List.of(
            new Item("SKU-001", 2, new BigDecimal("50.00")),
            new Item("SKU-002", 1, new BigDecimal("30.00"))
        );
        
        Order order = new Order("ORD-001", customer, items);
        
        System.out.println("=== Original Order ===");
        System.out.println("Order ID: " + order.id());
        System.out.println("Customer: " + order.customer().name());
        System.out.println("Status: " + order.status());
        System.out.println("Items: " + order.items().size());
        System.out.println("Total: $" + OrderFunctions.calculateTotal(order));
        
        System.out.println("\n=== Processing Order ===");
        
        // Process the order through the pipeline
        BigDecimal discountRate = new BigDecimal("0.9"); // 10% discount
        BigDecimal taxRate = new BigDecimal("0.08"); // 8% tax
        
        Order processedOrder = OrderFunctions.processOrder(order, discountRate, taxRate);
        
        System.out.println("Final Status: " + processedOrder.status());
        System.out.println("Final Items: " + processedOrder.items().size());
        System.out.println("Final Total: $" + OrderFunctions.calculateTotal(processedOrder));
        
        System.out.println("\n=== Original Order is unchanged ===");
        System.out.println("Original Status: " + order.status());
        System.out.println("Original Items: " + order.items().size());
    }
}
