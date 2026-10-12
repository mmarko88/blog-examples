package dev.professional.blog.functions_to_data;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OrderBenchmark {

    private Customer customer;
    private List<Item> items;
    private Order order;
    private MutableOrder mutableOrder;
    private BigDecimal discountRate;
    private BigDecimal taxRate;

    @Setup
    public void setup() {
        customer = new Customer("CUST-001", "John Doe");
        items = List.of(
            new Item("SKU-001", 2, new BigDecimal("50.00")),
            new Item("SKU-002", 1, new BigDecimal("30.00")),
            new Item("SKU-003", 3, new BigDecimal("20.00"))
        );
        order = new Order("ORD-001", customer, items);
        mutableOrder = new MutableOrder("ORD-001", customer, items);
        discountRate = new BigDecimal("0.9");
        taxRate = new BigDecimal("0.08");
    }

    @Benchmark
    public void benchmarkFunctionalCalculateTotal(Blackhole bh) {
        BigDecimal total = OrderFunctions.calculateTotal(order);
        bh.consume(total);
    }

    @Benchmark
    public void benchmarkMutableCalculateTotal(Blackhole bh) {
        BigDecimal total = OrderFunctions.calculateTotal(mutableOrder);
        bh.consume(total);
    }

    @Benchmark
    public Order benchmarkFunctionalProcessOrder() {
        return OrderFunctions.processOrder(order, discountRate, taxRate);
    }

    @Benchmark
    public void benchmarkMutableProcessOrder() {
        MutableOrder freshOrder = new MutableOrder("ORD-001", customer, items);
        freshOrder.process(discountRate, taxRate);
    }

    @Benchmark
    public Order benchmarkFunctionalValidate() {
        return OrderFunctions.validateOrder(order);
    }

    @Benchmark
    public void benchmarkMutableValidate() {
        MutableOrder freshOrder = new MutableOrder("ORD-001", customer, items);
        freshOrder.validate();
    }

    @Benchmark
    public Order benchmarkFunctionalApplyDiscount() {
        Order validated = OrderFunctions.validateOrder(order);
        return OrderFunctions.applyDiscount(validated, discountRate);
    }

    @Benchmark
    public void benchmarkMutableApplyDiscount() {
        MutableOrder freshOrder = new MutableOrder("ORD-001", customer, items);
        freshOrder.validate();
        freshOrder.applyDiscount(discountRate);
    }
}
