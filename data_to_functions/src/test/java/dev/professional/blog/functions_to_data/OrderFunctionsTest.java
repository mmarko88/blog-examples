package dev.professional.blog.functions_to_data;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderFunctionsTest {

    @Test
    void testCalculateTotal() {
        Customer customer = new Customer("CUST-001", "John");
        List<Item> items = List.of(
            new Item("SKU-001", 2, new BigDecimal("10.00")),
            new Item("SKU-002", 1, new BigDecimal("20.00"))
        );
        Order order = new Order("ORD-001", customer, items);
        
        BigDecimal total = OrderFunctions.calculateTotal(order);
        assertEquals(0, new BigDecimal("40.00").compareTo(total));
    }

    @Test
    void testValidateOrder() {
        Customer customer = new Customer("CUST-001", "John");
        List<Item> items = List.of(new Item("SKU-001", 1, new BigDecimal("10.00")));
        Order order = new Order("ORD-001", customer, items);
        
        Order validated = OrderFunctions.validateOrder(order);
        assertEquals(OrderStatus.VALIDATED, validated.status());
    }

    @Test
    void testValidateOrderWithEmptyItems() {
        Customer customer = new Customer("CUST-001", "John");
        Order order = new Order("ORD-001", customer, List.of());
        
        assertThrows(IllegalArgumentException.class, () -> {
            OrderFunctions.validateOrder(order);
        });
    }

    @Test
    void testApplyDiscount() {
        Customer customer = new Customer("CUST-001", "John");
        List<Item> items = List.of(new Item("SKU-001", 1, new BigDecimal("100.00")));
        Order order = new Order("ORD-001", customer, items, OrderStatus.VALIDATED);
        
        Order discounted = OrderFunctions.applyDiscount(order, new BigDecimal("0.9"));
        assertEquals(OrderStatus.DISCOUNTED, discounted.status());
        assertEquals(0, new BigDecimal("90.00").compareTo(discounted.items().get(0).price()));
    }

    @Test
    void testApplyDiscountWithoutValidation() {
        Customer customer = new Customer("CUST-001", "John");
        List<Item> items = List.of(new Item("SKU-001", 1, new BigDecimal("100.00")));
        Order order = new Order("ORD-001", customer, items, OrderStatus.PENDING);
        
        assertThrows(IllegalStateException.class, () -> {
            OrderFunctions.applyDiscount(order, new BigDecimal("0.9"));
        });
    }

    @Test
    void testOriginalOrderUnchanged() {
        Customer customer = new Customer("CUST-001", "John");
        List<Item> items = List.of(new Item("SKU-001", 1, new BigDecimal("100.00")));
        Order order = new Order("ORD-001", customer, items, OrderStatus.VALIDATED);
        
        Order discounted = OrderFunctions.applyDiscount(order, new BigDecimal("0.9"));
        
        assertEquals(OrderStatus.VALIDATED, order.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(order.items().get(0).price()));
        assertEquals(OrderStatus.DISCOUNTED, discounted.status());
        assertEquals(0, new BigDecimal("90.00").compareTo(discounted.items().get(0).price()));
    }

    @Test
    void testFunctionReusabilityWithDifferentTypes() {
        // Test that calculateTotal works with Order
        Customer customer = new Customer("CUST-001", "John");
        List<Item> items = List.of(
            new Item("SKU-001", 2, new BigDecimal("10.00")),
            new Item("SKU-002", 1, new BigDecimal("20.00"))
        );
        Order order = new Order("ORD-001", customer, items);
        BigDecimal orderTotal = OrderFunctions.calculateTotal(order);
        assertEquals(0, new BigDecimal("40.00").compareTo(orderTotal));

        // Test that the same function works with Invoice
        Invoice invoice = new Invoice("INV-001", "John Doe", items);
        BigDecimal invoiceTotal = OrderFunctions.calculateTotal(invoice);
        assertEquals(0, new BigDecimal("40.00").compareTo(invoiceTotal));

        // Both types return the same total for the same items
        assertEquals(0, orderTotal.compareTo(invoiceTotal));
    }

    @Test
    void testFunctionComposition() {
        Customer customer = new Customer("CUST-001", "John");
        List<Item> items = List.of(
            new Item("SKU-001", 2, new BigDecimal("50.00")),
            new Item("SKU-002", 1, new BigDecimal("30.00"))
        );
        Order order = new Order("ORD-001", customer, items);

        BigDecimal discountRate = new BigDecimal("0.9"); // 10% discount
        BigDecimal taxRate = new BigDecimal("0.08"); // 8% tax

        // Process order through the composed pipeline
        Order processed = OrderFunctions.processOrder(order, discountRate, taxRate);

        // Verify the pipeline worked correctly
        assertEquals(OrderStatus.COMPLETED, processed.status());
        assertEquals(3, processed.items().size()); // Original 2 items + tax item

        // Original order should be unchanged
        assertEquals(OrderStatus.PENDING, order.status());
        assertEquals(2, order.items().size());
    }
}
