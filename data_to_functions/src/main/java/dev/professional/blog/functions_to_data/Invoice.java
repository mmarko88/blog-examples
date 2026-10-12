package dev.professional.blog.functions_to_data;

import java.util.List;

public record Invoice(String invoiceNumber, String customerName, List<Item> items) implements ItemContainer {
    public Invoice(String invoiceNumber, String customerName, List<Item> items) {
        this.invoiceNumber = invoiceNumber;
        this.customerName = customerName;
        this.items = items;
    }
}
