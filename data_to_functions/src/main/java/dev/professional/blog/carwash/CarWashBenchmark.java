package dev.professional.blog.carwash;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CarWashBenchmark {

    private Owner owner;
    private Car car;
    private Car inspectedCar;

    @Setup
    public void setup() {
        owner = new Owner("O-1", "John Doe");
        car = new Car("ABC-123", owner, 9);
        inspectedCar = new Car("ABC-123", owner, 9, WashStatus.INSPECTED);
    }

    @Benchmark
    public void benchmarkFunctionalQuoteWash(Blackhole bh) {
        BigDecimal price = CarWashFunctions.quoteWash(car);
        bh.consume(price);
    }

    @Benchmark
    public void benchmarkMutableQuoteWash(Blackhole bh) {
        MutableCar freshCar = new MutableCar("ABC-123", owner, 9);
        BigDecimal price = freshCar.quoteWash();
        bh.consume(price);
    }

    @Benchmark
    public Car benchmarkFunctionalInspect() {
        return CarWashFunctions.inspect(car);
    }

    @Benchmark
    public MutableCar benchmarkMutableInspect() {
        MutableCar freshCar = new MutableCar("ABC-123", owner, 9);
        freshCar.inspect();
        return freshCar;
    }

    @Benchmark
    public Car benchmarkFunctionalScrub() {
        return CarWashFunctions.scrub(inspectedCar);
    }

    @Benchmark
    public MutableCar benchmarkMutableScrub() {
        MutableCar freshCar = new MutableCar("ABC-123", owner, 9, WashStatus.INSPECTED);
        freshCar.scrub();
        return freshCar;
    }

    @Benchmark
    public Car benchmarkFunctionalRunWashCycle() {
        return CarWashFunctions.runWashCycle(car);
    }

    @Benchmark
    public MutableCar benchmarkMutableRunWashCycle() {
        MutableCar freshCar = new MutableCar("ABC-123", owner, 9);
        freshCar.runWashCycle();
        return freshCar;
    }
}
