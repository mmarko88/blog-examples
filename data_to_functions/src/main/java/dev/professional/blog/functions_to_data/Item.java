package dev.professional.blog.functions_to_data;

import java.math.BigDecimal;

public record Item(String sku, int quantity, BigDecimal price) {
    public Item withPrice(BigDecimal newPrice) {
        return new Item(sku, quantity, newPrice);
    }
}
